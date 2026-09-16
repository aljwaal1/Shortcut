package com.explapp.shortcut.usage

data class DailyUsage(val dayStartMs: Long, val totalMs: Long)

data class AppUsageShare(
    val packageName: String,
    val durationMs: Long,
    val percentage: Double,
)

data class UsageDashboard(
    val todayMs: Long,
    val yesterdayMs: Long,
    val deltaMs: Long,
    val days: List<DailyUsage>,
    val ranked: List<AppUsageTotal>,
    val topFive: List<AppUsageShare>,
)

object AppUsageMetrics {
    fun build(
        samplesByDay: Map<Long, List<AppUsageSample>>,
        todayStartMs: Long,
        yesterdayStartMs: Long,
        dayDurationMs: Long = 86_400_000L,
    ): UsageDashboard {
        val rankedToday = AppUsageAggregator.aggregate(samplesByDay[todayStartMs].orEmpty())
        val todayMs = rankedToday.sumOf { it.durationMs }
        val yesterdayMs = AppUsageAggregator.aggregate(samplesByDay[yesterdayStartMs].orEmpty()).sumOf { it.durationMs }
        val firstDay = todayStartMs - 6L * dayDurationMs
        val days = (0L..6L).map { offset ->
            val start = firstDay + offset * dayDurationMs
            DailyUsage(start, AppUsageAggregator.aggregate(samplesByDay[start].orEmpty()).sumOf { it.durationMs })
        }
        val topFive = rankedToday.take(5).map { item ->
            AppUsageShare(
                packageName = item.packageName,
                durationMs = item.durationMs,
                percentage = if (todayMs > 0L) item.durationMs * 100.0 / todayMs else 0.0,
            )
        }
        return UsageDashboard(
            todayMs = todayMs,
            yesterdayMs = yesterdayMs,
            deltaMs = todayMs - yesterdayMs,
            days = days,
            ranked = rankedToday,
            topFive = topFive,
        )
    }
}
