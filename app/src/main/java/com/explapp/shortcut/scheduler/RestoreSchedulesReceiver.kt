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
                val shortcutIdsToSchedule = RestorePolicy.shortcuts(reconciledShortcuts).map { it.id }.toSet()
                val restoredShortcuts = reconciledShortcuts.map { item ->
                    if (item.id !in shortcutIdsToSchedule) item
                    else if (appScheduler.schedule(item)) item
                    else item.copy(isEnabled = false)
                }
                if (restoredShortcuts != reconciledShortcuts) shortcutStore.save(restoredShortcuts)

                val messageStore = MessageStore(appContext)
                val storedMessages = messageStore.load()
                val reconciledMessages = RestorePolicy.pauseExpiredMessageOneShots(now, storedMessages)
                if (reconciledMessages != storedMessages) messageStore.save(reconciledMessages)
                val messageScheduler = AndroidMessageScheduler(appContext)
                val messageIdsToSchedule = RestorePolicy.messages(reconciledMessages).map { it.id }.toSet()
                val restoredMessages = reconciledMessages.map { item ->
                    if (item.id !in messageIdsToSchedule) item
                    else if (messageScheduler.schedule(item)) item
                    else item.copy(isEnabled = false)
                }
                if (restoredMessages != reconciledMessages) messageStore.save(restoredMessages)

                val routineStore = RoutineStore(appContext)
                val routineScheduler = RoutineScheduler(appContext)
                val routines = routineStore.load()
                val restoredRoutines = routines.map { item ->
                    if (!item.isEnabled || !item.isValid()) item
                    else if (routineScheduler.schedule(item)) item
                    else item.copy(isEnabled = false)
                }
                if (restoredRoutines != routines) routineStore.save(restoredRoutines)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
