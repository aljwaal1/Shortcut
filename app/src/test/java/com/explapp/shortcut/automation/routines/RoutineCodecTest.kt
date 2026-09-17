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
