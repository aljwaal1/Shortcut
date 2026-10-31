package com.explapp.shortcut.automation.routines

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPersonalDeliveryCoordinatorTest {
    private class FakeClient : TelegramPersonalClient {
        var authorized = true
        var calls = 0
        var resolved: Long? = 123L
        override fun isAuthorized() = authorized
        override fun resolvePrivateRecipient(normalizedUsername: String): Long? {
            assertEquals("@recipient1", normalizedUsername)
            return resolved
        }
        override fun sendPhoto(chatId: Long, file: File, caption: String, requestId: String): PersonalDeliveryResult {
            calls++
            return PersonalDeliveryResult.Confirmed
        }
    }

    @Test fun rejectsInvalidUsernameWithoutSending() {
        val client = FakeClient()
        val file = File.createTempFile("personal-photo", ".png").apply { writeText("image") }
        try {
            val result = TelegramPersonalDeliveryCoordinator(client).deliver(PersonalPhotoRequest("one", "bad name", file))
            assertTrue(result is PersonalDeliveryResult.Failed)
            assertEquals(0, client.calls)
        } finally { file.delete() }
    }

    @Test fun refusesSendingBeforeAccountAuthorization() {
        val client = FakeClient().apply { authorized = false }
        val file = File.createTempFile("personal-photo", ".png").apply { writeText("image") }
        try {
            val result = TelegramPersonalDeliveryCoordinator(client).deliver(PersonalPhotoRequest("one", "@recipient1", file))
            assertEquals(PersonalDeliveryResult.NeedsLogin, result)
            assertEquals(0, client.calls)
        } finally { file.delete() }
    }

    @Test fun refusesUnknownRecipient() {
        val client = FakeClient().apply { resolved = null }
        val file = File.createTempFile("personal-photo", ".png").apply { writeText("image") }
        try {
            val result = TelegramPersonalDeliveryCoordinator(client).deliver(PersonalPhotoRequest("one", "recipient1", file))
            assertTrue(result is PersonalDeliveryResult.Failed)
            assertEquals(0, client.calls)
        } finally { file.delete() }
    }

    @Test fun sendsToVerifiedPrivateRecipientOnly() {
        val client = FakeClient()
        val file = File.createTempFile("personal-photo", ".png").apply { writeText("image") }
        try {
            val result = TelegramPersonalDeliveryCoordinator(client).deliver(PersonalPhotoRequest("one", "@recipient1", file))
            assertEquals(PersonalDeliveryResult.Confirmed, result)
            assertEquals(1, client.calls)
        } finally { file.delete() }
    }

    @Test fun transportExceptionIsUncertainNotFailureOrSuccess() {
        val client = object : TelegramPersonalClient {
            override fun isAuthorized() = true
            override fun resolvePrivateRecipient(normalizedUsername: String) = 123L
            override fun sendPhoto(chatId: Long, file: File, caption: String, requestId: String): PersonalDeliveryResult {
                throw java.io.IOException("socket closed after send")
            }
        }
        val file = File.createTempFile("personal-photo", ".png").apply { writeText("image") }
        try {
            val result = TelegramPersonalDeliveryCoordinator(client).deliver(PersonalPhotoRequest("one", "recipient1", file))
            assertTrue(result is PersonalDeliveryResult.Uncertain)
        } finally { file.delete() }
    }
}
