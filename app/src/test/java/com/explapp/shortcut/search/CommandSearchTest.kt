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
}
