package com.explapp.shortcut.automation.routines

object RoutineTemplateCatalog {
    fun templates(): List<AutomationRoutine> = listOf(
        AutomationRoutine(
            name = "Morning apps",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "07:30"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP, "")),
        ),
        AutomationRoutine(
            name = "Commute maps",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "08:00"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_MAPS, "")),
        ),
        AutomationRoutine(
            name = "Charging reminder",
            trigger = RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Charging started")),
        ),
        AutomationRoutine(
            name = "Low battery",
            trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Battery below 20%")),
        ),
        AutomationRoutine(
            name = "WhatsApp prepared message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_WHATSAPP, "", "")),
        ),
        AutomationRoutine(
            name = "Telegram prepared message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_TELEGRAM, "", "")),
        ),
        AutomationRoutine(
            name = "Open app + screenshot",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "", "2000")),
        ),
        AutomationRoutine(
            name = "App usage",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_TOOL, "app_usage")),
        ),
    )
}
