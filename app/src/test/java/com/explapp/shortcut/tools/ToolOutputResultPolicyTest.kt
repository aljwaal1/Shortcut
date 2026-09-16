package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputResultPolicyTest {
    @Test
    fun `all file producing tools expose open and share results`() {
        val tools = listOf(
            ToolId.MERGE_IMAGES,
            ToolId.IMAGES_TO_PDF,
            ToolId.GIF_CREATE,
            ToolId.IMAGE_RESIZE_COMPRESS,
            ToolId.IMAGE_TO_JPEG,
            ToolId.IMAGE_CROP,
            ToolId.QR_CREATE,
            ToolId.ZIP_FILES,
            ToolId.SCREENSHOT_CAPTURE,
        )

        tools.forEach { tool ->
            val policy = ToolOutputResultPolicy.forTool(tool)
            assertTrue("$tool must allow opening the result", policy.canOpen)
            assertTrue("$tool must allow sharing the result", policy.canShare)
        }
    }

    @Test
    fun `output mime types match their generated files`() {
        assertEquals("application/pdf", ToolOutputResultPolicy.forTool(ToolId.IMAGES_TO_PDF).mime)
        assertEquals("application/zip", ToolOutputResultPolicy.forTool(ToolId.ZIP_FILES).mime)
        assertEquals("image/gif", ToolOutputResultPolicy.forTool(ToolId.GIF_CREATE).mime)
        assertEquals("image/png", ToolOutputResultPolicy.forTool(ToolId.QR_CREATE).mime)
        assertEquals("image/png", ToolOutputResultPolicy.forTool(ToolId.IMAGE_CROP).mime)
        assertEquals("image/png", ToolOutputResultPolicy.forTool(ToolId.SCREENSHOT_CAPTURE).mime)
        assertEquals("image/jpeg", ToolOutputResultPolicy.forTool(ToolId.IMAGE_TO_JPEG).mime)
    }
}
