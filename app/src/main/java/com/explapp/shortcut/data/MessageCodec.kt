package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessageDeliveryMode
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduleAnchor
import com.explapp.shortcut.domain.ScheduledMessage
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.time.ZonedDateTime
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
            item.weeklyDayIso?.toString().orEmpty(),
            item.oneShotEpochDay?.toString().orEmpty(),
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
        .any { it.split('|').size != 12 }

    private fun decodeLine(line: String): ScheduledMessage? {
        val parts = line.split('|')
        return when (parts.size) {
            7 -> decodeLegacy(parts)
            10 -> decodeCurrentV1(parts)
            11 -> decodeCurrentV2(parts)
            12 -> decodeCurrent(parts)
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
            weeklyDayIso = if (repeat == RepeatOption.WEEKLY) java.time.LocalDate.now().dayOfWeek.value else null,
            oneShotEpochDay = legacyOneShotAnchor(repeat, parts[4].toIntOrNull() ?: return null, parts[5].toIntOrNull() ?: return null),
        ).takeIf { it.isValid() }
    }

    private fun decodeCurrentV1(parts: List<String>): ScheduledMessage? {
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
            weeklyDayIso = if (repeat == RepeatOption.WEEKLY) java.time.LocalDate.now().dayOfWeek.value else null,
            oneShotEpochDay = legacyOneShotAnchor(repeat, parts[5].toIntOrNull() ?: return null, parts[6].toIntOrNull() ?: return null),
        ).takeIf { it.isValid() }
    }

    private fun decodeCurrentV2(parts: List<String>): ScheduledMessage? {
        val platform = runCatching { MessagePlatform.valueOf(parts[2]) }.getOrNull() ?: return null
        val repeat = runCatching { RepeatOption.valueOf(parts[7]) }.getOrNull() ?: return null
        val mode = runCatching { MessageDeliveryMode.valueOf(parts[9]) }.getOrNull() ?: MessageDeliveryMode.PREPARED
        val hour = parts[5].toIntOrNull() ?: return null
        val minute = parts[6].toIntOrNull() ?: return null
        return ScheduledMessage(
            id = decodeText(parts[0])?.takeIf { it.isNotBlank() } ?: return null,
            name = decodeText(parts[1]) ?: return null,
            platform = platform,
            recipient = decodeText(parts[3]) ?: return null,
            message = decodeText(parts[4]) ?: return null,
            hour = hour,
            minute = minute,
            repeat = repeat,
            isEnabled = parts[8].toBooleanStrictOrNull() ?: true,
            deliveryMode = mode,
            weeklyDayIso = parts[10].toIntOrNull(),
            oneShotEpochDay = legacyOneShotAnchor(repeat, hour, minute),
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
            weeklyDayIso = parts[10].toIntOrNull(),
            oneShotEpochDay = parts[11].toLongOrNull(),
        ).takeIf { it.isValid() }
    }

    private fun legacyOneShotAnchor(repeat: RepeatOption, hour: Int, minute: Int): Long? =
        if (repeat == RepeatOption.ONCE) {
            ScheduleAnchor.nextOneShotEpochDay(ZonedDateTime.now(), hour, minute)
        } else {
            null
        }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
