package com.explapp.shortcut.messages

import com.explapp.shortcut.domain.MessagePlatform

data class MessageCapability(
    val opensPreparedMessage: Boolean,
    val autoSends: Boolean,
)

object MessageCapabilities {
    fun forPlatform(platform: MessagePlatform): MessageCapability = when (platform) {
        MessagePlatform.TELEGRAM,
        MessagePlatform.WHATSAPP -> MessageCapability(
            opensPreparedMessage = true,
            autoSends = false,
        )
    }
}
