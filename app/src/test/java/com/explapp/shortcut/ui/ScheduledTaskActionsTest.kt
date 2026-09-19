package com.explapp.shortcut.ui

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledTaskActionsTest {
    @Test
    fun editingShortcutPreservesStableId() {
        val original = ScheduledAppShortcut("id-a", "Maps", "maps", 8, 0, RepeatOption.DAILY)
        val draft = ScheduledAppShortcut("different", "Maps work", "maps", 9, 15, RepeatOption.WEEKDAYS)
        val edited = ScheduledTaskActions.applyShortcutEdit(original, draft)
        assertEquals("id-a", edited.id)
        assertEquals("Maps work", edited.name)
    }

    @Test
    fun editingMessagePreservesStableIdAndEnabledState() {
        val original = ScheduledMessage("m-a", "Msg", MessagePlatform.TELEGRAM, "user", "hello", 8, 0, RepeatOption.DAILY, false)
        val draft = original.copy(id = "wrong", message = "updated", isEnabled = true)
        val edited = ScheduledTaskActions.applyMessageEdit(original, draft)
        assertEquals("m-a", edited.id)
        assertFalse(edited.isEnabled)
        assertEquals("updated", edited.message)
    }

    @Test
    fun toggleChangesEnabledOnly() {
        val original = ScheduledAppShortcut("id-a", "Maps", "maps", 8, 0, RepeatOption.DAILY)
        val paused = ScheduledTaskActions.toggle(original)
        assertFalse(paused.isEnabled)
        assertEquals(original.id, paused.id)
        assertEquals(original.hour, paused.hour)
    }

    @Test
    fun duplicateHasNewId() {
        val original = ScheduledAppShortcut("id-a", "Maps", "maps", 8, 0, RepeatOption.DAILY)
        val copy = ScheduledTaskActions.duplicate(original, "id-b")
        assertNotEquals(original.id, copy.id)
        assertTrue(copy.isEnabled)
    }

    @Test
    fun nextRunIsFutureForEnabledTaskAndNullForPausedTask() {
        val now = ZonedDateTime.of(2026, 9, 17, 10, 0, 0, 0, ZoneId.of("UTC"))
        val enabled = ScheduledAppShortcut("id-a", "Maps", "maps", 9, 0, RepeatOption.DAILY)
        val paused = enabled.copy(isEnabled = false)

        assertTrue(ScheduledTaskActions.nextRun(now, enabled)!!.isAfter(now))
        assertEquals(null, ScheduledTaskActions.nextRun(now, paused))
    }
}
