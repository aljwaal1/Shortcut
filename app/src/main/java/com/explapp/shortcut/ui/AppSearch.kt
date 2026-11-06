package com.explapp.shortcut.ui

import com.explapp.shortcut.data.InstalledApp

object AppSearch {
    fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val needle = query.trim()
        return apps
            .asSequence()
            .filter { app -> needle.isBlank() || app.label.contains(needle, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
