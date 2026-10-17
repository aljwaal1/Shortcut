package com.explapp.shortcut.automation.routines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoutineExecutionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    local("Automation execution", "تنفيذ الأتمتة"),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(local("Shortcut automation", "أتمتة Shortcut"))
            .setContentText(local("Running a scheduled automation…", "جارٍ تنفيذ أتمتة مجدولة…"))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val routineId = inputData.getString(KEY_ROUTINE_ID).orEmpty()
        if (routineId.isBlank()) return@withContext Result.success()

        val store = RoutineStore(applicationContext)
        val routine = store
            .load()
            .firstOrNull { it.id == routineId && it.isEnabled && it.isValid() }
            ?: return@withContext Result.success()

        // RoutineDispatcher records the actual routine result in execution history.
        // The Worker itself succeeds even when a user-defined action fails; retrying
        // the whole routine could duplicate actions that already completed.
        try {
            RoutineDispatcher(applicationContext).execute(routine, userInitiated = false)
        } finally {
            if (RoutineWork.shouldDisableAfterAttempt(routine)) {
                store.upsert(routine.copy(isEnabled = false, updatedAtMs = System.currentTimeMillis()))
            }
        }
        Result.success()
    }

    private fun local(en: String, ar: String): String =
        if (applicationContext.resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"
        private const val CHANNEL_ID = "routine_worker"
        private const val NOTIFICATION_ID = 9411
    }
}

object RoutineWork {
    fun enqueue(context: Context, routineId: String) {
        if (routineId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<RoutineExecutionWorker>()
            .setInputData(workDataOf(RoutineExecutionWorker.KEY_ROUTINE_ID to routineId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(routineId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueMatching(context: Context, event: RoutineEvent) {
        RoutineStore(context.applicationContext)
            .load()
            .filter { RoutineTriggerMatcher.matches(it, event) }
            .forEach { enqueue(context, it.id) }
    }

    internal fun uniqueName(routineId: String): String = "shortcut-routine:$routineId"

    internal fun shouldDisableAfterAttempt(routine: AutomationRoutine): Boolean =
        routine.trigger.type == RoutineTriggerType.TIME &&
            routine.trigger.repeat == RoutineRepeat.ONCE

    private const val TAG = "shortcut-routine-execution"
}
