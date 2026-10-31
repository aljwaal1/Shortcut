package com.explapp.shortcut.search

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSearchTest {
    @Test
    fun matchesArabicEnglishLabelsAndKeywords() {
        val items = listOf(
            CommandItem("usage", "App usage", listOf("وقت الاستخدام", "screen time"), CommandKind.TOOL),
            CommandItem("qr", "QR", listOf("باركود", "code"), CommandKind.TOOL),
        )
        assertEquals(listOf("usage"), CommandSearch.filter(items, "الاستخدام").map { it.id })
        assertEquals(listOf("qr"), CommandSearch.filter(items, "code").map { it.id })
    }

    @Test
    fun matchesMultipleTokensAndArabicHamzaVariants() {
        val items = listOf(
            CommandItem("ocr", "Image OCR", listOf("استخراج النص من الصورة", "text image"), CommandKind.TOOL),
            CommandItem("pdf", "PDF text", listOf("استخراج النص من PDF"), CommandKind.TOOL),
        )
        assertEquals(listOf("ocr"), CommandSearch.filter(items, "استخراج صورة").map { it.id })
        assertEquals(listOf("ocr"), CommandSearch.filter(items, "إستخراج الصورة").map { it.id })
    }
}
