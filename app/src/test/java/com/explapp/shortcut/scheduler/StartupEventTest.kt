package com.explapp.shortcut.scheduler

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupEventTest {
    @Test
    fun bootPackageReplacementAndExactAlarmGrantRestoreSchedules() {
        assertTrue(StartupEvent.shouldReschedule(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupEvent.shouldReschedule(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(StartupEvent.shouldReschedule(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
    }

    @Test
    fun unrelatedBroadcastDoesNotRestoreSchedules() {
        assertFalse(StartupEvent.shouldReschedule(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }
}
