package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class RestorePolicyTest {
    @Test
    fun pausedShortcutsAreNotRestored() {
        val enabled = ScheduledAppShortcut("a", "Enabled", "pkg", 8, 0, RepeatOption.DAILY, true)
        val paused = ScheduledAppShortcut("b", "Paused", "pkg", 9, 0, RepeatOption.DAILY, false)

        assertEquals(listOf(enabled), RestorePolicy.shortcuts(listOf(enabled, paused)))
    }

    @Test
    fun pausedMessagesAreNotRestored() {
        val enabled = ScheduledMessage("a", "Enabled", MessagePlatform.TELEGRAM, "u", "x", 8, 0, RepeatOption.DAILY, true)
        val paused = ScheduledMessage("b", "Paused", MessagePlatform.TELEGRAM, "u", "x", 9, 0, RepeatOption.DAILY, false)

        assertEquals(listOf(enabled), RestorePolicy.messages(listOf(enabled, paused)))
    }
}
