package com.explapp.shortcut.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import java.time.ZonedDateTime
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RestoreSchedulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!StartupEvent.shouldReschedule(intent?.action)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val restored = runCatching { ScheduleRestore.perform(appContext) }.getOrDefault(false)
                if (!restored) ScheduleRestore.enqueueRetry(appContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

internal object ScheduleRestore {
    private const val RETRY_NAME = "shortcut-restore-schedules"
    fun perform(context: Context): Boolean {
        val now = ZonedDateTime.now()
        var allScheduled = true
        val shortcutStore = ShortcutStore(context)
        val originalShortcuts = shortcutStore.load()
        val shortcuts = RestorePolicy.pauseExpiredOneShots(now, originalShortcuts)
        if (shortcuts != originalShortcuts) shortcutStore.save(shortcuts)
        val shortcutScheduler = AndroidAlarmScheduler(context)
        val ids = RestorePolicy.shortcuts(shortcuts).map { it.id }.toSet()
        shortcuts.filter { it.id in ids }.forEach {
            if (!shortcutScheduler.schedule(it)) allScheduled = false
        }
        val messageStore = MessageStore(context)
        val originalMessages = messageStore.load()
        val messages = RestorePolicy.pauseExpiredMessageOneShots(now, originalMessages)
        if (messages != originalMessages) messageStore.save(messages)
        val messageScheduler = AndroidMessageScheduler(context)
        val messageIds = RestorePolicy.messages(messages).map { it.id }.toSet()
        messages.filter { it.id in messageIds }.forEach {
            if (!messageScheduler.schedule(it)) allScheduled = false
        }
        val routineScheduler = RoutineScheduler(context)
        RoutineStore(context).load().filter { it.isEnabled && it.isValid() }.forEach {
            if (!routineScheduler.schedule(it)) allScheduled = false
        }
        return allScheduled
    }

    fun enqueueRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<ScheduleRestoreWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_NAME, ExistingWorkPolicy.KEEP, request,
        )
    }
}

class ScheduleRestoreWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result =
        if (runCatching { ScheduleRestore.perform(applicationContext) }.getOrDefault(false)) {
            Result.success()
        } else {
            Result.retry()
        }
}
