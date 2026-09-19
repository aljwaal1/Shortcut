package com.explapp.shortcut.data

import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

object ShortcutCodec {
    fun encode(shortcuts: List<ScheduledAppShortcut>): String = shortcuts.joinToString("\n") { shortcut ->
        listOf(
            encodeText(shortcut.id),
            encodeText(shortcut.name),
            encodeText(shortcut.packageName),
            shortcut.hour.toString(),
            shortcut.minute.toString(),
            shortcut.repeat.name,
            shortcut.isEnabled.toString(),
        ).joinToString("|")
    }

    fun decode(raw: String): List<ScheduledAppShortcut> = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeLine)
        .toList()

    fun needsMigration(raw: String): Boolean = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .any { it.split('|').size == 5 }

    private fun decodeLine(line: String): ScheduledAppShortcut? {
        val parts = line.split('|')
        return when (parts.size) {
            5 -> decodeLegacy(parts)
            7 -> decodeCurrent(parts)
            else -> null
        }
    }

    private fun decodeLegacy(parts: List<String>): ScheduledAppShortcut? {
        val repeat = runCatching { RepeatOption.valueOf(parts[4]) }.getOrNull() ?: return null
        return ScheduledAppShortcut(
            id = UUID.randomUUID().toString(),
            name = decodeText(parts[0]) ?: return null,
            packageName = decodeText(parts[1]) ?: return null,
            hour = parts[2].toIntOrNull() ?: return null,
            minute = parts[3].toIntOrNull() ?: return null,
            repeat = repeat,
            isEnabled = true,
        ).takeIf { it.isValid() }
    }

    private fun decodeCurrent(parts: List<String>): ScheduledAppShortcut? {
        val repeat = runCatching { RepeatOption.valueOf(parts[5]) }.getOrNull() ?: return null
        return ScheduledAppShortcut(
            id = decodeText(parts[0])?.takeIf { it.isNotBlank() } ?: return null,
            name = decodeText(parts[1]) ?: return null,
            packageName = decodeText(parts[2]) ?: return null,
            hour = parts[3].toIntOrNull() ?: return null,
            minute = parts[4].toIntOrNull() ?: return null,
            repeat = repeat,
            isEnabled = parts[6].toBooleanStrictOrNull() ?: true,
        ).takeIf { it.isValid() }
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
