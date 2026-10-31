package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerEdgeTest {
    @Test fun connectFiresOnlyOnDisconnectedToConnected() {
        assertTrue(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_CONNECTED, false, true))
        assertFalse(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_CONNECTED, true, true))
        assertFalse(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_CONNECTED, null, true))
    }

    @Test fun disconnectFiresOnlyOnConnectedToDisconnected() {
        assertTrue(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_DISCONNECTED, true, false))
        assertFalse(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_DISCONNECTED, false, false))
        assertFalse(ChargerEdge.shouldFire(RoutineTriggerType.CHARGER_DISCONNECTED, null, false))
    }
}
