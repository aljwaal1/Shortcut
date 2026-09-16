package com.explapp.shortcut.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.explapp.shortcut.domain.ScheduledMessage
import java.time.ZonedDateTime

class AndroidMessageScheduler(
    private val context: Context,
) {
    fun schedule(message: ScheduledMessage) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = NextRunCalculator.nextRun(ZonedDateTime.now(), message)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(message),
            ScheduledMessageReceiver.intent(context, message),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = triggerAt.toInstant().toEpochMilli()
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel(message: ScheduledMessage) {
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode(message),
            ScheduledMessageReceiver.intent(context, message),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(existing)
        existing.cancel()
    }

    private fun requestCode(message: ScheduledMessage): Int =
        listOf(message.platform.name, message.recipient, message.hour, message.minute, message.name)
            .joinToString("|")
            .hashCode()
}
