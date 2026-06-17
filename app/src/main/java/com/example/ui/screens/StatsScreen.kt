package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.StorageSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: FileManagerViewModel) {
    val totalBytes by viewModel.totalBytes.collectAsState()
    val freeBytes by viewModel.freeBytes.collectAsState()
    val usedBytes by viewModel.usedBytes.collectAsState()
    val categoryAnalysis by viewModel.categoryAnalysis.collectAsState()
    val storageSource by viewModel.storageSource.collectAsState()

    val scrollState = rememberScrollState()

    // Helper to format bytes to human size
    val formatBytes = { size: Long ->
        if (size <= 0) "0 B" else {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }

    val usedPercentage = remember(totalBytes, usedBytes) {
        if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
    }

    val analyzedSpaceSum = remember(categoryAnalysis) {
        categoryAnalysis.values.sum()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Analytics 📊", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshStorageMetrics() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh statistics")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Source indicator chips
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (storageSource == StorageSource.SANDBOX) Icons.Filled.CloudQueue else Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (storageSource == StorageSource.SANDBOX) "Analyzing Workspace: Sandbox App folder" else "Analyzing Shared OS Memory Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Based on filesystem root query directories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Visual Pie Indicator Arc
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.surfaceVariant
                val sweepAngle = (usedPercentage / 100f) * 360f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw outer background empty circle track
                    drawCircle(
                        color = outlineColor,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw filled active sector overlay arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(primaryColor, Color(0xFF26C6DA), primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f%%", usedPercentage),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Memory Filled",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Sector stats Cards Grid (3 cards row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageSectorCard(
                    title = "Total Space",
                    value = formatBytes(totalBytes),
                    icon = Icons.Outlined.Storage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StorageSectorCard(
                    title = "Used Space",
                    value = formatBytes(usedBytes),
                    icon = Icons.Outlined.PieChart,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StorageSectorCard(
                    title = "Free Space",
                    value = formatBytes(freeBytes),
                    icon = Icons.Outlined.OfflinePin,
                    color = Color(0xFF81C784),
                    modifier = Modifier.weight(1f)
                )
            }

            // Category classification title header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "File Categories Breakdown 📁",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sum: ${formatBytes(analyzedSpaceSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            // Progress List by Extension Category
            if (categoryAnalysis.isEmpty() || analyzedSpaceSum == 0L) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No analyzed files listed to calculate. Create docs to populate metrics.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        categoryAnalysis.entries.sortedByDescending { it.value }.forEach { entry ->
                            val catSize = entry.value
                            val catName = entry.key
                            val proportion = if (analyzedSpaceSum <= 0L) 0f else catSize.toFloat() / analyzedSpaceSum.toFloat()

                            // Let's draw category row item
                            if (catSize > 0L) {
                                CategoryMetricsRow(
                                    name = catName,
                                    formattedSize = formatBytes(catSize),
                                    percentage = proportion * 100f,
                                    barProgress = proportion,
                                    color = getCategoryColor(catName)
                                )
                            }
                        }
                    }
                }
            }

            // 🚀 --- GITHUB INTEGRATION DASHBOARD ---
            Spacer(modifier = Modifier.height(16.dp))

            val githubToken by viewModel.githubToken.collectAsState()
            val githubUsername by viewModel.githubUsername.collectAsState()
            val githubRepo by viewModel.githubRepo.collectAsState()
            val githubBranch by viewModel.githubBranch.collectAsState()
            val githubProfile by viewModel.githubProfile.collectAsState()
            val githubReposList by viewModel.githubReposList.collectAsState()
            val githubIsSyncing by viewModel.githubIsSyncing.collectAsState()
            val githubLatestLogs by viewModel.githubLatestLogs.collectAsState()
            val githubSyncStatus by viewModel.githubSyncStatus.collectAsState()

            var inputToken by remember { mutableStateOf(githubToken) }
            var inputRepo by remember { mutableStateOf(githubRepo) }
            var inputBranch by remember { mutableStateOf(githubBranch) }
            var showTokenPassword by remember { mutableStateOf(false) }

            // Dynamic tracking list for input parameters on data change
            LaunchedEffect(githubToken, githubRepo, githubBranch) {
                inputToken = githubToken
                inputRepo = githubRepo
                inputBranch = githubBranch
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header title row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "GitHub Deploy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub Space Connector 🛸",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Status: $githubSyncStatus",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (githubToken.isNotEmpty() && !githubSyncStatus.contains("Error")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (githubIsSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (githubToken.isNotEmpty()) {
                        // --- CONNECTED ACCOUNT LAYOUT ---
                        // Profile summary block
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Default GitHub style graphic icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Avatar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = githubProfile?.displayName ?: githubUsername.ifEmpty { "Connected User" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Target Repository: $githubRepo ($githubBranch)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dynamic Repository Dropdown/Selector if loaded
                        if (githubReposList.isNotEmpty()) {
                            var showRepoDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showRepoDropdown = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Source, contentDescription = "Repos")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Change Repo: $inputRepo", overflow = TextOverflow.Ellipsis, maxLines = 1)
                                }
                                DropdownMenu(
                                    expanded = showRepoDropdown,
                                    onDismissRequest = { showRepoDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    githubReposList.forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text(r) },
                                            onClick = {
                                                inputRepo = r
                                                viewModel.saveGitHubSettings(githubToken, r, githubBranch)
                                                showRepoDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Core Operations Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.syncEntireWorkspace() },
                                modifier = Modifier.weight(1.5f),
                                enabled = !githubIsSyncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync all")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Sandbox App", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.disconnectGitHub() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Disconnect")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }

                    } else {
                        // --- CONFIGURATION INPUT FORM ---
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Setup your GitHub credentials below to push or sync workspace codes directly from Sandbox memory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. Token Input
                            OutlinedTextField(
                                value = inputToken,
                                onValueChange = { inputToken = it.trim() },
                                label = { Text("Personal Access Token (PAT)") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                                        Icon(
                                            imageVector = if (showTokenPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle text"
                                        )
                                    }
                                },
                                visualTransformation = if (showTokenPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // 2. Repo Name Input
                            OutlinedTextField(
                                value = inputRepo,
                                onValueChange = { inputRepo = it.trim() },
                                label = { Text("Repository Name (e.g. MyProject)") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                singleLine = true,
                                placeholder = { Text("my-github-repo") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // 3. Branch Input
                            OutlinedTextField(
                                value = inputBranch,
                                onValueChange = { inputBranch = it.trim() },
                                label = { Text("Target Branch") },
                                leadingIcon = { Icon(Icons.Default.AltRoute, contentDescription = null) },
                                singleLine = true,
                                placeholder = { Text("main") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action button
                            Button(
                                onClick = {
                                    if (inputToken.isEmpty()) {
                                        viewModel.showToast("Please input a Personal Access Token!")
                                        return@Button
                                    }
                                    if (inputRepo.isEmpty()) {
                                        viewModel.showToast("Please enter a Repository name!")
                                        return@Button
                                    }
                                    viewModel.saveGitHubSettings(
                                        token = inputToken,
                                        repo = inputRepo,
                                        branch = inputBranch.ifEmpty { "main" }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !githubIsSyncing
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Validate")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect to GitHub Repo")
                            }

                            Text(
                                text = "💡 Tip: Go to GitHub -> Settings -> Developer Settings -> Personal Access Tokens (classic) -> Generate new token -> Check 'repo' scopes.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // --- TERMINAL SYNC LOGS TIMELINE ---
                    if (githubLatestLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Deploy Terminal Logs 📟",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { viewModel.clearLogs() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear logs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E1E)
                            )
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(githubLatestLogs.size) { index ->
                                    val log = githubLatestLogs[index]
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (log.contains("✗")) Color(0xFFFF5252)
                                                else if (log.contains("✓")) Color(0xFF69F0AE)
                                                else Color(0xFFE0E0E0),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageSectorCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CategoryMetricsRow(
    name: String,
    formattedSize: String,
    percentage: Float,
    barProgress: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "$formattedSize (${String.format(Locale.getDefault(), "%.1f%%", percentage)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { barProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

fun getCategoryColor(name: String): Color {
    return when (name) {
        "Images" -> Color(0xFF66BB6A) // Emerald
        "Audio" -> Color(0xFFAB47BC) // Purple
        "Videos" -> Color(0xFFEF5350) // Coral Red
        "Code Files" -> Color(0xFF26A69A) // Teal Green
        "Web Documents" -> Color(0xFF29B6F6) // Sky Blue
        "Plain Texts" -> Color(0xFFFFA726) // Soft Orange
        "Configs/Data" -> Color(0xFFEC407A) // Pink
        "Archives" -> Color(0xFF78909C) // Blue Grey
        else -> Color(0xFFB0BEC5) // Cool Soft Grey
    }
}
