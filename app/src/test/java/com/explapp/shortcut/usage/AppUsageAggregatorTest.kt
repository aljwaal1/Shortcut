package com.explapp.shortcut.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageAggregatorTest {
    @Test
    fun aggregatesDurationsByPackageAndSortsLongestFirst() {
        val result = AppUsageAggregator.aggregate(
            listOf(
                AppUsageSample("a", 1_000L),
                AppUsageSample("b", 5_000L),
                AppUsageSample("a", 2_500L),
            ),
        )
        assertEquals(listOf("b", "a"), result.map { it.packageName })
        assertEquals(3_500L, result.first { it.packageName == "a" }.durationMs)
    }
}
