package com.explapp.shortcut.messages

import com.explapp.shortcut.domain.MessagePlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDeepLinkFactoryTest {
    @Test
    fun `whatsapp link normalizes international phone and prefills encoded text`() {
        val link = MessageDeepLinkFactory.build(
            platform = MessagePlatform.WHATSAPP,
            recipient = "+46 70-123 45 67",
            message = "Hej! كيف حالك؟",
        )

        assertEquals(
            "https://wa.me/46701234567?text=Hej%21%20%D9%83%D9%8A%D9%81%20%D8%AD%D8%A7%D9%84%D9%83%D8%9F",
            link,
        )
    }

    @Test
    fun `telegram link removes at sign and opens username with draft text`() {
        val link = MessageDeepLinkFactory.build(
            platform = MessagePlatform.TELEGRAM,
            recipient = "@example_user",
            message = "Meeting at 8",
        )

        assertEquals(
            "https://t.me/example_user?text=Meeting%20at%208",
            link,
        )
    }
}
