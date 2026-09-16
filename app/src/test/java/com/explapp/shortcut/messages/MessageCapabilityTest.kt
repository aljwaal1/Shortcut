package com.explapp.shortcut.messages

import com.explapp.shortcut.domain.MessagePlatform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCapabilityTest {
    @Test
    fun telegramStandardModePreparesButDoesNotAutoSend() {
        val capability = MessageCapabilities.forPlatform(MessagePlatform.TELEGRAM)
        assertTrue(capability.opensPreparedMessage)
        assertFalse(capability.autoSends)
    }

    @Test
    fun whatsappStandardModePreparesButDoesNotAutoSend() {
        val capability = MessageCapabilities.forPlatform(MessagePlatform.WHATSAPP)
        assertTrue(capability.opensPreparedMessage)
        assertFalse(capability.autoSends)
    }
}
