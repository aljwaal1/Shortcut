package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object NextRunCalculator {
    fun nextRun(now: ZonedDateTime, shortcut: ScheduledAppShortcut): ZonedDateTime =
        nextRun(now, shortcut.hour, shortcut.minute, shortcut.repeat, shortcut.weeklyDayIso)

    fun nextRun(now: ZonedDateTime, message: ScheduledMessage): ZonedDateTime =
        nextRun(now, message.hour, message.minute, message.repeat, message.weeklyDayIso)

    private fun nextRun(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        repeat: RepeatOption,
        weeklyDayIso: Int?,
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

            RepeatOption.WEEKLY -> {
                val targetDay = DayOfWeek.of(weeklyDayIso ?: now.dayOfWeek.value)
                var candidate = now
                    .with(TemporalAdjusters.nextOrSame(targetDay))
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
                if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
                candidate
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
