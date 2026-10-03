package com.explapp.shortcut.messages

import com.explapp.shortcut.domain.MessagePlatform
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MessageDeepLinkFactory {
    fun build(
        platform: MessagePlatform,
        recipient: String,
        message: String,
    ): String {
        val encodedMessage = encode(message)
        return when (platform) {
            MessagePlatform.WHATSAPP -> {
                val normalizedPhone = recipient.filter(Char::isDigit)
                "https://wa.me/$normalizedPhone?text=$encodedMessage"
            }

            MessagePlatform.TELEGRAM -> {
                val normalizedUsername = recipient.trim().removePrefix("@").trim()
                "https://t.me/$normalizedUsername?text=$encodedMessage"
            }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
