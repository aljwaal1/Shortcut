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
import android.os.Looper

object ScreenCaptureHelper {
    fun captureOnce(activity: Activity, resultCode: Int, data: Intent, onResult: (Bitmap?) -> Unit) {
        if (resultCode != RESULT_OK) {
            onResult(null)
            return
        }

        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        val projection = requireNotNull(manager.getMediaProjection(resultCode, data))
        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = requireNotNull(
            projection.createVirtualDisplay(
                "ShortcutScreenshot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null,
            ),
        )

        var finished = false
        reader.setOnImageAvailableListener({ imageReader ->
            if (finished) return@setOnImageAvailableListener
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            finished = true
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
            padded.recycle()
            image.close()
            display.release()
            reader.close()
            projection.stop()
            onResult(cropped)
        }, Handler(Looper.getMainLooper()))

        Handler(Looper.getMainLooper()).postDelayed({
            if (!finished) {
                finished = true
                runCatching { display.release() }
                runCatching { reader.close() }
                runCatching { projection.stop() }
                onResult(null)
            }
        }, 3000)
    }
}
