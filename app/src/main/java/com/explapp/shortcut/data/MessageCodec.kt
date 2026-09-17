package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessageDeliveryMode
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

object MessageCodec {
    fun encode(messages: List<ScheduledMessage>): String = messages.joinToString("\n") { item ->
        listOf(
            encodeText(item.id),
            encodeText(item.name),
            item.platform.name,
            encodeText(item.recipient),
            encodeText(item.message),
            item.hour.toString(),
            item.minute.toString(),
            item.repeat.name,
            item.isEnabled.toString(),
            item.deliveryMode.name,
        ).joinToString("|")
    }

    fun decode(raw: String): List<ScheduledMessage> = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeLine)
        .toList()

    fun needsMigration(raw: String): Boolean = raw
        .lineSequence()
        .filter { it.isNotBlank() }
        .any { it.split('|').size == 7 }

    private fun decodeLine(line: String): ScheduledMessage? {
        val parts = line.split('|')
        return when (parts.size) {
            7 -> decodeLegacy(parts)
            10 -> decodeCurrent(parts)
            else -> null
        }
    }

    private fun decodeLegacy(parts: List<String>): ScheduledMessage? {
        val platform = runCatching { MessagePlatform.valueOf(parts[1]) }.getOrNull() ?: return null
        val repeat = runCatching { RepeatOption.valueOf(parts[6]) }.getOrNull() ?: return null
        return ScheduledMessage(
            id = UUID.randomUUID().toString(),
            name = decodeText(parts[0]) ?: return null,
            platform = platform,
            recipient = decodeText(parts[2]) ?: return null,
            message = decodeText(parts[3]) ?: return null,
            hour = parts[4].toIntOrNull() ?: return null,
            minute = parts[5].toIntOrNull() ?: return null,
            repeat = repeat,
            isEnabled = true,
            deliveryMode = MessageDeliveryMode.PREPARED,
        ).takeIf { it.isValid() }
    }

    private fun decodeCurrent(parts: List<String>): ScheduledMessage? {
        val platform = runCatching { MessagePlatform.valueOf(parts[2]) }.getOrNull() ?: return null
        val repeat = runCatching { RepeatOption.valueOf(parts[7]) }.getOrNull() ?: return null
        val mode = runCatching { MessageDeliveryMode.valueOf(parts[9]) }.getOrNull() ?: MessageDeliveryMode.PREPARED
        return ScheduledMessage(
            id = decodeText(parts[0])?.takeIf { it.isNotBlank() } ?: return null,
            name = decodeText(parts[1]) ?: return null,
            platform = platform,
            recipient = decodeText(parts[3]) ?: return null,
            message = decodeText(parts[4]) ?: return null,
            hour = parts[5].toIntOrNull() ?: return null,
            minute = parts[6].toIntOrNull() ?: return null,
            repeat = repeat,
            isEnabled = parts[8].toBooleanStrictOrNull() ?: true,
            deliveryMode = mode,
        ).takeIf { it.isValid() }
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
