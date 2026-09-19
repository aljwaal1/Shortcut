package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class ImageBatchActivity : AppCompatActivity() {
    private lateinit var tool: ToolId
    private var mergeVertical = false

    private val multiPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) finish() else confirmSelection(uris)
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
            ToolId.IMAGES_TO_PDF, ToolId.GIF_CREATE, ToolId.IMAGE_RESIZE_COMPRESS -> multiPicker.launch("image/*")
            else -> finish()
        }
    }

    private fun confirmSelection(uris: List<Uri>) {
        val action = when (tool) {
            ToolId.MERGE_IMAGES -> local("Merge", "دمج")
            ToolId.IMAGES_TO_PDF -> local("Create PDF", "إنشاء PDF")
            ToolId.GIF_CREATE -> local("Create GIF", "إنشاء GIF")
            ToolId.IMAGE_RESIZE_COMPRESS -> local("Compress / resize", "ضغط / تغيير الحجم")
            else -> local("Start", "ابدأ")
        }
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(local(
                "${uris.size} image(s) selected. Android's picker order will be used. Choose again if the order is not correct. Original files will not be changed.",
                "تم اختيار ${uris.size} صورة. سيُستخدم ترتيب منتقي Android. اختر من جديد إذا لم يكن الترتيب مناسبًا. لن يتم تعديل الملفات الأصلية.",
            ))
            .setPositiveButton(action) { _, _ -> startOperation(uris) }
            .setNeutralButton(local("Choose again", "اختيار من جديد")) { _, _ -> multiPicker.launch("image/*") }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startOperation(uris: List<Uri>) {
        val mime = when (tool) {
            ToolId.IMAGES_TO_PDF -> "application/pdf"
            ToolId.GIF_CREATE -> "image/gif"
            else -> "image/jpeg"
        }
        val title = when (tool) {
            ToolId.MERGE_IMAGES -> local("Merging images", "دمج الصور")
            ToolId.IMAGES_TO_PDF -> local("Creating PDF", "إنشاء PDF")
            ToolId.GIF_CREATE -> local("Creating GIF", "إنشاء GIF")
            ToolId.IMAGE_RESIZE_COMPRESS -> local("Compressing images", "ضغط الصور")
            else -> local("Processing", "المعالجة")
        }
        runProgress(title, uris.size.coerceAtLeast(1), mime) { cancelled, step ->
            when (tool) {
                ToolId.MERGE_IMAGES -> listOf(merge(uris, mergeVertical, cancelled, step))
                ToolId.IMAGES_TO_PDF -> listOf(toPdf(uris, cancelled, step))
                ToolId.GIF_CREATE -> listOf(toGif(uris, cancelled, step))
                ToolId.IMAGE_RESIZE_COMPRESS -> compressResize(uris, cancelled, step)
                else -> emptyList()
            }
        }
    }

    private fun runProgress(
        title: String,
        total: Int,
        mime: String,
        work: (AtomicBoolean, (Int) -> Unit) -> List<Uri>,
    ) {
        val cancelled = AtomicBoolean(false)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = total }
        val label = TextView(this).apply { text = local("Preparing…", "جارٍ التحضير…") }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(progress)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> cancelled.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            var failure: Throwable? = null
            var outputs = emptyList<Uri>()
            runCatching {
                outputs = work(cancelled) { completed ->
                    runOnUiThread {
                        val safe = completed.coerceIn(0, total)
                        progress.progress = safe
                        label.text = local("$safe of $total", "$safe من $total")
                    }
                }
            }.onFailure { failure = it }
            runOnUiThread {
                dialog.dismiss()
                when {
                    failure != null -> showError(failure!!)
                    cancelled.get() -> {
                        toast(local("Operation cancelled", "تم إلغاء العملية"))
                        finish()
                    }
                    outputs.isNotEmpty() -> showResults(outputs, mime)
                    else -> finish()
                }
            }
        }.start()
    }

    private fun merge(uris: List<Uri>, vertical: Boolean, cancelled: AtomicBoolean, step: (Int) -> Unit): Uri {
        val bitmaps = mutableListOf<Bitmap>()
        uris.forEachIndexed { index, uri ->
            check(!cancelled.get()) { "Cancelled" }
            bitmaps += loadOrientedBitmap(uri, 1600)
            step(index + 1)
        }
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
        val output = saveBitmap(result, "Merged_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
        bitmaps.forEach(Bitmap::recycle)
        result.recycle()
        return output
    }

    private fun toPdf(uris: List<Uri>, cancelled: AtomicBoolean, step: (Int) -> Unit): Uri {
        val pdf = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                check(!cancelled.get()) { "Cancelled" }
                val bitmap = loadOrientedBitmap(uri, 1800)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create())
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                bitmap.recycle()
                step(index + 1)
            }
            val output = ToolOutputStore(this).create("Images_${System.currentTimeMillis()}.pdf", "application/pdf", false)
            contentResolver.openOutputStream(output).use { out -> requireNotNull(out); pdf.writeTo(out) }
            return output
        } finally {
            pdf.close()
        }
    }

    private fun toGif(uris: List<Uri>, cancelled: AtomicBoolean, step: (Int) -> Unit): Uri {
        val selected = uris.take(20)
        val frames = mutableListOf<Bitmap>()
        selected.forEachIndexed { index, uri ->
            check(!cancelled.get()) { "Cancelled" }
            frames += loadOrientedBitmap(uri, 720)
            step(index + 1)
        }
        val width = frames.maxOf { it.width }
        val height = frames.maxOf { it.height }
        val bytes = ByteArrayOutputStream()
        val encoder = GifEncoder(bytes, width, height, 0)
        val options = ImageOptions().apply { setDelay(350, TimeUnit.MILLISECONDS) }
        frames.forEach { frame ->
            check(!cancelled.get()) { "Cancelled" }
            val normalized = if (frame.width == width && frame.height == height) frame else Bitmap.createScaledBitmap(frame, width, height, true)
            encoder.addImage(bitmapPixels(normalized), options)
            if (normalized !== frame) normalized.recycle()
        }
        encoder.finishEncoding()
        frames.forEach(Bitmap::recycle)
        val output = ToolOutputStore(this).create("Animated_${System.currentTimeMillis()}.gif", "image/gif", true)
        contentResolver.openOutputStream(output).use { out -> requireNotNull(out).write(bytes.toByteArray()) }
        return output
    }

    private fun compressResize(uris: List<Uri>, cancelled: AtomicBoolean, step: (Int) -> Unit): List<Uri> {
        val outputs = mutableListOf<Uri>()
        uris.forEachIndexed { index, uri ->
            check(!cancelled.get()) { "Cancelled" }
            val bitmap = loadOrientedBitmap(uri, 1600)
            outputs += saveBitmap(bitmap, "Compressed_${System.currentTimeMillis()}_${index + 1}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 82)
            bitmap.recycle()
            step(index + 1)
        }
        return outputs
    }

    private fun showResults(outputs: List<Uri>, mime: String) {
        AlertDialog.Builder(this)
            .setTitle(local("Operation complete", "اكتملت العملية"))
            .setMessage(local("${outputs.size} output file(s) saved.", "تم حفظ ${outputs.size} ملف ناتج."))
            .setPositiveButton(local("Share", "مشاركة")) { _, _ -> share(outputs, mime) }
            .setNeutralButton(local("Open", "فتح")) { _, _ -> openFirst(outputs, mime) }
            .setNegativeButton(local("Done", "تم")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun share(outputs: List<Uri>, mime: String) {
        val contentUris = ArrayList(outputs.filter { it.scheme == "content" })
        if (contentUris.isEmpty()) return finish()
        val intent = if (contentUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, contentUris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = mime; putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris) }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(intent, local("Share result", "مشاركة النتيجة")))
        finish()
    }

    private fun openFirst(outputs: List<Uri>, mime: String) {
        val uri = outputs.firstOrNull { it.scheme == "content" } ?: return finish()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { toast(local("No app can open this file", "لا يوجد تطبيق لفتح الملف")) }
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
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
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

    private fun saveBitmap(bitmap: Bitmap, name: String, mime: String, format: Bitmap.CompressFormat, quality: Int): Uri {
        val output = ToolOutputStore(this).create(name, mime, true)
        contentResolver.openOutputStream(output).use { out -> requireNotNull(out); check(bitmap.compress(format, quality, out)) }
        return output
    }

    private fun bitmapPixels(bitmap: Bitmap): Array<IntArray> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Array(bitmap.width) { x -> IntArray(bitmap.height) { y -> pixels[y * bitmap.width + x] } }
    }

    private fun showError(t: Throwable) {
        if (t.message == "Cancelled") {
            toast(local("Operation cancelled", "تم إلغاء العملية"))
            finish()
            return
        }
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
