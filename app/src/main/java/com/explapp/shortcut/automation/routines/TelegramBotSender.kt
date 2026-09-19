package com.explapp.shortcut.automation.routines

import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class TelegramBotSender {
    fun validateBot(token: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        val connection = open("https://api.telegram.org/bot$cleanToken/getMe", "GET", 8_000, 10_000)
        ensureSuccess(connection)
        connection.disconnect()
    }

    fun validateDestination(token: String, destination: String): Result<Unit> = runCatching {
        resolveDestination(token, destination).getOrThrow()
    }

    fun resolveDestination(token: String, destination: String): Result<String> = runCatching {
        val cleanToken = token.trim()
        val raw = destination.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(raw.isNotBlank()) { "Telegram username is required" }

        validateBot(cleanToken).getOrThrow()

        if (raw.matches(Regex("-?\\d+"))) {
            val connection = open(
                "https://api.telegram.org/bot$cleanToken/getChat?chat_id=" + enc(raw),
                "GET",
                8_000,
                10_000,
            )
            ensureSuccess(connection)
            connection.disconnect()
            return@runCatching raw
        }

        val username = raw.removePrefix("@")
        require(username.isNotBlank()) { "Telegram username is required" }

        val publicHandle = "@$username"
        val direct = runCatching {
            val connection = open(
                "https://api.telegram.org/bot$cleanToken/getChat?chat_id=" + enc(publicHandle),
                "GET",
                8_000,
                10_000,
            )
            ensureSuccess(connection)
            connection.disconnect()
            publicHandle
        }
        if (direct.isSuccess) return@runCatching direct.getOrThrow()

        val updatesConnection = open(
            "https://api.telegram.org/bot$cleanToken/getUpdates?limit=100&timeout=0",
            "GET",
            8_000,
            12_000,
        )
        val body = readBodyAndEnsureSuccess(updatesConnection)
        updatesConnection.disconnect()

        val root = JSONObject(body)
        val updates = root.optJSONArray("result")
        if (updates != null) {
            for (i in updates.length() - 1 downTo 0) {
                val update = updates.optJSONObject(i) ?: continue
                val message = update.optJSONObject("message")
                    ?: update.optJSONObject("edited_message")
                    ?: update.optJSONObject("channel_post")
                    ?: continue
                val from = message.optJSONObject("from")
                val chat = message.optJSONObject("chat")
                val foundUsername = from?.optString("username").orEmpty().ifBlank {
                    chat?.optString("username").orEmpty()
                }
                if (foundUsername.equals(username, ignoreCase = true)) {
                    val id = chat?.optLong("id", 0L) ?: 0L
                    if (id != 0L) return@runCatching id.toString()
                }
            }
        }

        throw IllegalStateException(
            "Telegram: username @$username was not found. Ask the person to open the bot, press Start, and send it one message, then try again."
        )
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
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        ensureSuccess(connection)
        connection.disconnect()
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
        connection.disconnect()
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
            val detail = when {
                retryAfter != null && description != null -> description + " (retry after " + retryAfter + "s)"
                description != null -> description
                body.isNotBlank() -> body.take(300)
                else -> "HTTP " + code
            }
            throw IllegalStateException("Telegram: " + detail)
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

            val detail = when {
                retryAfter != null && description != null -> "$description (retry after ${retryAfter}s)"
                description != null -> description
                body.isNotBlank() -> body.take(300)
                else -> "HTTP $code"
            }
            throw IllegalStateException("Telegram: $detail")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
