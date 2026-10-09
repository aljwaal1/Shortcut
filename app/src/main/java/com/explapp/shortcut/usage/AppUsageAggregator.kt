package com.explapp.shortcut.usage

data class AppUsageSample(val packageName: String, val durationMs: Long)
data class AppUsageTotal(val packageName: String, val durationMs: Long)

object AppUsageAggregator {
    fun aggregate(samples: List<AppUsageSample>): List<AppUsageTotal> = samples
        .groupBy { it.packageName }
        .map { (packageName, items) -> AppUsageTotal(packageName, items.sumOf { it.durationMs.coerceAtLeast(0L) }) }
        .filter { it.durationMs > 0L }
        .sortedByDescending { it.durationMs }
}
