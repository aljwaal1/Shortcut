package com.explapp.shortcut.domain

import java.util.UUID

enum class MessagePlatform {
    WHATSAPP,
    TELEGRAM,
}

enum class MessageDeliveryMode {
    PREPARED,
    TELEGRAM_BOT_AUTO,
}

data class ScheduledMessage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val platform: MessagePlatform,
    val recipient: String,
    val message: String,
    val hour: Int,
    val minute: Int,
    val repeat: RepeatOption,
    val isEnabled: Boolean = true,
    val deliveryMode: MessageDeliveryMode = MessageDeliveryMode.PREPARED,
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
            recipient.isNotBlank() &&
            message.isNotBlank() &&
            hour in 0..23 &&
            minute in 0..59 &&
            (deliveryMode == MessageDeliveryMode.PREPARED || platform == MessagePlatform.TELEGRAM)

    fun duplicate(newId: String = UUID.randomUUID().toString()): ScheduledMessage =
        copy(id = newId, isEnabled = true)
}
