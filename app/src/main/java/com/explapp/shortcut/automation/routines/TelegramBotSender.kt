package com.explapp.shortcut.automation.routines

import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class TelegramBotSender {
    fun sendText(token: String, chatId: String, text: String): Result<Unit> = runCatching {
        require(token.isNotBlank() && chatId.isNotBlank())
        val body = "chat_id=" + enc(chatId.trim()) + "&text=" + enc(text)
        val connection = URL("https://api.telegram.org/bot" + token.trim() + "/sendMessage").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        ensureSuccess(connection)
        connection.disconnect()
    }

    fun sendPhoto(token: String, chatId: String, caption: String, file: File): Result<Unit> = runCatching {
        require(token.isNotBlank() && chatId.isNotBlank())
        require(file.exists()) { "Screenshot file does not exist" }
        require(file.length() > 0L) { "Screenshot file is empty" }

        val boundary = "ShortcutBoundary" + System.currentTimeMillis()
        val connection = URL("https://api.telegram.org/bot" + token.trim() + "/sendPhoto").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        DataOutputStream(connection.outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--" + boundary + "\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                out.write(value.toByteArray(StandardCharsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("chat_id", chatId.trim())
            if (caption.isNotBlank()) field("caption", caption)
            out.writeBytes("--" + boundary + "\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"" + file.name + "\"\r\n")
            out.writeBytes("Content-Type: image/png\r\n\r\n")
            file.inputStream().use { input -> input.copyTo(out) }
            out.writeBytes("\r\n--" + boundary + "--\r\n")
        }
        ensureSuccess(connection)
        connection.disconnect()
    }

    fun validateDestination(token: String, chatId: String): Result<Unit> = runCatching {
        require(token.isNotBlank() && chatId.isNotBlank())
        val url = "https://api.telegram.org/bot" + token.trim() + "/getChat?chat_id=" + enc(chatId.trim())
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        ensureSuccess(connection)
        connection.disconnect()
    }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = runCatching { stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty() }.getOrDefault("")
        if (code !in 200..299) {
            val description = runCatching {
                JSONObject(body).optString("description").takeIf { it.isNotBlank() }
            }.getOrNull()
            throw IllegalStateException(
                description?.let { "Telegram: $it" } ?: "Telegram HTTP $code"
            )
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
