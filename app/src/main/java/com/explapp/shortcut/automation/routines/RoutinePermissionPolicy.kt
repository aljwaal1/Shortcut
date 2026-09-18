package com.explapp.shortcut.automation.routines

object RoutinePermissionPolicy {
    fun needsNotifications(triggerType: RoutineTriggerType, actions: List<RoutineAction>): Boolean =
        triggerType in setOf(
            RoutineTriggerType.TIME,
            RoutineTriggerType.CHARGER_CONNECTED,
            RoutineTriggerType.CHARGER_DISCONNECTED,
            RoutineTriggerType.BATTERY_BELOW,
            RoutineTriggerType.BOOT,
        ) || actions.any {
            it.type in setOf(
                RoutineActionType.SHOW_NOTIFICATION,
                RoutineActionType.OPEN_APP_SCREENSHOT,
                RoutineActionType.TAKE_SCREENSHOT,
            )
        }

    fun needsExactAlarm(triggerType: RoutineTriggerType): Boolean =
        triggerType == RoutineTriggerType.TIME
}
