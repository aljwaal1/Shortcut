package com.explapp.shortcut.system

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemRouteTest {
    @Test
    fun routesAreStableAndPortable() {
        assertEquals("automations", SystemRoute.forAction(SystemAction.NEW_AUTOMATION).value)
        assertEquals("usage", SystemRoute.forAction(SystemAction.APP_USAGE).value)
        assertEquals("qr", SystemRoute.forAction(SystemAction.QR).value)
        assertEquals("routine:r-1", SystemRoute.forRoutine("r-1").value)
    }
}
