package com.explapp.shortcut.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.R
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.domain.MessageDeliveryMode
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionResult
import com.explapp.shortcut.messages.MessageDeepLinkFactory

class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = intent.toScheduledMessage() ?: return
        val store = MessageStore(context)
        val message = StoredScheduleResolver.message(payload.id, store.load()) ?: return
        val startedAt = System.currentTimeMillis()

        val openIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                MessageDeepLinkFactory.build(
                    platform = message.platform,
                    recipient = message.recipient,
                    message = message.message,
                ),
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val result = when (message.deliveryMode) {
            MessageDeliveryMode.PREPARED -> {
                if (showReadyNotification(context, message, openIntent)) {
                    TaskExecutionResult.prepared(message.name, startedAt)
                } else {
                    TaskExecutionResult.failure(
                        message.name,
                        "Notification permission is required for scheduled prepared messages",
                        startedAt,
                    )
                }
            }
            MessageDeliveryMode.TELEGRAM_BOT_AUTO -> TaskExecutionResult.failure(
                message.name,
                "Telegram Bot auto-send is not configured in this build",
                startedAt,
            )
        }
        TaskExecutionReporter(context).report(result)

        if (message.repeat == RepeatOption.ONCE) {
            store.removeById(message.id)
        } else {
            AndroidMessageScheduler(context).schedule(message)
        }
    }

    private fun showReadyNotification(context: Context, message: ScheduledMessage, openIntent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "scheduled_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, context.getString(R.string.scheduled_messages), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SchedulerIdentity.requestCode(message.id),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val appName = context.getString(
            if (message.platform == MessagePlatform.WHATSAPP) R.string.whatsapp else R.string.telegram,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(message.name)
            .setContentText("${context.getString(R.string.message_ready_fallback)} • $appName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(SchedulerIdentity.requestCode(message.id), notification)
        return true
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PLATFORM = "platform"
        private const val EXTRA_RECIPIENT = "recipient"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_REPEAT = "repeat"
        private const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_DELIVERY_MODE = "delivery_mode"

        fun identityIntent(context: Context, id: String): Intent =
            Intent(context, ScheduledMessageReceiver::class.java).setAction("scheduled-message:$id")

        fun intent(context: Context, message: ScheduledMessage): Intent =
            identityIntent(context, message.id).apply {
                putExtra(EXTRA_ID, message.id)
                putExtra(EXTRA_NAME, message.name)
                putExtra(EXTRA_PLATFORM, message.platform.name)
                putExtra(EXTRA_RECIPIENT, message.recipient)
                putExtra(EXTRA_MESSAGE, message.message)
                putExtra(EXTRA_HOUR, message.hour)
                putExtra(EXTRA_MINUTE, message.minute)
                putExtra(EXTRA_REPEAT, message.repeat.name)
                putExtra(EXTRA_ENABLED, message.isEnabled)
                putExtra(EXTRA_DELIVERY_MODE, message.deliveryMode.name)
            }

        private fun Intent.toScheduledMessage(): ScheduledMessage? {
            val id = getStringExtra(EXTRA_ID) ?: return null
            val name = getStringExtra(EXTRA_NAME) ?: return null
            val platform = runCatching { MessagePlatform.valueOf(getStringExtra(EXTRA_PLATFORM).orEmpty()) }.getOrNull() ?: return null
            val recipient = getStringExtra(EXTRA_RECIPIENT) ?: return null
            val body = getStringExtra(EXTRA_MESSAGE) ?: return null
            val hour = getIntExtra(EXTRA_HOUR, -1)
            val minute = getIntExtra(EXTRA_MINUTE, -1)
            val repeat = runCatching { RepeatOption.valueOf(getStringExtra(EXTRA_REPEAT).orEmpty()) }.getOrNull() ?: return null
            val mode = runCatching { MessageDeliveryMode.valueOf(getStringExtra(EXTRA_DELIVERY_MODE).orEmpty()) }.getOrNull()
                ?: MessageDeliveryMode.PREPARED
            return ScheduledMessage(
                id = id,
                name = name,
                platform = platform,
                recipient = recipient,
                message = body,
                hour = hour,
                minute = minute,
                repeat = repeat,
                isEnabled = getBooleanExtra(EXTRA_ENABLED, true),
                deliveryMode = mode,
            ).takeIf { it.isValid() }
        }
    }
}
