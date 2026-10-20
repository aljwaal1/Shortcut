package com.explapp.shortcut.automation.routines

import android.content.Context
import java.security.MessageDigest
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class TelegramApiException(
    val httpStatus: Int,
    val retryAfterSeconds: Int?,
    message: String,
) : IllegalStateException(message)

class TelegramBotSender(private val context: Context? = null) {
    private fun cacheKey(token: String, username: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(token.trim().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(24)
        return "$hash:${username.lowercase(java.util.Locale.ROOT)}"
    }
    private fun cachedChat(token: String, username: String): String? =
        context?.getSharedPreferences("telegram_recipient_chat_cache", Context.MODE_PRIVATE)
            ?.getString(cacheKey(token, username), null)
    private fun cacheChat(token: String, username: String, id: String) {
        if (id.matches(Regex("-?\\d+"))) {
            context?.getSharedPreferences("telegram_recipient_chat_cache", Context.MODE_PRIVATE)
                ?.edit()?.putString(cacheKey(token, username), id)?.apply()
        }
    }

    fun validateBot(token: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        val connection = open("https://api.telegram.org/bot$cleanToken/getMe", "GET", 8_000, 10_000)
        try {
            ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    fun validateDestination(token: String, destination: String): Result<Unit> = runCatching {
        resolveDestination(token, destination).getOrThrow()
    }

    fun resolveDestination(token: String, destination: String): Result<String> = runCatching {
        val cleanToken = token.trim()
        val raw = destination.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(raw.isNotBlank()) { "Telegram username is required" }

        if (raw.matches(Regex("-?\\d+"))) {
            val connection = open(
                "https://api.telegram.org/bot$cleanToken/getChat?chat_id=" + enc(raw),
                "GET",
                8_000,
                10_000,
            )
            try {
                val chat = JSONObject(readBodyAndEnsureSuccess(connection)).optJSONObject("result")
                require(chat?.optString("type") == "private") {
                    "Telegram destination must be a private user chat, not a group or channel"
                }
                return@runCatching raw
            } finally {
                connection.disconnect()
            }
        }

        val username = raw.removePrefix("@")
        require(username.matches(Regex("[A-Za-z0-9_]{5,32}"))) {
            "Telegram username must be 5–32 letters, digits or underscores"
        }
        require(!username.endsWith("bot", ignoreCase = true)) {
            "Choose a person, not another Telegram bot"
        }

        // Bot API does not resolve private users from @username using getChat.
        // Bind only a private message sent by the matching user to this bot.
        // Validate cached bindings before every send to prevent stale username reuse.
        cachedChat(cleanToken, username)?.let { cached ->
            val verified = runCatching { verifiedPrivateChat(cleanToken, cached, username) }.getOrDefault(false)
            if (verified) return@runCatching cached
            forgetCachedChat(cleanToken, username)
        }

        // Avoid competing with an existing webhook consumer and losing recipient updates.
        val webhookConnection = open(
            "https://api.telegram.org/bot$cleanToken/getWebhookInfo",
            "GET", 8_000, 10_000,
        )
        val webhookInfo = try {
            JSONObject(readBodyAndEnsureSuccess(webhookConnection)).optJSONObject("result")
        } finally {
            webhookConnection.disconnect()
        }
        if (!webhookInfo?.optString("url").isNullOrBlank()) {
            throw IllegalStateException(
                "Telegram bot uses a webhook. Recipient linking must be handled by that webhook; Shortcut will not poll or remove it."
            )
        }

        val updatesConnection = open(
            "https://api.telegram.org/bot$cleanToken/getUpdates?limit=100&timeout=0",
            "GET",
            8_000,
            12_000,
        )
        val body = try {
            readBodyAndEnsureSuccess(updatesConnection)
        } catch (error: IllegalStateException) {
            if (error.message.orEmpty().contains("webhook", ignoreCase = true)) {
                throw IllegalStateException(
                    "Telegram bot has a webhook configured. Set up the recipient through the bot integration first; getUpdates is unavailable.",
                )
            }
            throw error
        } finally {
            updatesConnection.disconnect()
        }

        val updates = JSONObject(body).optJSONArray("result")
        if (updates != null) {
            for (i in updates.length() - 1 downTo 0) {
                val message = updates.optJSONObject(i)?.optJSONObject("message") ?: continue
                val from = message.optJSONObject("from") ?: continue
                val chat = message.optJSONObject("chat") ?: continue
                if (chat.optString("type") != "private") continue
                if (from.optBoolean("is_bot", false)) continue
                val fromId = from.optLong("id", 0L)
                val chatId = chat.optLong("id", 0L)
                if (fromId == 0L || fromId != chatId) continue
                if (from.optString("username").equals(username, ignoreCase = true)) {
                    val id = chatId.toString()
                    cacheChat(cleanToken, username, id)
                    return@runCatching id
                }
            }
        }
        throw IllegalStateException(
            "Telegram: @$username is not linked to this bot. Ask the person to open the bot, press Start and send a message, then verify the recipient again."
        )
    }

    private fun forgetCachedChat(token: String, username: String) {
        context?.getSharedPreferences("telegram_recipient_chat_cache", Context.MODE_PRIVATE)
            ?.edit()?.remove(cacheKey(token, username))?.apply()
    }

    private fun verifiedPrivateChat(token: String, id: String, username: String): Boolean {
        if (!id.matches(Regex("[0-9]+"))) return false
        val connection = open(
            "https://api.telegram.org/bot${token.trim()}/getChat?chat_id=" + enc(id),
            "GET", 8_000, 10_000,
        )
        return try {
            val chat = JSONObject(readBodyAndEnsureSuccess(connection)).optJSONObject("result")
            chat?.optString("type") == "private" &&
                chat.optString("username").equals(username, ignoreCase = true)
        } finally {
            connection.disconnect()
        }
    }

    fun sendText(token: String, chatId: String, text: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val cleanDestination = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanDestination.isNotBlank()) { "Telegram username is required" }

        val resolvedChat = resolveDestination(cleanToken, cleanDestination).getOrThrow()

        val body = "chat_id=" + enc(resolvedChat) + "&text=" + enc(text)
        val connection = open(
            "https://api.telegram.org/bot$cleanToken/sendMessage",
            "POST",
            8_000,
            10_000,
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    fun sendDocument(token: String, chatId: String, caption: String, file: File): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val cleanDestination = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanDestination.isNotBlank()) { "Telegram username is required" }
        require(file.exists()) { "Attachment file does not exist" }
        require(file.length() > 0L) { "Attachment file is empty" }
        require(file.length() <= MAX_DOCUMENT_BYTES) { "Attachment is larger than Shortcut's Telegram safety limit" }
        require(caption.length <= 1024) { "Telegram document caption is longer than 1024 characters" }

        val resolvedChat = resolveDestination(cleanToken, cleanDestination).getOrThrow()
        val safeName = file.name
            .replace(Regex("[\\r\\n\\\"]"), "_")
            .take(120)
            .ifBlank { "Shortcut_document" }
        val boundary = "ShortcutBoundary" + System.currentTimeMillis()
        val connection = open(
            "https://api.telegram.org/bot$cleanToken/sendDocument",
            "POST",
            10_000,
            45_000,
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            setChunkedStreamingMode(64 * 1024)
        }

        try {
            DataOutputStream(connection.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
                    out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    out.write(value.toByteArray(StandardCharsets.UTF_8))
                    out.writeBytes("\r\n")
                }

                field("chat_id", resolvedChat)
                if (caption.isNotBlank()) field("caption", caption)

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"$safeName\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    fun sendPhoto(token: String, chatId: String, caption: String, file: File): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val cleanDestination = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanDestination.isNotBlank()) { "Telegram username is required" }
        require(file.exists()) { "Screenshot file does not exist" }
        require(file.length() > 0L) { "Screenshot file is empty" }
        require(caption.length <= 1024) { "Telegram photo caption is longer than 1024 characters" }

        val resolvedChat = resolveDestination(cleanToken, cleanDestination).getOrThrow()

        val boundary = "ShortcutBoundary" + System.currentTimeMillis()
        val connection = open(
            "https://api.telegram.org/bot$cleanToken/sendPhoto",
            "POST",
            10_000,
            30_000,
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            setChunkedStreamingMode(64 * 1024)
        }

        try {
            DataOutputStream(connection.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
                    out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    out.write(value.toByteArray(StandardCharsets.UTF_8))
                    out.writeBytes("\r\n")
                }

                field("chat_id", resolvedChat)
                if (caption.isNotBlank()) field("caption", caption)

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"screenshot.png\"\r\n")
                out.writeBytes("Content-Type: image/png\r\n\r\n")
                file.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, connectMs: Int, readMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectMs
            readTimeout = readMs
            useCaches = false
            setRequestProperty("User-Agent", "Shortcut-Android")
        }

    private fun readBodyAndEnsureSuccess(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = runCatching {
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.getOrDefault("")

        val json = runCatching { JSONObject(body) }.getOrNull()
        val apiOk = json?.optBoolean("ok", code in 200..299) ?: (code in 200..299)
        if (code !in 200..299 || !apiOk) {
            val description = json?.optString("description")?.takeIf { it.isNotBlank() }
            val retryAfter = json
                ?.optJSONObject("parameters")
                ?.optInt("retry_after", 0)
                ?.takeIf { it > 0 }
            val detail = description ?: "HTTP $code"
            throw TelegramApiException(code, retryAfter, "Telegram: $detail")
        }
        return body
    }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = runCatching {
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.getOrDefault("")

        val json = runCatching { JSONObject(body) }.getOrNull()
        val apiOk = json?.optBoolean("ok", code in 200..299) ?: (code in 200..299)
        if (code !in 200..299 || !apiOk) {
            val description = json?.optString("description")?.takeIf { it.isNotBlank() }
            val retryAfter = json
                ?.optJSONObject("parameters")
                ?.optInt("retry_after", 0)
                ?.takeIf { it > 0 }

            val detail = description ?: "HTTP $code"
            throw TelegramApiException(code, retryAfter, "Telegram: $detail")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val MAX_DOCUMENT_BYTES = 48L * 1024L * 1024L
    }
}
