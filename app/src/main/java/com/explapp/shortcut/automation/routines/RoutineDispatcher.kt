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

    fun execute(routine: AutomationRoutine, userInitiated: Boolean, resumeSafeWaits: Boolean = false): RoutineRunResult {
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
            val resumeIndex = if (resumeSafeWaits) progress.safeResumeIndex(routine) else null
            if (resumeSafeWaits && resumeIndex == null) {
                return RoutineRunResult(
                    routine.id, routine.name, RoutineRunStatus.FAILED,
                    emptyList(), startedAt, System.currentTimeMillis(),
                )
            }
            if (!resumeSafeWaits) {
                // A scheduled dispatch must not erase an uncertain checkpoint from a
                // previous process crash: that could replay an external side effect.
                if (progress.snapshot(routine.id)?.status == "UNCERTAIN") {
                    return RoutineRunResult(
                        routine.id, routine.name, RoutineRunStatus.FAILED,
                        emptyList(), startedAt, System.currentTimeMillis(),
                    )
                }
                if (!progress.begin(routine)) {
                    return RoutineRunResult(
                        routine.id, routine.name, RoutineRunStatus.FAILED,
                        emptyList(), startedAt, System.currentTimeMillis(),
                    )
                }
            }
            var checkpointWriteFailed = false
            val result = runCatching {
                RoutineExecutor(
                    AndroidRoutineActionRunner(context, userInitiated, routine.id, routine.name),
                    AndroidRoutineConditionEvaluator(context),
                    onProgress = { index, status ->
                        if (!progress.record(routine.id, index, status)) {
                            checkpointWriteFailed = true
                            error("Unable to persist automation progress")
                        }
                    },
                    shouldContinue = { !progress.isCancelled(routine.id) },
                    startIndex = resumeIndex ?: 0,
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
            // Preserve IN_FLIGHT after a checkpoint write failure: the last action
            // may already have produced an external effect before persistence failed.
            // Marking it FAILED would incorrectly allow a fresh replay.
            if (!checkpointWriteFailed && !progress.finish(routine.id, result.status)) {
                // Leave the persisted IN_FLIGHT state intact when the final status
                // cannot be committed. Never imply durable completion.
                checkpointWriteFailed = true
            }
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
