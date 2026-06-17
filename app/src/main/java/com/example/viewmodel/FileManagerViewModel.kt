package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.FileItem
import com.example.repository.StorageRepository
import com.example.repository.GitHubService
import com.example.repository.GitHubProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortType(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

enum class Screen {
    EXPLORER, EDITOR, HISTORY, STATS, SETTINGS
}

enum class StorageSource {
    SANDBOX,
    EXTERNAL
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StorageRepository(application)

    // --- GITHUB INTEGRATION SUITE DEFS ---
    private val gitHubService = GitHubService()
    private val prefs = application.getSharedPreferences("OSFileManagerPrefs", Context.MODE_PRIVATE)

    private val _githubToken = MutableStateFlow(prefs.getString("github_token", "") ?: "")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _githubUsername = MutableStateFlow(prefs.getString("github_username", "") ?: "")
    val githubUsername: StateFlow<String> = _githubUsername.asStateFlow()

    private val _githubRepo = MutableStateFlow(prefs.getString("github_repo", "") ?: "")
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _githubBranch = MutableStateFlow(prefs.getString("github_branch", "main") ?: "main")
    val githubBranch: StateFlow<String> = _githubBranch.asStateFlow()

    private val _githubProfile = MutableStateFlow<GitHubProfile?>(null)
    val githubProfile: StateFlow<GitHubProfile?> = _githubProfile.asStateFlow()

    private val _githubReposList = MutableStateFlow<List<String>>(emptyList())
    val githubReposList: StateFlow<List<String>> = _githubReposList.asStateFlow()

    private val _githubIsSyncing = MutableStateFlow(false)
    val githubIsSyncing: StateFlow<Boolean> = _githubIsSyncing.asStateFlow()

    private val _githubLatestLogs = MutableStateFlow<List<String>>(emptyList())
    val githubLatestLogs: StateFlow<List<String>> = _githubLatestLogs.asStateFlow()

    private val _githubSyncStatus = MutableStateFlow("Disconnected")
    val githubSyncStatus: StateFlow<String> = _githubSyncStatus.asStateFlow()

    // Navigator & Directory States
    private val _currentDir = MutableStateFlow<File>(repository.getSandboxFolder())
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _storageSource = MutableStateFlow(StorageSource.SANDBOX)
    val storageSource: StateFlow<StorageSource> = _storageSource.asStateFlow()

    // File listings & modifiers
    private val _showHidden = MutableStateFlow(prefs.getBoolean("show_hidden_files", false))
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortType = MutableStateFlow(
        try {
            SortType.valueOf(prefs.getString("default_sort_type", SortType.NAME_ASC.name) ?: SortType.NAME_ASC.name)
        } catch (e: Exception) {
            SortType.NAME_ASC
        }
    )
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    // Dynamic Navigation States
    val isAtRoot: StateFlow<Boolean> = combine(_currentDir, _storageSource) { dir, source ->
        val rootLimit = if (source == StorageSource.SANDBOX) {
            repository.getSandboxFolder()
        } else {
            Environment.getExternalStorageDirectory()
        }
        dir.absolutePath == rootLimit.absolutePath
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Persisted Editor Configuration Properties
    private val _editorFontSize = MutableStateFlow(prefs.getInt("editor_font_size", 14))
    val editorFontSize: StateFlow<Int> = _editorFontSize.asStateFlow()

    private val _editorWordWrap = MutableStateFlow(prefs.getBoolean("editor_word_wrap", true))
    val editorWordWrap: StateFlow<Boolean> = _editorWordWrap.asStateFlow()

    private val _editorEngine = MutableStateFlow(prefs.getInt("editor_engine_mode", 1))
    val editorEngine: StateFlow<Int> = _editorEngine.asStateFlow()

    private val _editorTheme = MutableStateFlow(prefs.getInt("editor_theme_mode", 0))
    val editorTheme: StateFlow<Int> = _editorTheme.asStateFlow()

    private val _editorAutoSave = MutableStateFlow(prefs.getBoolean("editor_auto_save", false))
    val editorAutoSave: StateFlow<Boolean> = _editorAutoSave.asStateFlow()

    private val _showFileDetails = MutableStateFlow(prefs.getBoolean("show_file_details", true))
    val showFileDetails: StateFlow<Boolean> = _showFileDetails.asStateFlow()

    fun saveEditorFontSize(size: Int) {
        prefs.edit().putInt("editor_font_size", size).apply()
        _editorFontSize.value = size
    }

    fun saveEditorWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean("editor_word_wrap", enabled).apply()
        _editorWordWrap.value = enabled
    }

    fun saveEditorEngine(mode: Int) {
        prefs.edit().putInt("editor_engine_mode", mode).apply()
        _editorEngine.value = mode
    }

    fun saveEditorTheme(theme: Int) {
        prefs.edit().putInt("editor_theme_mode", theme).apply()
        _editorTheme.value = theme
    }

    fun saveEditorAutoSave(enabled: Boolean) {
        prefs.edit().putBoolean("editor_auto_save", enabled).apply()
        _editorAutoSave.value = enabled
    }

    fun saveShowFileDetails(enabled: Boolean) {
        prefs.edit().putBoolean("show_file_details", enabled).apply()
        _showFileDetails.value = enabled
    }

    // Active screen navigation
    private val _currentScreen = MutableStateFlow(Screen.EXPLORER)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Core Listings Source
    private val _filesList = MutableStateFlow<List<FileItem>>(emptyList())

    // Combined files search and sorting flow
    val filesList: StateFlow<List<FileItem>> = combine(
        _filesList, _searchQuery, _sortType
    ) { rawList, query, sort ->
        var list = if (query.isEmpty()) {
            rawList
        } else {
            rawList.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Apply sort selection (directories always float to top of list)
        list = when (sort) {
            SortType.NAME_ASC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            SortType.NAME_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortType.DATE_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortType.DATE_ASC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified })
            SortType.SIZE_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
            SortType.SIZE_ASC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.size })
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Editing File
    private val _activeFile = MutableStateFlow<File?>(null)
    val activeFile: StateFlow<File?> = _activeFile.asStateFlow()

    private val _activeFileContent = MutableStateFlow("")
    val activeFileContent: StateFlow<String> = _activeFileContent.asStateFlow()

    private val _editorTitle = MutableStateFlow("Editor (No File)")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    // System Permission status
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    // History & Starred Lists
    private val _bookmarkedPaths = MutableStateFlow<List<String>>(repository.getBookmarks())
    val bookmarkedPaths: StateFlow<List<String>> = _bookmarkedPaths.asStateFlow()

    private val _recentPaths = MutableStateFlow<List<String>>(repository.getRecents())
    val recentPaths: StateFlow<List<String>> = _recentPaths.asStateFlow()

    // Storage Dashboard space metrics
    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _freeBytes = MutableStateFlow(0L)
    val freeBytes: StateFlow<Long> = _freeBytes.asStateFlow()

    private val _usedBytes = MutableStateFlow(0L)
    val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()

    private val _categoryAnalysis = MutableStateFlow<Map<String, Long>>(emptyMap())
    val categoryAnalysis: StateFlow<Map<String, Long>> = _categoryAnalysis.asStateFlow()

    // Active Toast details
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        // Init directories listing
        refreshDirectoryContents()
        refreshStorageMetrics()
        // Also verify GitHub connection on boot if token exists
        if (_githubToken.value.isNotEmpty()) {
            verifyGitHubConnection()
        }
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Set storage permission granted state on dynamic check
    fun setPermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        if (granted && _storageSource.value == StorageSource.EXTERNAL) {
            _currentDir.value = Environment.getExternalStorageDirectory()
        }
        refreshDirectoryContents()
        refreshStorageMetrics()
    }

    // Toggle between Storage Sources (Sandbox Workspace vs OS Device Storage)
    fun switchStorageSource(source: StorageSource) {
        viewModelScope.launch {
            _storageSource.value = source
            if (source == StorageSource.SANDBOX) {
                _currentDir.value = repository.getSandboxFolder()
            } else {
                if (_permissionsGranted.value) {
                    _currentDir.value = Environment.getExternalStorageDirectory()
                } else {
                    _currentDir.value = Environment.getExternalStorageDirectory() // Set but UI prompts
                }
            }
            _searchQuery.value = ""
            refreshDirectoryContents()
            refreshStorageMetrics()
        }
    }

    fun refreshDirectoryContents() {
        viewModelScope.launch {
            val dir = _currentDir.value
            val isShowHidden = _showHidden.value
            val list = withContext(Dispatchers.IO) {
                repository.listDirectory(dir, isShowHidden)
            }
            _filesList.value = list
        }
    }

    // Navigating deep inside folder
    fun navigateToDirectory(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            _currentDir.value = dir
            _searchQuery.value = ""
            refreshDirectoryContents()
        }
    }

    // Navigating upwards towards parent directory
    fun navigateBack() {
        val current = _currentDir.value
        val rootLimit = if (_storageSource.value == StorageSource.SANDBOX) {
            repository.getSandboxFolder()
        } else {
            // No absolute limit inside external storage, can navigate up to '/' if desired or stop at External Storage Directory
            Environment.getExternalStorageDirectory()
        }

        // Check if we already reached top limit
        if (current.absolutePath == rootLimit.absolutePath) {
            showToast("Reached directory root")
            return
        }

        val parent = current.parentFile
        if (parent != null) {
            _currentDir.value = parent
            _searchQuery.value = ""
            refreshDirectoryContents()
        }
    }

    fun navigateToScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun toggleShowHidden() {
        val newValue = !_showHidden.value
        prefs.edit().putBoolean("show_hidden_files", newValue).apply()
        _showHidden.value = newValue
        refreshDirectoryContents()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortType(sort: SortType) {
        prefs.edit().putString("default_sort_type", sort.name).apply()
        _sortType.value = sort
    }

    // File manipulation triggers
    fun createNewFile(name: String, content: String = "") {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.createFile(_currentDir.value, name, content)
            }
            result.onSuccess { file ->
                showToast("File '${file.name}' created.")
                refreshDirectoryContents()
                refreshStorageMetrics()
            }
            result.onFailure {
                showToast("Create failed: ${it.localizedMessage}")
            }
        }
    }

    fun createNewFolder(name: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.createDirectory(_currentDir.value, name)
            }
            result.onSuccess { dir ->
                showToast("Folder '${dir.name}' created.")
                refreshDirectoryContents()
                refreshStorageMetrics()
            }
            result.onFailure {
                showToast("Folder create failed: ${it.localizedMessage}")
            }
        }
    }

    fun deleteItem(item: FileItem) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.deleteItem(File(item.path))
            }
            result.onSuccess {
                showToast("Successfully deleted '${item.name}'")
                refreshDirectoryContents()
                
                // If the deleted file was the active open file in editor, close it
                if (_activeFile.value?.absolutePath == item.path) {
                    closeActiveFile()
                }
                
                // Refresh bookmarks and metrics
                _bookmarkedPaths.value = repository.getBookmarks()
                _recentPaths.value = repository.getRecents()
                refreshStorageMetrics()
            }
            result.onFailure {
                showToast("Delete failed: ${it.localizedMessage}")
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.renameItem(File(item.path), newName)
            }
            result.onSuccess { renamedFile ->
                showToast("Renamed successfully to '$newName'")
                refreshDirectoryContents()
                
                // If it is active file, update active file descriptor
                if (_activeFile.value?.absolutePath == item.path) {
                    _activeFile.value = renamedFile
                    _editorTitle.value = renamedFile.name
                }
                
                _bookmarkedPaths.value = repository.getBookmarks()
                _recentPaths.value = repository.getRecents()
            }
            result.onFailure {
                showToast("Rename failed: ${it.localizedMessage}")
            }
        }
    }

    fun moveItem(item: FileItem, targetFolder: FileItem) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.moveItem(File(item.path), File(targetFolder.path))
            }
            result.onSuccess { movedFile ->
                showToast("Moved '${item.name}' to '${targetFolder.name}'")
                refreshDirectoryContents()
                
                // If the moved file is active open file in editor, update its path
                if (_activeFile.value?.absolutePath == item.path) {
                    _activeFile.value = movedFile
                    _editorTitle.value = movedFile.name
                }
                
                _bookmarkedPaths.value = repository.getBookmarks()
                _recentPaths.value = repository.getRecents()
                refreshStorageMetrics()
            }
            result.onFailure {
                showToast("Move failed: ${it.localizedMessage}")
            }
        }
    }

    // Toggle Favorite
    fun toggleBookmark(path: String) {
        repository.toggleBookmark(path)
        _bookmarkedPaths.value = repository.getBookmarks()
        // Refresh item UI state if listing
        refreshDirectoryContents()
    }

    fun isBookmarked(path: String): Boolean {
        return repository.isBookmarked(path)
    }

    fun clearHistory() {
        repository.clearRecents()
        _recentPaths.value = emptyList()
        showToast("Cleared file history.")
    }

    // Active Editor Operations
    fun openFileInEditor(file: File) {
        viewModelScope.launch {
            _activeFile.value = file
            _editorTitle.value = file.name
            
            val content = withContext(Dispatchers.IO) {
                repository.readFileContent(file)
            }
            _activeFileContent.value = content
            _isModified.value = false
            
            // Register inside recent files
            repository.recordRecent(file.absolutePath)
            _recentPaths.value = repository.getRecents()
            
            // Switch tabs
            _currentScreen.value = Screen.EDITOR
        }
    }

    fun updateEditorContent(newContent: String) {
        _activeFileContent.value = newContent
        _isModified.value = true
    }

    fun saveActiveFile() {
        val file = _activeFile.value
        if (file == null) {
            showToast("No active file selected")
            return
        }
        viewModelScope.launch {
            val content = _activeFileContent.value
            val success = withContext(Dispatchers.IO) {
                repository.writeFileContent(file, content)
            }
            if (success) {
                _isModified.value = false
                showToast("Saved file '${file.name}' Successfully")
                refreshDirectoryContents()
                refreshStorageMetrics()
            } else {
                showToast("Write failure! Check permissions.")
            }
        }
    }

    fun closeActiveFile() {
        _activeFile.value = null
        _activeFileContent.value = ""
        _editorTitle.value = "Editor (No File)"
        _isModified.value = false
    }

    // Dynamic metrics calculators
    fun refreshStorageMetrics() {
        viewModelScope.launch {
            val isExt = _storageSource.value == StorageSource.EXTERNAL
            val (total, free, used) = withContext(Dispatchers.IO) {
                repository.getStorageMetrics(isExt)
            }
            _totalBytes.value = total
            _freeBytes.value = free
            _usedBytes.value = used

            val rootFolder = if (isExt) {
                Environment.getExternalStorageDirectory()
            } else {
                repository.getSandboxFolder()
            }

            val categories = withContext(Dispatchers.IO) {
                repository.analyzeFolderCategories(rootFolder)
            }
            _categoryAnalysis.value = categories
        }
    }

    fun getDirectoryContents(directory: File): List<FileItem> {
        return repository.listDirectory(directory, _showHidden.value)
    }

    fun addLog(log: String) {
        val current = _githubLatestLogs.value.toMutableList()
        current.add(0, log) // Newest first
        if (current.size > 50) current.removeAt(current.size - 1)
        _githubLatestLogs.value = current
    }

    fun clearLogs() {
        _githubLatestLogs.value = emptyList()
    }

    fun verifyGitHubConnection() {
        val token = _githubToken.value
        if (token.isEmpty()) {
            _githubSyncStatus.value = "Credentials missing"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _githubIsSyncing.value = true
            addLog("Verifying connection to GitHub...")
            val profileRes = gitHubService.checkConnection(token)
            profileRes.onSuccess { profile ->
                _githubProfile.value = profile
                _githubUsername.value = profile.username
                _githubSyncStatus.value = "Connected as ${profile.username}"
                addLog("Successfully connected as ${profile.displayName} (@${profile.username})!")
                
                // Save username
                prefs.edit().putString("github_username", profile.username).apply()

                // Now fetch user's repositories list
                addLog("Fetching user's repositories...")
                val reposRes = gitHubService.fetchRepositories(token)
                reposRes.onSuccess { reposList ->
                    _githubReposList.value = reposList
                    addLog("Fetched ${reposList.size} repositories from GitHub.")
                }.onFailure { repoErr ->
                    addLog("Warning: Could not fetch repos list: ${repoErr.localizedMessage}")
                }
            }.onFailure { err ->
                _githubProfile.value = null
                _githubSyncStatus.value = "Connection Error"
                addLog("Connection failed: ${err.localizedMessage}")
            }
            _githubIsSyncing.value = false
        }
    }

    fun saveGitHubSettings(token: String, repo: String, branch: String) {
        prefs.edit().apply {
            putString("github_token", token)
            putString("github_repo", repo)
            putString("github_branch", branch)
            apply()
        }
        _githubToken.value = token
        _githubRepo.value = repo
        _githubBranch.value = branch
        
        verifyGitHubConnection()
    }

    fun disconnectGitHub() {
        prefs.edit().apply {
            remove("github_token")
            remove("github_username")
            remove("github_repo")
            remove("github_branch")
            apply()
        }
        _githubToken.value = ""
        _githubUsername.value = ""
        _githubRepo.value = ""
        _githubBranch.value = "main"
        _githubProfile.value = null
        _githubReposList.value = emptyList()
        _githubSyncStatus.value = "Disconnected"
        addLog("Disconnected account and cleared tokens.")
    }

    private fun getAllFilesRecursively(dir: File): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists()) return result
        val files = dir.listFiles() ?: return result
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                result.addAll(getAllFilesRecursively(f))
            } else {
                result.add(f)
            }
        }
        return result
    }

    fun pushFolderToGitHub(dir: File) {
        val token = _githubToken.value
        val repo = _githubRepo.value
        val branch = _githubBranch.value
        val owner = _githubUsername.value

        if (token.isEmpty() || repo.isEmpty() || owner.isEmpty()) {
            showToast("GitHub is not configured or connected!")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _githubIsSyncing.value = true
            addLog("Preparing to recursively push folder: ${dir.name}")
            showToast("Syncing with GitHub started...")
            
            val sandboxRoot = repository.getSandboxFolder()
            val filesToPush = getAllFilesRecursively(dir)
            
            if (filesToPush.isEmpty()) {
                addLog("Zero files found inside folder ${dir.name} to upload.")
                _githubIsSyncing.value = false
                return@launch
            }
            
            addLog("Found ${filesToPush.size} files to upload.")
            var successCount = 0
            var failCount = 0

            for (file in filesToPush) {
                val relativePath = file.absolutePath.substringAfter(sandboxRoot.absolutePath + "/").trim()
                if (relativePath.isEmpty()) continue

                addLog("Syncing File: $relativePath ...")
                val bytes = try {
                    file.readBytes()
                } catch (e: Exception) {
                    addLog("Failed to read local core bytes of $relativePath: ${e.localizedMessage}")
                    failCount++
                    continue
                }

                val commitMsg = "Sync from Android AI File Manager: $relativePath"
                val pushResult = gitHubService.pushFile(token, owner, repo, branch, relativePath, bytes, commitMsg)

                pushResult.onSuccess { commitUrl ->
                    successCount++
                    addLog("✓ Synced: $relativePath")
                }.onFailure { err ->
                    failCount++
                    addLog("✗ Fail [${file.name}]: ${err.localizedMessage}")
                }
            }

            addLog("Sync Complete! Succeeded: $successCount, Failed: $failCount")
            withContext(Dispatchers.Main) {
                showToast("Workspace Synced: $successCount pushed, $failCount failed.")
            }
            _githubIsSyncing.value = false
        }
    }

    fun syncEntireWorkspace() {
        val sandboxFolder = repository.getSandboxFolder()
        pushFolderToGitHub(sandboxFolder)
    }

    fun pushSingleFileToGitHub(file: File, commitMsgInput: String = "") {
        val token = _githubToken.value
        val repo = _githubRepo.value
        val branch = _githubBranch.value
        val owner = _githubUsername.value

        if (token.isEmpty() || repo.isEmpty() || owner.isEmpty()) {
            showToast("GitHub is not configured or connected!")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _githubIsSyncing.value = true
            addLog("Uploading single file: ${file.name}")
            
            val sandboxRoot = repository.getSandboxFolder()
            val relativePath = file.absolutePath.substringAfter(sandboxRoot.absolutePath + "/").trim()
            if (relativePath.isEmpty()) {
                addLog("Error: Could not calculate root relation for ${file.name}")
                _githubIsSyncing.value = false
                return@launch
            }

            val fileBytes = try {
                file.readBytes()
            } catch (e: Exception) {
                addLog("Failed reading local bytes of ${file.name}: ${e.localizedMessage}")
                _githubIsSyncing.value = false
                return@launch
            }

            val commitMsg = commitMsgInput.ifEmpty { "Update file ${file.name} via AI File Manager" }
            val res = gitHubService.pushFile(token, owner, repo, branch, relativePath, fileBytes, commitMsg)

            res.onSuccess { commitUrl ->
                addLog("✓ Successfully pushed ${file.name}!")
                addLog("Commit details: $commitUrl")
                withContext(Dispatchers.Main) {
                    showToast("Single file '${file.name}' pushed successfully")
                }
            }.onFailure { err ->
                addLog("✗ Failed pushing ${file.name}: ${err.localizedMessage}")
                withContext(Dispatchers.Main) {
                    showToast("Failed to push file: ${err.localizedMessage}")
                }
            }
            _githubIsSyncing.value = false
        }
    }
}
