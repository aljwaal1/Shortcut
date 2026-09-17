package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    @Test
    fun roundTripPreservesScheduledMessageIdentityAndEnabledState() {
        val original = listOf(
            ScheduledMessage(
                id = "message-1",
                name = "Morning message",
                platform = MessagePlatform.WHATSAPP,
                recipient = "+46701234567",
                message = "صباح الخير | good morning",
                hour = 8,
                minute = 5,
                repeat = RepeatOption.WEEKDAYS,
                isEnabled = false,
            ),
        )

        assertEquals(original, MessageCodec.decode(MessageCodec.encode(original)))
    }

    @Test
    fun legacyMessageGetsStableIdAndDefaultsEnabled() {
        val legacy = "TW9ybmluZw|WHATSAPP|KzQ2NzAxMjM0NTY3|SGVsbG8|8|5|WEEKDAYS"

        val first = MessageCodec.decode(legacy).single()
        val migrated = MessageCodec.encode(listOf(first))
        val second = MessageCodec.decode(migrated).single()

        assertTrue(first.id.isNotBlank())
        assertTrue(first.isEnabled)
        assertEquals(first.id, second.id)
    }

    @Test
    fun duplicateCreatesNewMessageIdentity() {
        val original = ScheduledMessage(
            id = "message-original",
            name = "Telegram",
            platform = MessagePlatform.TELEGRAM,
            recipient = "user",
            message = "hello",
            hour = 9,
            minute = 15,
            repeat = RepeatOption.DAILY,
        )

        val duplicate = original.duplicate(newId = "message-copy")

        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.message, duplicate.message)
    }
}
