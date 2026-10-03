package com.explapp.shortcut.backup

import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineAction
import com.explapp.shortcut.automation.routines.RoutineActionType
import com.explapp.shortcut.automation.routines.RoutineTrigger
import com.explapp.shortcut.automation.routines.RoutineTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineTransferCodecTest {
    @Test
    fun importCreatesNewLocalIdentity() {
        val routine = AutomationRoutine(
            id = "source-id",
            name = "Commute",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "08:00"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_MAPS, "work")),
        )
        val raw = RoutineTransferCodec.encode(routine)
        val imported = RoutineTransferCodec.decode(raw, newId = "local-id").getOrThrow()
        assertEquals("Commute", imported.name)
        assertNotEquals(routine.id, imported.id)
        assertEquals("local-id", imported.id)
    }
}
