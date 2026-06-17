package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.viewmodel.FileManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeComponent(
    viewModel: FileManagerViewModel,
    rootDir: File,
    modifier: Modifier = Modifier,
    onItemLongClick: (FileItem) -> Unit = {}
) {
    val showHidden by viewModel.showHidden.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Global set of expanded folder paths
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // DRAG AND DROP FLOW STATE
    var dragItem by remember { mutableStateOf<FileItem?>(null) }
    var dragOffsetInContainer by remember { mutableStateOf(Offset.Zero) }
    var hoveredFolder by remember { mutableStateOf<FileItem?>(null) }
    
    // Tracks map of visible folder paths mapped to their container Rect bounds
    val folderPositions = remember { mutableStateMapOf<String, Pair<FileItem, Rect>>() }
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    // Folder Move confirmation dialog states
    var showConfirmMoveDialog by remember { mutableStateOf(false) }
    var itemToMove by remember { mutableStateOf<FileItem?>(null) }
    var destinationFolder by remember { mutableStateOf<FileItem?>(null) }

    // On startup, auto-expand the root folder
    LaunchedEffect(rootDir) {
        expandedPaths = expandedPaths + rootDir.absolutePath
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerCoordinates = it }
    ) {
        // High level toolbar controls inside the Tree Component
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Tree Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Interactive Tree View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Global expand/collapse helpers
                    Row {
                        TextButton(
                            onClick = {
                                expandedPaths = setOf(rootDir.absolutePath)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldLess,
                                contentDescription = "Collapse All",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Collapse", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        TextButton(
                            onClick = {
                                // Expand recursively (add active paths)
                                expandedPaths = expandedPaths + getRecursiveDirectories(rootDir, showHidden)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Expand All",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Expand All", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Lazy List representing our recursive structure
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 250.dp, max = 500.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // Start recursive Rendering from rootDir
                        val rootItem = FileItem(
                            name = if (rootDir.name.isEmpty()) "Root Storage" else rootDir.name,
                            path = rootDir.absolutePath,
                            isDirectory = true,
                            size = 0,
                            lastModified = rootDir.lastModified(),
                            isBookmarked = viewModel.isBookmarked(rootDir.absolutePath)
                        )

                        FileTreeNode(
                            item = rootItem,
                            depth = 0,
                            expandedPaths = expandedPaths,
                            searchQuery = searchQuery,
                            viewModel = viewModel,
                            onToggleExpand = { path ->
                                expandedPaths = if (expandedPaths.contains(path)) {
                                    expandedPaths - path
                                } else {
                                    expandedPaths + path
                                }
                            },
                            onItemLongClick = onItemLongClick,
                            containerCoordinates = containerCoordinates,
                            dragItem = dragItem,
                            dragOffsetInContainer = dragOffsetInContainer,
                            hoveredFolder = hoveredFolder,
                            folderPositions = folderPositions,
                            onDragStateChanged = { item, offset, hover ->
                                dragItem = item
                                dragOffsetInContainer = offset
                                hoveredFolder = hover
                            },
                            onMoveConfirmTriggered = { src, dest ->
                                itemToMove = src
                                destinationFolder = dest
                                showConfirmMoveDialog = true
                            }
                        )
                    }
                }
            }
        }

        // DRAGGING PREVIEW THUMBNAIL OVERLAY
        if (dragItem != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(LocalDensity.current) { dragOffsetInContainer.x.toDp() } + 16.dp,
                        y = with(LocalDensity.current) { dragOffsetInContainer.y.toDp() } - 20.dp
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (dragItem!!.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dragItem!!.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Move confirmation dialog
        if (showConfirmMoveDialog) {
            val src = itemToMove
            val dest = destinationFolder
            if (src != null && dest != null) {
                AlertDialog(
                    onDismissRequest = { showConfirmMoveDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = {
                        Text(
                            text = "Confirm File Move",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to move '${src.name}' into folder '${dest.name}'?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.moveItem(src, dest)
                                showConfirmMoveDialog = false
                            }
                        ) {
                            Text("Move")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showConfirmMoveDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeNode(
    item: FileItem,
    depth: Int,
    expandedPaths: Set<String>,
    searchQuery: String,
    viewModel: FileManagerViewModel,
    onToggleExpand: (String) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    containerCoordinates: androidx.compose.ui.layout.LayoutCoordinates?,
    dragItem: FileItem?,
    dragOffsetInContainer: Offset,
    hoveredFolder: FileItem?,
    folderPositions: MutableMap<String, Pair<FileItem, Rect>>,
    onDragStateChanged: (FileItem?, Offset, FileItem?) -> Unit,
    onMoveConfirmTriggered: (FileItem, FileItem) -> Unit
) {
    val isExpanded = expandedPaths.contains(item.path)
    val showHidden = viewModel.showHidden.collectAsState().value

    // Load directory listing on the fly
    val children = remember(item.path, showHidden, isExpanded) {
        if (item.isDirectory && isExpanded) {
            viewModel.getDirectoryContents(File(item.path))
        } else {
            emptyList()
        }
    }

    // Matching details
    val matchesSearch = remember(item.name, searchQuery) {
        searchQuery.isNotEmpty() && item.name.contains(searchQuery, ignoreCase = true)
    }

    val rotationDegrees by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "arrow_rotation"
    )

    // Layout coordinate tracking to update boundary map inside the relative container
    var nodeCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val nodeModifier = Modifier.onGloballyPositioned { coordinates ->
        nodeCoordinates = coordinates
        containerCoordinates?.let { container ->
            if (item.isDirectory) {
                if (coordinates.isAttached && container.isAttached) {
                    val topLeftInContainer = container.localPositionOf(coordinates, Offset.Zero)
                    val size = coordinates.size
                    val rect = Rect(
                        topLeftInContainer,
                        androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                    )
                    folderPositions[item.path] = Pair(item, rect)
                }
            }
        }
    }

    // Clean up folder position mappings when collapsed or removed from compositions
    DisposableEffect(item.path) {
        onDispose {
            folderPositions.remove(item.path)
        }
    }

    // Gestures detector mapping to handle items drag inputs
    val dragGestureModifier = Modifier.pointerInput(item, containerCoordinates, nodeCoordinates) {
        detectDragGesturesAfterLongPress(
            onDragStart = { localOffset ->
                containerCoordinates?.let { container ->
                    nodeCoordinates?.let { node ->
                        if (node.isAttached && container.isAttached) {
                            val startOffsetLocal = container.localPositionOf(node, localOffset)
                            onDragStateChanged(item, startOffsetLocal, null)
                        }
                    }
                }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                val newOffset = dragOffsetInContainer + dragAmount

                // Determine which target folder bounds currently contains the offset pointer
                var currentHovered: FileItem? = null
                for ((_, pair) in folderPositions) {
                    val (folder, rect) = pair
                    if (rect.contains(newOffset)) {
                        currentHovered = folder
                        break
                    }
                }

                // Verify the drop folder to avoid cycles (can't move onto self or parent folder)
                val finalHovered = if (currentHovered != null &&
                    currentHovered.path != item.path &&
                    currentHovered.path != File(item.path).parent
                ) {
                    currentHovered
                } else {
                    null
                }

                onDragStateChanged(item, newOffset, finalHovered)
            },
            onDragEnd = {
                if (dragItem != null && hoveredFolder != null) {
                    onMoveConfirmTriggered(dragItem, hoveredFolder)
                }
                onDragStateChanged(null, Offset.Zero, null)
            },
            onDragCancel = {
                onDragStateChanged(null, Offset.Zero, null)
            }
        )
    }

    Column {
        val isHoveredTarget = hoveredFolder?.path == item.path

        // Render Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(nodeModifier)
                .then(dragGestureModifier)
                .background(
                    when {
                        isHoveredTarget -> MaterialTheme.colorScheme.secondaryContainer
                        dragItem?.path == item.path -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        matchesSearch -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (isHoveredTarget) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else if (dragItem?.path == item.path) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (item.isDirectory) {
                            onToggleExpand(item.path)
                        } else {
                            if (item.isEditable) {
                                viewModel.openFileInEditor(File(item.path))
                            } else {
                                onItemLongClick(item)
                            }
                        }
                    },
                    onLongClick = {
                        onItemLongClick(item)
                    }
                )
                .padding(start = (depth * 16).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expansion Arrow (only for directories)
            if (item.isDirectory) {
                IconButton(
                    onClick = { onToggleExpand(item.path) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand toggle",
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(rotationDegrees),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            // File Type Specific Icon
            val iconDescriptor = getTreeIconForFile(item)
            Icon(
                imageVector = iconDescriptor.icon,
                contentDescription = null,
                tint = iconDescriptor.color,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Item Name Label
            Text(
                text = item.name,
                style = if (item.isDirectory) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyMedium,
                color = if (matchesSearch) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.0f)
            )

            // Star / Bookmark Badge Indicator
            if (item.isBookmarked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Starred",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .size(14.dp)
                        .padding(horizontal = 2.dp)
                )
            }
        }

        // Recursively render children if expanded
        if (item.isDirectory && isExpanded) {
            children.forEach { child ->
                FileTreeNode(
                    item = child,
                    depth = depth + 1,
                    expandedPaths = expandedPaths,
                    searchQuery = searchQuery,
                    viewModel = viewModel,
                    onToggleExpand = onToggleExpand,
                    onItemLongClick = onItemLongClick,
                    containerCoordinates = containerCoordinates,
                    dragItem = dragItem,
                    dragOffsetInContainer = dragOffsetInContainer,
                    hoveredFolder = hoveredFolder,
                    folderPositions = folderPositions,
                    onDragStateChanged = onDragStateChanged,
                    onMoveConfirmTriggered = onMoveConfirmTriggered
                )
            }
        }
    }
}

// Icon helper descriptor matching design codes
data class TreeIcon(val icon: ImageVector, val color: Color)

@Composable
fun getTreeIconForFile(item: FileItem): TreeIcon {
    return if (item.isDirectory) {
        TreeIcon(Icons.Filled.Folder, MaterialTheme.colorScheme.secondary)
    } else {
        val extension = item.extension
        val themeColors = MaterialTheme.colorScheme
        when (extension) {
            "kt", "kts", "java" -> TreeIcon(Icons.Filled.Code, Color(0xFF00C853)) // Vibrant emerald green for JVM/Android Code
            "py", "js", "ts", "swift", "cpp", "c", "h" -> TreeIcon(Icons.Default.IntegrationInstructions, Color(0xFF2979FF)) // Digital blue code
            "html", "xml", "css" -> TreeIcon(Icons.Default.Html, Color(0xFFFF9100)) // Ember orange
            "json", "yaml", "yml" -> TreeIcon(Icons.Default.SettingsApplications, Color(0xFF9C27B0)) // Purple configs
            "md", "markdown" -> TreeIcon(Icons.Default.EditNote, Color(0xFF00B0FF)) // Teal Doc
            "txt" -> TreeIcon(Icons.Default.Description, Color(0xFF78909C)) // Slate Grey Document
            "png", "jpg", "jpeg", "webp", "gif" -> TreeIcon(Icons.Default.Image, Color(0xFFFF4081)) // Hot neon pink images
            "mp3", "wav", "ogg" -> TreeIcon(Icons.Default.AudioFile, Color(0xFF00E5FF)) // Electric cyan Audio
            "zip", "tar", "gz" -> TreeIcon(Icons.Default.Grid3x3, Color(0xFFFFD600)) // Yellow Archive
            else -> TreeIcon(Icons.Outlined.InsertDriveFile, themeColors.onSurfaceVariant)
        }
    }
}

// Helper to calculate list of all directories recursively inside root folder for "Expand All"
fun getRecursiveDirectories(root: File, showHidden: Boolean): Set<String> {
    val result = mutableSetOf<String>()
    val files = root.listFiles() ?: return emptySet()
    for (file in files) {
        if (file.isDirectory) {
            if (showHidden || !file.name.startsWith(".")) {
                result.add(file.absolutePath)
                result.addAll(getRecursiveDirectories(file, showHidden))
            }
        }
    }
    return result
}
