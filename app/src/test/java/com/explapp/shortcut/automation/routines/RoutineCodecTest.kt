package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineCodecTest {
    @Test
    fun routineRoundTripPreservesOrderedActionsAndArabicName() {
        val original = listOf(
            AutomationRoutine(
                id = "r-1",
                name = "روتين الصباح",
                isEnabled = true,
                trigger = RoutineTrigger(RoutineTriggerType.TIME, "07:30"),
                actions = listOf(
                    RoutineAction(RoutineActionType.OPEN_APP, "com.google.android.apps.maps"),
                    RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "صباح الخير"),
                ),
            ),
        )

        assertEquals(original, RoutineCodec.decode(RoutineCodec.encode(original)))
    }

    @Test
    fun routineRoundTripPreservesRecurrenceAndOldDataDefaultsDaily() {
        val recurring = listOf(
            AutomationRoutine(
                id = "weekly",
                name = "Weekly",
                trigger = RoutineTrigger(RoutineTriggerType.TIME, "09:15", RoutineRepeat.WEEKLY, "5"),
                actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Done")),
            ),
        )
        assertEquals(recurring, RoutineCodec.decode(RoutineCodec.encode(recurring)))

        val once = listOf(
            AutomationRoutine(
                id = "once",
                name = "Once",
                trigger = RoutineTrigger(RoutineTriggerType.TIME, "10:45", RoutineRepeat.ONCE, "2026-09-20"),
                actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Done")),
            ),
        )
        assertEquals(once, RoutineCodec.decode(RoutineCodec.encode(once)))

        val legacy = """[{"id":"old","name":"Old","isEnabled":true,"updatedAtMs":0,"trigger":{"type":"TIME","value":"07:00"},"actions":[{"type":"SHOW_NOTIFICATION","value":"Hi","secondaryValue":""}]}]"""
        val decoded = RoutineCodec.decode(legacy).single()
        assertEquals(RoutineRepeat.DAILY, decoded.trigger.repeat)
        assertEquals("", decoded.trigger.repeatValue)
    }

    @Test
    fun duplicateCreatesNewIdAndEnablesRoutine() {
        val original = AutomationRoutine(
            id = "r-1",
            name = "Routine",
            isEnabled = false,
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_URL, "https://example.com")),
        )

        val copy = original.duplicate("r-2")
        assertNotEquals(original.id, copy.id)
        assertEquals(true, copy.isEnabled)
    }
}
