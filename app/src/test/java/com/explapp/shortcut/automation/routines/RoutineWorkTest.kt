package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineWorkTest {
    @Test
    fun uniqueWorkNameIsStablePerRoutine() {
        assertEquals(
            RoutineWork.uniqueName("routine-a"),
            RoutineWork.uniqueName("routine-a"),
        )
        assertNotEquals(
            RoutineWork.uniqueName("routine-a"),
            RoutineWork.uniqueName("routine-b"),
        )
    }
}
