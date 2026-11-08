package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPersonalDeliveryPolicyTest {
    @Test fun usernameIsNormalizedAndValidated() {
        assertEquals("@example_user", TelegramPersonalDeliveryPolicy.normalizeUsername("example_user"))
        assertEquals("@example_user", TelegramPersonalDeliveryPolicy.normalizeUsername(" @example_user "))
        assertEquals(null, TelegramPersonalDeliveryPolicy.normalizeUsername("x"))
        assertEquals(null, TelegramPersonalDeliveryPolicy.normalizeUsername("12345 67"))
    }

    @Test fun pendingSendMustNotBeReportedAsDelivered() {
        val policy = TelegramPersonalDeliveryPolicy
        assertFalse(policy.isConfirmed(TelegramPersonalDeliveryPolicy.State.SENDING))
        assertFalse(policy.isConfirmed(TelegramPersonalDeliveryPolicy.State.UNCERTAIN))
        assertTrue(policy.isConfirmed(TelegramPersonalDeliveryPolicy.State.CONFIRMED))
        assertFalse(policy.mayRetryAutomatically(TelegramPersonalDeliveryPolicy.State.UNCERTAIN))
        assertTrue(policy.requiresUserAttention(TelegramPersonalDeliveryPolicy.State.NEEDS_LOGIN))
    }
}
