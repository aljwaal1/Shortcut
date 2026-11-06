package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfSplitPlanTest {
    @Test fun numberedPagesSortLexicographically() {
        val pages = PdfSplitPlan.pages(12)
        assertEquals("Page_001.pdf", pages.first().name)
        assertEquals("Page_012.pdf", pages.last().name)
        assertEquals(12, pages.size)
    }

    @Test fun chapterBoundariesAreSortedUniqueAndCoverDocument() {
        val ranges = PdfSplitPlan.chapters(10, listOf(6, 1, 6, 3))
        assertEquals(listOf(1..2, 3..5, 6..10), ranges.map { it.firstPage..it.lastPage })
    }

    @Test fun editableRangesRejectGapsAndOverlap() {
        assertEquals(listOf(1..4, 5..10), PdfSplitPlan.parseRanges("1-4, 5-10", 10).map { it.firstPage..it.lastPage })
        assertThrows(IllegalArgumentException::class.java) { PdfSplitPlan.parseRanges("1-4, 4-10", 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfSplitPlan.parseRanges("2-10", 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfSplitPlan.parseRanges("1-11", 10) }
    }

    @Test fun zeroPagesRejected() {
        assertThrows(IllegalArgumentException::class.java) { PdfSplitPlan.pages(0) }
    }
}
