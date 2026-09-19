package com.explapp.shortcut.automation.routines

import android.content.Context
import com.explapp.shortcut.execution.TaskExecutionReporter

class RoutineDispatcher(private val context: Context) {
    fun dispatch(event: RoutineEvent): List<RoutineRunResult> = RoutineStore(context)
        .load()
        .filter { RoutineTriggerMatcher.matches(it, event) }
        .map { execute(it, userInitiated = false) }

    fun execute(routine: AutomationRoutine, userInitiated: Boolean): RoutineRunResult {
        val result = RoutineExecutor(
            AndroidRoutineActionRunner(context, userInitiated),
            AndroidRoutineConditionEvaluator(context),
        ).execute(routine)
        TaskExecutionReporter(context).report(result.toTaskExecutionResult())
        return result
    }
}
