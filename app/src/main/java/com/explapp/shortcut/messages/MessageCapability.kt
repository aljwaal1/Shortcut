package com.explapp.shortcut.messages

import com.explapp.shortcut.domain.MessagePlatform

data class MessageCapability(
    val opensPreparedMessage: Boolean,
    val autoSends: Boolean,
)

object MessageCapability {
    fun forPlatform(platform: MessagePlatform): com.explapp.shortcut.messages.MessageCapability =
        com.explapp.shortcut.messages.MessageCapability(
            opensPreparedMessage = true,
            autoSends = false,
        )
}
