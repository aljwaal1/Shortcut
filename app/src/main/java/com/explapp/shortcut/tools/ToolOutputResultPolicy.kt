package com.explapp.shortcut.tools

data class ToolOutputResultBehavior(
    val mime: String,
    val canOpen: Boolean = true,
    val canShare: Boolean = true,
)

object ToolOutputResultPolicy {
    fun forTool(tool: ToolId): ToolOutputResultBehavior = when (tool) {
        ToolId.MERGE_IMAGES, ToolId.IMAGE_RESIZE_COMPRESS, ToolId.IMAGE_TO_JPEG -> ToolOutputResultBehavior("image/jpeg")
        ToolId.IMAGES_TO_PDF -> ToolOutputResultBehavior("application/pdf")
        ToolId.GIF_CREATE -> ToolOutputResultBehavior("image/gif")
        ToolId.IMAGE_CROP, ToolId.QR_CREATE, ToolId.SCREENSHOT_CAPTURE -> ToolOutputResultBehavior("image/png")
        ToolId.ZIP_FILES -> ToolOutputResultBehavior("application/zip")
        else -> ToolOutputResultBehavior("application/octet-stream", canOpen = false, canShare = false)
    }
}
