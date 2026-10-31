package com.explapp.shortcut.tools

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

object ScreenCaptureHelper {
    fun captureOnce(activity: Activity, resultCode: Int, data: Intent, onResult: (Bitmap?) -> Unit) {
        if (resultCode != RESULT_OK) {
            onResult(null)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val workerThread = HandlerThread("ShortcutCaptureHelper").apply { start() }
        val worker = Handler(workerThread.looper)

        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            workerThread.quitSafely()
            onResult(null)
            return
        }

        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = projection.createVirtualDisplay(
            "ShortcutScreenshot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            worker,
        )

        if (display == null) {
            reader.close()
            projection.stop()
            workerThread.quitSafely()
            onResult(null)
            return
        }

        var finished = false

        fun finish(bitmap: Bitmap?) {
            if (finished) {
                bitmap?.recycle()
                return
            }
            finished = true
            runCatching { display.release() }
            runCatching { reader.close() }
            runCatching { projection.stop() }
            mainHandler.post { onResult(bitmap) }
            workerThread.quitSafely()
        }

        reader.setOnImageAvailableListener({ imageReader ->
            if (finished) return@setOnImageAvailableListener
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = runCatching {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                cropped
            }.getOrNull()
            image.close()
            finish(bitmap)
        }, worker)

        worker.postDelayed({
            if (!finished) finish(null)
        }, 3_000L)
    }
}
