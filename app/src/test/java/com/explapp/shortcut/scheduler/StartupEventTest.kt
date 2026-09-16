package com.explapp.shortcut.scheduler

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupEventTest {
    @Test
    fun bootAndPackageReplacementRestoreSchedules() {
        assertTrue(StartupEvent.shouldReschedule(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupEvent.shouldReschedule(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun unrelatedBroadcastDoesNotRestoreSchedules() {
        assertFalse(StartupEvent.shouldReschedule(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }
}
