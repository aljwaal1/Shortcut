package com.explapp.shortcut.automation.routines

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Transport-neutral personal Telegram sending. The transport must use an authenticated
 * TDLib *user* session; bot credentials and Android share intents are not transports.
 *
 * Sends are serialized per request key and UNKNOWN outcomes are never blindly replayed,
 * because a timeout after TDLib accepted the photo could cause duplicate delivery.
 */
internal class TelegramPersonalDeliveryCoordinator(
    private val client: TelegramPersonalClient,
) {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun deliver(request: PersonalPhotoRequest): PersonalDeliveryResult {
        if (request.requestId.isBlank() || !request.file.isFile || request.file.length() <= 0L) {
            return PersonalDeliveryResult.Failed("Missing request identifier or photo")
        }
        val username = TelegramPersonalDeliveryPolicy.normalizeUsername(request.username)
            ?: return PersonalDeliveryResult.Failed("Invalid Telegram username")
        if (!client.isAuthorized() && client === TelegramTdlibSession) {
            TelegramTdlibSession.waitForAuthorization()
        }
        if (!client.isAuthorized()) return PersonalDeliveryResult.NeedsLogin
        if (!inFlight.add(request.requestId)) return PersonalDeliveryResult.Uncertain(
            "Delivery with this request ID is already in progress"
        )
        return try {
            val recipient = client.resolvePrivateRecipient(username)
                ?: return PersonalDeliveryResult.Failed("Username is not a verified private account")
            client.sendPhoto(recipient, request.file, request.caption, request.requestId)
        } catch (e: Exception) {
            PersonalDeliveryResult.Uncertain("Delivery outcome not confirmed: ${e.javaClass.simpleName}")
        } finally {
            inFlight.remove(request.requestId)
        }
    }
}

internal data class PersonalPhotoRequest(
    val requestId: String,
    val username: String,
    val file: File,
    val caption: String = "",
)

internal sealed interface PersonalDeliveryResult {
    data object Confirmed : PersonalDeliveryResult
    data object NeedsLogin : PersonalDeliveryResult
    data class Failed(val reason: String) : PersonalDeliveryResult
    data class Uncertain(val reason: String) : PersonalDeliveryResult
}

internal interface TelegramPersonalClient {
    fun isAuthorized(): Boolean
    /** Must verify chat type == private and resolved user identity before sending. */
    fun resolvePrivateRecipient(normalizedUsername: String): Long?
    /** Confirmed must represent a server-acknowledged outgoing message, not merely queued. */
    fun sendPhoto(chatId: Long, file: File, caption: String, requestId: String): PersonalDeliveryResult
}
