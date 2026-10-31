package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramDeliveryPolicyTest {
    @Test fun rateLimitIncludesRetryDelay() {
        val error = TelegramApiException(429, 35, "rate limited")
        assertTrue(error.isRateLimited)
        assertEquals(35, error.retryAfterSeconds)
        assertEquals(TelegramDeliveryPolicy.Decision.RETRY_AFTER_REJECTION, TelegramDeliveryPolicy.classify(error))
    }

    @Test fun blockedOrInvalidRecipientDoesNotRetry() {
        for (status in listOf(400, 401, 403, 404)) {
            assertEquals(TelegramDeliveryPolicy.Decision.STOP_PERMANENT,
                TelegramDeliveryPolicy.classify(TelegramApiException(status, null, "rejected")))
        }
    }

    @Test fun serverErrorsAndNetworkTimeoutsNeedReview() {
        assertFalse(TelegramApiException(500, null, "server error").canRetryWithoutDuplicateRisk)
        assertEquals(TelegramDeliveryPolicy.Decision.REVIEW_UNKNOWN,
            TelegramDeliveryPolicy.classify(TelegramApiException(500, null, "server error")))
        assertEquals(TelegramDeliveryPolicy.Decision.REVIEW_UNKNOWN,
            TelegramDeliveryPolicy.classify(java.net.SocketTimeoutException("unknown delivery")))
    }
}
