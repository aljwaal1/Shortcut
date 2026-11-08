package com.explapp.shortcut.tools

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.IOException

/** One scanner implementation used by PDF tools and accountant shortcuts. */
class DocumentScannerActivity : ComponentActivity() {
    private var scannedPdf: Uri? = null
    private var pageImages = emptyList<Uri>()

    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish()
            return@registerForActivityResult
        }
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        scannedPdf = scan?.pdf?.uri
        pageImages = scan?.pages?.map { it.imageUri }.orEmpty()
        if (scannedPdf == null || pageImages.isEmpty()) {
            notice("No scanned pages / لا توجد صفحات ممسوحة")
            finish()
            return@registerForActivityResult
        }
        AlertDialog.Builder(this)
            .setTitle("Save document / حفظ المستند")
            .setItems(arrayOf("One PDF / ملف PDF واحد", "Separate PDFs / ملفات PDF منفصلة")) { _, which ->
                if (which == 0) saveOne.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, "Scan_${System.currentTimeMillis()}.pdf")
                }) else saveSeparate.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private val saveOne = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val destination = result.data?.data
        val source = scannedPdf
        if (result.resultCode == Activity.RESULT_OK && destination != null && source != null) {
            runCatching {
                contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Cannot open scan" }
                    contentResolver.openOutputStream(destination, "w").use { output ->
                        requireNotNull(output) { "Cannot save scan" }
                        input.copyTo(output)
                    }
                }
            }.onSuccess { notice("PDF saved / تم حفظ PDF") }
                .onFailure { notice("Save failed / فشل الحفظ: ${it.localizedMessage}") }
        }
        finish()
    }

    private val saveSeparate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val tree = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && tree != null) {
            // The SAF destination is user-selected; never overwrite an existing page.
            runCatching {
                val parent = DocumentsContract.getTreeDocumentId(tree)
                val directory = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
                pageImages.forEachIndexed { index, imageUri ->
                    val name = "Scan_${System.currentTimeMillis()}_page_${(index + 1).toString().padStart(3, '0')}.pdf"
                    val destination = DocumentsContract.createDocument(contentResolver, directory, "application/pdf", name)
                        ?: throw IOException("Cannot create page ${index + 1}")
                    contentResolver.openOutputStream(destination, "w").use { output ->
                        requireNotNull(output) { "Cannot write ${index + 1}" }
                        writeImageAsPdf(imageUri, output)
                    }
                }
            }.onSuccess { notice("Pages saved / تم حفظ الصفحات") }
                .onFailure { notice("Save failed / فشل الحفظ: ${it.localizedMessage}") }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                notice("Scanner unavailable / تعذر فتح الماسح: ${it.localizedMessage}")
                finish()
            }
    }

    private fun writeImageAsPdf(uri: Uri, output: java.io.OutputStream) {
        val bitmap = contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input) ?: throw IOException("Invalid scanned page")
        }
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                )
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                document.writeTo(output)
            } finally { document.close() }
        } finally { bitmap.recycle() }
    }

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
