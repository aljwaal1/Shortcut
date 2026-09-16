package com.explapp.shortcut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.execution.TaskExecutionHistory
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun ExecutionHistoryScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val reporter = remember(context) { TaskExecutionReporter(context.applicationContext) }
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf<TaskExecutionStatus?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val all = remember(refresh) { reporter.history() }
    val records = remember(all, filter) { TaskExecutionHistory.filter(all, filter) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (ar) "سجل التنفيذ" else "Execution history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (ar) "آخر ${all.size} نتيجة محفوظة محليًا" else "${all.size} locally saved results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (all.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(if (ar) "مسح" else "Clear") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(if (ar) "الكل" else "All") })
                FilterChip(selected = filter == TaskExecutionStatus.SUCCESS, onClick = { filter = TaskExecutionStatus.SUCCESS }, label = { Text(if (ar) "ناجح" else "Success") })
                FilterChip(selected = filter == TaskExecutionStatus.FAILURE, onClick = { filter = TaskExecutionStatus.FAILURE }, label = { Text(if (ar) "فاشل" else "Failure") })
            }
        }
        if (records.isEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (ar) "لا توجد نتائج ضمن هذا الفلتر." else "No results for this filter.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(records, key = { "${it.finishedAtMs}-${it.taskName}-${it.status}" }) { result ->
            val success = result.status == TaskExecutionStatus.SUCCESS
            val accent = if (success) Color(0xFF1B9C68) else MaterialTheme.colorScheme.error
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.08f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            (if (success) "✅ " else "❌ ") + result.taskName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            durationLabel(result.durationMs, ar),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                        )
                    }
                    result.reason?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.finishedAtMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(if (ar) "مسح سجل التنفيذ؟" else "Clear execution history?") },
            text = { Text(if (ar) "سيتم حذف النتائج المحفوظة فقط، ولن تُحذف المهام المجدولة." else "Only saved results will be deleted. Scheduled tasks stay unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    reporter.clearHistory()
                    refresh++
                    confirmClear = false
                }) { Text(if (ar) "مسح" else "Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(if (ar) "إلغاء" else "Cancel") } },
        )
    }
}

private fun durationLabel(ms: Long, ar: Boolean): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000.0)
    return if (ar) "%.1f ث".format(seconds) else "%.1fs".format(seconds)
}
