package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLargeFilePolicyTest {
    @Test fun smallDocumentsCanScanChapterHeadings() {
        assertTrue(PdfLargeFilePolicy.shouldScanAllPages(100))
    }

    @Test fun hugeDocumentsSkipExpensiveFullTextChapterDiscovery() {
        assertFalse(PdfLargeFilePolicy.shouldScanAllPages(5000))
    }

    @Test fun reservesSpaceForSourceAndExport() {
        assertEquals(200L, PdfLargeFilePolicy.requiredFreeBytes(100L))
    }
}
