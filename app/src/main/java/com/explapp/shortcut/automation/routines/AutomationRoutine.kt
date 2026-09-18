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

enum class RoutineConditionType {
    BATTERY_ABOVE,
    BATTERY_BELOW,
    DAY_OF_WEEK,
    VARIABLE_EQUALS,
    VARIABLE_CONTAINS,
}

data class RoutineCondition(
    val type: RoutineConditionType,
    val value: String,
    val secondaryValue: String = "",
) {
    fun isValid(): Boolean = when (type) {
        RoutineConditionType.BATTERY_ABOVE,
        RoutineConditionType.BATTERY_BELOW,
        -> value.toIntOrNull() in 0..100

        RoutineConditionType.DAY_OF_WEEK -> value.toIntOrNull() in 1..7
        RoutineConditionType.VARIABLE_EQUALS,
        RoutineConditionType.VARIABLE_CONTAINS,
        -> value.isNotBlank()
    }
}

enum class RoutineActionType {
    OPEN_APP,
    OPEN_APP_SCREENSHOT,
    TAKE_SCREENSHOT,
    WAIT,
    OPEN_URL,
    OPEN_MAPS,
    PREPARE_WHATSAPP,
    PREPARE_TELEGRAM,
    SEND_TELEGRAM_BOT,
    CUSTOM_SCRIPT,
    SET_VARIABLE,
    READ_CLIPBOARD,
    COPY_TO_CLIPBOARD,
    STOP_SHORTCUT,
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
    val parameters: Map<String, String> = emptyMap(),
) {
    fun isValid(): Boolean = when (type) {
        RoutineActionType.PREPARE_WHATSAPP,
        RoutineActionType.PREPARE_TELEGRAM,
        -> value.isNotBlank() && secondaryValue.isNotBlank()

        RoutineActionType.OPEN_APP_SCREENSHOT -> {
            val delayMs = secondaryValue.takeIf { it.isNotBlank() }?.toLongOrNull() ?: 3_000L
            value.isNotBlank() && delayMs in 500L..10_000L
        }

        RoutineActionType.TAKE_SCREENSHOT -> {
            val delayMs = value.takeIf { it.isNotBlank() }?.toLongOrNull() ?: 3_000L
            delayMs in 500L..10_000L
        }

        RoutineActionType.WAIT -> value.toLongOrNull()?.let { it in 100L..60_000L } == true

        RoutineActionType.SEND_TELEGRAM_BOT ->
            parameters["botToken"].orEmpty().isNotBlank() &&
                parameters["chatId"].orEmpty().isNotBlank() &&
                (secondaryValue.isNotBlank() || parameters["attachment"].orEmpty().isNotBlank())

        RoutineActionType.CUSTOM_SCRIPT -> value.isNotBlank()
        RoutineActionType.SET_VARIABLE -> value.isNotBlank()
        RoutineActionType.READ_CLIPBOARD -> value.isNotBlank()
        RoutineActionType.COPY_TO_CLIPBOARD -> value.isNotBlank()
        RoutineActionType.STOP_SHORTCUT -> true
        else -> value.isNotBlank()
    }
}

data class AutomationRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isEnabled: Boolean = true,
    val trigger: RoutineTrigger,
    val conditions: List<RoutineCondition> = emptyList(),
    val actions: List<RoutineAction>,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
            name.isNotBlank() &&
            trigger.isValid() &&
            conditions.all(RoutineCondition::isValid) &&
            actions.isNotEmpty() &&
            actions.all(RoutineAction::isValid)

    fun duplicate(newId: String = UUID.randomUUID().toString()): AutomationRoutine =
        copy(id = newId, isEnabled = true, updatedAtMs = System.currentTimeMillis())
}
