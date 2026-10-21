package com.explapp.shortcut.automation.routines

/**
 * Local, side-effect-free automation review inspired by OpenTasker's explicit
 * admission checks. Nothing is launched or sent during a review.
 */
object RoutineReview {
    enum class Severity { ERROR, WARNING }
    data class Finding(val severity: Severity, val actionIndex: Int?, val code: String)
    data class Report(val findings: List<Finding>) {
        val canRun: Boolean get() = findings.none { it.severity == Severity.ERROR }
        val warnings: List<Finding> get() = findings.filter { it.severity == Severity.WARNING }
    }

    fun inspect(routine: AutomationRoutine): Report {
        val findings = mutableListOf<Finding>()
        if (routine.name.isBlank()) findings += Finding(Severity.ERROR, null, "NAME_REQUIRED")
        if (!routine.trigger.isValid()) findings += Finding(Severity.ERROR, null, "INVALID_TRIGGER")
        if (routine.actions.isEmpty()) findings += Finding(Severity.ERROR, null, "NO_ACTIONS")
        routine.conditions.forEachIndexed { i, condition ->
            if (!condition.isValid()) findings += Finding(Severity.ERROR, null, "INVALID_CONDITION_$i")
        }
        if (routine.id.isBlank()) findings += Finding(Severity.ERROR, null, "ID_REQUIRED")
        if (!routine.isEnabled) findings += Finding(Severity.WARNING, null, "ROUTINE_DISABLED")
        routine.actions.forEachIndexed { index, action ->
            if (action.parameters["parallelGroup"].orEmpty().isNotBlank() &&
                action.type in setOf(
                    RoutineActionType.OPEN_APP_SCREENSHOT,
                    RoutineActionType.TAKE_SCREENSHOT,
                    RoutineActionType.STOP_SHORTCUT,
                    RoutineActionType.PREPARE_WHATSAPP,
                    RoutineActionType.PREPARE_TELEGRAM,
                    RoutineActionType.SHARE_FILE,
                    RoutineActionType.SHARE_TEXT,
                )
            ) findings += Finding(Severity.ERROR, index, "ACTION_UNSAFE_FOR_PARALLEL")
            if (action.parameters["parallelGroup"].orEmpty().isNotBlank() &&
                routine.actions.take(index).any { it.parameters["parallelGroup"] == action.parameters["parallelGroup"] } &&
                routine.actions.getOrNull(index - 1)?.parameters?.get("parallelGroup") != action.parameters["parallelGroup"]
            ) findings += Finding(Severity.ERROR, index, "PARALLEL_GROUP_NOT_CONTIGUOUS")
            if (!action.isValid()) findings += Finding(Severity.ERROR, index, "INVALID_ACTION")
            if (action.type == RoutineActionType.CUSTOM_SCRIPT) {
                findings += Finding(Severity.WARNING, index, "SCRIPT_REQUIRES_REVIEW")
            }
            if (action.type == RoutineActionType.PREPARE_TELEGRAM ||
                action.type == RoutineActionType.PREPARE_WHATSAPP
            ) {
                findings += Finding(Severity.WARNING, index, "USER_INTERACTION_REQUIRED")
            }
            if (action.type == RoutineActionType.SEND_TELEGRAM_BOT &&
                action.parameters["chatId"].orEmpty().startsWith("@")
            ) {
                findings += Finding(Severity.WARNING, index, "USERNAME_REQUIRES_EXISTING_BOT_CHAT")
            }
            if (action.type == RoutineActionType.OPEN_APP_SCREENSHOT ||
                action.type == RoutineActionType.TAKE_SCREENSHOT
            ) {
                if (routine.trigger.type == RoutineTriggerType.TIME &&
                    action.parameters["persistentCapture"] != "true"
                ) {
                    findings += Finding(Severity.WARNING, index, "SCHEDULED_CAPTURE_MAY_REQUIRE_CONSENT")
                }
                val next = routine.actions.getOrNull(index + 1)
                if (next?.type == RoutineActionType.SEND_TELEGRAM_BOT) {
                    if (next.parameters["botToken"].isNullOrBlank() ||
                        next.parameters["chatId"].isNullOrBlank()
                    ) {
                        findings += Finding(Severity.ERROR, index + 1, "SCREENSHOT_DELIVERY_NOT_CONFIGURED")
                    }
                }
            }
            if (action.type == RoutineActionType.STOP_SHORTCUT && index < routine.actions.lastIndex) {
                findings += Finding(Severity.WARNING, index, "ACTIONS_AFTER_STOP_UNREACHABLE")
            }
        }
        return Report(findings)
    }
}
