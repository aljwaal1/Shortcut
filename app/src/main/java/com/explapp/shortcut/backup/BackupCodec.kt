package com.explapp.shortcut.backup

import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class BackupPayload(
    val shortcuts: List<ScheduledAppShortcut>,
    val messages: List<ScheduledMessage>,
    val unlockWifiMapsEnabled: Boolean = false,
)

object BackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_IMPORT_CHARS = 512_000
    private const val MAX_ITEMS_PER_SECTION = 1_000

    private val json = Json {
        prettyPrint = true
        isLenient = false
        ignoreUnknownKeys = false
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("shortcuts", buildJsonArray {
                payload.shortcuts.forEach { shortcut -> add(shortcut.toJson()) }
            })
            put("messages", buildJsonArray {
                payload.messages.forEach { message -> add(message.toJson()) }
            })
            put("unlockWifiMapsEnabled", payload.unlockWifiMapsEnabled)
        },
    )

    fun decode(raw: String): Result<BackupPayload> = runCatching {
        require(raw.length <= MAX_IMPORT_CHARS) { "Backup is too large" }

        val root = json.parseToJsonElement(raw).jsonObject
        require(root.requiredInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported backup schema" }

        val shortcutsJson = root.requiredArray("shortcuts")
        val messagesJson = root.requiredArray("messages")
        require(shortcutsJson.size <= MAX_ITEMS_PER_SECTION) { "Too many shortcuts" }
        require(messagesJson.size <= MAX_ITEMS_PER_SECTION) { "Too many messages" }

        val shortcuts = shortcutsJson.map { it.jsonObject.toShortcut() }
        val messages = messagesJson.map { it.jsonObject.toMessage() }

        BackupPayload(
            shortcuts = shortcuts,
            messages = messages,
            // Sensitive/advanced imported automations always require explicit review.
            unlockWifiMapsEnabled = false,
        )
    }

    private fun ScheduledAppShortcut.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("packageName", packageName)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.name)
    }

    private fun ScheduledMessage.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("platform", platform.name)
        put("recipient", recipient)
        put("message", message)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.name)
    }

    private fun JsonObject.toShortcut(): ScheduledAppShortcut {
        val shortcut = ScheduledAppShortcut(
            name = requiredString("name"),
            packageName = requiredString("packageName"),
            hour = requiredInt("hour"),
            minute = requiredInt("minute"),
            repeat = enumValueOf(requiredString("repeat")),
        )
        require(shortcut.name.isNotBlank()) { "Shortcut name is required" }
        require(shortcut.isValid()) { "Invalid shortcut" }
        return shortcut
    }

    private fun JsonObject.toMessage(): ScheduledMessage {
        val scheduledMessage = ScheduledMessage(
            name = requiredString("name"),
            platform = MessagePlatform.valueOf(requiredString("platform")),
            recipient = requiredString("recipient"),
            message = requiredString("message"),
            hour = requiredInt("hour"),
            minute = requiredInt("minute"),
            repeat = RepeatOption.valueOf(requiredString("repeat")),
        )
        require(scheduledMessage.name.isNotBlank()) { "Message name is required" }
        require(scheduledMessage.isValid()) { "Invalid scheduled message" }
        return scheduledMessage
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: error("Missing $key")

    private fun JsonObject.requiredInt(key: String): Int =
        this[key]?.jsonPrimitive?.int ?: error("Missing $key")

    private fun JsonObject.requiredArray(key: String): JsonArray =
        this[key]?.jsonArray ?: error("Missing $key")
}
