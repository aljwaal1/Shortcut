package com.explapp.shortcut.quality

import android.content.Context
import java.text.DateFormat
import java.util.Date

data class CrashRecord(
    val timestampMs: Long,
    val threadName: String,
    val summary: String,
    val stackTrace: String,
) {
    fun displayTime(): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestampMs))
}

class CrashLogStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(thread: Thread, error: Throwable) {
        val summary = buildString {
            append(error.javaClass.simpleName)
            error.message?.takeIf { it.isNotBlank() }?.let {
                append(": ")
                append(it.take(500))
            }
        }
        prefs.edit()
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putString(KEY_THREAD, thread.name.take(120))
            .putString(KEY_SUMMARY, redactSecrets(summary))
            .putString(KEY_STACK, redactSecrets(error.stackTraceToString()).take(MAX_STACK_CHARS))
            .commit()
    }

    fun last(): CrashRecord? {
        val timestamp = prefs.getLong(KEY_TIME, 0L)
        if (timestamp <= 0L) return null
        return CrashRecord(
            timestampMs = timestamp,
            threadName = prefs.getString(KEY_THREAD, "").orEmpty(),
            summary = prefs.getString(KEY_SUMMARY, "").orEmpty(),
            stackTrace = prefs.getString(KEY_STACK, "").orEmpty(),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun redactSecrets(value: String): String =
        TELEGRAM_BOT_TOKEN.replace(value, "[REDACTED_BOT_TOKEN]")

    companion object {
        private const val PREFS = "shortcut_crash_log"
        private const val KEY_TIME = "time"
        private const val KEY_THREAD = "thread"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_STACK = "stack"
        private const val MAX_STACK_CHARS = 16_000
        private val TELEGRAM_BOT_TOKEN = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{20,}\b""")
    }
}
