package com.explapp.shortcut.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageMetricsTest {
    @Test
    fun buildsTodayYesterdaySevenDaysAndTopShares() {
        val day = 86_400_000L
        val today = 7 * day
        val yesterday = 6 * day
        val samples = mapOf(
            today to listOf(
                AppUsageSample("a", 3_600_000L),
                AppUsageSample("b", 1_800_000L),
                AppUsageSample("a", 600_000L),
            ),
            yesterday to listOf(
                AppUsageSample("a", 1_200_000L),
                AppUsageSample("c", 1_800_000L),
            ),
            5 * day to listOf(AppUsageSample("d", 900_000L)),
        )

        val dashboard = AppUsageMetrics.build(samples, today, yesterday, day)

        assertEquals(6_000_000L, dashboard.todayMs)
        assertEquals(3_000_000L, dashboard.yesterdayMs)
        assertEquals(3_000_000L, dashboard.deltaMs)
        assertEquals(7, dashboard.days.size)
        assertEquals(6_000_000L, dashboard.days.last().totalMs)
        assertEquals("a", dashboard.ranked.first().packageName)
        assertEquals(4_200_000L, dashboard.ranked.first().durationMs)
        assertEquals(70.0, dashboard.topFive.first().percentage, 0.001)
    }
}
