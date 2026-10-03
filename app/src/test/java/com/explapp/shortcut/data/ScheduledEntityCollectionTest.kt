package com.explapp.shortcut.data

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduledEntityCollectionTest {
    @Test
    fun shortcutEditReplacesSameIdInsteadOfAppending() {
        val original = ScheduledAppShortcut("id-1", "Maps", "maps", 8, 0, RepeatOption.DAILY)
        val edited = original.copy(name = "Maps work", hour = 9)

        val result = ScheduledEntityCollection.upsertShortcut(listOf(original), edited)

        assertEquals(1, result.size)
        assertEquals("Maps work", result.single().name)
        assertEquals("id-1", result.single().id)
    }

    @Test
    fun messageEditReplacesSameIdInsteadOfAppending() {
        val original = ScheduledMessage("m-1", "Msg", MessagePlatform.TELEGRAM, "user", "hello", 8, 0, RepeatOption.DAILY)
        val edited = original.copy(message = "updated", minute = 30)

        val result = ScheduledEntityCollection.upsertMessage(listOf(original), edited)

        assertEquals(1, result.size)
        assertEquals("updated", result.single().message)
        assertEquals("m-1", result.single().id)
    }
}
