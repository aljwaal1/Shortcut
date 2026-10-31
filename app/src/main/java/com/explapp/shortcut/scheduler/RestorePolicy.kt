package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.ScheduleAnchor
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import java.time.ZonedDateTime

object RestorePolicy {
    fun shortcuts(items: List<ScheduledAppShortcut>): List<ScheduledAppShortcut> =
        items.filter { it.isEnabled && it.isValid() }

    fun messages(items: List<ScheduledMessage>): List<ScheduledMessage> =
        items.filter { it.isEnabled && it.isValid() }

    fun pauseExpiredOneShots(
        now: ZonedDateTime,
        items: List<ScheduledAppShortcut>,
    ): List<ScheduledAppShortcut> =
        items.map { item ->
            if (
                item.isEnabled &&
                ScheduleAnchor.isExpiredOneShot(
                    now = now,
                    repeat = item.repeat,
                    oneShotEpochDay = item.oneShotEpochDay,
                    hour = item.hour,
                    minute = item.minute,
                )
            ) {
                item.copy(isEnabled = false)
            } else {
                item
            }
        }

    fun pauseExpiredMessageOneShots(
        now: ZonedDateTime,
        items: List<ScheduledMessage>,
    ): List<ScheduledMessage> =
        items.map { item ->
            if (
                item.isEnabled &&
                ScheduleAnchor.isExpiredOneShot(
                    now = now,
                    repeat = item.repeat,
                    oneShotEpochDay = item.oneShotEpochDay,
                    hour = item.hour,
                    minute = item.minute,
                )
            ) {
                item.copy(isEnabled = false)
            } else {
                item
            }
        }
}
