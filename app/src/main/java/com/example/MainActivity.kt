package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val viewModel: FileManagerViewModel by viewModels()

    // Permission launcher for Android 10 and below (SDK < 30)
    private val legacyPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        viewModel.setPermissionsGranted(readGranted || writeGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure webview directories exist to prevent chromium opendir warnings/errors
        try {
            val wasmDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!wasmDir.exists()) wasmDir.mkdirs()
            val jsDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!jsDir.exists()) jsDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()

        // Check and request standard permissions for devices below Android 11 (API 30)
        verifyAndRequestPermissions()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                // Register dynamic Toast notifications
                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentScreen != Screen.SETTINGS) {
                            AppBottomNavigationBar(
                                selectedScreen = currentScreen,
                                onScreenSelected = { viewModel.navigateToScreen(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (currentScreen != Screen.SETTINGS) innerPadding.calculateBottomPadding() else 0.dp) // Exclude top to allow status bar overlap
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_navigation"
                        ) { screen ->
                            when (screen) {
                                Screen.EXPLORER -> ExplorerScreen(viewModel = viewModel)
                                Screen.EDITOR -> EditorScreen(viewModel = viewModel)
                                Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
                                Screen.STATS -> StatsScreen(viewModel = viewModel)
                                Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Frequently verify dynamic permission state (important on returning from manage external settings page)
        val hasAccess = checkStorageAccessApproved()
        viewModel.setPermissionsGranted(hasAccess)
        viewModel.refreshStorageMetrics()
    }

    // Storage access approval calculations
    private fun checkStorageAccessApproved(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writeCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            readCheck == PackageManager.PERMISSION_GRANTED && writeCheck == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun verifyAndRequestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val readCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writeCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

            val permissionsNeeded = mutableListOf<String>()
            if (readCheck != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (writeCheck != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsNeeded.isNotEmpty()) {
                legacyPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
            } else {
                viewModel.setPermissionsGranted(true)
            }
        } else {
            // Android 11+ manages permissions via Environment.isExternalStorageManager()
            viewModel.setPermissionsGranted(Environment.isExternalStorageManager())
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier.testTag("app_bottom_navigator"),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedScreen == Screen.EXPLORER,
            onClick = { onScreenSelected(Screen.EXPLORER) },
            icon = {
                Icon(
                    imageVector = if (selectedScreen == Screen.EXPLORER) Icons.Filled.Folder else Icons.Outlined.Folder,
                    contentDescription = "Explorer"
                )
            },
            label = { Text("Explorer") },
            modifier = Modifier.testTag("nav_explorer_tab")
        )
        NavigationBarItem(
            selected = selectedScreen == Screen.EDITOR,
            onClick = { onScreenSelected(Screen.EDITOR) },
            icon = {
                Icon(
                    imageVector = if (selectedScreen == Screen.EDITOR) Icons.Filled.EditNote else Icons.Outlined.EditNote,
                    contentDescription = "Editor"
                )
            },
            label = { Text("Editor") },
            modifier = Modifier.testTag("nav_editor_tab")
        )
        NavigationBarItem(
            selected = selectedScreen == Screen.HISTORY,
            onClick = { onScreenSelected(Screen.HISTORY) },
            icon = {
                Icon(
                    imageVector = if (selectedScreen == Screen.HISTORY) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "History"
                )
            },
            label = { Text("History") },
            modifier = Modifier.testTag("nav_history_tab")
        )
        NavigationBarItem(
            selected = selectedScreen == Screen.STATS,
            onClick = { onScreenSelected(Screen.STATS) },
            icon = {
                Icon(
                    imageVector = if (selectedScreen == Screen.STATS) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                    contentDescription = "Stats"
                )
            },
            label = { Text("Stats") },
            modifier = Modifier.testTag("nav_stats_tab")
        )
    }
}
