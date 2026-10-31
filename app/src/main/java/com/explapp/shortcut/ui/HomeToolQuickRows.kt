package com.explapp.shortcut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.tools.ShortcutTool
import com.explapp.shortcut.tools.ToolCatalog
import com.explapp.shortcut.tools.ToolId
import com.explapp.shortcut.tools.ToolPreferencesStore
import com.explapp.shortcut.tools.ToolRouter

@Composable
fun HomeToolQuickRows() {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val store = remember(context) { ToolPreferencesStore(context.applicationContext) }
    val all = remember { ToolCatalog.all() }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var recents by remember { mutableStateOf(store.recents()) }

    fun open(id: ToolId) {
        recents = store.recordRecent(id)
        context.startActivity(ToolRouter.intent(context, id))
    }

    val favoriteTools = favorites.mapNotNull { id -> all.firstOrNull { it.id == id } }.take(4)
    val recentTools = recents.mapNotNull { id -> all.firstOrNull { it.id == id } }.take(4)
    if (favoriteTools.isEmpty() && recentTools.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (favoriteTools.isNotEmpty()) {
            QuickGroup(
                title = if (ar) "المفضلة" else "Favorites",
                tools = favoriteTools,
                icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                isArabic = ar,
                onOpen = ::open,
            )
        }
        if (recentTools.isNotEmpty()) {
            QuickGroup(
                title = if (ar) "استخدمت مؤخرًا" else "Recent tools",
                tools = recentTools,
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                isArabic = ar,
                onOpen = ::open,
            )
        }
    }
}

@Composable
private fun QuickGroup(
    title: String,
    tools: List<ShortcutTool>,
    icon: @Composable () -> Unit,
    isArabic: Boolean,
    onOpen: (ToolId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        tools.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { tool ->
                    ElevatedCard(onClick = { onOpen(tool.id) }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (isArabic) tool.titleAr else tool.titleEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (isArabic) "فتح ←" else "Open →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}
