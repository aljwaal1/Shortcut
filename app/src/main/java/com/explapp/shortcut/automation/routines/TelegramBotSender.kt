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

    fun validateDestination(token: String, chatId: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val cleanChat = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanChat.isNotBlank()) { "Chat ID is required" }

        validateBot(cleanToken).getOrThrow()

        val url = "https://api.telegram.org/bot$cleanToken/getChat?chat_id=" + enc(cleanChat)
        val connection = open(url, "GET", 8_000, 10_000)
        ensureSuccess(connection)
        connection.disconnect()
    }

    fun sendText(token: String, chatId: String, text: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val cleanChat = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanChat.isNotBlank()) { "Chat ID is required" }

        validateDestination(cleanToken, cleanChat).getOrThrow()

        val body = "chat_id=" + enc(cleanChat) + "&text=" + enc(text)
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
        val cleanChat = chatId.trim()
        require(cleanToken.isNotBlank()) { "Bot token is required" }
        require(cleanChat.isNotBlank()) { "Chat ID is required" }
        require(file.exists()) { "Screenshot file does not exist" }
        require(file.length() > 0L) { "Screenshot file is empty" }
        require(caption.length <= 1024) { "Telegram photo caption is longer than 1024 characters" }

        validateDestination(cleanToken, cleanChat).getOrThrow()

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

            field("chat_id", cleanChat)
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
