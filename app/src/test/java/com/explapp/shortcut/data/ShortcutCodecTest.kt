package com.explapp.shortcut.data

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutCodecTest {
    @Test
    fun shortcutListRoundTripsWithoutLosingArabicNames() {
        val shortcuts = listOf(
            ScheduledAppShortcut(
                name = "افتح الخرائط",
                packageName = "com.google.android.apps.maps",
                hour = 7,
                minute = 30,
                repeat = RepeatOption.WEEKDAYS,
            ),
        )

        val encoded = ShortcutCodec.encode(shortcuts)
        val decoded = ShortcutCodec.decode(encoded)

        assertEquals(shortcuts, decoded)
    }
}
