package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import java.nio.charset.StandardCharsets
import java.util.Base64

object MessageCodec {
    fun encode(messages: List<ScheduledMessage>): String = messages.joinToString("\n") { item ->
        listOf(
            encodeText(item.name),
            item.platform.name,
            encodeText(item.recipient),
            encodeText(item.message),
            item.hour.toString(),
            item.minute.toString(),
            item.repeat.name,
        ).joinToString("|")
    }

    fun decode(raw: String): List<ScheduledMessage> = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeLine)
        .toList()

    private fun decodeLine(line: String): ScheduledMessage? {
        val parts = line.split('|')
        if (parts.size != 7) return null

        val platform = runCatching { MessagePlatform.valueOf(parts[1]) }.getOrNull() ?: return null
        val repeat = runCatching { RepeatOption.valueOf(parts[6]) }.getOrNull() ?: return null
        val item = ScheduledMessage(
            name = decodeText(parts[0]) ?: return null,
            platform = platform,
            recipient = decodeText(parts[2]) ?: return null,
            message = decodeText(parts[3]) ?: return null,
            hour = parts[4].toIntOrNull() ?: return null,
            minute = parts[5].toIntOrNull() ?: return null,
            repeat = repeat,
        )
        return item.takeIf { it.isValid() }
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
