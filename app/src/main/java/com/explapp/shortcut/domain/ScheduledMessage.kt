package com.explapp.shortcut.domain

enum class MessagePlatform {
    WHATSAPP,
    TELEGRAM,
}

data class ScheduledMessage(
    val name: String,
    val platform: MessagePlatform,
    val recipient: String,
    val message: String,
    val hour: Int,
    val minute: Int,
    val repeat: RepeatOption,
) {
    fun isValid(): Boolean =
        recipient.isNotBlank() &&
            message.isNotBlank() &&
            hour in 0..23 &&
            minute in 0..59
}
