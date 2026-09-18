package com.explapp.shortcut.automation.routines

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

object RoutineCodec {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    fun encode(routines: List<AutomationRoutine>): String = json.encodeToString(
        JsonArray.serializer(),
        buildJsonArray { routines.forEach { add(it.toJson()) } },
    )

    fun decode(raw: String): List<AutomationRoutine> = runCatching {
        json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            runCatching { element.jsonObject.toRoutine() }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun AutomationRoutine.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("isEnabled", isEnabled)
        put("updatedAtMs", updatedAtMs)
        put("trigger", buildJsonObject {
            put("type", trigger.type.name)
            put("value", trigger.value)
        })
        put("conditions", buildJsonArray {
            conditions.forEach { condition ->
                add(buildJsonObject {
                    put("type", condition.type.name)
                    put("value", condition.value)
                    put("secondaryValue", condition.secondaryValue)
                })
            }
        })
        put("actions", buildJsonArray {
            actions.forEach { action ->
                add(buildJsonObject {
                    put("type", action.type.name)
                    put("value", action.value)
                    put("secondaryValue", action.secondaryValue)
                    put("continueOnError", action.continueOnError)
                    put("parameters", buildJsonObject {
                        action.parameters.forEach { (key, value) -> put(key, value) }
                    })
                })
            }
        })
    }

    private fun JsonObject.toRoutine(): AutomationRoutine {
        val triggerJson = getValue("trigger").jsonObject
        return AutomationRoutine(
            id = getValue("id").jsonPrimitive.content,
            name = getValue("name").jsonPrimitive.content,
            isEnabled = this["isEnabled"]?.jsonPrimitive?.boolean ?: true,
            updatedAtMs = this["updatedAtMs"]?.jsonPrimitive?.long ?: 0L,
            trigger = RoutineTrigger(
                type = RoutineTriggerType.valueOf(triggerJson.getValue("type").jsonPrimitive.content),
                value = triggerJson["value"]?.jsonPrimitive?.content.orEmpty(),
            ),
            conditions = this["conditions"]?.jsonArray?.mapNotNull { item ->
                runCatching {
                    val condition = item.jsonObject
                    RoutineCondition(
                        type = RoutineConditionType.valueOf(condition.getValue("type").jsonPrimitive.content),
                        value = condition["value"]?.jsonPrimitive?.content.orEmpty(),
                        secondaryValue = condition["secondaryValue"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }.getOrNull()
            }.orEmpty(),
            actions = getValue("actions").jsonArray.mapNotNull { item ->
                runCatching {
                    val action = item.jsonObject
                    RoutineAction(
                        type = RoutineActionType.valueOf(action.getValue("type").jsonPrimitive.content),
                        value = action["value"]?.jsonPrimitive?.content.orEmpty(),
                        secondaryValue = action["secondaryValue"]?.jsonPrimitive?.content.orEmpty(),
                        continueOnError = action["continueOnError"]?.jsonPrimitive?.boolean ?: false,
                        parameters = action["parameters"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
                    )
                }.getOrNull()
            },
        )
    }
}
