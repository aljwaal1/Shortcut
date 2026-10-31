package com.explapp.shortcut.usage

object UsageChartMath {
    fun normalizedBars(values: List<Long>): List<Float> {
        val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        return values.map { value -> (value.coerceAtLeast(0L).toFloat() / max.toFloat()).coerceIn(0f, 1f) }
    }

    fun donutSlices(values: List<Long>, maxNamedSlices: Int = 5): List<Long> {
        val positive = values.filter { it > 0L }
        if (positive.isEmpty()) return emptyList()
        val head = positive.take(maxNamedSlices.coerceAtLeast(1))
        val rest = positive.drop(head.size).sum()
        return if (rest > 0L) head + rest else head
    }
}
