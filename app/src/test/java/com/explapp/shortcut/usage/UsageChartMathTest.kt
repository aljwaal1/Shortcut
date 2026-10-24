package com.explapp.shortcut.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageChartMathTest {
    @Test
    fun bars_are_normalized_against_largest_day() {
        assertEquals(listOf(0f, 0.5f, 1f), UsageChartMath.normalizedBars(listOf(0L, 50L, 100L)))
    }

    @Test
    fun donut_keeps_top_five_and_groups_remainder_as_other() {
        val items = listOf(40L, 25L, 15L, 10L, 5L, 3L, 2L)
        val slices = UsageChartMath.donutSlices(items, maxNamedSlices = 5)

        assertEquals(listOf(40L, 25L, 15L, 10L, 5L, 5L), slices)
        assertEquals(100L, slices.sum())
    }

    @Test
    fun donut_ignores_zero_and_negative_durations() {
        assertEquals(listOf(10L), UsageChartMath.donutSlices(listOf(10L, 0L, -5L), maxNamedSlices = 5))
    }
}
