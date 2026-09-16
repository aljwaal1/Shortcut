package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledMessage

class MessageStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledMessage> =
        MessageCodec.decode(preferences.getString(KEY_MESSAGES, "").orEmpty())

    fun save(messages: List<ScheduledMessage>) {
        preferences.edit()
            .putString(KEY_MESSAGES, MessageCodec.encode(messages))
            .apply()
    }

    fun remove(message: ScheduledMessage) {
        save(load().filterNot { it == message })
    }

    private companion object {
        const val PREFS_NAME = "message_store"
        const val KEY_MESSAGES = "scheduled_messages"
    }
}
