package com.explapp.shortcut.automation.routines

import com.explapp.shortcut.tools.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoutineTemplateCatalogTest {
    @Test
    fun mapsTemplateOpensMapsWithoutRequiringInstalledAppSelection() {
        val template = RoutineTemplateCatalog.templates().first { it.name == "Commute maps" }
        val action = template.actions.single()
        assertEquals(RoutineActionType.OPEN_MAPS, action.type)
        assertEquals("", action.value)
        assertEquals(true, action.isValid())
    }

    @Test
    fun everyTemplateUsesARegisteredActionAndValidTrigger() {
        RoutineTemplateCatalog.templates().forEach { template ->
            assertEquals(true, template.trigger.isValid())
            template.actions.forEach { action ->
                assertNotNull(RoutineCatalog.meta(action.type))
            }
        }
    }

    @Test
    fun appUsageTemplatePointsToExistingToolId() {
        val template = RoutineTemplateCatalog.templates().first { it.name == "App usage" }
        val action = template.actions.single()

        assertEquals(RoutineActionType.OPEN_TOOL, action.type)
        assertNotNull(runCatching { ToolId.valueOf(action.value) }.getOrNull())
        assertEquals(ToolId.APP_OPEN_ROUTINE, ToolId.valueOf(action.value))
    }
}
