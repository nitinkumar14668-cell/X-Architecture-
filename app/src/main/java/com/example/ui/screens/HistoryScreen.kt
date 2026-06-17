package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.Screen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: FileManagerViewModel) {
    val bookmarkedPaths by viewModel.bookmarkedPaths.collectAsState()
    val recentPaths by viewModel.recentPaths.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Bookmarks, 1 = Recent History

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "History & Saved",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    if (activeSubTab == 1 && recentPaths.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Recents", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Sub options tab
                TabRow(selectedTabIndex = activeSubTab) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, "Bookmarks", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Starred (${bookmarkedPaths.size})")
                        }}
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, "Recents", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recents (${recentPaths.size})")
                        }}
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
            if (activeSubTab == 0) {
                // BOOKMARKS SUB TAB
                if (bookmarkedPaths.isEmpty()) {
                    EmptyHistoryState(
                        icon = Icons.Outlined.StarOutline,
                        title = "No Bookmarks Saved",
                        description = "Star directories, documentation notes, or configs on the explorer panel to catalog them here for instant switching."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(bookmarkedPaths) { path ->
                            val file = File(path)
                            BookmarkHistoryRow(
                                name = file.name,
                                path = path,
                                isDir = file.isDirectory,
                                onActionClick = { 
                                    if (file.isDirectory) {
                                        viewModel.navigateToDirectory(file)
                                        viewModel.navigateToScreen(Screen.EXPLORER)
                                    } else {
                                        viewModel.openFileInEditor(file)
                                        viewModel.navigateToScreen(Screen.EDITOR)
                                    }
                                },
                                onRemoveStar = {
                                    viewModel.toggleBookmark(path)
                                }
                            )
                        }
                    }
                }
            } else {
                // RECENTS SUB TAB
                if (recentPaths.isEmpty()) {
                    EmptyHistoryState(
                        icon = Icons.Outlined.HistoryToggleOff,
                        title = "No Recents Tracked",
                        description = "Editable files launched in the code tab will automatically leaves entries here so you don't lose track of active documents."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentPaths) { path ->
                            val file = File(path)
                            BookmarkHistoryRow(
                                name = file.name,
                                path = path,
                                isDir = false,
                                showStar = false,
                                onActionClick = {
                                    if (file.exists() && file.isFile) {
                                        viewModel.openFileInEditor(file)
                                        viewModel.navigateToScreen(Screen.EDITOR)
                                    } else {
                                        viewModel.showToast("File no longer exists or moved")
                                    }
                                },
                                onRemoveStar = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkHistoryRow(
    name: String,
    path: String,
    isDir: Boolean,
    showStar: Boolean = true,
    onActionClick: () -> Unit,
    onRemoveStar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActionClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDir) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showStar) {
                IconButton(onClick = onRemoveStar) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Remove Star",
                        tint = Color(0xFFFFD54F)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
