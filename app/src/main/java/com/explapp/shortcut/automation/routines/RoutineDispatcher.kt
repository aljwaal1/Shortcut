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
            val result = execute(routine, userInitiated)
            onComplete?.invoke(result)
        }.start()
    }

    fun dispatch(event: RoutineEvent): List<RoutineRunResult> = RoutineStore(context)
        .load()
        .filter { RoutineTriggerMatcher.matches(it, event) }
        .map { execute(it, userInitiated = false) }

    fun execute(routine: AutomationRoutine, userInitiated: Boolean): RoutineRunResult {
        val startedAt = System.currentTimeMillis()
        if (!runningRoutineIds.add(routine.id)) {
            return RoutineRunResult(
                routineId = routine.id,
                routineName = routine.name,
                status = RoutineRunStatus.PREPARED,
                actionResults = emptyList(),
                startedAtMs = startedAt,
                finishedAtMs = System.currentTimeMillis(),
            )
        }

        return try {
            val result = RoutineExecutor(
                AndroidRoutineActionRunner(context, userInitiated, routine.id, routine.name),
                AndroidRoutineConditionEvaluator(context),
            ).execute(routine, startedAtMs = startedAt)
            TaskExecutionReporter(context).report(result.toTaskExecutionResult())
            result
        } finally {
            runningRoutineIds.remove(routine.id)
        }
    }

    companion object {
        private val runningRoutineIds = ConcurrentHashMap.newKeySet<String>()
    }
}
