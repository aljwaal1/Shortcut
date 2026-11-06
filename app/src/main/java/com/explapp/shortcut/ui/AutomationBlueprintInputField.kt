/*
 * Adapted from OpenTasker (c) 2026 SysAdminDoc, MIT License.
 * Original: https://github.com/SysAdminDoc/OpenTasker/blob/a3b4fda4942eb61e9c81290d0ff390ad1f2160b8/feature/automation/src/main/kotlin/com/opentasker/feature/automation/AutomationBlueprintInputField.kt
 * Changes: package name only, for use within Shortcut.
 * The OpenTasker MIT license is retained in THIRD_PARTY_NOTICES/OpenTasker-LICENSE.txt.
 */
package com.explapp.shortcut.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Keyboard behavior understood by the automation blueprint editor. */
enum class AutomationInputKeyboard {
    TEXT,
    NUMBER,
    DECIMAL,
    ASCII,
}

/**
 * Reusable blueprint field presentation owned by the automation feature.
 * Resource-backed copy stays with the app so this feature remains independent of app resources.
 */
@Composable
fun AutomationBlueprintInputField(
    label: String,
    value: String,
    placeholder: String?,
    supportingText: String,
    errorText: String?,
    keyboard: AutomationInputKeyboard,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = {
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error)
            } else {
                Text(supportingText)
            }
        },
        isError = errorText != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard.toComposeType()),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun AutomationInputKeyboard.toComposeType(): KeyboardType = when (this) {
    AutomationInputKeyboard.NUMBER -> KeyboardType.Number
    AutomationInputKeyboard.DECIMAL -> KeyboardType.Decimal
    AutomationInputKeyboard.ASCII -> KeyboardType.Ascii
    AutomationInputKeyboard.TEXT -> KeyboardType.Text
}
