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
            when (item.action.type) {
                RoutineActionType.OPEN_APP -> append(" [package=${item.action.value}]")
                RoutineActionType.OPEN_APP_SCREENSHOT -> {
                    append(" [package=${item.action.value}")
                    append(", delayMs=${item.action.secondaryValue.ifBlank { "3000" }}")
                    append(", stamp=${item.action.parameters["stampDateTime"].toBoolean()}")
                    append(", persistent=${item.action.parameters["persistentCapture"].toBoolean()}]")
                }
                RoutineActionType.TAKE_SCREENSHOT ->
                    append(" [delayMs=${item.action.value.ifBlank { "3000" }}, stamp=${item.action.parameters["stampDateTime"].toBoolean()}]")
                RoutineActionType.WAIT -> append(" [delayMs=${item.action.value}]")
                else -> Unit
            }
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
