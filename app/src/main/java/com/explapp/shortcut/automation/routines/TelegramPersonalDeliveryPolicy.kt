package com.explapp.shortcut.automation.routines

/**
 * Personal-account Telegram delivery is a distinct transport from TelegramBotSender.
 * It must be backed by an authorized Telegram client (TDLib), never a bot token or
 * a share-sheet pretending to have delivered a message.
 */
internal object TelegramPersonalDeliveryPolicy {
    enum class State { NEEDS_LOGIN, READY, SENDING, CONFIRMED, FAILED, UNCERTAIN }

    fun normalizeUsername(raw: String): String? {
        val name = raw.trim().removePrefix("@")
        if (!name.matches(Regex("[A-Za-z0-9_]{5,32}"))) return null
        return "@$name"
    }

    fun canStart(state: State): Boolean = state == State.READY

    fun isConfirmed(state: State): Boolean = state == State.CONFIRMED

    fun mayRetryAutomatically(state: State): Boolean =
        state == State.NEEDS_LOGIN || state == State.FAILED

    fun requiresUserAttention(state: State): Boolean =
        state == State.NEEDS_LOGIN || state == State.UNCERTAIN
}
