package com.explapp.shortcut.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxTableWriterTest {
    @Test fun exportedWorkbookContainsCellsAndEscapesText() {
        val bytes = ByteArrayOutputStream()
        XlsxTableWriter.write(
            listOf(listOf("اسم", "القيمة"), listOf("بيع & شراء", "12.50")),
            bytes,
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("بيع &amp; شراء"))
        assertTrue(sheet.contains("12.50"))
    }

    @Test fun emptyRowsStillProduceValidWorkbook() {
        val bytes = ByteArrayOutputStream()
        XlsxTableWriter.write(emptyList(), bytes)
        assertTrue(bytes.size() > 200)
    }
}
