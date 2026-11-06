/* Adapted from OpenTasker (c) 2026 SysAdminDoc; MIT license.
 * Original: https://github.com/SysAdminDoc/OpenTasker/blob/a3b4fda4942eb61e9c81290d0ff390ad1f2160b8/app/src/main/java/com/opentasker/ui/utils/ExpandCollapseSemantics.kt
 * Adaptation: independent labels; see THIRD_PARTY_NOTICES/OpenTasker-LICENSE.txt.
 */
package com.explapp.shortcut.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Makes a whole row behave as the disclosure control for the section under it.
 *
 * A bare `Modifier.clickable { expanded = !expanded }` gives a screen reader nothing but "double tap
 * to activate": no role, and no way to tell whether the section is already open, so the same
 * announcement is read whether the next tap opens or closes it. Seven cards across Setup, the run
 * log, the Inspector, the scene library and both automation lists had exactly that.
 *
 * The state description is what carries the open/closed state; the click label says which way the
 * next tap goes. This mirrors the hand-written semantics already on the run log's variable-change
 * inspector, which is where the idiom came from.
 */
@Composable
fun Modifier.expandCollapseToggle(expanded: Boolean, onToggle: () -> Unit): Modifier {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val stateLabel = if (isArabic) {
        if (expanded) "موسّع" else "مطوي"
    } else if (expanded) "Expanded" else "Collapsed"
    val actionLabel = if (isArabic) {
        if (expanded) "طي" else "توسيع"
    } else if (expanded) "Collapse" else "Expand"
    return this
        .semantics { stateDescription = stateLabel }
        .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onToggle)
}


