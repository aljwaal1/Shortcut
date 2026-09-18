package com.explapp.shortcut.automation.routines

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

class CustomScriptRunner {
    fun run(script: String, variables: Map<String, String>): Result<String> = runCatching {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.classShutter = ClassShutter { false }
            val scope: Scriptable = cx.initSafeStandardObjects()
            variables.forEach { (key, value) ->
                scope.put(key, scope, value)
            }
            val wrapped = "(function(){\n$script\n})()"
            val result = cx.evaluateString(scope, wrapped, "ShortcutCustomScript", 1, null)
            Context.toString(result)
        } finally {
            Context.exit()
        }
    }
}
