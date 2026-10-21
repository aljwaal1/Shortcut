package com.explapp.shortcut.automation.routines

import android.content.Context
import com.explapp.shortcut.execution.TaskExecutionReporter
import java.util.concurrent.ConcurrentHashMap

class RoutineDispatcher(private val context: Context) {
    fun executeAsync(
        routine: AutomationRoutine,
        userInitiated: Boolean,
        onComplete: ((RoutineRunResult) -> Unit)? = null,
    ) {
        Thread {
            val result = runCatching { execute(routine, userInitiated) }.getOrElse {
                RoutineRunResult(
                    routineId = routine.id,
                    routineName = routine.name,
                    status = RoutineRunStatus.FAILED,
                    actionResults = emptyList(),
                    startedAtMs = System.currentTimeMillis(),
                    finishedAtMs = System.currentTimeMillis(),
                )
            }
            runCatching { onComplete?.invoke(result) }
        }.start()
    }

    fun dispatch(event: RoutineEvent): List<RoutineRunResult> = RoutineStore(context)
        .load()
        .filter { RoutineTriggerMatcher.matches(it, event) }
        .map { execute(it, userInitiated = false) }

    fun execute(routine: AutomationRoutine, userInitiated: Boolean): RoutineRunResult {
        val startedAt = System.currentTimeMillis()
        if (!runningRoutineIds.add(routine.id)) {
            val finishedAt = System.currentTimeMillis()
            // Duplicate dispatch is skipped; do not add a misleading PREPARED record.
            return RoutineRunResult(
                routineId = routine.id,
                routineName = routine.name,
                status = RoutineRunStatus.PREPARED,
                actionResults = emptyList(),
                startedAtMs = startedAt,
                finishedAtMs = finishedAt,
            )
        }

        return try {
            val progress = RoutineProgressStore(context)
            progress.begin(routine.id)
            val result = runCatching {
                RoutineExecutor(
                    AndroidRoutineActionRunner(context, userInitiated, routine.id, routine.name),
                    AndroidRoutineConditionEvaluator(context),
                    onProgress = { index, status -> progress.record(routine.id, index, status) },
                    shouldContinue = { !progress.isCancelled(routine.id) },
                ).execute(routine, startedAtMs = startedAt)
            }.getOrElse { _ ->
                RoutineRunResult(
                    routineId = routine.id,
                    routineName = routine.name,
                    status = RoutineRunStatus.FAILED,
                    actionResults = emptyList(),
                    startedAtMs = startedAt,
                    finishedAtMs = System.currentTimeMillis(),
                )
            }
            progress.finish(routine.id, result.status)
            val historyResult = result.toTaskExecutionResult().copy(
                details = listOf(
                    "RUN_SOURCE = " + if (userInitiated) "MANUAL" else "SCHEDULED",
                    "ROUTINE_ID = ${routine.id}",
                ) + result.toTaskExecutionResult().details,
            )
            TaskExecutionReporter(context).report(historyResult)
            result
        } finally {
            runningRoutineIds.remove(routine.id)
        }
    }

    companion object {
        private val runningRoutineIds = ConcurrentHashMap.newKeySet<String>()
    }
}
