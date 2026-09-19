package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage

object StoredScheduleResolver {
    fun shortcut(id: String, items: List<ScheduledAppShortcut>): ScheduledAppShortcut? =
        items.firstOrNull { it.id == id && it.isEnabled }

    fun message(id: String, items: List<ScheduledMessage>): ScheduledMessage? =
        items.firstOrNull { it.id == id && it.isEnabled }
}
