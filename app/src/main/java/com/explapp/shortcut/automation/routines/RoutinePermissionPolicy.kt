package com.explapp.shortcut.automation.routines

object RoutinePermissionPolicy {
    fun needsNotifications(triggerType: RoutineTriggerType, actions: List<RoutineAction>): Boolean =
        triggerType in setOf(
            RoutineTriggerType.TIME,
            RoutineTriggerType.CHARGER_CONNECTED,
            RoutineTriggerType.CHARGER_DISCONNECTED,
            RoutineTriggerType.BATTERY_BELOW,
            RoutineTriggerType.BOOT,
        ) || actions.any { it.type == RoutineActionType.SHOW_NOTIFICATION }

    fun needsExactAlarm(triggerType: RoutineTriggerType): Boolean =
        triggerType == RoutineTriggerType.TIME
}
