package com.explapp.shortcut.ui

import android.content.Context

/**
 * Local, bounded catalog preferences adapted from the durable blueprint-catalog storage pattern in
 * OpenTasker (c) 2026 SysAdminDoc, MIT License.
 * Source: https://github.com/SysAdminDoc/OpenTasker/blob/a3b4fda4942eb61e9c81290d0ff390ad1f2160b8/app/src/main/java/com/opentasker/core/templates/BlueprintStores.kt
 * Unlike blueprint installations, this store only saves stable template IDs, never actions or credentials.
 * See THIRD_PARTY_NOTICES/OpenTasker-LICENSE.txt.
 */
internal class TemplateLibraryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shortcut_template_library", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun toggleFavorite(id: String): Set<String> {
        require(id.isNotBlank()) { "Template id must not be blank" }
        val next = favorites().toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet("favorites", next).apply()
        return next
    }

    fun recent(): List<String> = prefs.getString("recent", "").orEmpty()
        .split('|').filter(String::isNotBlank).distinct().take(MAX_RECENT)

    fun recordOpen(id: String): List<String> {
        require(id.isNotBlank() && '|' !in id) { "Invalid template id" }
        val next = (listOf(id) + recent().filterNot { it == id }).take(MAX_RECENT)
        prefs.edit().putString("recent", next.joinToString("|")).apply()
        return next
    }

    private companion object { const val MAX_RECENT = 12 }
}

internal data class TemplateDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val searchTerms: String = "",
)

/** Pure filtering logic, shared by the UI and JVM tests. */
internal object TemplateLibraryFilter {
    const val ALL = "all"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ـ', ' ')

    fun select(
        templates: List<TemplateDescriptor>,
        query: String,
        category: String,
        favorites: Set<String>,
        recent: List<String>,
    ): List<TemplateDescriptor> {
        val terms = normalize(query).trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val filtered = templates.filter { template ->
            val searchable = normalize("${template.title} ${template.description} ${template.searchTerms}")
            (category == ALL || when (category) {
                FAVORITES -> template.id in favorites
                RECENT -> template.id in recent
                else -> template.category == category
            }) && terms.all { term ->
                searchable.contains(term)
            }
        }
        return if (category == RECENT) {
            val order = recent.withIndex().associate { it.value to it.index }
            filtered.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        } else filtered
    }
}
