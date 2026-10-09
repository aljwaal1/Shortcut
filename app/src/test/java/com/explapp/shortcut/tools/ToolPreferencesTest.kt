package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPreferencesTest {
    @Test
    fun favoriteToggleAddsThenRemovesTool() {
        val first = ToolPreferenceLogic.toggleFavorite(emptySet(), ToolId.MERGE_IMAGES)
        assertTrue(ToolId.MERGE_IMAGES in first)
        val second = ToolPreferenceLogic.toggleFavorite(first, ToolId.MERGE_IMAGES)
        assertFalse(ToolId.MERGE_IMAGES in second)
    }

    @Test
    fun recentsAreUniqueNewestFirstAndCappedAtEight() {
        var recents = emptyList<ToolId>()
        val ids = ToolId.entries.take(9)
        ids.forEach { recents = ToolPreferenceLogic.recordRecent(recents, it) }
        assertEquals(8, recents.size)
        assertEquals(ids.last(), recents.first())
        assertFalse(ids.first() in recents)

        val existing = recents.last()
        val moved = ToolPreferenceLogic.recordRecent(recents, existing)
        assertEquals(existing, moved.first())
        assertEquals(8, moved.size)
    }
}
