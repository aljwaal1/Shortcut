package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.domain.ScheduleAnchor
import java.time.ZonedDateTime

class MessageStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledMessage> {
        val raw = preferences.getString(KEY_MESSAGES, "").orEmpty()
        val decoded = MessageCodec.decode(raw)
        val now = ZonedDateTime.now()
        val normalized = decoded.map { item ->
            if (item.isEnabled && ScheduleAnchor.isExpiredOneShot(now, item.repeat, item.oneShotEpochDay, item.hour, item.minute)) {
                item.copy(isEnabled = false)
            } else item
        }
        if (raw.isNotBlank() && (MessageCodec.needsMigration(raw) || normalized != decoded)) save(normalized)
        return normalized
    }

    fun save(messages: List<ScheduledMessage>) {
        preferences.edit()
            .putString(KEY_MESSAGES, MessageCodec.encode(messages))
            .apply()
    }

    fun upsert(message: ScheduledMessage): List<ScheduledMessage> =
        ScheduledEntityCollection.upsertMessage(load(), message).also(::save)

    fun remove(message: ScheduledMessage) = removeById(message.id)

    fun removeById(id: String) {
        save(ScheduledEntityCollection.removeMessage(load(), id))
    }

    fun findById(id: String): ScheduledMessage? = load().firstOrNull { it.id == id }

    private companion object {
        const val PREFS_NAME = "message_store"
        const val KEY_MESSAGES = "scheduled_messages"
    }
}
