package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOperationStateTest {
    @Test
    fun progressAdvancesAndCapsAtTotal() {
        val start = MediaOperationProgress(total = 3, completed = 0, cancelled = false)
        val one = start.next()
        val done = one.next().next().next()
        assertEquals(1, one.completed)
        assertEquals(1f / 3f, one.fraction, 0.0001f)
        assertEquals(3, done.completed)
        assertEquals(1f, done.fraction, 0.0001f)
    }

    @Test
    fun cancellationIsDistinctFromFailure() {
        val state = MediaOperationProgress(total = 2, completed = 1, cancelled = false).cancel()
        assertTrue(state.cancelled)
        assertFalse(state.isComplete)
        assertEquals(MediaOperationResult.Cancelled, state.result())
    }
}
