package com.explapp.shortcut.templates

enum class TemplateId {
    OPEN_APP_ON_SCHEDULE,
    SCHEDULE_WHATSAPP,
    SCHEDULE_TELEGRAM,
    UNLOCK_WIFI_MAPS,
    CAR_MODE,
    SLEEP_MODE,
    BATTERY_80,
}

data class ShortcutTemplate(
    val id: TemplateId,
    val ready: Boolean,
)

object TemplateCatalog {
    fun core(): List<ShortcutTemplate> = listOf(
        ShortcutTemplate(TemplateId.OPEN_APP_ON_SCHEDULE, ready = true),
        ShortcutTemplate(TemplateId.SCHEDULE_WHATSAPP, ready = true),
        ShortcutTemplate(TemplateId.SCHEDULE_TELEGRAM, ready = true),
        ShortcutTemplate(TemplateId.UNLOCK_WIFI_MAPS, ready = true),
        ShortcutTemplate(TemplateId.CAR_MODE, ready = false),
        ShortcutTemplate(TemplateId.SLEEP_MODE, ready = false),
        ShortcutTemplate(TemplateId.BATTERY_80, ready = false),
    )
}
