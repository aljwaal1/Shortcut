package com.explapp.shortcut.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequirementTest {
    @Test
    fun notificationPermissionIsRequiredFromApi33() {
        assertFalse(PermissionRequirement.notificationRuntimePermissionRequired(32))
        assertTrue(PermissionRequirement.notificationRuntimePermissionRequired(33))
    }

    @Test
    fun exactAlarmSpecialAccessIsRequiredFromApi31() {
        assertFalse(PermissionRequirement.exactAlarmAccessRequired(30))
        assertTrue(PermissionRequirement.exactAlarmAccessRequired(31))
    }
}
