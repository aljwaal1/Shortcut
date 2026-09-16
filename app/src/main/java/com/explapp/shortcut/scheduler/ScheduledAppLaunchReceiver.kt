package com.explapp.shortcut.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.R
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionResult

class ScheduledAppLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shortcut = intent.toShortcut() ?: return
        val startedAt = System.currentTimeMillis()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(shortcut.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        var failureReason: String? = null
        val launched = if (launchIntent != null) {
            runCatching {
                context.startActivity(launchIntent)
                true
            }.onFailure { failureReason = it.message ?: it.javaClass.simpleName }
                .getOrDefault(false)
        } else {
            failureReason = "App not found"
            false
        }

        TaskExecutionReporter(context).report(
            if (launched) TaskExecutionResult.success(shortcut.name, startedAt)
            else TaskExecutionResult.failure(shortcut.name, failureReason ?: "Could not open app", startedAt),
        )

        if (!launched) showOpenNowNotification(context, shortcut)

        if (shortcut.repeat == RepeatOption.ONCE) {
            ShortcutStore(context).remove(shortcut)
        } else {
            AndroidAlarmScheduler(context).schedule(shortcut)
        }
    }

    private fun showOpenNowNotification(context: Context, shortcut: ScheduledAppShortcut) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(shortcut.packageName) ?: return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channelId = "scheduled_app_launch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, context.getString(R.string.scheduled_automations), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            shortcut.packageName.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(shortcut.name)
            .setContentText(context.getString(R.string.open_now_fallback))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()
        notificationManager.notify(shortcut.packageName.hashCode(), notification)
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_REPEAT = "repeat"

        fun intent(context: Context, shortcut: ScheduledAppShortcut): Intent =
            Intent(context, ScheduledAppLaunchReceiver::class.java).apply {
                putExtra(EXTRA_NAME, shortcut.name)
                putExtra(EXTRA_PACKAGE, shortcut.packageName)
                putExtra(EXTRA_HOUR, shortcut.hour)
                putExtra(EXTRA_MINUTE, shortcut.minute)
                putExtra(EXTRA_REPEAT, shortcut.repeat.name)
            }

        private fun Intent.toShortcut(): ScheduledAppShortcut? {
            val name = getStringExtra(EXTRA_NAME) ?: return null
            val packageName = getStringExtra(EXTRA_PACKAGE) ?: return null
            val hour = getIntExtra(EXTRA_HOUR, -1)
            val minute = getIntExtra(EXTRA_MINUTE, -1)
            val repeat = runCatching { RepeatOption.valueOf(getStringExtra(EXTRA_REPEAT).orEmpty()) }.getOrNull() ?: return null
            return ScheduledAppShortcut(name, packageName, hour, minute, repeat).takeIf { it.isValid() }
        }
    }
}
