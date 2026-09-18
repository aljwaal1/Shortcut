package com.explapp.shortcut.automation.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.messages.MessageDeepLinkFactory
import com.explapp.shortcut.tools.ScreenCaptureActivity
import com.explapp.shortcut.usage.AppUsageActivity

class AndroidRoutineActionRunner(
    private val context: Context,
    private val userInitiated: Boolean,
) : RoutineActionRunner {
    override fun run(action: RoutineAction): RoutineActionResult = when (action.type) {
        RoutineActionType.OPEN_APP -> openExternal(action, context.packageManager.getLaunchIntentForPackage(action.value))
        RoutineActionType.OPEN_APP_SCREENSHOT -> openAppScreenshot(action)
        RoutineActionType.OPEN_URL -> openExternal(action, Intent(Intent.ACTION_VIEW, Uri.parse(action.value)))
        RoutineActionType.OPEN_MAPS -> {
            val query = Uri.encode(action.value)
            openExternal(action, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
        }
        RoutineActionType.PREPARE_WHATSAPP -> preparedMessage(action, MessagePlatform.WHATSAPP)
        RoutineActionType.PREPARE_TELEGRAM -> preparedMessage(action, MessagePlatform.TELEGRAM)
        RoutineActionType.OPEN_TOOL -> {
            if (action.value == "app_usage") openExternal(action, Intent(context, AppUsageActivity::class.java))
            else RoutineActionResult.failure(action, "Unknown tool: ${action.value}")
        }
        RoutineActionType.SHOW_NOTIFICATION -> {
            if (showNotification("Shortcut", action.value, null)) RoutineActionResult.success(action)
            else RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun openAppScreenshot(action: RoutineAction): RoutineActionResult {
        if (context.packageManager.getLaunchIntentForPackage(action.value) == null) {
            return RoutineActionResult.failure(action, "Target app is unavailable")
        }
        val delayMs = action.secondaryValue.toLongOrNull()?.coerceIn(500L, 10_000L) ?: 2_000L
        val workflowIntent = Intent(context, ScreenCaptureActivity::class.java)
            .putExtra(ScreenCaptureActivity.EXTRA_LAUNCH_PACKAGE, action.value)
            .putExtra(ScreenCaptureActivity.EXTRA_CAPTURE_DELAY_MS, delayMs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (userInitiated) {
            return runCatching {
                context.startActivity(workflowIntent)
                RoutineActionResult.success(action)
            }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
        }

        return if (
            showNotification(
                title = "Open app + screenshot",
                text = "Tap to approve screen capture and continue",
                intent = workflowIntent,
            )
        ) {
            RoutineActionResult.prepared(action, "Screen-capture consent required")
        } else {
            RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun preparedMessage(action: RoutineAction, platform: MessagePlatform): RoutineActionResult {
        if (action.value.isBlank() || action.secondaryValue.isBlank()) return RoutineActionResult.failure(action, "Recipient and message are required")
        val uri = Uri.parse(MessageDeepLinkFactory.build(platform, action.value, action.secondaryValue))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (userInitiated) openExternal(action, intent) else {
            if (showNotification(
                    title = if (platform == MessagePlatform.WHATSAPP) "WhatsApp message ready" else "Telegram message ready",
                    text = action.secondaryValue,
                    intent = intent,
                )
            ) RoutineActionResult.prepared(action, "User action required")
            else RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun openExternal(action: RoutineAction, rawIntent: Intent?): RoutineActionResult {
        val intent = rawIntent ?: return RoutineActionResult.failure(action, "Target is unavailable")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!userInitiated) {
            return if (showNotification("Shortcut action ready", action.value.ifBlank { action.type.name }, intent)) {
                RoutineActionResult.prepared(action, "User action required")
            } else {
                RoutineActionResult.failure(action, "Notification permission is required")
            }
        }
        return runCatching {
            context.startActivity(intent)
            RoutineActionResult.success(action)
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
    }

    private fun showNotification(title: String, text: String, intent: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automation routines", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text.take(120))
            .setAutoCancel(true)
        if (intent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (intent.dataString ?: intent.component?.className.orEmpty()).hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return runCatching {
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
            true
        }.getOrDefault(false)
    }

    companion object { private const val CHANNEL = "automation_routines" }
}
