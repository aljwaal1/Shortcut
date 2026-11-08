package com.explapp.shortcut.automation.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.LocalDate

/**
 * ACTION_USER_PRESENT is registered dynamically; Android does not guarantee
 * delivery while the process is stopped. No background bypass is attempted.
 */
object RoutineUnlockMonitor {
    private var receiver: BroadcastReceiver? = null

    @Synchronized fun start(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                val prefs = app.getSharedPreferences("routine_unlock_events", Context.MODE_PRIVATE)
                val today = LocalDate.now().toString()
                val bootMarker = android.os.SystemClock.elapsedRealtime()
                val currentBoot = System.currentTimeMillis() - bootMarker
                val previousBoot = prefs.getLong("boot_epoch", Long.MIN_VALUE)
                val newBoot = previousBoot == Long.MIN_VALUE || kotlin.math.abs(currentBoot - previousBoot) > 120_000L
                val firstToday = prefs.getString("day", null) != today
                prefs.edit().putString("day", today).putLong("boot_epoch", currentBoot).apply()
                RoutineWork.enqueueMatching(app, RoutineEvent(RoutineTriggerType.DEVICE_UNLOCKED))
                if (firstToday) RoutineWork.enqueueMatching(app, RoutineEvent(RoutineTriggerType.FIRST_UNLOCK_DAILY))
                if (newBoot) RoutineWork.enqueueMatching(app, RoutineEvent(RoutineTriggerType.FIRST_UNLOCK_AFTER_BOOT))
            }
        }
        app.registerReceiver(listener, IntentFilter(Intent.ACTION_USER_PRESENT))
        receiver = listener
    }
}
