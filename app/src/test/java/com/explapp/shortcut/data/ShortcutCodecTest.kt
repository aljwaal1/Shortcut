package com.explapp.shortcut.data

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCodecTest {
    @Test
    fun shortcutListRoundTripsWithoutLosingIdentityOrArabicNames() {
        val shortcuts = listOf(
            ScheduledAppShortcut(
                id = "shortcut-1",
                name = "افتح الخرائط",
                packageName = "com.google.android.apps.maps",
                hour = 7,
                minute = 30,
                repeat = RepeatOption.WEEKDAYS,
                isEnabled = false,
            ),
        )

        val decoded = ShortcutCodec.decode(ShortcutCodec.encode(shortcuts))

        assertEquals(shortcuts, decoded)
    }

    @Test
    fun legacyShortcutGetsStableIdAndDefaultsEnabled() {
        val legacy = "2YHYp9iqINin2YTYrtix2KfYpti3|Y29tLmdvb2dsZS5hbmRyb2lkLmFwcHMubWFwcw|7|30|WEEKDAYS"

        val first = ShortcutCodec.decode(legacy).single()
        val migrated = ShortcutCodec.encode(listOf(first))
        val second = ShortcutCodec.decode(migrated).single()

        assertTrue(first.id.isNotBlank())
        assertTrue(first.isEnabled)
        assertEquals(first.id, second.id)
    }

    @Test
    fun duplicateCreatesNewIdentity() {
        val original = ScheduledAppShortcut(
            id = "shortcut-original",
            name = "Maps",
            packageName = "maps",
            hour = 8,
            minute = 0,
            repeat = RepeatOption.DAILY,
        )

        val duplicate = original.duplicate(newId = "shortcut-copy")

        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.packageName, duplicate.packageName)
        assertEquals(original.hour, duplicate.hour)
    }
}
