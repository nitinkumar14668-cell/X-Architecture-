package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.editor.CodeSyntaxHighlighter
import com.example.viewmodel.FileManagerViewModel
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: FileManagerViewModel) {
    val activeFile by viewModel.activeFile.collectAsState()
    val content by viewModel.activeFileContent.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val githubIsSyncing by viewModel.githubIsSyncing.collectAsState()

    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    val fontSize by viewModel.editorFontSize.collectAsState()
    val wordWrap by viewModel.editorWordWrap.collectAsState()
    val editorEngine by viewModel.editorEngine.collectAsState()
    val editorTheme by viewModel.editorTheme.collectAsState()
    val editorAutoSave by viewModel.editorAutoSave.collectAsState()

    // Auto-save logic: Debounces for 2.5 seconds before auto-saving
    LaunchedEffect(content, editorAutoSave) {
        if (editorAutoSave && isModified) {
            kotlinx.coroutines.delay(2500)
            viewModel.saveActiveFile()
        }
    }

    // Tab state: 0 = Code Edit, 1 = Preview/Render (only if Markdown or HTML)
    var selectedTab by remember { mutableStateOf(0) }

    val fileExtension = remember(activeFile) {
        activeFile?.name?.substringAfterLast('.', "")?.lowercase() ?: ""
    }

    val isPreviewable = remember(fileExtension) {
        fileExtension == "md" || fileExtension == "markdown" || fileExtension == "html" || fileExtension == "htm"
    }

    LaunchedEffect(activeFile) {
        selectedTab = 0 // Default to edit on opening file
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeFile != null) {
                            "${activeFile?.name}${if (isModified) " *" else ""}"
                        } else {
                            "Code Editor"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (activeFile != null) {
                        // Find & Replace trigger
                        IconButton(onClick = { showFindReplace = !showFindReplace }) {
                            Icon(Icons.Default.FindReplace, contentDescription = "Find & Replace")
                        }

                        // Text Settings Dialog trigger / menu
                        var showSettingsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(Icons.Default.TextFormat, contentDescription = "Font properties")
                            }
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Font Size: $fontSize sp") },
                                    onClick = {},
                                    leadingIcon = { Icon(Icons.Default.FormatSize, null) },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { if (fontSize > 10) viewModel.saveEditorFontSize(fontSize - 1) }) {
                                                Icon(Icons.Default.Remove, "Decrease")
                                            }
                                            IconButton(onClick = { if (fontSize < 30) viewModel.saveEditorFontSize(fontSize + 1) }) {
                                                Icon(Icons.Default.Add, "Increase")
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Word Wrap: ${if (wordWrap) "ON" else "OFF"}") },
                                    onClick = { viewModel.saveEditorWordWrap(!wordWrap) },
                                    leadingIcon = { Icon(Icons.Default.WrapText, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Editor: ${if (editorEngine == 0) "Monaco Pro" else "Built-in Text"}") },
                                    onClick = { 
                                         viewModel.saveEditorEngine(if (editorEngine == 0) 1 else 0)
                                         showSettingsMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.SettingsSuggest, null) },
                                    trailingIcon = {
                                         Icon(
                                             imageVector = if (editorEngine == 0) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                             contentDescription = "Toggle editor engine",
                                             tint = if (editorEngine == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                         )
                                    }
                                )
                            }
                        }

                        // Save file
                        Button(
                            onClick = { viewModel.saveActiveFile() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isModified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_file_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }

                        if (githubToken.isNotEmpty() && githubRepo.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    viewModel.saveActiveFile()
                                    activeFile?.let { file ->
                                        viewModel.pushSingleFileToGitHub(file)
                                    }
                                },
                                enabled = !githubIsSyncing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Save & Push to GitHub",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close editor
                        IconButton(onClick = { viewModel.closeActiveFile() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close File")
                        }
                    }
                }

                // Sub header selection tabs (Code Editor vs Markdown/HTML previews)
                if (activeFile != null && isPreviewable) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Code Code")
                            }}
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, "Preview", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (fileExtension == "md" || fileExtension == "markdown") "MD Render" else "Web Live")
                            }}
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeFile == null) {
                // Empty state if no file is opened
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "No Open File",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Document Opened",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Navigate to the 'File Explorer' tab, and tap any supported code or text file (.txt, .kt, .html, .md, .json) to load and edit its source here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Find & Replace retractable panel
                    AnimatedVisibility(
                        visible = showFindReplace,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = findText,
                                        onValueChange = { findText = it },
                                        placeholder = { Text("Find expression...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 6.dp)
                                            .testTag("editor_find_input"),
                                        textStyle = TextStyle(fontSize = 13.sp)
                                    )
                                    OutlinedTextField(
                                        value = replaceText,
                                        onValueChange = { replaceText = it },
                                        placeholder = { Text("Replace with...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 6.dp)
                                            .testTag("editor_replace_input"),
                                        textStyle = TextStyle(fontSize = 13.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        if (findText.isNotEmpty()) {
                                            val index = content.indexOf(findText, ignoreCase = true)
                                            if (index >= 0) {
                                                val before = content.substring(0, index)
                                                val after = content.substring(index + findText.length)
                                                viewModel.updateEditorContent(before + replaceText + after)
                                                viewModel.showToast("Replaced one occurrence")
                                            } else {
                                                viewModel.showToast("Query expression not found")
                                            }
                                        }
                                    }) {
                                        Text("Replace Next")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = {
                                        if (findText.isNotEmpty()) {
                                            if (content.contains(findText, ignoreCase = true)) {
                                                val replaced = content.replace(findText, replaceText, ignoreCase = true)
                                                viewModel.updateEditorContent(replaced)
                                                viewModel.showToast("Replaced all occurrences successfully!")
                                            } else {
                                                viewModel.showToast("Query expression not found")
                                            }
                                        }
                                    }) {
                                        Text("Replace All")
                                    }
                                }
                            }
                        }
                    }

                    // Content Section based on selected tab
                    if (selectedTab == 0) {
                        // CODE EDIT PANEL
                        if (editorEngine == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(8.dp)
                            ) {
                                MonacoEditorWebView(
                                    content = content,
                                    fileExtension = fileExtension,
                                    fontSize = fontSize,
                                    wordWrap = wordWrap,
                                    editorTheme = editorTheme,
                                    onContentChanged = { viewModel.updateEditorContent(it) },
                                    modifier = Modifier.fillMaxSize().testTag("code_editor_monaco_webview")
                                )
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                                // Line numbering indicator column (drawn only on text scroll)
                                val scrollState = rememberScrollState()
                                
                                // Let's split content by newlines to count and print lines
                                val lines = remember(content) {
                                    val split = content.split('\n')
                                    if (split.isEmpty()) listOf("") else split
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(42.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .verticalScroll(scrollState)
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                        for (i in 1..lines.size) {
                                            Text(
                                                text = "$i",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (fontSize - 1).sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                lineHeight = (fontSize + 6).sp
                                            )
                                        }
                                    }
                                }

                                // The Editor text area
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(scrollState)
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    val syntaxHighlighter = remember(fileExtension) {
                                        CodeSyntaxHighlighter(fileExtension)
                                    }

                                    BasicTextField(
                                        value = content,
                                        onValueChange = { viewModel.updateEditorContent(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("code_editor_textarea"),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = fontSize.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = (fontSize + 6).sp
                                        ),
                                        visualTransformation = syntaxHighlighter,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                        
                        // Small words index stats bar
                        FileStatsFooter(text = content)
                    } else {
                        // INTERACTIVE PREVIEWS (Markdown or WebView)
                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            if (fileExtension == "md" || fileExtension == "markdown") {
                                // Native MD Previews scroll state
                                MarkdownNativePreview(content = content)
                            } else {
                                // WebView Simulator for HTML/XML
                                HtmlWebViewPreview(htmlContent = content)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileStatsFooter(text: String) {
    val characters = text.length
    val words = remember(text) {
        if (text.isBlank()) 0 else text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }
    val lines = remember(text) {
        text.count { it == '\n' } + 1
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Lines: $lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Words: $words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Characters: $characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Custom Markdown Renderer in Pure Jetpack Compose
@Composable
fun MarkdownNativePreview(content: String) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        val lines = content.split('\n')
        var inCodeBlock = false
        var currentCodeBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Render code block card
                    CodeBlockCard(code = currentCodeBlock.toString().trim())
                    currentCodeBlock = StringBuilder()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                currentCodeBlock.append(line).append('\n')
                continue
            }

            // Standard Markdown components
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.substring(2),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.substring(3),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.substring(4),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                trimmed.startsWith("> ") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .border(width = 4.dp, color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = trimmed.substring(2),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Default.CheckBoxOutlineBlank, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(trimmed.substring(6), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("* [x] ") -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Default.CheckBox, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(trimmed.substring(6), style = MaterialTheme.typography.bodyMedium, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, start = 4.dp, end = 12.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.secondary, androidx.compose.foundation.shape.CircleShape)
                        )
                        Text(trimmed.substring(2), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                else -> {
                    // Regular paragraph, process basic formatting indices like bold/italics
                    Text(
                        text = renderStyledParagraph(trimmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(code: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Source Sandbox File",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF58BBF2),
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF58BBF2).copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFCDD6F4),
                lineHeight = 18.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

// Basic markdown bold (*some*) index builder helper
fun renderStyledParagraph(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*)(.*?)\\1|(`)(.*?)\\3")
        regex.findAll(text).forEach { match ->
            // Print text before match
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val isBold = match.value.startsWith("**")
            val rawTerm = if (isBold) match.groupValues[2] else match.groupValues[4]
            
            withStyle(
                style = if (isBold) {
                    SpanStyle(fontWeight = FontWeight.Bold, color = Color.Unspecified)
                } else {
                    SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f), color = Color(0xFFEA80FC))
                }
            ) {
                append(rawTerm)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

// In-app HTML rendering WebView panel
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebViewPreview(htmlContent: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    )
}

// --- MONACO WEB EDITOR AND SUPPORTING COMPOSABLES ---

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditorWebView(
    content: String,
    fileExtension: String,
    fontSize: Int,
    wordWrap: Boolean,
    editorTheme: Int,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val monacoLanguage = remember(fileExtension) { getMonacoLanguage(fileExtension) }

    // Keep track of the active webView instance
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val themeName = remember(editorTheme) {
        when (editorTheme) {
            1 -> "vs"
            2 -> "hc-black"
            else -> "vs-dark"
        }
    }

    // Encapsulate content inside a base64 string to safely pass it
    val base64Content = remember(content) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    // Update editor whenever parameters change
    LaunchedEffect(base64Content, monacoLanguage, fontSize, wordWrap) {
        webViewRef?.let { webView ->
            val jsCall = "setEditorContentBase64('$base64Content', '$monacoLanguage', $fontSize, $wordWrap)"
            webView.evaluateJavascript(jsCall, null)
        }
    }

    // Dynamic theme changer
    LaunchedEffect(themeName) {
        webViewRef?.let { webView ->
            webView.evaluateJavascript("if (typeof monaco !== 'undefined') { monaco.editor.setTheme('$themeName'); }", null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                
                // Set up bridge
                addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun onContentChanged(newContent: String) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onContentChanged(newContent)
                        }
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        webViewRef = view
                        val jsCall = "setEditorContentBase64('$base64Content', '$monacoLanguage', $fontSize, $wordWrap)"
                        view?.evaluateJavascript(jsCall, null)
                        // Ensure theme is set correctly after load
                        view?.evaluateJavascript("if (typeof monaco !== 'undefined') { monaco.editor.setTheme('$themeName'); }", null)
                    }
                }
                
                // Load HTML template
                loadDataWithBaseURL("https://localhost", getMonacoHtmlTemplate(themeName), "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (webViewRef == null) {
                webViewRef = webView
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    )
}

fun getMonacoLanguage(extension: String): String {
    return when(extension.lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "html", "htm" -> "html"
        "xml" -> "xml"
        "css" -> "css"
        "json" -> "json"
        "md", "markdown" -> "markdown"
        "sql" -> "sql"
        "sh", "bash" -> "shell"
        "yml", "yaml" -> "yaml"
        "c", "cpp", "h" -> "cpp"
        "swift" -> "swift"
        else -> "plaintext"
    }
}

private fun getMonacoHtmlTemplate(themeName: String): String {
    val bgColor = if (themeName == "vs") "#ffffff" else "#121212"
    val textColor = if (themeName == "vs") "#000000" else "#ffffff"
    val spinnerBorder = if (themeName == "vs") "rgba(0,0,0,.1)" else "rgba(255,255,255,.1)"
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                html, body, #editor {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background-color: $bgColor;
                }
                #progress {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    color: $textColor;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    font-size: 14px;
                    text-align: center;
                    z-index: 1000;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }
                .spinner {
                    border: 3px solid $spinnerBorder;
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    border-left-color: #6200EE;
                    animation: spin 1s linear infinite;
                    margin-bottom: 8px;
                }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
            </style>
            <!-- Load Monaco Editor loader.js -->
            <script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.39.0/min/vs/loader.min.js"></script>
        </head>
        <body>
            <div id="progress">
                <div class="spinner"></div>
                <div style="font-weight: bold; font-size: 14px; margin-top:8px;">Launching Monaco Pro Studio...</div>
                <div style="opacity: 0.7; font-size: 11px; margin-top:12px; max-width: 80%; line-height: 1.4;">Requires active internet connection. If it does not load, switch the <b>Editor Engine style</b> to <b>Built-in Text Mode</b> in App Settings for full offline syntax highlighting.</div>
            </div>
            <div id="editor"></div>
            <script>
                var editor;
                var isEditorReady = false;
                var pendingContent = null;
                var pendingLanguage = "plaintext";
                var pendingFontSize = 14;
                var pendingWordWrap = "on";

                require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.39.0/min/vs' } });
                require(['vs/editor/editor.main'], function() {
                    document.getElementById('progress').style.display = 'none';

                    editor = monaco.editor.create(document.getElementById('editor'), {
                        value: "",
                        language: pendingLanguage,
                        theme: '$themeName',
                        automaticLayout: true,
                        fontSize: pendingFontSize,
                        wordWrap: pendingWordWrap,
                        minimap: { enabled: false },
                        lineNumbersMinChars: 3,
                        scrollBeyondLastLine: false,
                        roundedSelection: true,
                        scrollbar: {
                            vertical: 'visible',
                            horizontal: 'visible'
                        }
                    });

                    isEditorReady = true;

                    if (pendingContent !== null) {
                        editor.setValue(pendingContent);
                        pendingContent = null;
                    }

                    editor.onDidChangeModelContent(function(e) {
                        var content = editor.getValue();
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onContentChanged(content);
                        }
                    });
                });

                function setEditorContentBase64(base64Str, language, fontSize, wordWrap) {
                    var binary = window.atob(base64Str);
                    var bytes = new Uint8Array(binary.length);
                    for (var i = 0; i < binary.length; i++) {
                        bytes[i] = binary.charCodeAt(i);
                    }
                    var decoded = new TextDecoder('utf-8').decode(bytes);
                    var wrapStr = wordWrap ? "on" : "off";

                    if (isEditorReady && editor) {
                        var normalizedDecoded = decoded.replace(/\r\n/g, '\n');
                        var normalizedCurrent = editor.getValue().replace(/\r\n/g, '\n');
                        if (normalizedCurrent !== normalizedDecoded) {
                            editor.setValue(decoded);
                        }
                        editor.updateOptions({
                            fontSize: fontSize,
                            wordWrap: wrapStr
                        });
                        var model = editor.getModel();
                        if (model) {
                            monaco.editor.setModelLanguage(model, language);
                        }
                    } else {
                        pendingContent = decoded;
                        pendingLanguage = language;
                        pendingFontSize = fontSize;
                        pendingWordWrap = wrapStr;
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
