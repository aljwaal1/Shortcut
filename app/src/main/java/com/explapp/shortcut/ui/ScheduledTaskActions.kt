package com.explapp.shortcut.ui

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.scheduler.NextRunCalculator
import java.time.ZonedDateTime

object ScheduledTaskActions {
    fun applyShortcutEdit(original: ScheduledAppShortcut, draft: ScheduledAppShortcut): ScheduledAppShortcut =
        draft.copy(id = original.id, isEnabled = original.isEnabled)

    fun applyMessageEdit(original: ScheduledMessage, draft: ScheduledMessage): ScheduledMessage =
        draft.copy(id = original.id, isEnabled = original.isEnabled)

    fun toggle(shortcut: ScheduledAppShortcut): ScheduledAppShortcut =
        shortcut.copy(isEnabled = !shortcut.isEnabled)

    fun toggle(message: ScheduledMessage): ScheduledMessage =
        message.copy(isEnabled = !message.isEnabled)

    fun duplicate(shortcut: ScheduledAppShortcut, newId: String): ScheduledAppShortcut =
        shortcut.duplicate(newId)

    fun duplicate(message: ScheduledMessage, newId: String): ScheduledMessage =
        message.duplicate(newId)

    fun nextRun(now: ZonedDateTime, shortcut: ScheduledAppShortcut): ZonedDateTime? =
        if (shortcut.isEnabled) NextRunCalculator.nextRun(now, shortcut) else null

    fun nextRun(now: ZonedDateTime, message: ScheduledMessage): ZonedDateTime? =
        if (message.isEnabled) NextRunCalculator.nextRun(now, message) else null
}
