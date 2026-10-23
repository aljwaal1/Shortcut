package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Entirely offline PDF splitting: Android's document picker owns input and output permissions. */
class PdfSplitActivity : AppCompatActivity() {
    private var source: Uri? = null
    private var sourceName = "Document"
    private var pageCount = 0
    private var suggestedStarts = listOf(1)
    private lateinit var status: TextView
    private lateinit var rangeEditor: EditText
    private lateinit var chaptersButton: RadioButton
    private lateinit var exportButton: Button
    private lateinit var selectButton: Button
    private var busy = false

    private val selectPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            source = uri
            sourceName = documentName(uri).removeSuffix(".pdf").removeSuffix(".PDF")
            inspectSource(uri)
        } else if (source == null) finish()
    }
    private val selectFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching { contentResolver.takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            exportTo(tree)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (18 * resources.displayMetrics.density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        panel.addView(TextView(this).apply {
            text = local("Split PDF", "تقسيم PDF")
            textSize = 23f
        })
        panel.addView(TextView(this).apply {
            text = local("Choose a PDF, choose the splitting method and a destination folder. The source stays unchanged.",
                "اختر ملف PDF، وطريقة التقسيم، ومجلد الحفظ. لن يتغير الملف الأصلي.")
            setPadding(0, pad / 2, 0, pad / 2)
        })
        selectButton = Button(this).apply {
            text = local("Choose PDF file", "اختيار ملف PDF")
            setOnClickListener { if (!busy) selectPdf.launch(arrayOf("application/pdf")) }
        }
        panel.addView(selectButton)
        val methods = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val pagesButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = local("One PDF per page (Pages)", "ملف PDF لكل صفحة (Pages)")
            isChecked = true
        }
        chaptersButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = local("One PDF per chapter / unit (Chapters)", "ملف PDF لكل فصل أو وحدة (Chapters)")
        }
        methods.addView(pagesButton)
        methods.addView(chaptersButton)
        panel.addView(methods)
        val hint = TextView(this).apply {
            text = local("Chapter ranges: edit suggestions or enter your own, e.g. 1-12, 13-30. Every page must be included exactly once.",
                "حدود الفصول: عدّل المقترحات أو أدخلها يدويًا، مثل 1-12, 13-30. يجب تضمين كل صفحة مرة واحدة.")
        }
        panel.addView(hint)
        rangeEditor = EditText(this).apply {
            hint = "1-12, 13-30"
            minLines = 2
            setSingleLine(false)
        }
        panel.addView(rangeEditor)
        methods.setOnCheckedChangeListener { _, _ ->
            hint.visibility = if (chaptersButton.isChecked) View.VISIBLE else View.GONE
            rangeEditor.visibility = hint.visibility
        }
        hint.visibility = View.GONE
        rangeEditor.visibility = View.GONE
        status = TextView(this).apply {
            text = local("Select a PDF to begin", "اختر ملف PDF للبدء")
            setPadding(0, pad, 0, pad)
        }
        panel.addView(status)
        exportButton = Button(this).apply {
            text = local("Choose destination folder and export", "اختيار مجلد الحفظ وبدء التقسيم")
            isEnabled = false
            setOnClickListener {
                if (!busy && source != null) {
                    if (chaptersButton.isChecked) {
                        runCatching { PdfSplitPlan.parseRanges(rangeEditor.text.toString(), pageCount) }
                            .onFailure { showError(it); return@setOnClickListener }
                    }
                    selectFolder.launch(null)
                }
            }
        }
        panel.addView(exportButton)
        setContentView(ScrollView(this).apply { addView(panel) })
        selectPdf.launch(arrayOf("application/pdf"))
    }

    private fun inspectSource(uri: Uri) = background(
        local("Reading PDF and finding chapters…", "جارٍ قراءة PDF والبحث عن الفصول…")
    ) {
        PDFBoxResourceLoader.init(applicationContext)
        contentResolver.openInputStream(uri).use { input ->
            PDDocument.load(requireNotNull(input) { "Cannot read selected PDF" }).use { document ->
                val count = document.numberOfPages
                require(count > 0) { "PDF has no pages" }
                val starts = detectChapters(document)
                runOnUiThread {
                    pageCount = count
                    suggestedStarts = starts
                    val suggestions = PdfSplitPlan.chapters(count, starts)
                    rangeEditor.setText(suggestions.joinToString(", ") { "${it.firstPage}-${it.lastPage}" })
                    status.text = local(
                        "$sourceName · $count pages · ${suggestions.size} proposed chapter(s). Review the boundaries before exporting.",
                        "$sourceName · $count صفحة · ${suggestions.size} فصل مقترح. راجع الحدود قبل التقسيم.",
                    )
                    exportButton.isEnabled = true
                }
            }
        }
    }

    private fun detectChapters(document: PDDocument): List<Int> {
        val starts = mutableListOf<Int>()
        val outline = document.documentCatalog.documentOutline
        fun walk(item: PDOutlineItem?, depth: Int) {
            var current = item
            var inspected = 0
            while (current != null && inspected++ < 1000) {
                val chapter = current
                runCatching { chapter.findDestinationPage(document) }.getOrNull()?.let { page ->
                    val index = (0 until document.numberOfPages).firstOrNull { i ->
                        document.getPage(i).cosObject == page.cosObject
                    }
                    if (index != null) starts += index + 1
                }
                if (depth < 2) walk(chapter.firstChild, depth + 1)
                current = chapter.nextSibling
            }
        }
        walk(outline?.firstChild, 0)
        if (starts.any { it > 1 }) return (starts + 1).distinct().sorted()
        val textReader = PDFTextStripper()
        val heading = Regex("^(?:chapter|unit|part|الفصل|الوحدة|الباب)\\s*(?:[0-9٠-٩]+|[:：-])?", RegexOption.IGNORE_CASE)
        for (page in 1..document.numberOfPages) {
            val lines = runCatching {
                textReader.startPage = page
                textReader.endPage = page
                textReader.getText(document).lineSequence().map(String::trim).filter(String::isNotBlank).take(6).toList()
            }.getOrDefault(emptyList())
            if (lines.any { heading.containsMatchIn(it) }) starts += page
        }
        return (starts + 1).distinct().sorted()
    }

    private fun exportTo(tree: Uri) = background(local("Exporting pages…", "جارٍ تقسيم الصفحات…")) {
        val inputUri = requireNotNull(source)
        val ranges = if (chaptersButton.isChecked) PdfSplitPlan.parseRanges(rangeEditor.text.toString(), pageCount)
            else PdfSplitPlan.pages(pageCount)
        PDFBoxResourceLoader.init(applicationContext)
        val rootDocumentId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocumentId)
        val safeSource = ToolOutputNamePolicy.sanitize(sourceName).take(75)
        val jobName = "${safeSource}_${System.currentTimeMillis()}"
        val job = requireNotNull(DocumentsContract.createDocument(contentResolver, root,
            DocumentsContract.Document.MIME_TYPE_DIR, jobName)) { "Cannot create output folder" }
        val subfolder = requireNotNull(DocumentsContract.createDocument(contentResolver, job,
            DocumentsContract.Document.MIME_TYPE_DIR, if (chaptersButton.isChecked) "Chapters" else "Pages")) {
            "Cannot create output subfolder"
        }
        var saved = 0
        try {
            contentResolver.openInputStream(inputUri).use { input ->
                PDDocument.load(requireNotNull(input)).use { original ->
                    require(original.numberOfPages == pageCount) { "PDF changed since selection" }
                    ranges.forEachIndexed { index, part ->
                        val destination = requireNotNull(DocumentsContract.createDocument(contentResolver,
                            subfolder, "application/pdf", part.name)) { "Could not create ${part.name}" }
                        try {
                            PDDocument().use { result ->
                                for (page in part.firstPage..part.lastPage) result.importPage(original.getPage(page - 1))
                                contentResolver.openOutputStream(destination, "w").use { output ->
                                    result.save(requireNotNull(output) { "Cannot write ${part.name}" })
                                }
                            }
                            saved++
                        } catch (failure: Exception) {
                            runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
                            throw failure
                        }
                        if (index % 2 == 0 || index == ranges.lastIndex) runOnUiThread {
                            status.text = local("Saved $saved of ${ranges.size}", "حُفظ $saved من ${ranges.size}")
                        }
                    }
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Saved $saved of ${ranges.size} in $jobName. ${error.message}", error)
        }
        runOnUiThread {
            status.text = local("Saved $saved files in $jobName/${if (chaptersButton.isChecked) "Chapters" else "Pages"}",
                "تم حفظ $saved ملف في $jobName/${if (chaptersButton.isChecked) "Chapters" else "Pages"}")
            AlertDialog.Builder(this)
                .setTitle(local("PDF splitting complete", "اكتمل تقسيم PDF"))
                .setMessage(status.text)
                .setPositiveButton(local("Open folder", "فتح المجلد")) { _, _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(tree, DocumentsContract.Document.MIME_TYPE_DIR)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }.onFailure {
                        Toast.makeText(this, local("Open your Files app to view the folder", "افتح تطبيق الملفات للوصول إلى المجلد"), Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(local("Close", "إغلاق"), null)
                .show()
        }
    }

    private fun documentName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { "Document.pdf" }

    private fun background(message: String, operation: () -> Unit) {
        busy = true
        status.text = message
        exportButton.isEnabled = false
        selectButton.isEnabled = false
        Thread {
            runCatching(operation).onFailure { error -> runOnUiThread { showError(error) } }
            runOnUiThread {
                busy = false
                selectButton.isEnabled = true
                exportButton.isEnabled = source != null && pageCount > 0
            }
        }.start()
    }

    private fun showError(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(local("PDF operation failed", "تعذّر تقسيم PDF"))
            .setMessage(error.message ?: local("Could not process PDF", "تعذرت معالجة الملف"))
            .setPositiveButton(local("OK", "حسنًا"), null)
            .show()
    }

    private fun local(en: String, ar: String) =
        if (resources.configuration.locales[0].language == "ar") ar else en
}
