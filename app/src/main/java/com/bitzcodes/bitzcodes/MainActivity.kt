package com.bitzcodes.bitzcodes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bitzcodes.bitzcodes.ui.ChatViewModel
import com.bitzcodes.bitzcodes.ui.chat.ChatScreen
import com.bitzcodes.bitzcodes.ui.editor.EditorScreen
import com.bitzcodes.bitzcodes.ui.theme.BitzcodesTheme
import com.bitzcodes.bitzcodes.ui.workspace.WorkspaceScreen
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob SDK
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isDarkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            BitzcodesTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppShell(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showGlobalSettings by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Workspace Tab
                NavigationBarItem(
                    selected = currentRoute == "workspace",
                    onClick = {
                        navController.navigate("workspace") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Workspaces") },
                    label = { Text("Workspaces") }
                )

                // Chat Tab
                NavigationBarItem(
                    selected = currentRoute == "chat",
                    onClick = {
                        navController.navigate("chat") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "AI Chat") },
                    label = { Text("AI Chat") }
                )

                // Editor Tab
                NavigationBarItem(
                    selected = currentRoute == "editor",
                    onClick = {
                        navController.navigate("editor") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Code, contentDescription = "File Editor") },
                    label = { Text("Editor") }
                )

                // Settings Tab/Button
                NavigationBarItem(
                    selected = false,
                    onClick = { showGlobalSettings = true },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "workspace",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("workspace") {
                WorkspaceScreen(viewModel)
            }
            composable("chat") {
                ChatScreen(viewModel)
            }
            composable("editor") {
                EditorScreen(viewModel)
            }
        }
    }

    // Global Settings Dialog
    if (showGlobalSettings) {
        val settings by viewModel.settings.collectAsState()
        var rulesText by remember { mutableStateOf(settings.globalRules) }
        var themeSelection by remember { mutableStateOf(settings.themeMode) }

        AlertDialog(
            onDismissRequest = { showGlobalSettings = false },
            title = { Text("Global BITZCODES Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Global Rules
                    Column {
                        Text("Global AI System Rules", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = rulesText,
                            onValueChange = { rulesText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("e.g. Prefer Kotlin over Java; Always use Jetpack Compose; ...") }
                        )
                    }

                    // Theme selector
                    Column {
                        Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("system", "light", "dark").forEach { mode ->
                                FilterChip(
                                    selected = themeSelection == mode,
                                    onClick = { themeSelection = mode },
                                    label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveSettings(rulesText, themeSelection)
                        showGlobalSettings = false
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGlobalSettings = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
