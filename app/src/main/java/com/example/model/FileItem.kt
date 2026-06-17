package com.example.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isBookmarked: Boolean = false
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    val formattedSize: String
        get() {
            if (isDirectory) return "--"
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val isEditable: Boolean
        get() {
            if (isDirectory) return false
            val editableExtensions = setOf(
                "txt", "kt", "kts", "java", "py", "js", "ts", "html", "xml", 
                "css", "json", "yaml", "yml", "md", "markdown", "ini", "sh", "properties", "c", "cpp", "h"
            )
            return editableExtensions.contains(extension) || extension.isEmpty()
        }

    val fileTypeLabel: String
        get() {
            if (isDirectory) return "Folder"
            return when (extension) {
                "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "swift" -> "Source Code"
                "html", "xml", "css" -> "Web Document"
                "json", "yaml", "yml" -> "Config File"
                "md", "markdown" -> "Markdown Doc"
                "txt" -> "Text Document"
                "png", "jpg", "jpeg", "webp", "gif", "svg" -> "Image"
                "mp3", "wav", "ogg", "flac" -> "Audio"
                "mp4", "mkv", "avi" -> "Video"
                "pdf" -> "PDF Document"
                "zip", "tar", "gz" -> "Archive"
                else -> "File"
            }
        }
}
