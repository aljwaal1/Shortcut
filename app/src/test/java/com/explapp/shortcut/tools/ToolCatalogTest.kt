package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun containsEveryApprovedSeptember16ToolInFourSections() {
        val tools = ToolCatalog.all()
        assertEquals(28, tools.size)
        assertEquals(4, tools.map { it.section }.distinct().size)
        assertTrue(ToolId.entries.all { id -> tools.any { it.id == id } })
    }

    @Test
    fun appUsageAndMyAutomationsAreSeparateTools() {
        val tools = ToolCatalog.all()
        val usage = tools.single { it.id == ToolId.APP_OPEN_ROUTINE }
        val automations = tools.single { it.id == ToolId.MY_AUTOMATIONS }

        assertEquals("App usage time", usage.titleEn)
        assertEquals("My automations", automations.titleEn)
        assertTrue(usage.titleAr != automations.titleAr)
    }
}
