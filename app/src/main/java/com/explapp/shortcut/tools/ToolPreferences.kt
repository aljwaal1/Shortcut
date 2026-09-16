package com.explapp.shortcut.tools

import android.content.Context

object ToolPreferenceLogic {
    fun toggleFavorite(favorites: Set<ToolId>, toolId: ToolId): Set<ToolId> =
        favorites.toMutableSet().apply {
            if (!add(toolId)) remove(toolId)
        }

    fun recordRecent(recents: List<ToolId>, toolId: ToolId, limit: Int = 8): List<ToolId> {
        if (limit <= 0) return emptyList()
        return (listOf(toolId) + recents.filterNot { it == toolId }).take(limit)
    }
}

class ToolPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("tool_preferences", Context.MODE_PRIVATE)

    fun favorites(): Set<ToolId> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
        .mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() }
        .toSet()

    fun recents(): List<ToolId> = prefs.getString(KEY_RECENTS, "").orEmpty()
        .split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() }
        .distinct()
        .take(8)

    fun toggleFavorite(toolId: ToolId): Set<ToolId> {
        val updated = ToolPreferenceLogic.toggleFavorite(favorites(), toolId)
        prefs.edit().putStringSet(KEY_FAVORITES, updated.map { it.name }.toSet()).apply()
        return updated
    }

    fun recordRecent(toolId: ToolId): List<ToolId> {
        val updated = ToolPreferenceLogic.recordRecent(recents(), toolId)
        prefs.edit().putString(KEY_RECENTS, updated.joinToString(",") { it.name }).apply()
        return updated
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
    }
}
