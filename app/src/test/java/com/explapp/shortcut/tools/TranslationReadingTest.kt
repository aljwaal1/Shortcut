package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationReadingTest {
    @Test fun segmentsStayWithinLimitAndKeepText() {
        val text = "First sentence. Second sentence! Third sentence?"
        val chunks = TranslationReading.chunks(text, 24)
        assertTrue(chunks.all { it.length <= 24 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test fun pageIndexClampedForSavedBookmarks() {
        assertEquals(0, TranslationReading.pageIndex(-10, 4))
        assertEquals(3, TranslationReading.pageIndex(90, 4))
        assertEquals(0, TranslationReading.pageIndex(5, 0))
    }

    @Test fun emptyTextDoesNotYieldChunks() {
        assertTrue(TranslationReading.chunks("   ").isEmpty())
    }
}
