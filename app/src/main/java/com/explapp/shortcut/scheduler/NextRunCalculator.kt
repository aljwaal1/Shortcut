package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.time.ZonedDateTime

object NextRunCalculator {
    fun nextRun(
        now: ZonedDateTime,
        shortcut: ScheduledAppShortcut,
    ): ZonedDateTime {
        val todayAtTime = now
            .withHour(shortcut.hour)
            .withMinute(shortcut.minute)
            .withSecond(0)
            .withNano(0)

        return when (shortcut.repeat) {
            RepeatOption.ONCE,
            RepeatOption.DAILY,
            -> if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)

            RepeatOption.WEEKLY -> if (todayAtTime.isAfter(now)) {
                todayAtTime
            } else {
                todayAtTime.plusWeeks(1)
            }

            RepeatOption.WEEKDAYS -> {
                var candidate = if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
                while (candidate.dayOfWeek.value !in shortcut.repeat.isoWeekdays) {
                    candidate = candidate.plusDays(1)
                }
                candidate
            }
        }
    }
}
