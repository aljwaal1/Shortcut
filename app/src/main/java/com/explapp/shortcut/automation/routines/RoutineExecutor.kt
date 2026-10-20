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
        if (!routine.isEnabled || !routine.isValid() || !RoutineReview.inspect(routine).canRun) {
            val end = finishedAtMs ?: System.currentTimeMillis()
            return RoutineRunResult(routine.id, routine.name, RoutineRunStatus.FAILED, emptyList(), startedAtMs, end)
        }

        if (routine.conditions.any { !conditionEvaluator.matches(it) }) {
            val end = finishedAtMs ?: System.currentTimeMillis()
            return RoutineRunResult(routine.id, routine.name, RoutineRunStatus.SUCCESS, emptyList(), startedAtMs, end)
        }

        val results = mutableListOf<RoutineActionResult>()
        var index = 0
        while (index < routine.actions.size) {
            var action = routine.actions[index]
            var screenshotTelegramHandoff = false

            if (
                action.type in setOf(RoutineActionType.OPEN_APP_SCREENSHOT, RoutineActionType.TAKE_SCREENSHOT) &&
                index + 1 < routine.actions.size &&
                routine.actions[index + 1].type == RoutineActionType.SEND_TELEGRAM_BOT
            ) {
                val telegram = routine.actions[index + 1]
                if (telegram.parameters["botToken"].isNullOrBlank() ||
                    telegram.parameters["chatId"].isNullOrBlank()
                ) {
                    results += RoutineActionResult.failure(
                        telegram,
                        "Telegram bot token and recipient must be configured before capturing a screenshot",
                    )
                    break
                }
                action = action.copy(
                    parameters = action.parameters + mapOf(
                        "telegramBotToken" to telegram.parameters["botToken"].orEmpty(),
                        "telegramChatId" to telegram.parameters["chatId"].orEmpty(),
                        "telegramCaption" to telegram.secondaryValue,
                    ),
                )
                // This action hands delivery to the screenshot pipeline. Only skip the
                // standalone Telegram action when the capture was successfully queued.
                // A failed capture must never be reported as a completed send.
                screenshotTelegramHandoff = true
            }

            val result = runCatching { runner.run(action) }
                .getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
            results += result
            if (screenshotTelegramHandoff && result.status == RoutineActionStatus.SUCCESS) {
                // Capturing and delivering happen asynchronously; completion is not proof of sending.
                results += RoutineActionResult.prepared(
                    routine.actions[index + 1],
                    "Screenshot delivery has not been confirmed",
                )
                break
            }
            if (screenshotTelegramHandoff && result.status == RoutineActionStatus.PREPARED) {
                // The sender is part of the pending screenshot pipeline, not a second action.
                break
            }
            if (result.status == RoutineActionStatus.PREPARED) break
            if (result.status == RoutineActionStatus.FAILED && (screenshotTelegramHandoff || !action.continueOnError)) break
            if (action.type == RoutineActionType.STOP_SHORTCUT) break
            index += if (screenshotTelegramHandoff) 2 else 1
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
