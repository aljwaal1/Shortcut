package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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
    @Test
    fun expiredAnchoredOneShotIsPausedDuringRestore() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 2, 12, 0, 0, 0, zone)
        val expired = ScheduledAppShortcut(
            id = "once",
            name = "Once",
            packageName = "pkg",
            hour = 9,
            minute = 0,
            repeat = RepeatOption.ONCE,
            isEnabled = true,
            oneShotEpochDay = now.toLocalDate().toEpochDay(),
        )

        val reconciled = RestorePolicy.pauseExpiredOneShots(now, listOf(expired))

        assertEquals(false, reconciled.single().isEnabled)
    }

    @Test
    fun futureAnchoredOneShotRemainsEnabledDuringRestore() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, zone)
        val future = ScheduledMessage(
            id = "once-message",
            name = "Once",
            platform = MessagePlatform.TELEGRAM,
            recipient = "user",
            message = "hello",
            hour = 9,
            minute = 0,
            repeat = RepeatOption.ONCE,
            isEnabled = true,
            oneShotEpochDay = now.toLocalDate().toEpochDay(),
        )

        val reconciled = RestorePolicy.pauseExpiredMessageOneShots(now, listOf(future))

        assertEquals(true, reconciled.single().isEnabled)
    }
}

