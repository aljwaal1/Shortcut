/* Adapted from OpenTasker (c) 2026 SysAdminDoc, MIT License.
 * Original: https://github.com/SysAdminDoc/OpenTasker/blob/a3b4fda4942eb61e9c81290d0ff390ad1f2160b8/app/src/main/java/com/opentasker/ui/screens/InlineNotice.kt
 * Adaptation: package, string resources and radius aligned with Shortcut.
 * See THIRD_PARTY_NOTICES/OpenTasker-LICENSE.txt.
 */
package com.explapp.shortcut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared inline banner for empty, informational, and failure states. */
@Composable
internal fun InlineNotice(
    title: String,
    body: String,
    color: Color,
    action: (@Composable () -> Unit)? = null,
) {
    val isError = color == MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (isError) Icons.Filled.Error else Icons.Filled.Info,
                contentDescription = if (isError) "Error" else "Information",
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                action?.invoke()
            }
        }
    }
}
