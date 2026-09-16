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
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = NextRunCalculator.nextRun(ZonedDateTime.now(), shortcut)
        val pendingIntent = pendingIntent(shortcut, PendingIntent.FLAG_UPDATE_CURRENT)

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

    fun cancel(shortcut: ScheduledAppShortcut) {
        val existing = pendingIntent(shortcut, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(
        shortcut: ScheduledAppShortcut,
        baseFlag: Int,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode(shortcut),
        ScheduledAppLaunchReceiver.intent(context, shortcut),
        baseFlag or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestCode(shortcut: ScheduledAppShortcut): Int =
        listOf(shortcut.packageName, shortcut.hour, shortcut.minute, shortcut.name)
            .joinToString("|")
            .hashCode()
}
