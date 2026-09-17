package com.explapp.shortcut.backup

import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineCodec
import java.util.UUID

object RoutineTransferCodec {
    const val MAX_CHARS = 128_000

    fun encode(routine: AutomationRoutine): String = RoutineCodec.encode(listOf(routine))

    fun decode(raw: String, newId: String = UUID.randomUUID().toString()): Result<AutomationRoutine> = runCatching {
        require(raw.length <= MAX_CHARS) { "Routine document is too large" }
        val decoded = RoutineCodec.decode(raw)
        require(decoded.size == 1) { "Expected one routine" }
        decoded.single().copy(id = newId, isEnabled = false, updatedAtMs = System.currentTimeMillis())
    }
}
