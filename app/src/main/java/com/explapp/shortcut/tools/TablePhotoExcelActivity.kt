package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import java.util.Locale

/**
 * Capture a table from a photo, review editable tab-separated cells, then export actual XLSX.
 * OCR isn't authoritative: the correction dialog intentionally appears before every save.
 */
class TablePhotoExcelActivity : ComponentActivity() {
    private var editedCells: String = ""
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val image = runCatching { InputImage.fromFilePath(this, uri) }.getOrElse {
            toast("Cannot open image / تعذر فتح الصورة"); return@registerForActivityResult
        }
        recognize(image)
    }
    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { photo: Bitmap? ->
        if (photo != null) recognize(InputImage.fromBitmap(photo, 0))
    }

    private fun recognize(image: InputImage) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { result ->
                editedCells = ocrLinesToTsv(result)
                showEditor()
            }
            .addOnFailureListener {
                toast("Text detection failed / تعذر قراءة الجدول: ${it.localizedMessage}")
            }
    }
    private val createWorkbook = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output)
                    val rows = editedCells.lines().filter { it.isNotBlank() }.map { it.split('\t') }
                    XlsxTableWriter.write(rows, output)
                }
            }.onSuccess { toast("Excel saved / تم حفظ ملف Excel") }
                .onFailure { toast("Save failed / فشل الحفظ: ${it.localizedMessage}") }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 44, 36, 24)
        }
        root.addView(TextView(this).apply {
            text = "تصوير جدول إلى Excel"
            textSize = 24f
            setPadding(0, 0, 0, 18)
        })
        root.addView(TextView(this).apply {
            text = "ابدأ بتصوير الجدول أو اختر صورة محفوظة، ثم راجع الخلايا قبل حفظ ملف Excel."
            textSize = 16f
            setPadding(0, 0, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "تصوير جدول بالكاميرا"
            setOnClickListener { cameraPicker.launch(null) }
        })
        root.addView(Button(this).apply {
            text = "اختيار صورة من الهاتف"
            setOnClickListener { imagePicker.launch("image/*") }
        })
        setContentView(root)
    }

    private fun showEditor() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        layout.addView(TextView(this).apply {
            text = "Review each number and column before export. Separate columns with TAB.\nراجع الأرقام والأعمدة قبل التصدير. افصل الأعمدة بعلامة TAB."
            textSize = 15f
        })
        val editor = EditText(this).apply {
            setText(editedCells)
            minLines = 8
            maxLines = 16
            isSingleLine = false
            setHorizontallyScrolling(true)
            textSize = 14f
        }
        layout.addView(editor)
        AlertDialog.Builder(this)
            .setTitle("Table → Excel / جدول إلى Excel")
            .setView(layout)
            .setPositiveButton("Save XLSX / حفظ") { _, _ ->
                editedCells = editor.text.toString()
                if (editedCells.isBlank()) { toast("Table is empty / الجدول فارغ"); finish() }
                else createWorkbook.launch("Scanned_Table_${System.currentTimeMillis()}.xlsx")
            }
            .setNegativeButton("Cancel / إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun ocrLinesToTsv(result: Text): String {
        val cells = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val bounds: Rect = line.boundingBox ?: return@mapNotNull null
            bounds to line.text.replace('\t', ' ').replace('\n', ' ').trim()
        }.filter { it.second.isNotEmpty() }.sortedWith(compareBy({ it.first.centerY() }, { it.first.left }))
        if (cells.isEmpty()) return ""
        val rows = mutableListOf<MutableList<Pair<Rect, String>>>()
        for (cell in cells) {
            val last = rows.lastOrNull()
            val tolerance = (cell.first.height() / 2).coerceAtLeast(10)
            if (last != null && kotlin.math.abs(last.first().first.centerY() - cell.first.centerY()) <= tolerance) {
                last += cell
            } else rows += mutableListOf(cell)
        }
        return rows.joinToString("\n") { row ->
            row.sortedBy { it.first.left }.joinToString("\t") { it.second }
        }
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
