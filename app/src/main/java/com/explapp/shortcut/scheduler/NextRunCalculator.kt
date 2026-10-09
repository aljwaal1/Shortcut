package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object NextRunCalculator {
    fun nextRun(now: ZonedDateTime, shortcut: ScheduledAppShortcut): ZonedDateTime? =
        nextRun(
            now = now,
            hour = shortcut.hour,
            minute = shortcut.minute,
            repeat = shortcut.repeat,
            weeklyDayIso = shortcut.weeklyDayIso,
            oneShotEpochDay = shortcut.oneShotEpochDay,
        )

    fun nextRun(now: ZonedDateTime, message: ScheduledMessage): ZonedDateTime? =
        nextRun(
            now = now,
            hour = message.hour,
            minute = message.minute,
            repeat = message.repeat,
            weeklyDayIso = message.weeklyDayIso,
            oneShotEpochDay = message.oneShotEpochDay,
        )

    private fun nextRun(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        repeat: RepeatOption,
        weeklyDayIso: Int?,
        oneShotEpochDay: Long?,
    ): ZonedDateTime? {
        val todayAtTime = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        return when (repeat) {
            RepeatOption.ONCE -> {
                if (oneShotEpochDay == null) {
                    if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
                } else {
                    val anchored = LocalDate.ofEpochDay(oneShotEpochDay)
                        .atTime(hour, minute)
                        .atZone(now.zone)
                        .withSecond(0)
                        .withNano(0)
                    anchored.takeIf { it.isAfter(now) }
                }
            }

            RepeatOption.DAILY -> if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)

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
