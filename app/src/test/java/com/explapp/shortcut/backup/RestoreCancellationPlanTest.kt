package com.explapp.shortcut.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreCancellationPlanTest {
    @Test
    fun cancelsEveryPreviouslyScheduledIdBeforeImportedStateIsApplied() {
        val plan = RestoreCancellationPlan.from(
            shortcutIds = listOf("s1", "s2"),
            messageIds = listOf("m1"),
            routineIds = listOf("r1", "r2"),
        )

        assertEquals(listOf("s1", "s2"), plan.shortcutIds)
        assertEquals(listOf("m1"), plan.messageIds)
        assertEquals(listOf("r1", "r2"), plan.routineIds)
    }
}
