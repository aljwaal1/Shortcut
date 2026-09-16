package com.explapp.shortcut.execution

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class TaskExecutionReporter(private val context: Context) {
    private val prefs = context.getSharedPreferences("task_execution_results", Context.MODE_PRIVATE)

    fun report(result: TaskExecutionResult) {
        prefs.edit().putString("last", JSONObject().apply {
            put("taskName", result.taskName)
            put("status", result.status.name)
            put("reason", result.reason)
            put("startedAtMs", result.startedAtMs)
            put("finishedAtMs", result.finishedAtMs)
        }.toString()).apply()
        notify(result)
    }

    fun last(): TaskExecutionResult? {
        val raw = prefs.getString("last", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            TaskExecutionResult(
                taskName = json.getString("taskName"),
                status = TaskExecutionStatus.valueOf(json.getString("status")),
                reason = if (json.isNull("reason")) null else json.getString("reason"),
                startedAtMs = json.getLong("startedAtMs"),
                finishedAtMs = json.getLong("finishedAtMs"),
            )
        }.getOrNull()
    }

    private fun notify(result: TaskExecutionResult) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Task results", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val ar = context.resources.configuration.locales[0].language == "ar"
        val durationText = formatDuration(result.durationMs, ar)
        val title = if (ar) result.summaryAr else result.summaryEn
        val detail = buildString {
            append(durationText)
            if (result.status == TaskExecutionStatus.FAILURE && !result.reason.isNullOrBlank()) {
                append(" • ")
                append(result.reason)
            }
        }
        val icon = if (result.status == TaskExecutionStatus.SUCCESS) android.R.drawable.checkbox_on_background
        else android.R.drawable.ic_delete
        manager.notify(
            (result.taskName.hashCode() * 31 + result.finishedAtMs.hashCode()),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(detail)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun formatDuration(ms: Long, ar: Boolean): String {
        val seconds = (ms / 1000.0).coerceAtLeast(0.0)
        return if (ar) "المدة: %.1f ث".format(seconds) else "Duration: %.1fs".format(seconds)
    }

    companion object { private const val CHANNEL = "task_execution_results" }
}
