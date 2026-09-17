package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage

object RestorePolicy {
    fun shortcuts(items: List<ScheduledAppShortcut>): List<ScheduledAppShortcut> =
        items.filter { it.isEnabled && it.isValid() }

    fun messages(items: List<ScheduledMessage>): List<ScheduledMessage> =
        items.filter { it.isEnabled && it.isValid() }
}
