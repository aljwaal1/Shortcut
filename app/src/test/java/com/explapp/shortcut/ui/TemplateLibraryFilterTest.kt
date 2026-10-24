package com.explapp.shortcut.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateLibraryFilterTest {
    private val entries = listOf(
        TemplateDescriptor("telegram", "Telegram message", "Send a message", "messaging"),
        TemplateDescriptor("pdf", "Merge PDF", "Combine files", "media"),
        TemplateDescriptor("clock", "Scheduled app", "Open on time", "automation"),
    )

    @Test fun searchMatchesTitleAndDescriptionIgnoringCase() {
        assertEquals(listOf("telegram"), TemplateLibraryFilter.select(
            entries, "  SEND  ", TemplateLibraryFilter.ALL, emptySet(), emptyList(),
        ).map { it.id })
    }

    @Test fun favoritesOnlyReturnsSavedTemplateIds() {
        assertEquals(listOf("pdf"), TemplateLibraryFilter.select(
            entries, "", TemplateLibraryFilter.FAVORITES, setOf("pdf"), emptyList(),
        ).map { it.id })
    }

    @Test fun recentIsSortedByLastUse() {
        assertEquals(listOf("clock", "telegram"), TemplateLibraryFilter.select(
            entries, "", TemplateLibraryFilter.RECENT, emptySet(), listOf("clock", "telegram"),
        ).map { it.id })
    }

    @Test fun categoryAndSearchMustBothMatch() {
        assertEquals(emptyList<String>(), TemplateLibraryFilter.select(
            entries, "PDF", "messaging", emptySet(), emptyList(),
        ).map { it.id })
    }
}
