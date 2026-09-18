package com.explapp.shortcut.automation.routines

import java.util.UUID

enum class RoutineTriggerType {
    MANUAL,
    TIME,
    CHARGER_CONNECTED,
    CHARGER_DISCONNECTED,
    BATTERY_BELOW,
    NFC,
    BOOT,
}

enum class RoutineActionType {
    OPEN_APP,
    OPEN_APP_SCREENSHOT,
    OPEN_URL,
    OPEN_MAPS,
    PREPARE_WHATSAPP,
    PREPARE_TELEGRAM,
    OPEN_TOOL,
    SHOW_NOTIFICATION,
}

data class RoutineTrigger(
    val type: RoutineTriggerType,
    val value: String = "",
) {
    fun isValid(): Boolean = when (type) {
        RoutineTriggerType.TIME -> {
            val parts = value.split(':')
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()
            parts.size == 2 && hour in 0..23 && minute in 0..59
        }
        RoutineTriggerType.BATTERY_BELOW -> value.toIntOrNull() in 1..100
        else -> true
    }
}

data class RoutineAction(
    val type: RoutineActionType,
    val value: String,
    val secondaryValue: String = "",
    val continueOnError: Boolean = false,
) {
    fun isValid(): Boolean = when (type) {
        RoutineActionType.PREPARE_WHATSAPP,
        RoutineActionType.PREPARE_TELEGRAM,
        -> value.isNotBlank() && secondaryValue.isNotBlank()

        RoutineActionType.OPEN_APP_SCREENSHOT -> {
            val delayMs = secondaryValue.takeIf { it.isNotBlank() }?.toLongOrNull() ?: 2_000L
            value.isNotBlank() && delayMs in 500L..10_000L
        }

        else -> value.isNotBlank()
    }
}

data class AutomationRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isEnabled: Boolean = true,
    val trigger: RoutineTrigger,
    val actions: List<RoutineAction>,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
            name.isNotBlank() &&
            trigger.isValid() &&
            actions.isNotEmpty() &&
            actions.all(RoutineAction::isValid)

    fun duplicate(newId: String = UUID.randomUUID().toString()): AutomationRoutine =
        copy(id = newId, isEnabled = true, updatedAtMs = System.currentTimeMillis())
}
