package com.explapp.shortcut.usage

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
                Text(local("Last 7 days", "آخر 7 أيام"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                val max = dashboard.days.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dashboard.days.forEach { day ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dayLabel(day.dayStartMs), modifier = Modifier.size(width = 46.dp, height = 24.dp), style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(progress = { day.totalMs.toFloat() / max.toFloat() }, modifier = Modifier.weight(1f))
                            Text(formatDuration(day.totalMs, ar), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item { Text(local("Top apps today", "أكثر التطبيقات اليوم"), style = MaterialTheme.typography.titleLarge) }
            dashboard.ranked.forEachIndexed { index, item ->
                item(key = item.packageName) {
                    val label = runCatching {
                        val info = context.packageManager.getApplicationInfo(item.packageName, 0)
                        context.packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(item.packageName)
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
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

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
