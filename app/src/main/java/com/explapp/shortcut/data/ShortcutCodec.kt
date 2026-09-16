package com.explapp.shortcut.data

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.nio.charset.StandardCharsets
import java.util.Base64

object ShortcutCodec {
    fun encode(shortcuts: List<ScheduledAppShortcut>): String = shortcuts.joinToString("\n") { shortcut ->
        listOf(
            encodeText(shortcut.name),
            encodeText(shortcut.packageName),
            shortcut.hour.toString(),
            shortcut.minute.toString(),
            shortcut.repeat.name,
        ).joinToString("|")
    }

    fun decode(raw: String): List<ScheduledAppShortcut> = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeLine)
        .toList()

    private fun decodeLine(line: String): ScheduledAppShortcut? {
        val parts = line.split('|')
        if (parts.size != 5) return null

        val repeat = runCatching { RepeatOption.valueOf(parts[4]) }.getOrNull() ?: return null
        val shortcut = ScheduledAppShortcut(
            name = decodeText(parts[0]) ?: return null,
            packageName = decodeText(parts[1]) ?: return null,
            hour = parts[2].toIntOrNull() ?: return null,
            minute = parts[3].toIntOrNull() ?: return null,
            repeat = repeat,
        )
        return shortcut.takeIf { it.isValid() }
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
