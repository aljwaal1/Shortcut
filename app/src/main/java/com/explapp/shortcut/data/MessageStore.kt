package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledMessage

class MessageStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledMessage> {
        val raw = preferences.getString(KEY_MESSAGES, "").orEmpty()
        val decoded = MessageCodec.decode(raw)
        if (raw.isNotBlank() && MessageCodec.needsMigration(raw)) save(decoded)
        return decoded
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
