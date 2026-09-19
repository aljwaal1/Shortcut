package com.explapp.shortcut.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.ui.VisualIdentity

@Composable
fun ToolHubScreen(
    onBack: () -> Unit,
    onTool: (ToolId) -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val context = LocalContext.current
    val store = remember(context) { ToolPreferencesStore(context.applicationContext) }
    val all = ToolCatalog.all()
    var favorites by remember { mutableStateOf(store.favorites()) }
    var recents by remember { mutableStateOf(store.recents()) }

    fun openTool(tool: ShortcutTool) {
        recents = store.recordRecent(tool.id)
        onTool(tool.id)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ToolHubHero(isArabic = isArabic, onBack = onBack) }

        val favoriteTools = favorites.mapNotNull { id -> all.firstOrNull { it.id == id } }
        if (favoriteTools.isNotEmpty()) {
            item {
                QuickSectionHeader(
                    title = if (isArabic) "المفضلة" else "Favorites",
                    subtitle = if (isArabic) "أدواتك المثبتة للوصول السريع" else "Pinned tools for quick access",
                    icon = Icons.Default.Star,
                )
            }
            favoriteTools.chunked(2).forEach { pair ->
                item {
                    ToolPair(
                        pair = pair,
                        isArabic = isArabic,
                        favorites = favorites,
                        onOpen = ::openTool,
                        onFavorite = { id -> favorites = store.toggleFavorite(id) },
                    )
                }
            }
        }

        val recentTools = recents.mapNotNull { id -> all.firstOrNull { it.id == id } }
        if (recentTools.isNotEmpty()) {
            item {
                QuickSectionHeader(
                    title = if (isArabic) "استخدمت مؤخرًا" else "Recent tools",
                    subtitle = if (isArabic) "آخر الأدوات التي فتحتها" else "Tools you opened recently",
                    icon = Icons.Default.AutoAwesome,
                )
            }
            recentTools.take(4).chunked(2).forEach { pair ->
                item {
                    ToolPair(
                        pair = pair,
                        isArabic = isArabic,
                        favorites = favorites,
                        onOpen = ::openTool,
                        onFavorite = { id -> favorites = store.toggleFavorite(id) },
                    )
                }
            }
        }

        ToolSection.entries.forEach { section ->
            val sectionTools = all.filter { it.section == section }
            item { SectionHeader(section = section, isArabic = isArabic, count = sectionTools.size) }
            sectionTools.chunked(2).forEach { pair ->
                item {
                    ToolPair(
                        pair = pair,
                        isArabic = isArabic,
                        favorites = favorites,
                        onOpen = ::openTool,
                        onFavorite = { id -> favorites = store.toggleFavorite(id) },
                    )
                }
            }
        }
        item { Box(Modifier.padding(bottom = 28.dp)) }
    }
}

@Composable
private fun ToolPair(
    pair: List<ShortcutTool>,
    isArabic: Boolean,
    favorites: Set<ToolId>,
    onOpen: (ShortcutTool) -> Unit,
    onFavorite: (ToolId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pair.forEach { tool ->
            ToolCard(
                tool = tool,
                isArabic = isArabic,
                isFavorite = tool.id in favorites,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(tool) },
                onFavorite = { onFavorite(tool.id) },
            )
        }
        if (pair.size == 1) Box(Modifier.weight(1f))
    }
}

@Composable
private fun ToolHubHero(isArabic: Boolean, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
            .padding(start = 12.dp, end = 22.dp, top = 18.dp, bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).size(96.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            }
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 3.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    if (isArabic) "الأدوات والاختصارات" else "Tools & shortcuts",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (isArabic) "اختر أداة وابدأ مباشرة — بدون قوائم معقدة" else "Pick a tool and get straight to the action",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.86f),
                )
                Text(
                    if (isArabic) "صور • ملفات • أدوات سريعة • أتمتة" else "Images • Files • Quick tools • Automation",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.70f),
                )
            }
        }
    }
}

@Composable
private fun QuickSectionHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(section: ToolSection, isArabic: Boolean, count: Int) {
    val style = VisualIdentity.forSection(section)
    val accent = Color(style.accentArgb)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.14f), modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(sectionIcon(section), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(sectionTitle(section, isArabic), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(sectionSubtitle(section, isArabic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f)) {
            Text(
                count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ToolCard(
    tool: ShortcutTool,
    isArabic: Boolean,
    isFavorite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val style = VisualIdentity.forSection(tool.section)
    val accent = Color(style.accentArgb)
    val dark = isSystemInDarkTheme()
    val container = if (dark) accent.copy(alpha = 0.12f) else Color(style.softArgb)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 142.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = if (dark) 0.24f else 0.16f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(sectionIcon(tool.section), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isArabic) "المفضلة" else "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (isArabic) tool.titleAr else tool.titleEn,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (isArabic) "افتح الأداة  ←" else "Open tool  →",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun sectionTitle(section: ToolSection, ar: Boolean): String = when (section) {
    ToolSection.IMAGES -> if (ar) "الصور" else "Images"
    ToolSection.PDF_FILES -> if (ar) "PDF والملفات" else "PDF & files"
    ToolSection.QUICK -> if (ar) "أدوات سريعة" else "Quick tools"
    ToolSection.AUTOMATION -> if (ar) "الأتمتة" else "Automation"
}

private fun sectionSubtitle(section: ToolSection, ar: Boolean): String = when (section) {
    ToolSection.IMAGES -> if (ar) "دمج، تحويل، OCR وتحرير" else "Merge, convert, OCR and edit"
    ToolSection.PDF_FILES -> if (ar) "PDF، ZIP واستخراج النص" else "PDF, ZIP and text extraction"
    ToolSection.QUICK -> if (ar) "QR، Screenshot والحافظة" else "QR, screenshots and clipboard"
    ToolSection.AUTOMATION -> if (ar) "السيارة، البطارية، NFC والاستخدام" else "Car, battery, NFC and usage"
}

private fun sectionIcon(section: ToolSection): ImageVector = when (section) {
    ToolSection.IMAGES -> Icons.Default.Image
    ToolSection.PDF_FILES -> Icons.Default.Description
    ToolSection.QUICK -> Icons.Default.AutoAwesome
    ToolSection.AUTOMATION -> Icons.Default.SettingsSuggest
}
