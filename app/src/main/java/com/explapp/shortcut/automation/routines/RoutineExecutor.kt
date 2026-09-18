package com.explapp.shortcut.automation.routines

fun interface RoutineConditionEvaluator {
    fun matches(condition: RoutineCondition): Boolean
}

class RoutineExecutor(
    private val runner: RoutineActionRunner,
    private val conditionEvaluator: RoutineConditionEvaluator = RoutineConditionEvaluator { true },
) {
    fun execute(
        routine: AutomationRoutine,
        startedAtMs: Long = System.currentTimeMillis(),
        finishedAtMs: Long? = null,
    ): RoutineRunResult {
        if (!routine.isEnabled || !routine.isValid()) {
            val end = finishedAtMs ?: System.currentTimeMillis()
            return RoutineRunResult(routine.id, routine.name, RoutineRunStatus.FAILED, emptyList(), startedAtMs, end)
        }

        if (routine.conditions.any { !conditionEvaluator.matches(it) }) {
            val end = finishedAtMs ?: System.currentTimeMillis()
            return RoutineRunResult(routine.id, routine.name, RoutineRunStatus.SKIPPED, emptyList(), startedAtMs, end)
        }

        val results = mutableListOf<RoutineActionResult>()
        var index = 0
        while (index < routine.actions.size) {
            var action = routine.actions[index]

            if (
                action.type == RoutineActionType.OPEN_APP_SCREENSHOT &&
                index + 1 < routine.actions.size &&
                routine.actions[index + 1].type == RoutineActionType.SEND_TELEGRAM_BOT
            ) {
                val telegram = routine.actions[index + 1]
                action = action.copy(
                    parameters = action.parameters + mapOf(
                        "telegramBotToken" to telegram.parameters["botToken"].orEmpty(),
                        "telegramChatId" to telegram.parameters["chatId"].orEmpty(),
                        "telegramCaption" to telegram.secondaryValue,
                    ),
                )
                index += 1
            }

            val result = runCatching { runner.run(action) }
                .getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
            results += result
            if (result.status == RoutineActionStatus.FAILED && !action.continueOnError) break
            index += 1
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
