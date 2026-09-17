package com.explapp.shortcut.automation.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

object RoutineRequestCode {
    fun fromId(id: String): Int = ("routine:$id").hashCode()
}

class RoutineScheduler(private val context: Context) {
    fun schedule(routine: AutomationRoutine) {
        if (!routine.isEnabled || routine.trigger.type != RoutineTriggerType.TIME) return
        val parts = routine.trigger.value.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return
        val now = ZonedDateTime.now()
        var at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val pending = pendingIntent(routine.id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val millis = at.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    fun cancel(id: String) {
        val pending = pendingIntent(id, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(id: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        RoutineRequestCode.fromId(id),
        Intent(context, RoutineAlarmReceiver::class.java).setAction("routine-time:$id").putExtra(EXTRA_ID, id),
        flags,
    )

    companion object { const val EXTRA_ID = "routine_id" }
}

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RoutineScheduler.EXTRA_ID) ?: return
        val routine = RoutineStore(context).load().firstOrNull { it.id == id && it.isEnabled } ?: return
        RoutineDispatcher(context).execute(routine, userInitiated = false)
        RoutineScheduler(context).schedule(routine)
    }
}

class RoutineSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dispatcher = RoutineDispatcher(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> dispatcher.dispatch(RoutineEvent(RoutineTriggerType.CHARGER_CONNECTED))
            Intent.ACTION_POWER_DISCONNECTED -> dispatcher.dispatch(RoutineEvent(RoutineTriggerType.CHARGER_DISCONNECTED))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                dispatcher.dispatch(RoutineEvent(RoutineTriggerType.BOOT))
                val scheduler = RoutineScheduler(context)
                RoutineStore(context).load().filter { it.isEnabled }.forEach(scheduler::schedule)
            }
        }
    }
}
