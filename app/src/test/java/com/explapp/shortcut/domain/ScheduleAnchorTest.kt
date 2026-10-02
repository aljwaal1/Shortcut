package com.explapp.shortcut.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleAnchorTest {
    private val zone = ZoneId.of("Europe/Stockholm")

    @Test
    fun oneShotUsesTodayWhenSelectedTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 10, 2, 10, 0, 0, 0, zone)
        val epochDay = ScheduleAnchor.nextOneShotEpochDay(now, 14, 30)
        assertEquals(now.toLocalDate().toEpochDay(), epochDay)
    }

    @Test
    fun oneShotUsesTomorrowWhenSelectedTimeAlreadyPassed() {
        val now = ZonedDateTime.of(2026, 10, 2, 16, 0, 0, 0, zone)
        val epochDay = ScheduleAnchor.nextOneShotEpochDay(now, 14, 30)
        assertEquals(now.toLocalDate().plusDays(1).toEpochDay(), epochDay)
    }
}
