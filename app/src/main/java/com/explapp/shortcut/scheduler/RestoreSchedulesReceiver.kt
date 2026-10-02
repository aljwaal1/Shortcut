package com.explapp.shortcut.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import java.time.ZonedDateTime

class RestoreSchedulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!StartupEvent.shouldReschedule(intent?.action)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val now = ZonedDateTime.now()

                val shortcutStore = ShortcutStore(appContext)
                val storedShortcuts = shortcutStore.load()
                val reconciledShortcuts = RestorePolicy.pauseExpiredOneShots(now, storedShortcuts)
                if (reconciledShortcuts != storedShortcuts) shortcutStore.save(reconciledShortcuts)
                val appScheduler = AndroidAlarmScheduler(appContext)
                RestorePolicy.shortcuts(reconciledShortcuts)
                    .forEach { item -> runCatching { appScheduler.schedule(item) } }

                val messageStore = MessageStore(appContext)
                val storedMessages = messageStore.load()
                val reconciledMessages = RestorePolicy.pauseExpiredMessageOneShots(now, storedMessages)
                if (reconciledMessages != storedMessages) messageStore.save(reconciledMessages)
                val messageScheduler = AndroidMessageScheduler(appContext)
                RestorePolicy.messages(reconciledMessages)
                    .forEach { item -> runCatching { messageScheduler.schedule(item) } }

                val routineScheduler = RoutineScheduler(appContext)
                RoutineStore(appContext).load()
                    .filter { it.isEnabled && it.isValid() }
                    .forEach { item -> runCatching { routineScheduler.schedule(item) } }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
