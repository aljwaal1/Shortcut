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
        routine.actions.forEachIndexed { index, action ->
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
            if (action.type == RoutineActionType.STOP_SHORTCUT && index < routine.actions.lastIndex) {
                findings += Finding(Severity.WARNING, index, "ACTIONS_AFTER_STOP_UNREACHABLE")
            }
        }
        return Report(findings)
    }
}
