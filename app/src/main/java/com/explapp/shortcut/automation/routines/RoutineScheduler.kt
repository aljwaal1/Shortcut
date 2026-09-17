package com.explapp.shortcut.automation.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.explapp.shortcut.automation.currentBatteryLevel
import com.explapp.shortcut.automation.isDeviceCharging
import java.time.ZonedDateTime

object RoutineRequestCode {
    fun fromId(id: String): Int = ("routine:$id").hashCode()
    fun batteryFromId(id: String): Int = ("routine-state:$id").hashCode()
}

private class RoutineEdgeState(context: Context) {
    private val prefs = context.getSharedPreferences("routine_edges", Context.MODE_PRIVATE)

    fun wasBelow(id: String): Boolean = prefs.getBoolean("battery:$id", false)
    fun setBelow(id: String, below: Boolean) { prefs.edit().putBoolean("battery:$id", below).apply() }

    fun chargerState(id: String): Boolean? {
        val key = "charger:$id"
        if (!prefs.contains(key)) return null
        return prefs.getBoolean(key, false)
    }

    fun setChargerState(id: String, connected: Boolean) {
        prefs.edit().putBoolean("charger:$id", connected).apply()
    }

    fun clear(id: String) {
        prefs.edit()
            .remove("battery:$id")
            .remove("charger:$id")
            .apply()
    }
}

class RoutineScheduler(private val context: Context) {
    fun schedule(routine: AutomationRoutine) {
        if (!routine.isEnabled || !routine.isValid()) return
        when (routine.trigger.type) {
            RoutineTriggerType.TIME -> scheduleTime(routine)
            RoutineTriggerType.BATTERY_BELOW -> scheduleStatePoll(routine)
            RoutineTriggerType.CHARGER_CONNECTED,
            RoutineTriggerType.CHARGER_DISCONNECTED,
            -> {
                val state = RoutineEdgeState(context)
                if (state.chargerState(routine.id) == null) {
                    state.setChargerState(routine.id, isDeviceCharging(context))
                }
                scheduleStatePoll(routine)
            }
            else -> Unit
        }
    }

    private fun scheduleTime(routine: AutomationRoutine) {
        val parts = routine.trigger.value.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return
        val now = ZonedDateTime.now()
        var at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val pending = statePending(routine.id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, time = true) ?: return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val millis = at.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun scheduleStatePoll(routine: AutomationRoutine) {
        val pending = statePending(routine.id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, time = false) ?: return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + STATE_POLL_INTERVAL_MS,
            pending,
        )
    }

    fun cancel(id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        statePending(id, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE, time = true)?.let {
            alarm.cancel(it); it.cancel()
        }
        statePending(id, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE, time = false)?.let {
            alarm.cancel(it); it.cancel()
        }
        RoutineEdgeState(context).clear(id)
    }

    private fun statePending(id: String, flags: Int, time: Boolean): PendingIntent? {
        val requestCode = if (time) RoutineRequestCode.fromId(id) else RoutineRequestCode.batteryFromId(id)
        val receiver = if (time) RoutineAlarmReceiver::class.java else RoutineStateReceiver::class.java
        val action = if (time) "routine-time:$id" else "routine-state:$id"
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver).setAction(action).putExtra(EXTRA_ID, id),
            flags,
        )
    }

    companion object {
        const val EXTRA_ID = "routine_id"
        private const val STATE_POLL_INTERVAL_MS = 15 * 60 * 1000L
    }
}

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RoutineScheduler.EXTRA_ID) ?: return
        val routine = RoutineStore(context).load().firstOrNull { it.id == id && it.isEnabled && it.isValid() } ?: return
        RoutineDispatcher(context).execute(routine, userInitiated = false)
        RoutineScheduler(context).schedule(routine)
    }
}

class RoutineStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RoutineScheduler.EXTRA_ID) ?: return
        val routine = RoutineStore(context).load().firstOrNull { it.id == id && it.isEnabled && it.isValid() } ?: return
        val state = RoutineEdgeState(context)

        when (routine.trigger.type) {
            RoutineTriggerType.BATTERY_BELOW -> {
                val threshold = routine.trigger.value.toIntOrNull() ?: return
                val level = currentBatteryLevel(context)
                val wasBelow = state.wasBelow(id)
                val isBelow = BatteryThresholdEdge.isBelow(level, threshold)
                if (BatteryThresholdEdge.shouldFire(wasBelow, level, threshold)) {
                    RoutineDispatcher(context).execute(routine, userInitiated = false)
                }
                state.setBelow(id, isBelow)
            }

            RoutineTriggerType.CHARGER_CONNECTED,
            RoutineTriggerType.CHARGER_DISCONNECTED,
            -> {
                val connected = isDeviceCharging(context)
                val previous = state.chargerState(id)
                if (ChargerEdge.shouldFire(routine.trigger.type, previous, connected)) {
                    RoutineDispatcher(context).execute(routine, userInitiated = false)
                }
                state.setChargerState(id, connected)
            }

            else -> return
        }

        RoutineScheduler(context).schedule(routine)
    }
}

class RoutineSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dispatcher = RoutineDispatcher(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                dispatcher.dispatch(RoutineEvent(RoutineTriggerType.BOOT))
                reschedule(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> reschedule(context)
        }
    }

    private fun reschedule(context: Context) {
        val scheduler = RoutineScheduler(context)
        RoutineStore(context).load().filter { it.isEnabled && it.isValid() }.forEach(scheduler::schedule)
    }
}
