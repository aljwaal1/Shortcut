package com.explapp.shortcut.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulingPermissionPlanTest {
    @Test
    fun api33RequestsNotificationThenExactAlarm() {
        assertEquals(
            listOf(SchedulingPermissionStep.NOTIFICATIONS, SchedulingPermissionStep.EXACT_ALARM),
            SchedulingPermissionPlan.steps(apiLevel = 33, notificationsGranted = false, exactAlarmGranted = false),
        )
    }

    @Test
    fun skipsPermissionsAlreadyGrantedOrNotRequired() {
        assertEquals(
            listOf(SchedulingPermissionStep.EXACT_ALARM),
            SchedulingPermissionPlan.steps(apiLevel = 33, notificationsGranted = true, exactAlarmGranted = false),
        )
        assertEquals(
            emptyList<SchedulingPermissionStep>(),
            SchedulingPermissionPlan.steps(apiLevel = 30, notificationsGranted = false, exactAlarmGranted = false),
        )
    }
}
