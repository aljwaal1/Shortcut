package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineValidationTest {
    @Test
    fun rejectsIncompleteTemplateActions() {
        val routine = AutomationRoutine(
            name = "Morning",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "07:30"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP, "")),
        )
        assertFalse(routine.isValid())
    }

    @Test
    fun rejectsPreparedMessageWithoutBody() {
        val routine = AutomationRoutine(
            name = "Message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_TELEGRAM, "user", "")),
        )
        assertFalse(routine.isValid())
    }

    @Test
    fun validatesOpenAppScreenshotPackageAndDelay() {
        assertTrue(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "com.example.app", "2000").isValid())
        assertTrue(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "com.example.app", "").isValid())
        assertFalse(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "", "2000").isValid())
        assertFalse(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "com.example.app", "50").isValid())
        assertFalse(RoutineAction(RoutineActionType.OPEN_APP_SCREENSHOT, "com.example.app", "30000").isValid())
    }

    @Test
    fun validatesAdvancedActionsAndConditions() {
        assertTrue(RoutineAction(RoutineActionType.WAIT, "3000").isValid())
        assertFalse(RoutineAction(RoutineActionType.WAIT, "10").isValid())
        assertTrue(RoutineAction(RoutineActionType.TAKE_SCREENSHOT, "5000").isValid())
        assertTrue(RoutineAction(RoutineActionType.CUSTOM_SCRIPT, "return input;").isValid())
        assertTrue(RoutineAction(RoutineActionType.SET_VARIABLE, "name", "value").isValid())
        assertTrue(RoutineAction(RoutineActionType.READ_CLIPBOARD, "clipboardText").isValid())
        assertTrue(RoutineAction(RoutineActionType.COPY_TO_CLIPBOARD, "hello").isValid())
        assertTrue(RoutineAction(RoutineActionType.STOP_SHORTCUT, "").isValid())
        assertTrue(RoutineAction(RoutineActionType.SAVE_TEXT_FILE, "hello", "note.txt").isValid())
        assertTrue(RoutineAction(RoutineActionType.SHARE_TEXT, "hello").isValid())
        assertTrue(RoutineAction(RoutineActionType.SHARE_FILE, "{{lastFile}}").isValid())
        assertTrue(RoutineAction(RoutineActionType.WEB_SEARCH, "android automation").isValid())
        assertTrue(
            RoutineAction(
                RoutineActionType.SEND_TELEGRAM_BOT,
                value = "telegram",
                secondaryValue = "hello",
                parameters = mapOf("botToken" to "token", "chatId" to "123"),
            ).isValid(),
        )
        assertTrue(RoutineCondition(RoutineConditionType.BATTERY_ABOVE, "30").isValid())
        assertFalse(RoutineCondition(RoutineConditionType.DAY_OF_WEEK, "8").isValid())
    }

    @Test
    fun validatesTimeRecurrenceValues() {
        assertTrue(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.ONCE, "2026-09-20").isValid())
        assertFalse(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.ONCE, "2026-02-30").isValid())
        assertTrue(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.DAILY).isValid())
        assertTrue(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.WEEKLY, "1").isValid())
        assertFalse(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.WEEKLY, "8").isValid())
        assertTrue(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.MONTHLY, "31").isValid())
        assertFalse(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.MONTHLY, "32").isValid())
        assertTrue(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.YEARLY, "12-25").isValid())
        assertFalse(RoutineTrigger(RoutineTriggerType.TIME, "08:00", RoutineRepeat.YEARLY, "13-40").isValid())
    }

    @Test
    fun validatesTimeAndBatteryTriggerValues() {
        val action = RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Hello")
        assertTrue(AutomationRoutine(name = "Time", trigger = RoutineTrigger(RoutineTriggerType.TIME, "23:59"), actions = listOf(action)).isValid())
        assertFalse(AutomationRoutine(name = "Bad time", trigger = RoutineTrigger(RoutineTriggerType.TIME, "25:00"), actions = listOf(action)).isValid())
        assertTrue(AutomationRoutine(name = "Battery", trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"), actions = listOf(action)).isValid())
        assertFalse(AutomationRoutine(name = "Bad battery", trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "0"), actions = listOf(action)).isValid())
    }
}
