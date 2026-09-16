package com.explapp.shortcut.usage

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class AppUsageActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("app_usage_tracker", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val status = if (enabled) local("Enabled", "مفعّل") else local("Disabled", "غير مفعّل")
        AlertDialog.Builder(this)
            .setTitle(local("App usage time", "وقت استخدام التطبيقات"))
            .setMessage(local(
                "Status: $status\nUses Android Usage Access only. No Accessibility service is used.",
                "الحالة: $status\nيستخدم صلاحية Usage Access من Android فقط، بدون Accessibility.",
            ))
            .setPositiveButton(if (enabled) local("View usage", "عرض الاستخدام") else local("Enable", "تفعيل")) { _, _ ->
                if (enabled) showPeriodChooser() else enableTracker()
            }
            .setNeutralButton(if (enabled) local("Disable", "إيقاف") else local("Usage Access", "صلاحية الاستخدام")) { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                    Toast.makeText(this, local("Usage tracking disabled", "تم إيقاف تتبع الاستخدام"), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    finish()
                }
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun enableTracker() {
        if (!hasUsageAccess()) {
            AlertDialog.Builder(this)
                .setTitle(local("Usage Access required", "صلاحية Usage Access مطلوبة"))
                .setMessage(local(
                    "Android requires Usage Access to calculate how long each app stays in the foreground.",
                    "يحتاج Android إلى Usage Access لحساب مدة بقاء كل تطبيق في الواجهة.",
                ))
                .setPositiveButton(local("Open settings", "فتح الإعدادات")) { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    finish()
                }
                .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
                .show()
            return
        }
        val now = System.currentTimeMillis()
        prefs.edit().putBoolean(KEY_ENABLED, true).putLong(KEY_ENABLED_AT, now).apply()
        Toast.makeText(this, local("Usage tracking enabled", "تم تفعيل تتبع الاستخدام"), Toast.LENGTH_SHORT).show()
        showPeriodChooser()
    }

    private fun showPeriodChooser() {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return showHome()
        if (!hasUsageAccess()) return enableTracker()
        val labels = arrayOf(local("Today", "اليوم"), local("Last 7 days", "آخر 7 أيام"), local("Since enabled", "منذ التفعيل"))
        AlertDialog.Builder(this)
            .setTitle(local("Choose period", "اختر الفترة"))
            .setItems(labels) { _, which ->
                val now = System.currentTimeMillis()
                val begin = when (which) {
                    0 -> startOfToday()
                    1 -> now - TimeUnit.DAYS.toMillis(7)
                    else -> prefs.getLong(KEY_ENABLED_AT, startOfToday())
                }
                showUsage(begin, now, labels[which])
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showUsage(begin: Long, end: Long, title: String) {
        val manager = getSystemService(UsageStatsManager::class.java)
        val totals = AppUsageAggregator.aggregate(
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
                .filter { it.totalTimeInForeground > 0L }
                .map { AppUsageSample(it.packageName, it.totalTimeInForeground) },
        )
        val rows = totals.map { item ->
            val label = runCatching {
                val info = packageManager.getApplicationInfo(item.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(item.packageName)
            "$label — ${formatDuration(item.durationMs)}"
        }
        val total = totals.sumOf { it.durationMs }
        val list = ListView(this).apply {
            adapter = ArrayAdapter(this@AppUsageActivity, android.R.layout.simple_list_item_1, rows.ifEmpty { listOf(local("No usage data", "لا توجد بيانات استخدام")) })
        }
        AlertDialog.Builder(this)
            .setTitle("$title • ${formatDuration(total)}")
            .setView(list)
            .setPositiveButton(local("Done", "تم")) { _, _ -> finish() }
            .setNeutralButton(local("Periods", "الفترات")) { _, _ -> showPeriodChooser() }
            .show()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return if (resources.configuration.locales[0].language == "ar") {
            if (hours > 0) "${hours}س ${minutes}د" else "${minutes}د"
        } else {
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    }

    private fun local(en: String, ar: String): String = if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENABLED_AT = "enabledAt"
    }
}
