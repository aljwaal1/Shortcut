package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputUriPolicyTest {
    @Test
    fun `file uri from legacy Android must be wrapped by FileProvider`() {
        assertEquals(OutputUriDelivery.FILE_PROVIDER, OutputUriPolicy.deliveryFor("file"))
    }

    @Test
    fun `content uri can be delivered directly`() {
        assertEquals(OutputUriDelivery.DIRECT_CONTENT, OutputUriPolicy.deliveryFor("content"))
    }

    @Test
    fun `unsupported uri scheme is rejected explicitly`() {
        assertEquals(OutputUriDelivery.UNSUPPORTED, OutputUriPolicy.deliveryFor("http"))
    }
}
