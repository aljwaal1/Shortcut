package com.explapp.shortcut.backup

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val sample = BackupPayload(
        shortcuts = listOf(
            ScheduledAppShortcut(
                name = "Work",
                packageName = "com.example.work",
                hour = 8,
                minute = 30,
                repeat = RepeatOption.WEEKDAYS,
            ),
        ),
        messages = listOf(
            ScheduledMessage(
                name = "Morning",
                platform = MessagePlatform.WHATSAPP,
                recipient = "+962700000000",
                message = "Good morning",
                hour = 9,
                minute = 0,
                repeat = RepeatOption.DAILY,
            ),
        ),
        unlockWifiMapsEnabled = true,
    )

    @Test
    fun roundTripKeepsStandardAutomationsButDisablesAdvancedOnImport() {
        val encoded = BackupCodec.encode(sample)
        val decoded = BackupCodec.decode(encoded).getOrThrow()

        assertEquals(sample.shortcuts, decoded.shortcuts)
        assertEquals(sample.messages, decoded.messages)
        assertFalse(decoded.unlockWifiMapsEnabled)
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        val invalid = """{"schemaVersion":99,"shortcuts":[],"messages":[],"unlockWifiMapsEnabled":false}"""
        assertTrue(BackupCodec.decode(invalid).isFailure)
    }

    @Test
    fun rejectsMalformedJsonAndOversizedPayload() {
        assertTrue(BackupCodec.decode("not json").isFailure)
        assertTrue(BackupCodec.decode("x".repeat(BackupCodec.MAX_IMPORT_CHARS + 1)).isFailure)
    }
}
