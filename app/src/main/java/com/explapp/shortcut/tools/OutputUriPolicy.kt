package com.explapp.shortcut.tools

enum class OutputUriDelivery {
    DIRECT_CONTENT,
    FILE_PROVIDER,
    UNSUPPORTED,
}

object OutputUriPolicy {
    fun deliveryFor(scheme: String?): OutputUriDelivery = when (scheme?.lowercase()) {
        "content" -> OutputUriDelivery.DIRECT_CONTENT
        "file" -> OutputUriDelivery.FILE_PROVIDER
        else -> OutputUriDelivery.UNSUPPORTED
    }
}
