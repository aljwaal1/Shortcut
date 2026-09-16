package com.explapp.shortcut.backup

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupRestorePlanTest {
    @Test
    fun importedBackupReplacesLocalCollectionsAndKeepsAdvancedDisabled() {
        val importedShortcut = ScheduledAppShortcut("Imported", "com.example.imported", 7, 15, RepeatOption.DAILY)
        val payload = BackupPayload(
            shortcuts = listOf(importedShortcut),
            messages = emptyList(),
            unlockWifiMapsEnabled = true,
        )

        val plan = BackupRestorePlan.from(payload)

        assertEquals(listOf(importedShortcut), plan.shortcuts)
        assertEquals(emptyList<Any>(), plan.messages)
        assertFalse(plan.unlockWifiMapsEnabled)
    }
}
