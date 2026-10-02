package com.explapp.shortcut.domain

import java.time.ZonedDateTime

object ScheduleAnchor {
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
