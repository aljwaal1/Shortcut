package com.explapp.shortcut.tools

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal standards-based XLSX export with no server and no heavyweight workbook dependency.
 * OCR output is kept as text to avoid silently changing amounts or dropping leading zeros.
 */
object XlsxTableWriter {
    fun write(rows: List<List<String>>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun file(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            file("[Content_Types].xml", header + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            file("_rels/.rels", header + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            file("xl/workbook.xml", header + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Scanned table" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            file("xl/_rels/workbook.xml.rels", header + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val cells = buildString {
                rows.forEachIndexed { rowIndex, row ->
                    append("<row r=\"").append(rowIndex + 1).append("\">")
                    row.forEachIndexed { colIndex, cell ->
                        append("<c r=\"").append(columnLabel(colIndex)).append(rowIndex + 1)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escapeXml(cell)).append("</t></is></c>")
                    }
                    append("</row>")
                }
            }
            file("xl/worksheets/sheet1.xml", header +
                """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
                cells + "</sheetData></worksheet>")
        }
    }

    private fun columnLabel(index: Int): String {
        require(index >= 0)
        var n = index + 1
        return buildString {
            while (n > 0) {
                n--
                append(('A'.code + n % 26).toChar())
                n /= 26
            }
        }.reversed()
    }

    private fun escapeXml(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 32) append(ch)
            }
        }
    }
}
