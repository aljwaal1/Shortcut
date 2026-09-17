package com.explapp.shortcut.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.automation.routines.RoutineDispatcher
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.automation.routines.RoutineTemplateCatalog
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.tools.ToolCatalog
import com.explapp.shortcut.tools.ToolId
import com.explapp.shortcut.tools.ToolRouter
import com.explapp.shortcut.ui.MyAutomationsActivity
import com.explapp.shortcut.ui.ScheduledTasksManagerActivity
import com.explapp.shortcut.ui.ShortcutTheme

class CommandPaletteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShortcutTheme { CommandPaletteScreen(onBack = ::finish) } }
    }
}

@Composable
private fun CommandPaletteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var query by remember { mutableStateOf("") }
    val routines = remember { RoutineStore(context).load() }
    val tools = remember { ToolCatalog.all() }
    val scheduledShortcuts = remember { ShortcutStore(context).load() }
    val scheduledMessages = remember { MessageStore(context).load() }
    val templates = remember { RoutineTemplateCatalog.templates() }

    val all = remember(routines, tools, scheduledShortcuts, scheduledMessages, templates, ar) {
        buildList {
            tools.forEach { tool -> add(CommandItem("tool:${tool.id.name}", if (ar) tool.titleAr else tool.titleEn, listOf(tool.titleAr, tool.titleEn), CommandKind.TOOL)) }
            routines.forEach { routine -> add(CommandItem("routine:${routine.id}", routine.name, listOf(routine.trigger.type.name), CommandKind.ROUTINE)) }
            scheduledShortcuts.forEach { item -> add(CommandItem("scheduled-app:${item.id}", item.name, listOf(item.packageName), CommandKind.SCHEDULED)) }
            scheduledMessages.forEach { item -> add(CommandItem("scheduled-message:${item.id}", item.name, listOf(item.recipient, item.platform.name), CommandKind.SCHEDULED)) }
            templates.forEachIndexed { index, item -> add(CommandItem("template:$index", item.name, listOf(item.trigger.type.name), CommandKind.TEMPLATE)) }
        }
    }
    val filtered = remember(all, query) { CommandSearch.filter(all, query) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (ar) "بحث الأوامر" else "Command Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (ar) "ابحث في الأدوات والأتمتة والقوالب" else "Search tools, automations and templates") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        items(filtered, key = { it.id }) { item ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        item.id.startsWith("tool:") -> {
                            val id = runCatching { ToolId.valueOf(item.id.substringAfter(':')) }.getOrNull()
                            if (id != null) context.startActivity(ToolRouter.intent(context, id))
                        }
                        item.id.startsWith("routine:") -> routines.firstOrNull { it.id == item.id.substringAfter(':') }
                            ?.let { RoutineDispatcher(context).execute(it, userInitiated = true) }
                        item.id.startsWith("scheduled-") -> context.startActivity(Intent(context, ScheduledTasksManagerActivity::class.java))
                        item.id.startsWith("template:") -> context.startActivity(Intent(context, MyAutomationsActivity::class.java))
                    }
                },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(item.label, fontWeight = FontWeight.Bold)
                    Text(item.kind.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
