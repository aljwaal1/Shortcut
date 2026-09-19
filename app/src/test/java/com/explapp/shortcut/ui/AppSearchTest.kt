package com.explapp.shortcut.ui

import com.explapp.shortcut.data.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {
    @Test
    fun filtersByNameIgnoringCaseAndKeepsAlphabeticalOrder() {
        val apps = listOf(
            InstalledApp("Zoom", "zoom"),
            InstalledApp("Google Maps", "maps"),
            InstalledApp("Camera", "camera"),
            InstalledApp("Maps Lite", "maps.lite"),
        )

        val result = AppSearch.filter(apps, "MAP")

        assertEquals(listOf("Google Maps", "Maps Lite"), result.map { it.label })
    }
}
