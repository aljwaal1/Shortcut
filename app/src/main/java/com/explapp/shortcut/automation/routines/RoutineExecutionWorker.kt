package com.explapp.shortcut.automation.routines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionResult
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

        val reporter = TaskExecutionReporter(applicationContext)
        val enqueuedElapsedMs = inputData.getLong(KEY_ENQUEUED_ELAPSED_MS, -1L)
        val queueWaitMs = (SystemClock.elapsedRealtime() - enqueuedElapsedMs)
            .takeIf { enqueuedElapsedMs >= 0L && it >= 0L }
        reporter.report(
            TaskExecutionResult.prepared(
                routine.name + " / WORKER",
                System.currentTimeMillis(),
                details = listOf("EVENT = WORKER_STARTED", "ROUTINE_ID = ${routine.id}", "RUN_SOURCE = SCHEDULED") +
                    listOfNotNull(queueWaitMs?.let { "QueueWaitMs = $it" }),
            ), notifyUser = false,
        )

        // RoutineDispatcher records the actual routine result in execution history.
        // The Worker itself succeeds even when a user-defined action fails; retrying
        // the whole routine could duplicate actions that already completed.
        val execution = runCatching {
            RoutineDispatcher(applicationContext).execute(routine, userInitiated = false)
        }
        if (execution.isFailure) {
            reporter.report(
                TaskExecutionResult.failure(
                    routine.name + " / WORKER",
                    execution.exceptionOrNull()?.message ?: "Worker infrastructure exception",
                    System.currentTimeMillis(),
                    details = listOf("EVENT = WORKER_EXCEPTION", "ROUTINE_ID = ${routine.id}", "RUN_SOURCE = SCHEDULED"),
                ), notifyUser = false,
            )
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
        if (execution.isFailure || execution.getOrNull()?.status == RoutineRunStatus.FAILED) {
            Result.failure()
        } else {
            Result.success()
        }
    }

    private fun local(en: String, ar: String): String =
        if (applicationContext.resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_ENQUEUED_ELAPSED_MS = "enqueued_elapsed_ms"
        private const val CHANNEL_ID = "routine_worker"
        private const val NOTIFICATION_ID = 9411
    }
}

object RoutineWork {
    fun enqueue(context: Context, routineId: String) {
        if (routineId.isBlank()) return
        val routine = RoutineStore(context.applicationContext).load()
            .firstOrNull { it.id == routineId && it.isEnabled && it.isValid() } ?: return
        enqueueValidated(context, routine)
    }

    private fun enqueueValidated(context: Context, routine: AutomationRoutine) {
        val needsNetwork = requiresNetwork(routine)
        val enqueuedElapsedMs = SystemClock.elapsedRealtime()
        val request = OneTimeWorkRequestBuilder<RoutineExecutionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setInputData(workDataOf(
                RoutineExecutionWorker.KEY_ROUTINE_ID to routine.id,
                RoutineExecutionWorker.KEY_ENQUEUED_ELAPSED_MS to enqueuedElapsedMs,
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()

        val enqueueResult = runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName(routine.id),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
        TaskExecutionReporter(context.applicationContext).report(
            if (enqueueResult.isSuccess) TaskExecutionResult.prepared(
                routine.name + " / QUEUE", System.currentTimeMillis(),
                details = listOf("EVENT = WORK_ENQUEUE_REQUESTED", "ROUTINE_ID = ${routine.id}",
                    "REQUEST_ID = ${request.id}", "RUN_SOURCE = SCHEDULED",
                    "NOTE = WorkManager accepted enqueue request, not proof of execution"),
            ) else TaskExecutionResult.failure(
                routine.name + " / QUEUE",
                enqueueResult.exceptionOrNull()?.message ?: "Could not enqueue WorkManager request",
                System.currentTimeMillis(),
                details = listOf("EVENT = WORK_ENQUEUE_FAILED", "ROUTINE_ID = ${routine.id}", "RUN_SOURCE = SCHEDULED"),
            ), notifyUser = false,
        )
    }

    /** Cancel queued/scheduled work; a running non-cooperative external action may still finish. */
    fun cancel(context: Context, routineId: String) {
        RoutineProgressStore(context).requestCancel(routineId)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueName(routineId))
    }

    fun enqueueMatching(context: Context, event: RoutineEvent) {
        val routines = RoutineStore(context.applicationContext).load()
        matchingRoutines(routines, event).forEach { enqueueValidated(context, it) }
    }

    internal fun matchingRoutines(routines: List<AutomationRoutine>, event: RoutineEvent): List<AutomationRoutine> =
        routines.filter { RoutineTriggerMatcher.matches(it, event) }

    internal fun requiresNetwork(routine: AutomationRoutine): Boolean =
        routine.actions.any { it.type == RoutineActionType.SEND_TELEGRAM_BOT }

    internal fun uniqueName(routineId: String): String = "shortcut-routine:$routineId"

    internal fun shouldDisableAfterAttempt(routine: AutomationRoutine, result: RoutineRunResult?): Boolean =
        result?.status == RoutineRunStatus.SUCCESS && routine.trigger.type == RoutineTriggerType.TIME &&
            routine.trigger.repeat == RoutineRepeat.ONCE

    private const val TAG = "shortcut-routine-execution"
}
