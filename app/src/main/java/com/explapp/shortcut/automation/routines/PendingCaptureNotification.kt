package com.explapp.shortcut.automation.routines

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

/** One independently actionable, ongoing notice per blocked routine. */
object PendingCaptureNotification {
    private const val CHANNEL = "shortcut_pending_capture"
    private const val EXTRA_ROUTINE = "routine_id"
    const val ACTION_DISMISS = "com.explapp.shortcut.DISMISS_PENDING_CAPTURE"

    private fun id(routineId: String) = 50_000 + (routineId.hashCode() and 0x0fffffff)

    fun show(context: Context, routineId: String, name: String, intent: Intent): Boolean {
        if (routineId.isBlank()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Pending automations", NotificationManager.IMPORTANCE_HIGH))
        }
        val request = id(routineId)
        intent.data = android.net.Uri.parse("shortcut://retry-capture/" + android.net.Uri.encode(routineId))
        val retry = PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(
            context, request,
            Intent(context, PendingCaptureNotificationReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ROUTINE, routineId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ar = context.resources.configuration.locales[0].language == "ar"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle((if (ar) "أتمتة تنتظر الموافقة: " else "Automation needs approval: ") + name.ifBlank { routineId })
            .setContentText(if (ar) "اضغط إعادة المحاولة ووافق على تصوير الشاشة يدويًا." else "Tap Retry and approve screen capture manually.")
            .setContentIntent(retry)
            .addAction(android.R.drawable.ic_popup_sync, if (ar) "إعادة المحاولة" else "Retry", retry)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, if (ar) "إلغاء المهمة المعلقة" else "Dismiss pending task", dismiss)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        return runCatching { manager.notify(request, n); true }.getOrDefault(false)
    }

    fun clear(context: Context, routineId: String) {
        if (routineId.isNotBlank()) {
            context.getSystemService(NotificationManager::class.java).cancel(id(routineId))
        }
    }

    fun pendingRoutine(intent: Intent) = intent.getStringExtra(EXTRA_ROUTINE).orEmpty()
}

class PendingCaptureNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PendingCaptureNotification.ACTION_DISMISS) return
        val id = PendingCaptureNotification.pendingRoutine(intent)
        PendingCaptureNotification.clear(context, id)
        val routine = RoutineStore(context).load().firstOrNull { it.id == id }
        if (routine != null) {
            com.explapp.shortcut.execution.TaskExecutionReporter(context).report(
                com.explapp.shortcut.execution.TaskExecutionResult.failure(
                    routine.name, "User dismissed pending Android screen-capture permission",
                    System.currentTimeMillis(),
                    details = listOf("EVENT = PENDING_PERMISSION_DISMISSED", "ROUTINE_ID = $id", "RUN_SOURCE = USER"),
                ), notifyUser = false,
            )
        }
    }
}
