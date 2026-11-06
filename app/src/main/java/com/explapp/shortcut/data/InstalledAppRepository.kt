package com.explapp.shortcut.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val label: String,
    val packageName: String,
)

class InstalledAppRepository(
    private val context: Context,
) {
    fun loadLaunchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = context.packageManager

        val activities = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return activities
            .map { resolveInfo ->
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
