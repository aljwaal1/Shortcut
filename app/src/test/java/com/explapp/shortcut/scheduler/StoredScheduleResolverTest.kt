package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StoredScheduleResolverTest {
    @Test
    fun deletedShortcutAlarmDoesNotResolve() {
        assertNull(StoredScheduleResolver.shortcut("old", emptyList()))
    }

    @Test
    fun resolvesCurrentStoredMessageInsteadOfStaleIntentPayload() {
        val current = ScheduledMessage(
            id = "m1",
            name = "Current",
            platform = MessagePlatform.TELEGRAM,
            recipient = "user",
            message = "new text",
            hour = 9,
            minute = 0,
            repeat = RepeatOption.DAILY,
        )
        assertSame(current, StoredScheduleResolver.message("m1", listOf(current)))
    }

    @Test
    fun disabledStoredShortcutDoesNotResolve() {
        val paused = ScheduledAppShortcut(
            id = "s1",
            name = "Maps",
            packageName = "pkg",
            hour = 8,
            minute = 0,
            repeat = RepeatOption.DAILY,
            isEnabled = false,
        )
        assertNull(StoredScheduleResolver.shortcut("s1", listOf(paused)))
    }
}
