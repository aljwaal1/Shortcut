package com.explapp.shortcut.automation.routines

import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TelegramBotSender {
    fun sendText(token: String, chatId: String, text: String): Result<Unit> = runCatching {
        require(token.isNotBlank() && chatId.isNotBlank())
        val body = "chat_id=" + enc(chatId) + "&text=" + enc(text)
        val connection = URL("https://api.telegram.org/bot" + token + "/sendMessage").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        check(connection.responseCode in 200..299) { "Telegram HTTP " + connection.responseCode }
        connection.disconnect()
    }

    fun sendPhoto(token: String, chatId: String, caption: String, file: File): Result<Unit> = runCatching {
        require(file.exists())
        val boundary = "ShortcutBoundary" + System.currentTimeMillis()
        val connection = URL("https://api.telegram.org/bot" + token + "/sendPhoto").openConnection() as HttpURLConnection
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
            field("chat_id", chatId)
            if (caption.isNotBlank()) field("caption", caption)
            out.writeBytes("--" + boundary + "\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"" + file.name + "\"\r\n")
            out.writeBytes("Content-Type: image/png\r\n\r\n")
            file.inputStream().use { input -> input.copyTo(out) }
            out.writeBytes("\r\n--" + boundary + "--\r\n")
        }
        check(connection.responseCode in 200..299) { "Telegram HTTP " + connection.responseCode }
        connection.disconnect()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
