package com.explapp.shortcut.automation.routines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
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
        if (routineId.isBlank()) return@withContext Result.failure()

        val store = RoutineStore(applicationContext)
        val routine = store
            .load()
            .firstOrNull { it.id == routineId && it.isEnabled && it.isValid() }
            ?: return@withContext Result.success()

        // RoutineDispatcher records the actual routine result in execution history.
        // The Worker itself succeeds even when a user-defined action fails; retrying
        // the whole routine could duplicate actions that already completed.
        val execution = runCatching {
            RoutineDispatcher(applicationContext).execute(routine, userInitiated = false)
        }
        val persistedState = RoutineProgressStore(applicationContext).snapshot(routine.id)?.status
        if (persistedState == RoutineRunStatus.SUCCESS.name &&
            RoutineWork.shouldDisableAfterAttempt(routine, execution.getOrNull())
        ) {
            runCatching {
                store.upsert(routine.copy(isEnabled = false, updatedAtMs = System.currentTimeMillis()))
            }
        }
        // Do not retry completed user actions: network sends may already have succeeded.
        // Unexpected infrastructure failures still surface as a failed worker.
        // Infrastructure failures may follow an already completed external action.
        // Never request a blind WorkManager retry for the entire routine.
        if (execution.isFailure) Result.failure() else Result.success()
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
        val routine = RoutineStore(context.applicationContext).load()
            .firstOrNull { it.id == routineId && it.isEnabled && it.isValid() } ?: return
        val needsNetwork = requiresNetwork(routine)
        val request = OneTimeWorkRequestBuilder<RoutineExecutionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
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

    /** Cancel queued/scheduled work; a running non-cooperative external action may still finish. */
    fun cancel(context: Context, routineId: String) {
        RoutineProgressStore(context).requestCancel(routineId)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueName(routineId))
    }

    fun enqueueMatching(context: Context, event: RoutineEvent) {
        RoutineStore(context.applicationContext)
            .load()
            .filter { RoutineTriggerMatcher.matches(it, event) }
            .forEach { enqueue(context, it.id) }
    }

    internal fun requiresNetwork(routine: AutomationRoutine): Boolean =
        routine.actions.any { it.type == RoutineActionType.SEND_TELEGRAM_BOT }

    internal fun uniqueName(routineId: String): String = "shortcut-routine:$routineId"

    internal fun shouldDisableAfterAttempt(routine: AutomationRoutine, result: RoutineRunResult?): Boolean =
        result?.status == RoutineRunStatus.SUCCESS && routine.trigger.type == RoutineTriggerType.TIME &&
            routine.trigger.repeat == RoutineRepeat.ONCE

    private const val TAG = "shortcut-routine-execution"
}
