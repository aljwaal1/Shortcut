package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ImageBatchActivity : AppCompatActivity() {
    private lateinit var tool: ToolId
    private var mergeVertical = false

    private val multiPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult finish()
        when (tool) {
            ToolId.MERGE_IMAGES -> merge(uris, mergeVertical)
            ToolId.IMAGES_TO_PDF -> toPdf(uris)
            ToolId.GIF_CREATE -> toGif(uris)
            else -> finish()
        }
    }

    private val singlePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) finish() else compressResize(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tool = runCatching { ToolId.valueOf(intent.getStringExtra(EXTRA_TOOL).orEmpty()) }.getOrElse {
            finish(); return
        }
        when (tool) {
            ToolId.MERGE_IMAGES -> AlertDialog.Builder(this)
                .setTitle(local("Merge direction", "اتجاه الدمج"))
                .setItems(arrayOf(local("Horizontal", "أفقي"), local("Vertical", "عمودي"))) { _, which ->
                    mergeVertical = which == 1
                    multiPicker.launch("image/*")
                }
                .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            ToolId.IMAGES_TO_PDF, ToolId.GIF_CREATE -> multiPicker.launch("image/*")
            ToolId.IMAGE_RESIZE_COMPRESS -> singlePicker.launch("image/*")
            else -> finish()
        }
    }

    private fun merge(uris: List<Uri>, vertical: Boolean) {
        runCatching {
            val bitmaps = uris.map { loadOrientedBitmap(it, 1600) }
            val width = if (vertical) bitmaps.maxOf { it.width } else bitmaps.sumOf { it.width }
            val height = if (vertical) bitmaps.sumOf { it.height } else bitmaps.maxOf { it.height }
            require(width > 0 && height > 0 && width.toLong() * height <= 40_000_000L) { "Result is too large" }
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var x = 0f
            var y = 0f
            bitmaps.forEach { bitmap ->
                canvas.drawBitmap(bitmap, x, y, null)
                if (vertical) y += bitmap.height else x += bitmap.width
            }
            saveBitmap(result, "Merged_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
            bitmaps.forEach(Bitmap::recycle)
            result.recycle()
        }.onSuccess { toast(local("Merged image saved", "تم حفظ الصورة المدمجة")) }
            .onFailure(::showError)
        finish()
    }

    private fun toPdf(uris: List<Uri>) {
        runCatching {
            val pdf = PdfDocument()
            uris.forEachIndexed { index, uri ->
                val bitmap = loadOrientedBitmap(uri, 1800)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create())
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                bitmap.recycle()
            }
            val output = ToolOutputStore(this).create("Images_${System.currentTimeMillis()}.pdf", "application/pdf", false)
            contentResolver.openOutputStream(output).use { out -> requireNotNull(out); pdf.writeTo(out) }
            pdf.close()
        }.onSuccess { toast(local("PDF saved", "تم حفظ PDF")) }
            .onFailure(::showError)
        finish()
    }

    private fun toGif(uris: List<Uri>) {
        runCatching {
            val frames = uris.take(20).map { loadOrientedBitmap(it, 720) }
            val width = frames.maxOf { it.width }
            val height = frames.maxOf { it.height }
            val bytes = ByteArrayOutputStream()
            val encoder = GifEncoder(bytes, width, height, 0)
            val options = ImageOptions().apply { setDelay(350, TimeUnit.MILLISECONDS) }
            frames.forEach { frame ->
                val normalized = if (frame.width == width && frame.height == height) frame
                else Bitmap.createScaledBitmap(frame, width, height, true)
                encoder.addImage(bitmapPixels(normalized), options)
                if (normalized !== frame) normalized.recycle()
            }
            encoder.finishEncoding()
            frames.forEach(Bitmap::recycle)
            val output = ToolOutputStore(this).create("Animated_${System.currentTimeMillis()}.gif", "image/gif", true)
            contentResolver.openOutputStream(output).use { out -> requireNotNull(out).write(bytes.toByteArray()) }
        }.onSuccess { toast(local("GIF saved", "تم حفظ GIF")) }
            .onFailure(::showError)
        finish()
    }

    private fun compressResize(uri: Uri) {
        runCatching {
            val bitmap = loadOrientedBitmap(uri, 1600)
            saveBitmap(bitmap, "Compressed_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 82)
            bitmap.recycle()
        }.onSuccess { toast(local("Compressed image saved", "تم حفظ الصورة المضغوطة")) }
            .onFailure(::showError)
        finish()
    }

    private fun loadOrientedBitmap(uri: Uri, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
        val decoded = contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unable to decode image")

        val orientation = contentResolver.openInputStream(uri).use { input: InputStream? ->
            if (input == null) ExifInterface.ORIENTATION_NORMAL
            else runCatching { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(90f) }
        }
        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        val scale = maxDimension.toFloat() / max(oriented.width, oriented.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== oriented) oriented.recycle() }
        } else oriented
    }

    private fun saveBitmap(bitmap: Bitmap, name: String, mime: String, format: Bitmap.CompressFormat, quality: Int) {
        val output = ToolOutputStore(this).create(name, mime, true)
        contentResolver.openOutputStream(output).use { out -> requireNotNull(out); check(bitmap.compress(format, quality, out)) }
    }

    private fun bitmapPixels(bitmap: Bitmap): Array<IntArray> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Array(bitmap.width) { x -> IntArray(bitmap.height) { y -> pixels[y * bitmap.width + x] } }
    }

    private fun showError(t: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(local("Could not complete action", "تعذر تنفيذ العملية"))
            .setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun local(en: String, ar: String): String = if (resources.configuration.locales[0].language == "ar") ar else en

    companion object { const val EXTRA_TOOL = "tool" }
}
