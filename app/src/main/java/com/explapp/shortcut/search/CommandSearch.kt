package com.explapp.shortcut.search

import java.util.Locale

enum class CommandKind { TOOL, ROUTINE, TEMPLATE, SCHEDULED }

data class CommandItem(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
    val kind: CommandKind,
)

object CommandSearch {
    fun filter(items: List<CommandItem>, query: String): List<CommandItem> {
        val tokens = normalize(query)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return items

        return items.mapNotNull { item ->
            val label = normalize(item.label)
            val keywords = item.keywords.map(::normalize)
            val haystack = buildString {
                append(label)
                append(' ')
                append(keywords.joinToString(" "))
            }

            if (!tokens.all(haystack::contains)) return@mapNotNull null

            val score = tokens.sumOf { token ->
                when {
                    label == token -> 100
                    label.startsWith(token) -> 60
                    label.contains(token) -> 40
                    keywords.any { it.startsWith(token) } -> 25
                    else -> 10
                }
            }
            item to score
        }
            .sortedWith(
                compareByDescending<Pair<CommandItem, Int>> { it.second }
                    .thenBy { it.first.label.lowercase(Locale.ROOT) },
            )
            .map { it.first }
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
}
