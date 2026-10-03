package com.explapp.shortcut.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutCollectionTest {

    private val morningMaps = ScheduledAppShortcut(
        name = "Morning Maps",
        packageName = "com.google.android.apps.maps",
        hour = 7,
        minute = 30,
        repeat = RepeatOption.DAILY,
    )

    private val eveningMusic = ScheduledAppShortcut(
        name = "Evening Music",
        packageName = "com.spotify.music",
        hour = 18,
        minute = 0,
        repeat = RepeatOption.DAILY,
    )

    @Test
    fun removeDeletesOnlySelectedShortcut() {
        val updated = ShortcutCollection.remove(
            shortcuts = listOf(morningMaps, eveningMusic),
            shortcut = morningMaps,
        )

        assertEquals(listOf(eveningMusic), updated)
    }
}
