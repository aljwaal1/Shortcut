package com.explapp.shortcut.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun ToolHubScreen(
    onBack: () -> Unit,
    onTool: (ToolId) -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val all = ToolCatalog.all()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Text(if (isArabic) "الأدوات والاختصارات" else "Tools & shortcuts", style = MaterialTheme.typography.headlineSmall)
                    Text(if (isArabic) "أدوات سريعة وعملية بدون تعقيد" else "Fast, practical tools without clutter", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        ToolSection.entries.forEach { section ->
            val sectionTools = all.filter { it.section == section }
            item {
                Text(
                    sectionTitle(section, isArabic),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            sectionTools.chunked(2).forEach { pair ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { tool ->
                            Card(
                                onClick = { onTool(tool.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(sectionIcon(section), contentDescription = null)
                                    Text(if (isArabic) tool.titleAr else tool.titleEn, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Column(Modifier.weight(1f)) {}
                        }
                    }
                }
            }
        }
        item { Column(Modifier.padding(bottom = 24.dp)) {} }
    }
}

private fun sectionTitle(section: ToolSection, ar: Boolean): String = when (section) {
    ToolSection.IMAGES -> if (ar) "الصور" else "Images"
    ToolSection.PDF_FILES -> if (ar) "PDF والملفات" else "PDF & files"
    ToolSection.QUICK -> if (ar) "أدوات سريعة" else "Quick tools"
    ToolSection.AUTOMATION -> if (ar) "الأتمتة" else "Automation"
}

private fun sectionIcon(section: ToolSection) = when (section) {
    ToolSection.IMAGES -> Icons.Default.Image
    ToolSection.PDF_FILES -> Icons.Default.Description
    ToolSection.QUICK -> Icons.Default.AutoAwesome
    ToolSection.AUTOMATION -> Icons.Default.SettingsSuggest
}
