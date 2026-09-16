package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class QuickFileActivity : AppCompatActivity() {
    private lateinit var tool: ToolId

    private val filesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) finish() else createZip(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tool = runCatching { ToolId.valueOf(intent.getStringExtra(EXTRA_TOOL).orEmpty()) }.getOrElse {
            finish(); return
        }
        when (tool) {
            ToolId.QR_CREATE -> promptQr()
            ToolId.ZIP_FILES -> filesPicker.launch(arrayOf("*/*"))
            else -> finish()
        }
    }

    private fun promptQr() {
        val input = EditText(this).apply { hint = local("Text or URL", "نص أو رابط") }
        AlertDialog.Builder(this)
            .setTitle(local("Create QR code", "إنشاء QR Code"))
            .setView(input)
            .setPositiveButton(local("Create", "إنشاء")) { _, _ -> createQr(input.text.toString()) }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun createQr(text: String) {
        runCatching {
            require(text.isNotBlank()) { "Text is empty" }
            val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 900, 900)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
            val output = ToolOutputStore(this).create("QR_${System.currentTimeMillis()}.png", "image/png", true)
            contentResolver.openOutputStream(output).use { out ->
                requireNotNull(out)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            bitmap.recycle()
            output
        }.onSuccess { output ->
            ToolResultActions.show(this, listOf(output), ToolOutputResultPolicy.forTool(ToolId.QR_CREATE).mime)
        }.onFailure(::showError)
    }

    private fun createZip(uris: List<Uri>) {
        runCatching {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                uris.forEachIndexed { index, uri ->
                    val name = queryName(uri).ifBlank { "file_$index" }
                    zip.putNextEntry(ZipEntry(name))
                    contentResolver.openInputStream(uri).use { input -> requireNotNull(input).copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val output = ToolOutputStore(this).create("Shortcut_${System.currentTimeMillis()}.zip", "application/zip", false)
            contentResolver.openOutputStream(output).use { out -> requireNotNull(out).write(bytes.toByteArray()) }
            output
        }.onSuccess { output ->
            ToolResultActions.show(this, listOf(output), ToolOutputResultPolicy.forTool(ToolId.ZIP_FILES).mime)
        }.onFailure(::showError)
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return ""
    }

    private fun showError(t: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(local("Could not complete action", "تعذر تنفيذ العملية"))
            .setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun local(en: String, ar: String): String = if (resources.configuration.locales[0].language == "ar") ar else en

    companion object { const val EXTRA_TOOL = "tool" }
}
