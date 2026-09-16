package com.explapp.shortcut.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AppUsageDashboard(
    enabled: Boolean,
    hasAccess: Boolean,
    dashboard: UsageDashboard?,
    onToggle: (Boolean) -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val local: (String, String) -> String = { en, arabic -> if (ar) arabic else en }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(local("App usage", "استخدام التطبيقات"), style = MaterialTheme.typography.headlineMedium)
            Text(local("Your usage stays on this device", "إحصائياتك تبقى على هذا الجهاز"), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(local("Usage tracking", "تتبع الاستخدام"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                enabled && hasAccess -> local("Enabled", "مفعّل")
                                enabled -> local("Permission required", "تحتاج صلاحية الاستخدام")
                                else -> local("Disabled", "غير مفعّل")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
            }
        }
        if (enabled && !hasAccess) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(local("Usage Access required", "صلاحية Usage Access مطلوبة"), style = MaterialTheme.typography.titleMedium)
                        Text(local(
                            "Android requires Usage Access to calculate how long apps stay in the foreground.",
                            "يحتاج Android إلى Usage Access لحساب مدة بقاء التطبيقات في الواجهة.",
                        ))
                        Button(onClick = onOpenUsageAccess) { Text(local("Open settings", "فتح الإعدادات")) }
                    }
                }
            }
        }
        if (enabled && hasAccess && dashboard != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(local("Today", "اليوم"), formatDuration(dashboard.todayMs, ar), Modifier.weight(1f))
                    MetricCard(local("Yesterday", "أمس"), formatDuration(dashboard.yesterdayMs, ar), Modifier.weight(1f))
                }
            }
            item {
                val delta = dashboard.deltaMs
                val comparison = when {
                    delta > 0 -> local("↑ ${formatDuration(delta, ar)} more than yesterday", "↑ ${formatDuration(delta, ar)} أكثر من أمس")
                    delta < 0 -> local("↓ ${formatDuration(-delta, ar)} less than yesterday", "↓ ${formatDuration(-delta, ar)} أقل من أمس")
                    else -> local("Same as yesterday", "مثل أمس")
                }
                Text(comparison, style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(local("Last 7 days", "آخر 7 أيام"), style = MaterialTheme.typography.titleLarge)
                        WeeklyBarChart(dashboard.days, ar)
                    }
                }
            }
            if (dashboard.ranked.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(local("Today's usage by app", "استخدام اليوم حسب التطبيقات"), style = MaterialTheme.typography.titleLarge)
                            AppUsageDonut(dashboard, ar)
                        }
                    }
                }
            }
            item { Text(local("Top apps today", "أكثر التطبيقات اليوم"), style = MaterialTheme.typography.titleLarge) }
            dashboard.ranked.forEachIndexed { index, item ->
                item(key = item.packageName) {
                    val label = appLabel(context, item.packageName)
                    val icon = runCatching { context.packageManager.getApplicationIcon(item.packageName).toBitmap(72, 72).asImageBitmap() }.getOrNull()
                    val percentage = if (dashboard.todayMs > 0L) item.durationMs * 100.0 / dashboard.todayMs else 0.0
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. $label", style = MaterialTheme.typography.titleMedium)
                                LinearProgressIndicator(progress = { (percentage / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatDuration(item.durationMs, ar), style = MaterialTheme.typography.labelLarge)
                                Text("%.0f%%".format(percentage), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (dashboard.ranked.isEmpty()) {
                item { Text(local("No usage data yet", "لا توجد بيانات استخدام بعد")) }
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WeeklyBarChart(days: List<DailyUsage>, ar: Boolean) {
    val heights = UsageChartMath.normalizedBars(days.map { it.totalMs })
    val barColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(formatDuration(day.totalMs, ar), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.height(138.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .width(24.dp)
                            .fillMaxHeight(heights.getOrElse(index) { 0f }.coerceAtLeast(0.035f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(barColor),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(dayLabel(day.dayStartMs), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AppUsageDonut(dashboard: UsageDashboard, ar: Boolean) {
    val context = LocalContext.current
    val top = dashboard.ranked.filter { it.durationMs > 0L }.take(5)
    val otherMs = dashboard.ranked.drop(top.size).sumOf { it.durationMs.coerceAtLeast(0L) }
    val durations = UsageChartMath.donutSlices(dashboard.ranked.map { it.durationMs }, 5)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.outline,
    )
    val total = durations.sum().coerceAtLeast(1L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                durations.forEachIndexed { index, value ->
                    val sweep = value.toFloat() / total.toFloat() * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 34.dp.toPx(), cap = StrokeCap.Butt),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ar) "اليوم" else "Today", style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(dashboard.todayMs, ar), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            top.forEachIndexed { index, item ->
                DonutLegendRow(
                    color = colors[index % colors.size],
                    label = appLabel(context, item.packageName),
                    duration = item.durationMs,
                    total = dashboard.todayMs,
                    ar = ar,
                )
            }
            if (otherMs > 0L) {
                DonutLegendRow(
                    color = colors[5],
                    label = if (ar) "أخرى" else "Other",
                    duration = otherMs,
                    total = dashboard.todayMs,
                    ar = ar,
                )
            }
        }
    }
}

@Composable
private fun DonutLegendRow(color: Color, label: String, duration: Long, total: Long, ar: Boolean) {
    val percent = if (total > 0L) duration * 100.0 / total else 0.0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(formatDuration(duration, ar), style = MaterialTheme.typography.bodySmall)
        }
        Text("%.0f%%".format(percent), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun appLabel(context: android.content.Context, packageName: String): String = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

private fun formatDuration(ms: Long, ar: Boolean): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (ar) {
        if (hours > 0) "${hours}س ${minutes}د" else "${minutes}د"
    } else {
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

private fun dayLabel(time: Long): String = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(time))
