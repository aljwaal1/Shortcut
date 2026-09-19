package com.explapp.shortcut.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.time.ZonedDateTime

class AndroidAlarmScheduler(
    private val context: Context,
) {
    fun schedule(shortcut: ScheduledAppShortcut) {
        if (!shortcut.isEnabled || !shortcut.isValid()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = NextRunCalculator.nextRun(ZonedDateTime.now(), shortcut)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SchedulerIdentity.requestCode(shortcut.id),
            ScheduledAppLaunchReceiver.intent(context, shortcut),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = triggerAt.toInstant().toEpochMilli()
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        runCatching {
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
    }

    fun cancel(shortcut: ScheduledAppShortcut) = cancelById(shortcut.id)

    fun cancelById(id: String) {
        val existing = PendingIntent.getBroadcast(
            context,
            SchedulerIdentity.requestCode(id),
            ScheduledAppLaunchReceiver.identityIntent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(existing)
        existing.cancel()
    }
}
