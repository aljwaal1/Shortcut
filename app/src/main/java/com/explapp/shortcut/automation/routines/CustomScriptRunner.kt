package com.explapp.shortcut.automation.routines

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Scriptable

class CustomScriptRunner {
    fun run(script: String, variables: Map<String, String>): Result<String> = runCatching {
        val deadlineNanos = System.nanoTime() + MAX_SCRIPT_NANOS
        val factory = object : ContextFactory() {
            override fun makeContext(): Context = super.makeContext().apply {
                optimizationLevel = -1
                instructionObserverThreshold = INSTRUCTION_OBSERVER_THRESHOLD
            }

            override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                if (System.nanoTime() > deadlineNanos) {
                    throw EvaluatorException("Script timed out")
                }
            }
        }

        factory.call { cx ->
            cx.setClassShutter(ClassShutter { false })
            val scope: Scriptable = cx.initSafeStandardObjects()
            variables.forEach { (key, value) ->
                scope.put(key, scope, value)
            }
            val wrapped = "(function(){\n$script\n})()"
            val result = cx.evaluateString(scope, wrapped, "ShortcutCustomScript", 1, null)
            Context.toString(result)
        }
    }

    companion object {
        private const val INSTRUCTION_OBSERVER_THRESHOLD = 10_000
        private const val MAX_SCRIPT_NANOS = 3_000_000_000L
    }
}
