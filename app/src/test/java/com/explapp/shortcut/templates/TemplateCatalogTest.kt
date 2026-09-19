package com.explapp.shortcut.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCatalogTest {
    @Test
    fun coreCatalogKeepsReadyTemplatesFirst() {
        val templates = TemplateCatalog.core()

        assertEquals(7, templates.size)
        assertEquals(TemplateId.OPEN_APP_ON_SCHEDULE, templates[0].id)
        assertEquals(TemplateId.SCHEDULE_WHATSAPP, templates[1].id)
        assertEquals(TemplateId.SCHEDULE_TELEGRAM, templates[2].id)
        assertEquals(TemplateId.UNLOCK_WIFI_MAPS, templates[3].id)
        assertTrue(templates.take(4).all { it.ready })
    }

    @Test
    fun futureTemplatesRemainVisibleButNotReady() {
        val future = TemplateCatalog.core().drop(4)

        assertEquals(
            listOf(TemplateId.CAR_MODE, TemplateId.SLEEP_MODE, TemplateId.BATTERY_80),
            future.map { it.id },
        )
        assertTrue(future.none { it.ready })
    }
}
