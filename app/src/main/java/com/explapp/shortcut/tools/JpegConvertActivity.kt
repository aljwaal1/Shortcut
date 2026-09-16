package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class JpegConvertActivity : AppCompatActivity() {
    private var keepMetadata = false

    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) finish() else confirmBatch(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(local("JPEG metadata", "بيانات JPEG"))
            .setMessage(local(
                "Choose whether to keep common camera/date/location metadata or remove it for more privacy.",
                "اختر الاحتفاظ ببيانات الكاميرا والتاريخ والموقع الشائعة أو حذفها لخصوصية أكبر.",
            ))
            .setPositiveButton(local("Remove metadata", "حذف البيانات")) { _, _ ->
                keepMetadata = false
                picker.launch("image/*")
            }
            .setNeutralButton(local("Keep metadata", "الاحتفاظ بالبيانات")) { _, _ ->
                keepMetadata = true
                picker.launch("image/*")
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun confirmBatch(uris: List<Uri>) {
        AlertDialog.Builder(this)
            .setTitle(local("Convert to JPEG", "تحويل إلى JPEG"))
            .setMessage(local(
                "${uris.size} image(s) selected. They will be processed in the order returned by Android. Original files will not be changed.",
                "تم اختيار ${uris.size} صورة. ستُعالج حسب الترتيب الذي أعاده Android، ولن يتم تعديل الملفات الأصلية.",
            ))
            .setPositiveButton(local("Start", "ابدأ")) { _, _ -> convertBatch(uris) }
            .setNeutralButton(local("Choose again", "اختيار من جديد")) { _, _ -> picker.launch("image/*") }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun convertBatch(uris: List<Uri>) {
        val cancelled = AtomicBoolean(false)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = uris.size
            progress = 0
        }
        val label = TextView(this).apply { text = local("Preparing…", "جارٍ التحضير…") }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(progress)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(local("Converting images", "تحويل الصور"))
            .setView(container)
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> cancelled.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputs = mutableListOf<Uri>()
            var failure: Throwable? = null
            uris.forEachIndexed { index, sourceUri ->
                if (cancelled.get()) return@forEachIndexed
                runOnUiThread {
                    label.text = local("Image ${index + 1} of ${uris.size}", "الصورة ${index + 1} من ${uris.size}")
                }
                runCatching { convertOne(sourceUri, index) }
                    .onSuccess { outputs += it }
                    .onFailure { failure = it; cancelled.set(true) }
                runOnUiThread { progress.progress = index + 1 }
            }
            runOnUiThread {
                dialog.dismiss()
                when {
                    failure != null -> showFailure(failure!!)
                    cancelled.get() -> {
                        Toast.makeText(this, local("Conversion cancelled", "تم إلغاء التحويل"), Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> showCompleted(outputs)
                }
            }
        }.start()
    }

    private fun convertOne(sourceUri: Uri, index: Int): Uri {
        val sourceExif = if (keepMetadata) {
            contentResolver.openInputStream(sourceUri).use { input -> input?.let { ExifInterface(it) } }
        } else null
        val bitmap = loadOrientedBitmap(sourceUri, 2400)
        val outputUri = ToolOutputStore(this).create(
            "Converted_${System.currentTimeMillis()}_${index + 1}.jpg",
            "image/jpeg",
            true,
        )
        contentResolver.openOutputStream(outputUri).use { out ->
            requireNotNull(out)
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out))
        }
        bitmap.recycle()
        if (sourceExif != null && outputUri.scheme == "content") copyCommonExif(sourceExif, outputUri)
        return outputUri
    }

    private fun showCompleted(outputs: List<Uri>) {
        AlertDialog.Builder(this)
            .setTitle(local("JPEG conversion complete", "اكتمل تحويل JPEG"))
            .setMessage(local("${outputs.size} file(s) saved.", "تم حفظ ${outputs.size} ملف."))
            .setPositiveButton(local("Share", "مشاركة")) { _, _ -> share(outputs) }
            .setNeutralButton(local("Open first", "فتح الأول")) { _, _ -> openFirst(outputs) }
            .setNegativeButton(local("Done", "تم")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun share(outputs: List<Uri>) {
        val contentUris = ArrayList(outputs.filter { it.scheme == "content" })
        if (contentUris.isEmpty()) return finish()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, local("Share JPEG files", "مشاركة ملفات JPEG")))
        finish()
    }

    private fun openFirst(outputs: List<Uri>) {
        val uri = outputs.firstOrNull { it.scheme == "content" } ?: return finish()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(this, local("No app can open this file", "لا يوجد تطبيق لفتح الملف"), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun showFailure(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(local("Conversion failed", "فشل التحويل"))
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun copyCommonExif(source: ExifInterface, outputUri: Uri) {
        contentResolver.openFileDescriptor(outputUri, "rw")?.use { descriptor ->
            val target = ExifInterface(descriptor.fileDescriptor)
            COMMON_TAGS.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
            target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            target.saveAttributes()
        }
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

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        private val COMMON_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
        )
    }
}
