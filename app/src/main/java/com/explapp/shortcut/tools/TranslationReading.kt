package com.explapp.shortcut.tools

/** Small, predictable input units for on-device translators. */
object TranslationReading {
    fun pageIndex(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)

    fun chunks(text: String, maxChars: Int = 450): List<String> {
        require(maxChars > 0)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var rest = trimmed
        while (rest.length > maxChars) {
            val candidate = rest.substring(0, maxChars)
            val punctuation = candidate.indices.lastOrNull { index ->
                candidate[index] in ".!?؟؛。\\n" &&
                    index + 1 < rest.length &&
                    rest[index + 1].isWhitespace()
            }?.plus(1) ?: -1
            val space = candidate.lastIndexOf(' ')
            val split = when {
                punctuation >= maxChars / 4 -> punctuation
                space >= maxChars / 2 -> space
                else -> maxChars
            }
            result += rest.substring(0, split).trim()
            rest = rest.substring(split).trimStart()
        }
        if (rest.isNotEmpty()) result += rest
        return result
    }
}
