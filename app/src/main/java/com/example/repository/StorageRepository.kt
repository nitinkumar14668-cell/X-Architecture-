package com.example.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.model.FileItem
import java.io.File
import java.io.FileWriter

class StorageRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("OSFileManagerPrefs", Context.MODE_PRIVATE)
    private val BOOKMARKS_KEY = "starred_files_v1"
    private val RECENTS_KEY = "recent_files_v1"

    init {
        // Automatically initialize internal sandbox folder with beautiful sample projects on first startup
        initializeSandboxWithSamples()
    }

    // Get bookmarks (set of absolute paths)
    fun getBookmarks(): List<String> {
        val set = sharedPrefs.getStringSet(BOOKMARKS_KEY, emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    // Add or remove path to bookmarks
    fun toggleBookmark(path: String) {
        val current = getBookmarks().toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        sharedPrefs.edit().putStringSet(BOOKMARKS_KEY, current).apply()
    }

    fun isBookmarked(path: String): Boolean {
        return getBookmarks().contains(path)
    }

    // Get recents list (recent paths with order maintained, max 20)
    fun getRecents(): List<String> {
        val listStr = sharedPrefs.getString(RECENTS_KEY, "") ?: ""
        if (listStr.isEmpty()) return emptyList()
        return listStr.split("|").filter { it.isNotEmpty() && File(it).exists() }
    }

    // Update recents with newly opened path
    fun recordRecent(path: String) {
        val current = getRecents().toMutableList()
        current.remove(path)
        current.add(0, path)
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        sharedPrefs.edit().putString(RECENTS_KEY, current.joinToString("|")).apply()
    }

    // Clear whole recents history
    fun clearRecents() {
        sharedPrefs.edit().remove(RECENTS_KEY).apply()
    }

    // List contents of a directory
    fun listDirectory(directory: File, showHidden: Boolean = false): List<FileItem> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        val bookmarksSet = getBookmarks().toSet()
        val filesList = directory.listFiles() ?: return emptyList()

        return filesList
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isBookmarked = bookmarksSet.contains(file.absolutePath)
                )
            }
            .sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
    }

    // Create a new File
    fun createFile(parentDir: File, fileName: String, content: String = ""): Result<File> {
        return try {
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                return Result.failure(Exception("Failed to load or create container path"))
            }
            val newFile = File(parentDir, fileName)
            if (newFile.exists()) {
                return Result.failure(Exception("File with similar name already exists"))
            }
            if (newFile.createNewFile()) {
                newFile.writeText(content)
                Result.success(newFile)
            } else {
                Result.failure(Exception("Permission or OS fault side creation error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Create a new Folder
    fun createDirectory(parentDir: File, folderName: String): Result<File> {
        return try {
            val newDir = File(parentDir, folderName)
            if (newDir.exists()) {
                return Result.failure(Exception("Folder with similar name already exists"))
            }
            if (newDir.mkdirs()) {
                Result.success(newDir)
            } else {
                Result.failure(Exception("Unable to construct folder paths on system"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Read full file content
    fun readFileContent(file: File): String {
        return try {
            if (file.exists() && file.isFile) {
                file.readText(Charsets.UTF_8)
            } else ""
        } catch (e: Exception) {
            "Error reading file contents: ${e.localizedMessage}"
        }
    }

    // Overwrite file content
    fun writeFileContent(file: File, content: String): Boolean {
        return try {
            if (file.exists() && file.isFile) {
                file.writeText(content, Charsets.UTF_8)
                true
            } else false
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error saving content: ${e.localizedMessage}")
            false
        }
    }

    // Delete item (recursive for folder)
    fun deleteItem(file: File): Result<Boolean> {
        return try {
            val success = file.deleteRecursively()
            if (success) {
                // If bookmarked, remove
                val bookmarks = getBookmarks().toMutableSet()
                if (bookmarks.remove(file.absolutePath)) {
                    sharedPrefs.edit().putStringSet(BOOKMARKS_KEY, bookmarks).apply()
                }
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to fully delete specified path target"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Rename file or folder
    fun renameItem(file: File, newName: String): Result<File> {
        return try {
            val destination = File(file.parentFile, newName)
            if (destination.exists()) {
                return Result.failure(Exception("An item with the name '$newName' already exists"))
            }
            if (file.renameTo(destination)) {
                // Update bookmarks if needed
                val bookmarks = getBookmarks().toMutableSet()
                if (bookmarks.remove(file.absolutePath)) {
                    bookmarks.add(destination.absolutePath)
                    sharedPrefs.edit().putStringSet(BOOKMARKS_KEY, bookmarks).apply()
                }
                Result.success(destination)
            } else {
                Result.failure(Exception("Rename failed due to partition lock or storage error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Move file or folder
    fun moveItem(file: File, targetDir: File): Result<File> {
        return try {
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return Result.failure(Exception("Destination folder does not exist or is not a directory"))
            }
            if (file.absolutePath == targetDir.absolutePath || targetDir.absolutePath.startsWith(file.absolutePath + File.separator)) {
                return Result.failure(Exception("Cannot move a folder into itself or its own subfolder"))
            }
            val destination = File(targetDir, file.name)
            if (destination.exists()) {
                return Result.failure(Exception("An item with the name '${file.name}' already exists in the destination folder"))
            }
            if (file.renameTo(destination)) {
                // Update bookmarks if needed
                val bookmarks = getBookmarks().toMutableSet()
                if (bookmarks.remove(file.absolutePath)) {
                    bookmarks.add(destination.absolutePath)
                    sharedPrefs.edit().putStringSet(BOOKMARKS_KEY, bookmarks).apply()
                }
                // Update recents if needed
                val recents = getRecents().toMutableList()
                val idx = recents.indexOf(file.absolutePath)
                if (idx != -1) {
                    recents[idx] = destination.absolutePath
                    sharedPrefs.edit().putString(RECENTS_KEY, recents.joinToString("|")).apply()
                }
                Result.success(destination)
            } else {
                Result.failure(Exception("Move failed due to storage system error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get details of system partition (for shared/external storage, or internal)
    fun getStorageMetrics(isExternal: Boolean): Triple<Long, Long, Long> {
        return try {
            val path = if (isExternal) {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                context.filesDir.absolutePath
            }
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            Triple(totalBytes, freeBytes, usedBytes)
        } catch (e: Exception) {
            Triple(0L, 0L, 0L)
        }
    }

    // Gather metrics on stored files recursively (folders, formats distribution)
    fun analyzeFolderCategories(root: File): Map<String, Long> {
        val analysis = mutableMapOf(
            "Images" to 0L,
            "Audio" to 0L,
            "Videos" to 0L,
            "Code Files" to 0L,
            "Web Documents" to 0L,
            "Plain Texts" to 0L,
            "Configs/Data" to 0L,
            "Archives" to 0L,
            "Others" to 0L
        )

        fun scan(file: File) {
            if (file.isDirectory) {
                val list = file.listFiles() ?: return
                for (subf in list) {
                    scan(subf)
                }
            } else {
                val ext = file.extension.lowercase()
                val size = file.length()
                when (ext) {
                    "png", "jpg", "jpeg", "webp", "gif", "svg" -> analysis["Images"] = analysis["Images"]!! + size
                    "mp3", "wav", "ogg", "flac", "m4a" -> analysis["Audio"] = analysis["Audio"]!! + size
                    "mp4", "mkv", "avi", "mov", "webm" -> analysis["Videos"] = analysis["Videos"]!! + size
                    "kt", "kts", "java", "py", "pyw", "js", "ts", "c", "cpp", "h", "swift", "sh" -> analysis["Code Files"] = analysis["Code Files"]!! + size
                    "html", "htm", "xml", "css", "pdf" -> analysis["Web Documents"] = analysis["Web Documents"]!! + size
                    "md", "markdown", "txt" -> analysis["Plain Texts"] = analysis["Plain Texts"]!! + size
                    "json", "yaml", "yml", "ini", "properties" -> analysis["Configs/Data"] = analysis["Configs/Data"]!! + size
                    "zip", "tar", "gz", "rar", "7z" -> analysis["Archives"] = analysis["Archives"]!! + size
                    else -> analysis["Others"] = analysis["Others"]!! + size
                }
            }
        }

        try {
            if (root.exists()) {
                scan(root)
            }
        } catch (e: Exception) {
            Log.e("StorageRepository", "Fail analyzing category distribution tree")
        }
        return analysis
    }

    // Create the Sandbox/Internal Storage workspace folders & sample project if they don't exist
    fun getSandboxFolder(): File {
        val sandbox = File(context.filesDir, "sandbox")
        if (!sandbox.exists()) {
            sandbox.mkdirs()
        }
        return sandbox
    }

    private fun initializeSandboxWithSamples() {
        try {
            val sandbox = getSandboxFolder()
            
            // Welcome file
            val welcomeFile = File(sandbox, "Welcome.md")
            if (!welcomeFile.exists()) {
                welcomeFile.writeText("""
# OS File Manager & Editor 🚀

Welcome to your advanced mobile file browser and full-stack editor space. This application serves as a developer-friendly desktop environment on your phone!

## Core Capabilities:
1. **Physical File Explorer**: Access all of your shared user storage (MANAGE_EXTERNAL_STORAGE) or use this internal App Sandbox.
2. **Advanced Code/Text Editor**: Edit script packages on the go with real Monospaced typography, customizable line wrapping, and manual/auto-saving options.
3. **M3 Custom Highlights Compiler**: Highlights code tokens automatically for Markdown, Kotlin, HTML, and JSON scripts.
4. **Interactive Tab Previewers**:
    - **MarkDown previews**: Renders formatted headings, checklists, blockquotes, and text modifiers natively.
    - **Web Browser rendering**: Allows executing and sandboxing HTML, CSS, and JS.
5. **Storage Space Analytics**: Visual dynamic graph demonstrating partition bounds by extension types (Photos, Playlists, XML, Archival zips).

### Code highlight preview:
```kotlin
fun main() {
    val developerName = "Nitin Kumar"
    println("Building elite apps for storage sorting!")
}
```

*Created with ❤️ in Kotlin and Jetpack Compose.*
""".trimIndent())
            }

            // Hello world file
            val ktFile = File(sandbox, "HelloWorld.kt")
            if (!ktFile.exists()) {
                ktFile.writeText("""
package com.example.sandbox

import java.time.LocalDateTime

/**
 * Super useful script demonstrating premium in-app 
 * syntax parsing and editing mechanics in real-time.
 */
fun main(args: Array<String>) {
    val currentTime = LocalDateTime.now()
    println("Ready to manage system storage at system time: ${'$'}currentTime")
    
    val files = listOf("Welcome.md", "HelloWorld.kt", "Index.html", "ThemeConfig.json")
    for (file in files) {
        println("Inspecting workspace file: ${'$'}file...")
    }
}
""".trimIndent())
            }

            // HTML file
            val htmlFile = File(sandbox, "Index.html")
            if (!htmlFile.exists()) {
                htmlFile.writeText("""
<!DOCTYPE html>
<html>
<head>
    <title>OS Dev Preview</title>
    <style>
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background-color: #1a1a24;
            color: #e3e3e8;
            padding: 30px;
            text-align: center;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: #252538;
            padding: 40px;
            border-radius: 20px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
            border: 2px solid #58bbf2;
        }
        h1 {
            color: #58bbf2;
            margin-bottom: 5px;
        }
        p {
            color: #9cade0;
            line-height: 1.6;
        }
        .btn {
            background: linear-gradient(135deg, #0288d1, #26c6da);
            color: white;
            border: none;
            padding: 12px 30px;
            font-size: 16px;
            font-weight: bold;
            border-radius: 30px;
            cursor: pointer;
            box-shadow: 0 5px 15px rgba(0, 136, 209, 0.4);
            margin-top: 20px;
            transition: 0.3s ease;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Render Active Code Natively!</h1>
        <p>This is a real web view. The editor executes this active HTML rendering out-of-the-box inside the file engine. You can edit any parameter here and watch it reflect instantly!</p>
        <button class="btn" onclick="alert('File Manager code injected successfully!')">Click To Test</button>
    </div>
</body>
</html>
""".trimIndent())
            }

            // JSON config file
            val jsonFile = File(sandbox, "ThemeConfig.json")
            if (!jsonFile.exists()) {
                jsonFile.writeText("""
{
  "theme": "Midnight Slate",
  "version": "1.0.4",
  "features": {
    "liveMarkdown": true,
    "syntaxHighlighter": true,
    "automaticLocalBackup": false,
    "fileSortOrder": "COMPARE_BY_NAME_ASC"
  },
  "editorSetups": {
    "fontSizeDp": 15.0,
    "tabSpacing": 4,
    "showLineNumbers": true,
    "fontFamilyName": "JetBrains Mono"
  }
}
""".trimIndent())
            }

            // Let's create an environment subfolder too, to make the sandbox directory look realistic and nested!
            val subProject = File(sandbox, "WebProject")
            if (!subProject.exists()) {
                subProject.mkdir()
                val webConfig = File(subProject, "package.json")
                webConfig.writeText("""
{
  "name": "os-preview-project",
  "version": "1.0.0",
  "scripts": {
    "build": "vite build --base=./",
    "dev": "vite"
  },
  "dependencies": {
    "lucide-react": "^0.300.0"
  }
}
""".trimIndent())
            }

        } catch (e: Exception) {
            Log.e("StorageRepository", "Failed sandbox pre-pop: ${e.message}")
        }
    }
}
