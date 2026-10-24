package com.explapp.shortcut.automation.routines

/**
 * Automatic retries are limited to idempotent local waiting.
 * Notifications and external sends are deliberately not retried because a failure
 * might occur after the side-effect has already happened.
 */
object RoutineRetryPolicy {
    fun count(action: RoutineAction): Int =
        if (action.type == RoutineActionType.WAIT) action.parameters["retryCount"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
        else 0

    fun valid(action: RoutineAction): Boolean {
        val raw = action.parameters["retryCount"] ?: return true
        return action.type == RoutineActionType.WAIT && raw.toIntOrNull() in 0..3
    }

    fun run(action: RoutineAction, runner: RoutineActionRunner): RoutineActionResult {
        var outcome: RoutineActionResult
        var retries = count(action)
        do {
            outcome = runCatching { runner.run(action) }
                .getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
            if (outcome.status != RoutineActionStatus.FAILED || retries-- <= 0) return outcome
        } while (true)
    }
}
