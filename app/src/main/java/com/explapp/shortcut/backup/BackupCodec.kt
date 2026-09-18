package com.explapp.shortcut.backup

import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineCodec
import com.explapp.shortcut.domain.MessageDeliveryMode
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

data class BackupPayload(
    val shortcuts: List<ScheduledAppShortcut>,
    val messages: List<ScheduledMessage>,
    val routines: List<AutomationRoutine> = emptyList(),
    val favoriteToolIds: List<String> = emptyList(),
    val recentToolIds: List<String> = emptyList(),
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
            put("shortcuts", buildJsonArray { payload.shortcuts.forEach { add(it.toJson()) } })
            put("messages", buildJsonArray { payload.messages.forEach { add(it.toJson()) } })
            put(
                "routines",
                json.parseToJsonElement(
                    RoutineCodec.encode(
                        payload.routines.map { routine ->
                            routine.copy(
                                actions = routine.actions.map { action ->
                                    action.copy(parameters = action.parameters - "botToken" - "telegramBotToken")
                                },
                            )
                        },
                    ),
                ),
            )
            put("favoriteToolIds", buildJsonArray { payload.favoriteToolIds.forEach { add(JsonPrimitive(it)) } })
            put("recentToolIds", buildJsonArray { payload.recentToolIds.forEach { add(JsonPrimitive(it)) } })
            put("unlockWifiMapsEnabled", payload.unlockWifiMapsEnabled)
            // Credentials, tokens and other secrets are intentionally not part of portable backup.
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

        val routinesRaw = root["routines"]?.let { json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) } ?: "[]"
        val routines = RoutineCodec.decode(routinesRaw).take(MAX_ITEMS_PER_SECTION)

        BackupPayload(
            shortcuts = shortcutsJson.map { it.jsonObject.toShortcut() },
            messages = messagesJson.map { it.jsonObject.toMessage() },
            routines = routines,
            favoriteToolIds = root.optionalStringArray("favoriteToolIds"),
            recentToolIds = root.optionalStringArray("recentToolIds"),
            // Sensitive/advanced automation always requires explicit review after import.
            unlockWifiMapsEnabled = false,
        )
    }

    private fun ScheduledAppShortcut.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("packageName", packageName)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.name)
        put("isEnabled", isEnabled)
    }

    private fun ScheduledMessage.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("platform", platform.name)
        put("recipient", recipient)
        put("message", message)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.name)
        put("isEnabled", isEnabled)
        put("deliveryMode", deliveryMode.name)
    }

    private fun JsonObject.toShortcut(): ScheduledAppShortcut {
        val shortcut = ScheduledAppShortcut(
            id = optionalString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = requiredString("name"),
            packageName = requiredString("packageName"),
            hour = requiredInt("hour"),
            minute = requiredInt("minute"),
            repeat = enumValueOf(requiredString("repeat")),
            isEnabled = optionalBoolean("isEnabled") ?: true,
        )
        require(shortcut.name.isNotBlank()) { "Shortcut name is required" }
        require(shortcut.isValid()) { "Invalid shortcut" }
        return shortcut
    }

    private fun JsonObject.toMessage(): ScheduledMessage {
        val scheduledMessage = ScheduledMessage(
            id = optionalString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = requiredString("name"),
            platform = MessagePlatform.valueOf(requiredString("platform")),
            recipient = requiredString("recipient"),
            message = requiredString("message"),
            hour = requiredInt("hour"),
            minute = requiredInt("minute"),
            repeat = RepeatOption.valueOf(requiredString("repeat")),
            isEnabled = optionalBoolean("isEnabled") ?: true,
            deliveryMode = optionalString("deliveryMode")?.let { runCatching { MessageDeliveryMode.valueOf(it) }.getOrNull() }
                ?: MessageDeliveryMode.PREPARED,
        )
        require(scheduledMessage.name.isNotBlank()) { "Message name is required" }
        require(scheduledMessage.isValid()) { "Invalid scheduled message" }
        return scheduledMessage
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: error("Missing $key")

    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.optionalBoolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.requiredInt(key: String): Int =
        this[key]?.jsonPrimitive?.int ?: error("Missing $key")

    private fun JsonObject.requiredArray(key: String): JsonArray =
        this[key]?.jsonArray ?: error("Missing $key")

    private fun JsonObject.optionalStringArray(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
}
