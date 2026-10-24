package com.explapp.shortcut.ui

import com.explapp.shortcut.tools.ToolSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VisualIdentityTest {
    @Test
    fun everyToolSectionHasDistinctVisualIdentity() {
        val styles = ToolSection.entries.map { VisualIdentity.forSection(it) }

        assertEquals(4, styles.size)
        assertEquals(4, styles.map { it.accentArgb }.distinct().size)
        assertEquals(4, styles.map { it.softArgb }.distinct().size)
        assertNotEquals(styles.first().accentArgb, styles.last().accentArgb)
    }
}
