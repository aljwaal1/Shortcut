package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun containsEveryApprovedSeptember16ToolInFourSections() {
        val tools = ToolCatalog.all()
        assertEquals(27, tools.size)
        assertEquals(4, tools.map { it.section }.distinct().size)
        assertTrue(ToolId.entries.all { id -> tools.any { it.id == id } })
    }
}
