package com.explapp.shortcut.search

enum class CommandKind { TOOL, ROUTINE, TEMPLATE, SCHEDULED }

data class CommandItem(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
    val kind: CommandKind,
)

object CommandSearch {
    fun filter(items: List<CommandItem>, query: String): List<CommandItem> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return items
        return items.filter { item ->
            item.label.lowercase().contains(needle) || item.keywords.any { it.lowercase().contains(needle) }
        }
    }
}
