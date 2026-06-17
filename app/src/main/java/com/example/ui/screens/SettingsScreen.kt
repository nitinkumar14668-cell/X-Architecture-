package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.StorageSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: FileManagerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val scrollState = rememberScrollState()

    // Collect GitHub related states
    val githubToken by viewModel.githubToken.collectAsState()
    val githubUsername by viewModel.githubUsername.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val githubBranch by viewModel.githubBranch.collectAsState()
    val githubProfile by viewModel.githubProfile.collectAsState()
    val githubSyncStatus by viewModel.githubSyncStatus.collectAsState()
    val githubIsSyncing by viewModel.githubIsSyncing.collectAsState()

    // Collect editor settings
    val editorFontSize by viewModel.editorFontSize.collectAsState()
    val editorWordWrap by viewModel.editorWordWrap.collectAsState()
    val editorEngine by viewModel.editorEngine.collectAsState()
    val editorTheme by viewModel.editorTheme.collectAsState()
    val editorAutoSave by viewModel.editorAutoSave.collectAsState()

    // Collect show hidden files configuration
    val showHidden by viewModel.showHidden.collectAsState()
    val storageSource by viewModel.storageSource.collectAsState()
    val showFileDetails by viewModel.showFileDetails.collectAsState()
    val sortType by viewModel.sortType.collectAsState()

    // Interactive component states
    var inputToken by remember { mutableStateOf(githubToken) }
    var inputRepo by remember { mutableStateOf(githubRepo) }
    var inputBranch by remember { mutableStateOf(githubBranch) }
    var showTokenPassword by remember { mutableStateOf(false) }

    // Dialog confirms
    var showClearConfirm by remember { mutableStateOf(false) }

    // Sync inputToken, inputRepo and inputBranch when they load
    LaunchedEffect(githubToken, githubRepo, githubBranch) {
        inputToken = githubToken
        inputRepo = githubRepo
        inputBranch = githubBranch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateToScreen(Screen.EXPLORER) },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to File Explorer"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        modifier = Modifier.testTag("app_settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION: GITHUB CONNECTOR
            SettingsSectionHeader(title = "GitHub Synchronization Hub", icon = Icons.Default.CloudQueue)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Status Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (githubProfile != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (githubProfile != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (githubProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (githubProfile != null) "Connected as: @${githubUsername}" else "GitHub Disconnected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Status: $githubSyncStatus",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (githubProfile != null) {
                            TextButton(
                                onClick = { viewModel.disconnectGitHub() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }

                    // Configuration text fields
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("GitHub Token (PAT)") },
                        placeholder = { Text("ghp_*******************") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_github_token_input"),
                        singleLine = true,
                        visualTransformation = if (showTokenPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                                Icon(
                                    imageVector = if (showTokenPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle token visibility"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = inputRepo,
                        onValueChange = { inputRepo = it },
                        label = { Text("Repository Name (e.g. MyProject)") },
                        placeholder = { Text("sandbox-web-project") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_github_repo_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Book, null, modifier = Modifier.size(20.dp)) }
                    )

                    OutlinedTextField(
                        value = inputBranch,
                        onValueChange = { inputBranch = it },
                        label = { Text("Target Branch") },
                        placeholder = { Text("main") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_github_branch_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(20.dp)) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveGitHubSettings(
                                    inputToken.trim(),
                                    inputRepo.trim(),
                                    inputBranch.trim()
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("settings_save_github_btn"),
                            enabled = inputToken.isNotEmpty() && inputRepo.isNotEmpty() && !githubIsSyncing
                        ) {
                            if (githubIsSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing...")
                            } else {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save & Connect")
                            }
                        }
                    }
                }
            }

            // SECTION: EDITOR SETTINGS
            SettingsSectionHeader(title = "Code Editor Customizations", icon = Icons.Default.EditNote)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Editor Mode selector
                    Column {
                        Text(
                            text = "Editor Engine Integration",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Select your preferred terminal/web view technology",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Selection row 1: Monaco
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (editorEngine == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.saveEditorEngine(0) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Monaco Pro Web Engine", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Recommended. Full VS-Code interface, auto completion, color scheme.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = editorEngine == 0, onClick = { viewModel.saveEditorEngine(0) })
                        }

                        // Selection row 2: Built-in Basic
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (editorEngine == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.saveEditorEngine(1) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TextFields, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Built-in Basic Editor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Ultra lightweight editor with color highlight overlays.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = editorEngine == 1, onClick = { viewModel.saveEditorEngine(1) })
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Font Size layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Font Size", fontWeight = FontWeight.SemiBold)
                            Text("Adjust code viewing scale dynamically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (editorFontSize > 10) viewModel.saveEditorFontSize(editorFontSize - 1) }) {
                                Icon(Icons.Default.Remove, "Decrease size")
                            }
                            Text(
                                text = "$editorFontSize sp",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { if (editorFontSize < 32) viewModel.saveEditorFontSize(editorFontSize + 1) }) {
                                Icon(Icons.Default.Add, "Increase size")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Word wrapping layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Word Wrapping Rule", fontWeight = FontWeight.SemiBold)
                            Text("Auto wrap long text strings on horizontal boundaries", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = editorWordWrap,
                            onCheckedChange = { viewModel.saveEditorWordWrap(it) },
                            modifier = Modifier.testTag("settings_wordwrap_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Auto-Save Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Auto-Save", fontWeight = FontWeight.SemiBold)
                            Text("Automatically save code files when modified or closed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = editorAutoSave,
                            onCheckedChange = { viewModel.saveEditorAutoSave(it) },
                            modifier = Modifier.testTag("settings_autosave_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Themes layout
                    Column {
                        Text("Monaco Theme Style", fontWeight = FontWeight.SemiBold)
                        Text("Choose color scheme for deep coding sessions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themesList = listOf("Dark", "Light", "High Contrast")
                            themesList.forEachIndexed { idx, label ->
                                Button(
                                    onClick = { viewModel.saveEditorTheme(idx) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (editorTheme == idx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (editorTheme == idx) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    val icon = when(idx) {
                                        0 -> Icons.Default.Brightness4
                                        1 -> Icons.Default.Brightness7
                                        else -> Icons.Default.Contrast
                                    }
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: FILE PREFERENCES
            SettingsSectionHeader(title = "Directory Explorer Configs", icon = Icons.Default.Folder)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Hidden Files
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Hidden Items", fontWeight = FontWeight.SemiBold)
                            Text("Display files starting with a leading dot (.)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showHidden,
                            onCheckedChange = { viewModel.toggleShowHidden() },
                            modifier = Modifier.testTag("settings_hidden_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Default Storage drive selection
                    Column {
                        Text("Active Storage Source Path", fontWeight = FontWeight.SemiBold)
                        Text("Choose workspace directory drive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.switchStorageSource(StorageSource.SANDBOX) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (storageSource == StorageSource.SANDBOX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (storageSource == StorageSource.SANDBOX) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Inbox, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sandbox Workspace")
                            }

                            Button(
                                onClick = { viewModel.switchStorageSource(StorageSource.EXTERNAL) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (storageSource == StorageSource.EXTERNAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (storageSource == StorageSource.EXTERNAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shared Device Files")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Show File details sub row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show File Sub-Details", fontWeight = FontWeight.SemiBold)
                            Text("Display file size, subdirectory counts, and modified dates on file list items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showFileDetails,
                            onCheckedChange = { viewModel.saveShowFileDetails(it) },
                            modifier = Modifier.testTag("settings_details_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Default Directory sorting configuration
                    Column {
                        Text("Default Directory Sorting Mode", fontWeight = FontWeight.SemiBold)
                        Text("Select the primary sorting rule applied when browsing folders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var expandedSortDropdown by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expandedSortDropdown = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sorting: ${sortType.label}")
                            }
                            DropdownMenu(
                                expanded = expandedSortDropdown,
                                onDismissRequest = { expandedSortDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                com.example.viewmodel.SortType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.label) },
                                        onClick = {
                                            viewModel.updateSortType(type)
                                            expandedSortDropdown = false
                                        },
                                        leadingIcon = {
                                            val icon = when (type) {
                                                com.example.viewmodel.SortType.NAME_ASC -> Icons.Default.SortByAlpha
                                                com.example.viewmodel.SortType.NAME_DESC -> Icons.Default.SortByAlpha
                                                com.example.viewmodel.SortType.DATE_ASC -> Icons.Default.CalendarToday
                                                com.example.viewmodel.SortType.DATE_DESC -> Icons.Default.CalendarToday
                                                com.example.viewmodel.SortType.SIZE_ASC -> Icons.Default.BarChart
                                                com.example.viewmodel.SortType.SIZE_DESC -> Icons.Default.BarChart
                                            }
                                            Icon(icon, null, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: HISTORIES & SYSTEM CLEANUPS
            SettingsSectionHeader(title = "History & Memory Maintenance", icon = Icons.Default.DeleteSweep)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Edit History Logs", fontWeight = FontWeight.SemiBold)
                            Text("Remove all recent/starred workspace listings from app view", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("settings_clear_history_btn")
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rescan Workspace Files", fontWeight = FontWeight.SemiBold)
                            Text("Force indexing and refresh folder counts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { 
                            viewModel.refreshStorageMetrics()
                            viewModel.switchStorageSource(storageSource)
                        }) {
                            Icon(Icons.Default.Refresh, "Rescan disk", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // FOOTER BRANDING SECTION
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "OS File Manager & Editor Studio Pro",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version 1.5.0 Premium Ultimate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Real-time Sync • Monaco Integration • Safe Storage System",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    // CONFIRM DIALOGS
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete History Logs?") },
            text = { Text("This is irreversible and will remove all bookmarks and recents indexing lists. Your actual files on storage will NOT be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
