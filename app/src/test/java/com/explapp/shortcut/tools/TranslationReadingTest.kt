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

    @Test fun prefersSentenceBoundariesForLongNaturalText() {
        val text = "Hello to you. Here is another sentence. And a third section."
        val chunks = TranslationReading.chunks(text, 35)
        assertEquals("Hello to you. Here is another sentence.", chunks[0] + " " + chunks[1])
        assertTrue(chunks.all { it.length <= 35 })
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
