package com.explapp.shortcut.automation.routines

import com.explapp.shortcut.execution.TaskExecutionResult

fun RoutineRunResult.toTaskExecutionResult(): TaskExecutionResult = when (status) {
    RoutineRunStatus.SUCCESS -> TaskExecutionResult.success(routineName, startedAtMs, finishedAtMs)
    RoutineRunStatus.PREPARED -> TaskExecutionResult.prepared(routineName, startedAtMs, finishedAtMs)
    RoutineRunStatus.FAILED -> TaskExecutionResult.failure(
        taskName = routineName,
        reason = actionResults.firstOrNull { it.status == RoutineActionStatus.FAILED }?.reason ?: "Routine failed",
        startedAtMs = startedAtMs,
        finishedAtMs = finishedAtMs,
    )
}
