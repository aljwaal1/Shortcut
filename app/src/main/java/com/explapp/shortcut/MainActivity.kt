package com.explapp.shortcut

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.tools.ToolHubScreen
import com.explapp.shortcut.tools.ToolRouter
import com.explapp.shortcut.ui.ShortcutApp
import com.explapp.shortcut.ui.ShortcutTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShortcutTheme {
                var showTools by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("shortcut_root"),
                ) {
                    if (showTools) {
                        ToolHubScreen(
                            onBack = { showTools = false },
                            onTool = { tool -> startActivity(ToolRouter.intent(this@MainActivity, tool)) },
                        )
                    } else {
                        ShortcutApp()
                        SmallFloatingActionButton(
                            onClick = { showTools = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 12.dp),
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Tools")
                        }
                    }
                }
            }
        }
    }
}
