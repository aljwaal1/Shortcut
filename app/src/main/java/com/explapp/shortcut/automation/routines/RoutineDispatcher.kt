package com.explapp.shortcut.automation.routines

import android.content.Context

class RoutineDispatcher(private val context: Context) {
    fun dispatch(event: RoutineEvent): List<RoutineRunResult> = RoutineStore(context)
        .load()
        .filter { RoutineTriggerMatcher.matches(it, event) }
        .map { execute(it, userInitiated = false) }

    fun execute(routine: AutomationRoutine, userInitiated: Boolean): RoutineRunResult =
        RoutineExecutor(AndroidRoutineActionRunner(context, userInitiated)).execute(routine)
}
