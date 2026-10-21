package com.explapp.shortcut.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateLibrarySearchRegressionTest {
    private val entries = listOf(
        TemplateDescriptor("pdf", "Extract PDF text", "Select a document", "pdf",
            "استخراج النص من PDF مستند"),
        TemplateDescriptor("telegram", "Telegram message", "Send message", "messaging",
            "رسالة تيليجرام"),
        TemplateDescriptor("image", "Compress image", "Reduce file size", "images",
            "ضغط الصور"),
    )

    @Test fun arabicAliasFindsPdfTemplate() {
        assertEquals(listOf("pdf"), TemplateLibraryFilter.select(
            entries, "استخراج", TemplateLibraryFilter.ALL, emptySet(), emptyList()
        ).map { it.id })
    }

    @Test fun englishAliasFindsTelegram() {
        assertEquals(listOf("telegram"), TemplateLibraryFilter.select(
            entries, "Telegram", TemplateLibraryFilter.ALL, emptySet(), emptyList()
        ).map { it.id })
    }

    @Test fun favoriteFilterDoesNotLeakOtherTemplates() {
        assertEquals(listOf("pdf"), TemplateLibraryFilter.select(
            entries, "", TemplateLibraryFilter.FAVORITES, setOf("pdf"), emptyList()
        ).map { it.id })
    }

    @Test fun recentOrderIsPreserved() {
        assertEquals(listOf("image", "pdf"), TemplateLibraryFilter.select(
            entries, "", TemplateLibraryFilter.RECENT, emptySet(), listOf("image", "pdf")
        ).map { it.id })
    }

    @Test fun unorderedMixedLanguageQueryMatchesAcrossFields() {
        assertEquals(listOf("pdf"), TemplateLibraryFilter.select(
            entries, "PDF استخراج", TemplateLibraryFilter.ALL, emptySet(), emptyList()
        ).map { it.id })
        assertEquals(listOf("pdf"), TemplateLibraryFilter.select(
            entries, "استخراج PDF", TemplateLibraryFilter.ALL, emptySet(), emptyList()
        ).map { it.id })
    }

    @Test fun allTermsMustMatch() {
        assertTrue(TemplateLibraryFilter.select(
            entries, "PDF Telegram", TemplateLibraryFilter.ALL, emptySet(), emptyList()
        ).isEmpty())
    }

    @Test fun presetsHaveUniqueIdsAndCategories() {
        assertEquals(TemplateToolPresets.entries.size, TemplateToolPresets.entries.map { it.id }.toSet().size)
        assertTrue(TemplateToolPresets.entries.all { it.id.isNotBlank() && it.category in setOf("pdf", "images", "files", "quick") })
    }

    @Test fun presetsHaveBilingualLabels() {
        assertTrue(TemplateToolPresets.entries.all {
            it.titleAr.isNotBlank() && it.titleEn.isNotBlank() &&
                it.descriptionAr.isNotBlank() && it.descriptionEn.isNotBlank()
        })
    }
}
