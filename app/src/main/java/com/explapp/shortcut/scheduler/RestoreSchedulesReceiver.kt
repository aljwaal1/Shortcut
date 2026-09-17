package com.explapp.shortcut.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore

class RestoreSchedulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!StartupEvent.shouldReschedule(intent?.action)) return

        val appContext = context.applicationContext
        val appScheduler = AndroidAlarmScheduler(appContext)
        RestorePolicy.shortcuts(ShortcutStore(appContext).load())
            .forEach(appScheduler::schedule)

        val messageScheduler = AndroidMessageScheduler(appContext)
        RestorePolicy.messages(MessageStore(appContext).load())
            .forEach(messageScheduler::schedule)
    }
}
