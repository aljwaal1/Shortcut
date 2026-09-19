package com.explapp.shortcut.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.explapp.shortcut.ui.ShortcutTheme
import java.util.Calendar

class AppUsageActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("app_usage_tracker", MODE_PRIVATE) }
    private var refreshTick by mutableIntStateOf(0)
    private var enabledState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabledState = prefs.getBoolean(KEY_ENABLED, false)
        setContent {
            ShortcutTheme {
                val tick = refreshTick
                val hasAccess = hasUsageAccess()
                val dashboard = if (enabledState && hasAccess) loadDashboard(tick) else null
                AppUsageDashboard(
                    enabled = enabledState,
                    hasAccess = hasAccess,
                    dashboard = dashboard,
                    onToggle = { enabled ->
                        if (enabled) {
                            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
                            if (prefs.getLong(KEY_ENABLED_AT, 0L) == 0L) {
                                prefs.edit().putLong(KEY_ENABLED_AT, System.currentTimeMillis()).apply()
                            }
                            enabledState = true
                            if (!hasUsageAccess()) openUsageAccess()
                        } else {
                            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                            enabledState = false
                        }
                    },
                    onOpenUsageAccess = ::openUsageAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    private fun openUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun loadDashboard(refreshKey: Int): UsageDashboard {
        refreshKey.hashCode()
        val manager = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val starts = (6 downTo 0).map { offset -> startOfDay(offset) }
        val samplesByDay = linkedMapOf<Long, List<AppUsageSample>>()
        starts.forEachIndexed { index, start ->
            val end = if (index == starts.lastIndex) now else starts[index + 1]
            samplesByDay[start] = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .asSequence()
                .filter { it.totalTimeInForeground > 0L }
                .map { AppUsageSample(it.packageName, it.totalTimeInForeground) }
                .toList()
        }
        val today = starts.last()
        val yesterday = starts[starts.lastIndex - 1]
        val nominalDay = (today - yesterday).coerceAtLeast(1L)
        return AppUsageMetrics.build(samplesByDay, today, yesterday, nominalDay)
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENABLED_AT = "enabledAt"
    }
}
