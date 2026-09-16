package com.explapp.shortcut.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledAppShortcutTest {

    @Test
    fun shortcutIsValidWhenAppAndTimeAreSelected() {
        val shortcut = ScheduledAppShortcut(
            name = "Morning Maps",
            packageName = "com.google.android.apps.maps",
            hour = 7,
            minute = 30,
            repeat = RepeatOption.DAILY,
        )

        assertTrue(shortcut.isValid())
    }

    @Test
    fun shortcutIsInvalidWithoutTargetApp() {
        val shortcut = ScheduledAppShortcut(
            name = "Morning Maps",
            packageName = "",
            hour = 7,
            minute = 30,
            repeat = RepeatOption.ONCE,
        )

        assertFalse(shortcut.isValid())
    }

    @Test
    fun weekdaysRepeatContainsMondayThroughFriday() {
        assertEquals(
            setOf(1, 2, 3, 4, 5),
            RepeatOption.WEEKDAYS.isoWeekdays,
        )
    }
}
