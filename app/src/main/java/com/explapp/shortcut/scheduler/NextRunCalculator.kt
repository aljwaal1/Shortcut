package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import java.time.ZonedDateTime

object NextRunCalculator {
    fun nextRun(now: ZonedDateTime, shortcut: ScheduledAppShortcut): ZonedDateTime =
        nextRun(now, shortcut.hour, shortcut.minute, shortcut.repeat)

    fun nextRun(now: ZonedDateTime, message: ScheduledMessage): ZonedDateTime =
        nextRun(now, message.hour, message.minute, message.repeat)

    private fun nextRun(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        repeat: RepeatOption,
    ): ZonedDateTime {
        val todayAtTime = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        return when (repeat) {
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
                while (candidate.dayOfWeek.value !in repeat.isoWeekdays) {
                    candidate = candidate.plusDays(1)
                }
                candidate
            }
        }
    }
}
