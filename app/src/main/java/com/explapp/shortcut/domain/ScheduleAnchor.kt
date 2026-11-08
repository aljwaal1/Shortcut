package com.explapp.shortcut.domain

import java.time.ZonedDateTime

object ScheduleAnchor {
    fun isExpiredOneShot(
        now: ZonedDateTime,
        repeat: RepeatOption,
        oneShotEpochDay: Long?,
        hour: Int,
        minute: Int,
    ): Boolean {
        if (repeat != RepeatOption.ONCE || oneShotEpochDay == null) return false
        val scheduled = java.time.LocalDate.ofEpochDay(oneShotEpochDay)
            .atTime(hour, minute)
            .atZone(now.zone)
        return !scheduled.isAfter(now)
    }

    fun nextOneShotEpochDay(now: ZonedDateTime, hour: Int, minute: Int): Long {
        val todayAtTime = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        return if (todayAtTime.isAfter(now)) {
            now.toLocalDate().toEpochDay()
        } else {
            now.toLocalDate().plusDays(1).toEpochDay()
        }
    }
}
