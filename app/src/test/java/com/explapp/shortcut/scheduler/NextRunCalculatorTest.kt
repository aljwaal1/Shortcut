package com.explapp.shortcut.scheduler

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextRunCalculatorTest {

    private val zone = ZoneId.of("Europe/Stockholm")

    @Test
    fun onceScheduleMovesToTomorrowWhenTimePassed() {
        val now = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zone)
        val shortcut = ScheduledAppShortcut(
            name = "Maps",
            packageName = "pkg",
            hour = 7,
            minute = 30,
            repeat = RepeatOption.ONCE,
        )

        val next = NextRunCalculator.nextRun(now, shortcut)

        assertEquals(ZonedDateTime.of(2026, 9, 17, 7, 30, 0, 0, zone), next)
    }

    @Test
    fun dailyScheduleMovesToTomorrowWhenTimePassed() {
        val now = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zone)
        val shortcut = ScheduledAppShortcut(
            name = "Maps",
            packageName = "pkg",
            hour = 7,
            minute = 30,
            repeat = RepeatOption.DAILY,
        )

        val next = NextRunCalculator.nextRun(now, shortcut)

        assertEquals(ZonedDateTime.of(2026, 9, 17, 7, 30, 0, 0, zone), next)
    }

    @Test
    fun weekdaysScheduleSkipsWeekend() {
        val friday = ZonedDateTime.of(2026, 9, 18, 20, 0, 0, 0, zone)
        val shortcut = ScheduledAppShortcut(
            name = "Maps",
            packageName = "pkg",
            hour = 7,
            minute = 30,
            repeat = RepeatOption.WEEKDAYS,
        )

        val next = NextRunCalculator.nextRun(friday, shortcut)

        assertEquals(ZonedDateTime.of(2026, 9, 21, 7, 30, 0, 0, zone), next)
    }
}
