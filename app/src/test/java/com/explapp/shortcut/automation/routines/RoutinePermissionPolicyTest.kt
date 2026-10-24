package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePermissionPolicyTest {
    @Test
    fun backgroundTriggersRequireNotifications() {
        assertTrue(RoutinePermissionPolicy.needsNotifications(RoutineTriggerType.TIME, emptyList()))
        assertTrue(RoutinePermissionPolicy.needsNotifications(RoutineTriggerType.BATTERY_BELOW, emptyList()))
        assertTrue(RoutinePermissionPolicy.needsNotifications(RoutineTriggerType.CHARGER_CONNECTED, emptyList()))
        assertTrue(RoutinePermissionPolicy.needsNotifications(RoutineTriggerType.BOOT, emptyList()))
    }

    @Test
    fun manualOpenAppDoesNotRequireNotifications() {
        assertFalse(
            RoutinePermissionPolicy.needsNotifications(
                RoutineTriggerType.MANUAL,
                listOf(RoutineAction(RoutineActionType.OPEN_APP, "pkg")),
            ),
        )
    }

    @Test
    fun notificationActionAlwaysRequiresNotificationPermission() {
        assertTrue(
            RoutinePermissionPolicy.needsNotifications(
                RoutineTriggerType.MANUAL,
                listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "hello")),
            ),
        )
    }

    @Test
    fun onlyTimeTriggerNeedsExactAlarmAccess() {
        assertTrue(RoutinePermissionPolicy.needsExactAlarm(RoutineTriggerType.TIME))
        assertFalse(RoutinePermissionPolicy.needsExactAlarm(RoutineTriggerType.BATTERY_BELOW))
    }
}
