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
)

data class RoutineAction(
    val type: RoutineActionType,
    val value: String,
    val secondaryValue: String = "",
    val continueOnError: Boolean = false,
)

data class AutomationRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isEnabled: Boolean = true,
    val trigger: RoutineTrigger,
    val actions: List<RoutineAction>,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun isValid(): Boolean = id.isNotBlank() && name.isNotBlank() && actions.isNotEmpty()

    fun duplicate(newId: String = UUID.randomUUID().toString()): AutomationRoutine =
        copy(id = newId, isEnabled = true, updatedAtMs = System.currentTimeMillis())
}
