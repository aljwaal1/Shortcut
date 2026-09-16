package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.max

class ImageCropActivity : AppCompatActivity() {
    private var ratio = 1f

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        cropAndSave(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val labels = arrayOf("1:1", "4:3", "3:4", "16:9", "9:16")
        val ratios = floatArrayOf(1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f)
        AlertDialog.Builder(this)
            .setTitle(local("Choose crop ratio", "اختر نسبة القص"))
            .setItems(labels) { _, which ->
                ratio = ratios[which]
                picker.launch("image/*")
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun cropAndSave(uri: Uri) {
        runCatching {
            val source = loadOrientedBitmap(uri, 2400)
            val rect = ImageCropMath.centerCrop(source.width, source.height, ratio)
            val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
            val outputUri = ToolOutputStore(this).create(
                "Cropped_${System.currentTimeMillis()}.png",
                "image/png",
                true,
            )
            contentResolver.openOutputStream(outputUri).use { out ->
                requireNotNull(out)
                check(cropped.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            if (cropped !== source) cropped.recycle()
            source.recycle()
        }.onSuccess {
            Toast.makeText(this, local("Cropped image saved", "تم حفظ الصورة المقصوصة"), Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: local("Crop failed", "فشل القص"), Toast.LENGTH_LONG).show()
        }
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

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en
}
