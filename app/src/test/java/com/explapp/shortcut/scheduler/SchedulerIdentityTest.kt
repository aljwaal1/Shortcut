package com.explapp.shortcut.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SchedulerIdentityTest {
    @Test
    fun requestCodeIsStableForSameId() {
        assertEquals(
            SchedulerIdentity.requestCode("task-1"),
            SchedulerIdentity.requestCode("task-1"),
        )
    }

    @Test
    fun differentIdsProduceDifferentRequestCodes() {
        assertNotEquals(
            SchedulerIdentity.requestCode("task-1"),
            SchedulerIdentity.requestCode("task-2"),
        )
    }
}
