package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import android.os.Environment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.FileItem
import com.example.ui.components.FileTreeComponent
import com.example.ui.components.QuickPreviewModal
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.SortType
import com.example.viewmodel.StorageSource
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(viewModel: FileManagerViewModel) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDir.collectAsState()
    val storageSource by viewModel.storageSource.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val filesList by viewModel.filesList.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val githubIsSyncing by viewModel.githubIsSyncing.collectAsState()
    val isAtRoot by viewModel.isAtRoot.collectAsState()
    val showFileDetails by viewModel.showFileDetails.collectAsState()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
    var itemDetailsPreview by remember { mutableStateOf<FileItem?>(null) }
    var itemToQuickPreview by remember { mutableStateOf<FileItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isTreeView by remember { mutableStateOf(false) }

    // Dialog Input states
    var inputNewName by remember { mutableStateOf("") }
    var inputNewExt by remember { mutableStateOf("txt") }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Main Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isAtRoot) {
                                viewModel.navigateToScreen(Screen.SETTINGS)
                            } else {
                                viewModel.navigateBack()
                            }
                        },
                        modifier = Modifier.testTag("navigate_back_button")
                    ) {
                        Icon(
                            imageVector = if (isAtRoot) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isAtRoot) "Open Settings Options" else "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "File Explorer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )

                    // Sort button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort Options")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label) },
                                    onClick = {
                                        viewModel.updateSortType(type)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortType == type) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = "Active")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Toggle Tree View Mode
                    IconButton(
                        onClick = { isTreeView = !isTreeView },
                        modifier = Modifier.testTag("toggle_tree_view_button")
                    ) {
                        Icon(
                            imageVector = if (isTreeView) Icons.Default.ViewList else Icons.Default.AccountTree,
                            contentDescription = if (isTreeView) "Switch to List View" else "Switch to Tree View",
                            tint = if (isTreeView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Toggle hidden files
                    IconButton(onClick = { viewModel.toggleShowHidden() }) {
                        Icon(
                            imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Show Hidden"
                        )
                    }
                }

                // Storage Source Selector Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val sandboxSelected = storageSource == StorageSource.SANDBOX
                    IconButtonWithLabel(
                        selected = sandboxSelected,
                        icon = Icons.Default.FolderSpecial,
                        label = "Sandbox Space",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.switchStorageSource(StorageSource.SANDBOX) }
                    )
                    IconButtonWithLabel(
                        selected = !sandboxSelected,
                        icon = Icons.Default.Storage,
                        label = "OS Device Files",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.switchStorageSource(StorageSource.EXTERNAL) }
                    )
                }

                // Scrollable Breadcrumbs
                BreadcrumbBar(
                    currentDir = currentDir,
                    storageSource = storageSource,
                    onNavigate = { viewModel.navigateToDirectory(it) }
                )

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("search_files_input"),
                    placeholder = { Text("Search files & folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                var isExpanded by remember { mutableStateOf(false) }

                if (isExpanded) {
                    FloatingActionButton(
                        onClick = {
                            inputNewName = ""
                            inputNewExt = "txt"
                            showCreateFileDialog = true
                            isExpanded = false
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("fab_create_file")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NoteAdd, "New File")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New File")
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            inputNewName = ""
                            showCreateFolderDialog = true
                            isExpanded = false
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("fab_create_folder")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreateNewFolder, "New Folder")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Folder")
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isExpanded = !isExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_options")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Expand Options"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Check Permissions warning dialog, if in External storage and not granted
            if (storageSource == StorageSource.EXTERNAL && !permissionsGranted) {
                PermissionRequestLayout(
                    onGrantTrigger = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            // On lower devices, MainActivity will trigger standard storage permissions
                            viewModel.showToast("Accept standard storage permissions when prompted")
                        }
                    }
                )
            } else {
                if (isTreeView) {
                    FileTreeComponent(
                        viewModel = viewModel,
                        rootDir = currentDir,
                        modifier = Modifier.fillMaxSize(),
                        onItemLongClick = { item ->
                            itemDetailsPreview = item
                        }
                    )
                } else {
                    // Normal Files Explorer Grid/List
                    if (filesList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = "Empty Folder",
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Empty Folder",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Create a new file or directory inside using the action button.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(filesList, key = { it.path }) { item ->
                                FileRowItem(
                                    item = item,
                                    showFileDetails = showFileDetails,
                                    onClick = {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(File(item.path))
                                        } else {
                                            if (item.isEditable) {
                                                viewModel.openFileInEditor(File(item.path))
                                            } else {
                                                // Inspect metadata / image viewer dialog
                                                itemDetailsPreview = item
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        itemDetailsPreview = item
                                    },
                                    onQuickPreview = {
                                        itemToQuickPreview = item
                                    },
                                    onToggleBookmark = {
                                        viewModel.toggleBookmark(item.path)
                                    },
                                    onRenameTrigger = {
                                        inputNewName = item.name
                                        itemToRename = item
                                    },
                                    onDeleteTrigger = {
                                        itemToDelete = item
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE FILE DIALOG
    if (showCreateFileDialog) {
        val extensionOptions = listOf("txt", "kt", "html", "md", "json", "xml", "css", "py")
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New File 📝") },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputNewName,
                        onValueChange = { inputNewName = it },
                        label = { Text("File Name (No Extension)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (inputNewName.isNotEmpty()) {
                                viewModel.createNewFile("$inputNewName.$inputNewExt")
                                showCreateFileDialog = false
                            }
                        })
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select Template Extension:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow {
                        items(extensionOptions) { ext ->
                            FilterChip(
                                selected = inputNewExt == ext,
                                onClick = { inputNewExt = ext },
                                label = { Text(".$ext") },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputNewName.trim().isNotEmpty()) {
                            val defaultContent = when (inputNewExt) {
                                "kt" -> "package com.example\n\nfun main() {\n    println(\"Hello World\")\n}"
                                "html" -> "<html>\n<head>\n    <title>My Webpage</title>\n</head>\n<body>\n    <h1>New Page Created Natively</h1>\n</body>\n</html>"
                                "json" -> "{\n  \"status\": \"success\",\n  \"code\": 200\n}"
                                "md" -> "# Draft Markdown Document\n\nEnter text notes..."
                                else -> ""
                            }
                            viewModel.createNewFile("${inputNewName.trim()}.$inputNewExt", defaultContent)
                            showCreateFileDialog = false
                        } else {
                            viewModel.showToast("Name cannot be empty")
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CREATE FOLDER DIALOG
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder 📁") },
            text = {
                OutlinedTextField(
                    value = inputNewName,
                    onValueChange = { inputNewName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (inputNewName.isNotEmpty()) {
                            viewModel.createNewFolder(inputNewName.trim())
                            showCreateFolderDialog = false
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputNewName.trim().isNotEmpty()) {
                        viewModel.createNewFolder(inputNewName.trim())
                        showCreateFolderDialog = false
                    } else {
                        viewModel.showToast("Name cannot be empty")
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // RENAME DIALOG
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Path") },
            text = {
                OutlinedTextField(
                    value = inputNewName,
                    onValueChange = { inputNewName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputNewName.trim().isNotEmpty()) {
                        viewModel.renameItem(item, inputNewName.trim())
                        itemToRename = null
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }

    // DELETE CONFIRM DIALOG
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Confirm Deletion ⚠️") },
            text = { Text("Are you absolutely sure you want to permanently delete '${item.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // METADATA & DETAILS PREVIEW DIALOG
    itemDetailsPreview?.let { item ->
        Dialog(onDismissRequest = { itemDetailsPreview = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = getFileIcon(item),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.fileTypeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Metadata Table
                    DetailRow("Path:", item.path)
                    DetailRow("Size:", item.formattedSize)
                    DetailRow("Last Modified:", item.formattedDate)
                    DetailRow("Is Folder:", if (item.isDirectory) "Yes" else "No")
                    DetailRow("Editable Text:", if (item.isEditable) "Yes" else "No")

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // --- GitHub Actions ---
                    if (githubToken.isNotEmpty() && githubRepo.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (item.isDirectory) {
                                    viewModel.pushFolderToGitHub(java.io.File(item.path))
                                } else {
                                    viewModel.pushSingleFileToGitHub(java.io.File(item.path))
                                }
                                itemDetailsPreview = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            enabled = !githubIsSyncing
                        ) {
                            Icon(
                                imageVector = com.example.ui.components.getTreeIconForFile(item).icon,
                                contentDescription = "Push",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (item.isDirectory) "Push Folder to GitHub" else "Push File to GitHub", fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        OutlinedButton(
                            onClick = {
                                viewModel.showToast("Setup GitHub details in the Analytics screen first!")
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Configure GitHub inside Analytics Screen", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (!item.isDirectory) {
                        Button(
                            onClick = {
                                itemToQuickPreview = item
                                itemDetailsPreview = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Quick Preview"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quick Preview Content")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Quick Action triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = {
                            viewModel.toggleBookmark(item.path)
                        }) {
                            Icon(
                                imageVector = if (item.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Favorite"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (item.isBookmarked) "Unstar" else "Star")
                        }
                        
                        Button(onClick = { itemDetailsPreview = null }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    // QUICK PREVIEW DIALOG
    itemToQuickPreview?.let { item ->
        QuickPreviewModal(
            item = item,
            viewModel = viewModel,
            onDismissRequest = { itemToQuickPreview = null },
            onOpenInEditor = { file -> viewModel.openFileInEditor(file) }
        )
    }
}

@Composable
fun IconButtonWithLabel(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentDir: File,
    storageSource: StorageSource,
    onNavigate: (File) -> Unit
) {
    val items = remember(currentDir, storageSource) {
        val pathList = mutableListOf<File>()
        var path: File? = currentDir
        while (path != null) {
            pathList.add(0, path)
            
            // Limit upstream traversal if sandbox to avoid escaping app bounds in sandbox tab
            if (storageSource == StorageSource.SANDBOX && path.name == "sandbox") {
                break
            }
            // Similarly, keep the external shared directory as visual start if desired
            if (storageSource == StorageSource.EXTERNAL && path.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                break
            }
            
            path = path.parentFile
        }
        pathList
    }

    val listState = rememberLazyListState()
    
    // Auto-scroll to the end of the breadcrumb list when directory changes
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.size - 1)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(vertical = 8.dp)
            .testTag("breadcrumb_lazy_row"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(items) { index, file ->
            val isLast = index == items.size - 1
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent
                    )
                    .clickable { onNavigate(file) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("breadcrumb_item_$index")
            ) {
                if (index == 0) {
                    Icon(
                        imageVector = if (storageSource == StorageSource.SANDBOX) Icons.Default.Inbox else Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                val nodeName = if (index == 0) {
                    if (storageSource == StorageSource.SANDBOX) "Sandbox" else "Shared Storage"
                } else {
                    file.name
                }

                Text(
                    text = nodeName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            if (index < items.size - 1) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Separator",
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    showFileDetails: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickPreview: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRenameTrigger: () -> Unit,
    onDeleteTrigger: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getFileIcon(item),
            contentDescription = null,
            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showFileDetails) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bookmark Trigger
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (item.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Favorite",
                tint = if (item.isBookmarked) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Action dropdown menu
        Box {
            IconButton(onClick = { expandedMenu = true }) {
                Icon(Icons.Default.MoreVert, "More actions")
            }
            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Details & Properties") },
                    onClick = {
                        expandedMenu = false
                        onLongClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Info, null) }
                )
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Quick Preview 🔍") },
                        onClick = {
                            expandedMenu = false
                            onQuickPreview()
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        expandedMenu = false
                        onRenameTrigger()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expandedMenu = false
                        onDeleteTrigger()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (label.startsWith("Path")) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (label.startsWith("Path")) 12.sp else 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PermissionRequestLayout(onGrantTrigger: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Storage Access Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "To manage system directories, edit documents, and analyze total partition spaces, this app requires standard Android Shared Storage permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantTrigger,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("grant_permissions_button")
            ) {
                Icon(Icons.Default.VerifiedUser, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Directory Access Now")
            }
        }
    }
}

fun getFileIcon(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Filled.Folder
    return when (item.extension) {
        "kt", "kts", "java", "py", "pyw", "js", "ts", "c", "cpp", "h", "swift", "sh" -> Icons.Filled.Code
        "html", "xml", "css" -> Icons.Filled.Html
        "json", "yaml", "yml" -> Icons.Filled.Settings
        "md", "markdown" -> Icons.Default.MenuBook
        "txt" -> Icons.Default.Description
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Filled.Image
        "mp3", "wav", "ogg", "flac" -> Icons.Filled.AudioFile
        "mp4", "mkv", "avi" -> Icons.Filled.VideoFile
        "pdf" -> Icons.Filled.PictureAsPdf
        "zip", "tar", "gz" -> Icons.Filled.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}
