package com.explapp.shortcut.automation.routines

import com.explapp.shortcut.execution.TaskExecutionResult

private fun RoutineRunResult.stepDetails(): List<String> =
    actionResults.mapIndexed { index, item ->
        val status = when (item.status) {
            RoutineActionStatus.SUCCESS -> "SUCCESS"
            RoutineActionStatus.PREPARED -> "PREPARED"
            RoutineActionStatus.FAILED -> "FAILURE"
        }
        buildString {
            append(index + 1)
            append(". ")
            append(item.action.type.name)
            append(" = ")
            append(status)
            item.reason?.takeIf { it.isNotBlank() }?.let {
                append(" — ")
                append(it)
            }
        }
    }

fun RoutineRunResult.toTaskExecutionResult(): TaskExecutionResult {
    val details = stepDetails()
    return when (status) {
        RoutineRunStatus.SUCCESS -> TaskExecutionResult.success(
            routineName,
            startedAtMs,
            finishedAtMs,
            details,
        )
        RoutineRunStatus.PREPARED -> TaskExecutionResult.prepared(
            routineName,
            startedAtMs,
            finishedAtMs,
            details,
        )
        RoutineRunStatus.FAILED -> TaskExecutionResult.failure(
            taskName = routineName,
            reason = actionResults.firstOrNull { it.status == RoutineActionStatus.FAILED }?.reason ?: "Routine failed",
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
            details = details,
        )
    }
}
