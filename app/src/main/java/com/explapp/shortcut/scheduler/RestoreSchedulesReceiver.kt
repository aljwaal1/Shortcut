package com.explapp.shortcut.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.explapp.shortcut.data.ShortcutStore

class RestoreSchedulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!StartupEvent.shouldReschedule(intent?.action)) return

        val scheduler = AndroidAlarmScheduler(context.applicationContext)
        ShortcutStore(context.applicationContext)
            .load()
            .filter { it.isValid() }
            .forEach(scheduler::schedule)
    }
}
