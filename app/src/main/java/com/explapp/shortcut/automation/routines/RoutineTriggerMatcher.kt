package com.explapp.shortcut.automation.routines

data class RoutineEvent(
    val type: RoutineTriggerType,
    val value: String = "",
)

object RoutineTriggerMatcher {
    fun matches(routine: AutomationRoutine, event: RoutineEvent): Boolean =
        routine.isEnabled && matches(routine.trigger, event)

    fun matches(trigger: RoutineTrigger, event: RoutineEvent): Boolean {
        if (trigger.type != event.type) return false
        return when (trigger.type) {
            RoutineTriggerType.BATTERY_BELOW -> {
                val threshold = trigger.value.toIntOrNull() ?: return false
                val actual = event.value.toIntOrNull() ?: return false
                actual <= threshold
            }
            RoutineTriggerType.TIME,
            RoutineTriggerType.NFC,
            -> trigger.value == event.value
            else -> true
        }
    }
}
