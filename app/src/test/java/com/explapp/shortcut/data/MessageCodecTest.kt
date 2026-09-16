package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCodecTest {
    @Test
    fun roundTripPreservesScheduledMessages() {
        val original = listOf(
            ScheduledMessage(
                name = "Morning message",
                platform = MessagePlatform.WHATSAPP,
                recipient = "+46701234567",
                message = "صباح الخير | good morning",
                hour = 8,
                minute = 5,
                repeat = RepeatOption.WEEKDAYS,
            ),
        )

        assertEquals(original, MessageCodec.decode(MessageCodec.encode(original)))
    }
}
