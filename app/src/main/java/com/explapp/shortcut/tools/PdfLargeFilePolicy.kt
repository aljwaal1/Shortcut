package com.explapp.shortcut.tools

/** Bound expensive content inspection without limiting PDF page splitting itself. */
object PdfLargeFilePolicy {
    fun shouldScanAllPages(pageCount: Int): Boolean = pageCount in 1..300

    fun requiredFreeBytes(sourceBytes: Long): Long {
        require(sourceBytes >= 0)
        return if (sourceBytes > Long.MAX_VALUE / 2) Long.MAX_VALUE else sourceBytes * 2
    }
}
