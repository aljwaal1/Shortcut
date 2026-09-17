package com.explapp.shortcut.automation.routines

enum class RoutineActionStatus { SUCCESS, PREPARED, FAILED }
enum class RoutineRunStatus { SUCCESS, PREPARED, FAILED }

data class RoutineActionResult(
    val action: RoutineAction,
    val status: RoutineActionStatus,
    val reason: String? = null,
) {
    companion object {
        fun success(action: RoutineAction) = RoutineActionResult(action, RoutineActionStatus.SUCCESS)
        fun prepared(action: RoutineAction, reason: String? = null) = RoutineActionResult(action, RoutineActionStatus.PREPARED, reason)
        fun failure(action: RoutineAction, reason: String) = RoutineActionResult(action, RoutineActionStatus.FAILED, reason)
    }
}

data class RoutineRunResult(
    val routineId: String,
    val routineName: String,
    val status: RoutineRunStatus,
    val actionResults: List<RoutineActionResult>,
    val startedAtMs: Long,
    val finishedAtMs: Long,
) {
    val durationMs: Long get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)
}

fun interface RoutineActionRunner {
    fun run(action: RoutineAction): RoutineActionResult
}
