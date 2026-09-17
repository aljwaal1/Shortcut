package com.explapp.shortcut.automation.routines

class RoutineExecutor(private val runner: RoutineActionRunner) {
    fun execute(
        routine: AutomationRoutine,
        startedAtMs: Long = System.currentTimeMillis(),
        finishedAtMs: Long? = null,
    ): RoutineRunResult {
        if (!routine.isEnabled || !routine.isValid()) {
            val end = finishedAtMs ?: System.currentTimeMillis()
            return RoutineRunResult(routine.id, routine.name, RoutineRunStatus.FAILED, emptyList(), startedAtMs, end)
        }

        val results = mutableListOf<RoutineActionResult>()
        for (action in routine.actions) {
            val result = runCatching { runner.run(action) }
                .getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
            results += result
            if (result.status == RoutineActionStatus.FAILED && !action.continueOnError) break
        }

        val status = when {
            results.any { it.status == RoutineActionStatus.FAILED } -> RoutineRunStatus.FAILED
            results.any { it.status == RoutineActionStatus.PREPARED } -> RoutineRunStatus.PREPARED
            else -> RoutineRunStatus.SUCCESS
        }
        return RoutineRunResult(
            routineId = routine.id,
            routineName = routine.name,
            status = status,
            actionResults = results,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs ?: System.currentTimeMillis(),
        )
    }
}
