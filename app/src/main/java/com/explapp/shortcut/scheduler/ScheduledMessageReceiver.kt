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
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.messages.MessageDeepLinkFactory

class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.toScheduledMessage() ?: return
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

        val launched = runCatching {
            context.startActivity(openIntent)
            true
        }.getOrDefault(false)

        if (!launched) {
            showReadyNotification(context, message, openIntent)
        }

        if (message.repeat == RepeatOption.ONCE) {
            MessageStore(context).remove(message)
        } else {
            AndroidMessageScheduler(context).schedule(message)
        }
    }

    private fun showReadyNotification(
        context: Context,
        message: ScheduledMessage,
        openIntent: Intent,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "scheduled_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.scheduled_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            message.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(message.name)
            .setContentText(context.getString(R.string.message_ready_fallback))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(message.hashCode(), notification)
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PLATFORM = "platform"
        private const val EXTRA_RECIPIENT = "recipient"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_REPEAT = "repeat"

        fun intent(context: Context, message: ScheduledMessage): Intent =
            Intent(context, ScheduledMessageReceiver::class.java).apply {
                putExtra(EXTRA_NAME, message.name)
                putExtra(EXTRA_PLATFORM, message.platform.name)
                putExtra(EXTRA_RECIPIENT, message.recipient)
                putExtra(EXTRA_MESSAGE, message.message)
                putExtra(EXTRA_HOUR, message.hour)
                putExtra(EXTRA_MINUTE, message.minute)
                putExtra(EXTRA_REPEAT, message.repeat.name)
            }

        private fun Intent.toScheduledMessage(): ScheduledMessage? {
            val name = getStringExtra(EXTRA_NAME) ?: return null
            val platform = runCatching {
                MessagePlatform.valueOf(getStringExtra(EXTRA_PLATFORM).orEmpty())
            }.getOrNull() ?: return null
            val recipient = getStringExtra(EXTRA_RECIPIENT) ?: return null
            val body = getStringExtra(EXTRA_MESSAGE) ?: return null
            val hour = getIntExtra(EXTRA_HOUR, -1)
            val minute = getIntExtra(EXTRA_MINUTE, -1)
            val repeat = runCatching {
                RepeatOption.valueOf(getStringExtra(EXTRA_REPEAT).orEmpty())
            }.getOrNull() ?: return null

            return ScheduledMessage(name, platform, recipient, body, hour, minute, repeat)
                .takeIf { it.isValid() }
        }
    }
}
