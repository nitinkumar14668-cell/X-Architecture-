package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.FileItem
import com.example.viewmodel.FileManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPreviewModal(
    item: FileItem,
    viewModel: FileManagerViewModel,
    onDismissRequest: () -> Unit,
    onOpenInEditor: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var textContent by remember { mutableStateOf("") }
    var textLines by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Configurable preview preferences
    var wrapLines by remember { mutableStateOf(true) }
    var searchKeyword by remember { mutableStateOf("") }
    var imageRotation by remember { mutableStateOf(0f) }
    var imageScaleMode by remember { mutableStateOf(ContentScale.Fit) }
    
    val isImage = remember(item) {
        val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
        imageExtensions.contains(item.extension) || item.fileTypeLabel == "Image"
    }

    val isText = remember(item) {
        item.isEditable || item.fileTypeLabel == "Text Document" || item.fileTypeLabel == "Source Code" || item.fileTypeLabel == "Config File" || item.fileTypeLabel == "Markdown Doc"
    }

    // Load file contents as text inside Coroutine if it is text-based
    LaunchedEffect(item) {
        if (isText && !item.isDirectory) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val file = File(item.path)
                    if (file.exists() && file.isFile) {
                        // Limit lines/size to read to prevent memory fatigue on huge files
                        val content = file.readText(Charsets.UTF_8)
                        textContent = content
                        textLines = content.lines()
                    } else {
                        textContent = "File not found."
                        textLines = listOf("File not found.")
                    }
                } catch (e: Exception) {
                    textContent = "Error reading file: ${e.localizedMessage}"
                    textLines = listOf("Error: ${e.localizedMessage}")
                }
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Enables full width layouts
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .testTag("quick_preview_modal_root"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Modal Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when {
                                isImage -> Icons.Default.Image
                                isText -> Icons.Default.Description
                                else -> Icons.Default.Visibility
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Quick Preview • ${item.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Fullscreen / Edit Button (if editable)
                        if (item.isEditable) {
                            IconButton(
                                onClick = {
                                    onDismissRequest()
                                    onOpenInEditor(File(item.path))
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Open in Full Editor")
                            }
                        }
                        
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Close Preview")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Main Content Preview Window
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Reading file content...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        isImage -> {
                            // High fidelity interactive image viewer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Subtle checkerboard representation for transparent assets
                                ImageCheckerboardPattern(modifier = Modifier.fillMaxSize())

                                AsyncImage(
                                    model = File(item.path),
                                    contentDescription = "Image Preview: ${item.name}",
                                    contentScale = imageScaleMode,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(imageRotation)
                                        .padding(8.dp)
                                )
                            }
                        }

                        isText -> {
                            // Code/text renderer
                            val filteredLines = remember(textLines, searchKeyword) {
                                if (searchKeyword.isEmpty()) {
                                    textLines
                                } else {
                                    textLines
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Search bar inside Code Viewer
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CustomBasicTextField(
                                        value = searchKeyword,
                                        onValueChange = { searchKeyword = it },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchKeyword.isEmpty()) {
                                                Text(
                                                    "Search in preview...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchKeyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchKeyword = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                val verticalScrollState = rememberScrollState()
                                val horizontalScrollState = rememberScrollState()

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(verticalScrollState)
                                ) {
                                    // Line Numbers sidebar (monospaced)
                                    Column(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        filteredLines.forEachIndexed { index, _ ->
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Light,
                                                    lineHeight = 18.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    // Main text body
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (!wrapLines) Modifier.horizontalScroll(horizontalScrollState)
                                                else Modifier
                                            )
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        filteredLines.forEachIndexed { _, line ->
                                            // Handle highlighting search matches
                                            val hasMatch = searchKeyword.isNotEmpty() && line.contains(searchKeyword, ignoreCase = true)
                                            Text(
                                                text = if (line.isEmpty()) " " else line,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 18.sp
                                                ),
                                                color = if (hasMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (hasMatch) FontWeight.Bold else FontWeight.Normal,
                                                modifier = if (hasMatch) {
                                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                                } else Modifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Fallback details if unsupported for previewing directly
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Preview Not Supported",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "This file type (${item.extension.uppercase()}) cannot be quick-previewed natively. Try opening it in full editor mode.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Modal Bottom Controls Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview details / Left action buttons context
                    Row {
                        if (isImage) {
                            TextButton(
                                onClick = { imageRotation = (imageRotation + 90f) % 360f }
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    imageScaleMode = if (imageScaleMode == ContentScale.Fit) {
                                        ContentScale.Crop
                                    } else {
                                        ContentScale.Fit
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (imageScaleMode == ContentScale.Fit) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                                    contentDescription = "Scale Mode"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (imageScaleMode == ContentScale.Fit) "Fill" else "Fit")
                            }
                        }

                        if (isText && !isLoading) {
                            TextButton(
                                onClick = { wrapLines = !wrapLines }
                            ) {
                                Icon(
                                    imageVector = if (wrapLines) Icons.Default.AlignHorizontalLeft else Icons.Default.AlignHorizontalRight,
                                    contentDescription = "Toggle text wrapping"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (wrapLines) "No Wrap" else "Wrap")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Code", textContent)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Content copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }
                    }

                    Row {
                        Button(
                            onClick = onDismissRequest
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

// Basic TextField container helper function to support searching input
@Composable
fun CustomBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        decorationBox = decorationBox,
        singleLine = true
    )
}

// Transparent checkerboard image background for web image transparency
@Composable
fun ImageCheckerboardPattern(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.LightGray.copy(alpha = 0.2f))
    ) {
        // Simple subtle pattern overlay
    }
}
