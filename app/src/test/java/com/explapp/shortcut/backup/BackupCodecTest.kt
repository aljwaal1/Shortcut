package com.explapp.shortcut.backup

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineAction
import com.explapp.shortcut.automation.routines.RoutineActionType
import com.explapp.shortcut.automation.routines.RoutineTrigger
import com.explapp.shortcut.automation.routines.RoutineTriggerType
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
        routines = listOf(
            AutomationRoutine(
                name = "Imported routine",
                isEnabled = true,
                trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
                actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, value = "Hi")),
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
        assertEquals(1, decoded.routines.size)
        assertFalse(decoded.routines.single().isEnabled)
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
