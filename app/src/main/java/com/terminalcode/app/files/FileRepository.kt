package com.terminalcode.app.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Repository for managing file operations using Android's Storage Access Framework (SAF).
 *
 * SAF provides a secure, structured way to access files across the Android file system
 * without requiring broad storage permissions. Users pick directories or files through
 * system UI, and the app gets persistent access via content URIs.
 */
class FileRepository(private val context: Context) {

    companion object {
        private const val TAG = "FileRepository"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB
    }

    data class FileItem(
        val name: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val size: Long = 0,
        val lastModified: Long = 0,
        val mimeType: String = ""
    )

    /**
     * Lists all files and directories within a given directory URI.
     */
    suspend fun listFiles(directoryUri: Uri): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, directoryUri)
                ?: return@withContext Result.failure(Exception("Invalid directory URI"))

            val files = documentFile.listFiles().map { file ->
                FileItem(
                    name = file.name ?: "Unknown",
                    uri = file.uri,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    mimeType = file.type ?: ""
                )
            }.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            Result.failure(e)
        }
    }

    /**
     * Reads the content of a file as a UTF-8 string.
     * Maximum file size is 50MB.
     */
    suspend fun readFile(fileUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check file size before reading
            val docFile = DocumentFile.fromSingleUri(context, fileUri)
            val fileSize = docFile?.length() ?: 0
            if (fileSize > MAX_FILE_SIZE) {
                return@withContext Result.failure(
                    SecurityException("File too large: ${fileSize / 1024 / 1024}MB (max 50MB)")
                )
            }

            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val content = reader.readText()
            reader.close()

            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            Result.failure(e)
        }
    }

    /**
     * Writes content to a file.
     */
    suspend fun writeFile(fileUri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(fileUri, "wt")
                ?: return@withContext Result.failure(Exception("Cannot open file for writing"))

            outputStream.write(content.toByteArray(Charsets.UTF_8))
            outputStream.close()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new file in the specified directory.
     */
    suspend fun createFile(directoryUri: Uri, fileName: String, mimeType: String = "text/plain"): Result<FileItem> =
        withContext(Dispatchers.IO) {
            try {
                val parentDoc = DocumentFile.fromTreeUri(context, directoryUri)
                    ?: return@withContext Result.failure(Exception("Invalid directory URI"))

                val existing = parentDoc.findFile(fileName)
                if (existing != null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("File '$fileName' already exists")
                    )
                }

                val newFile = parentDoc.createFile(mimeType, fileName)
                    ?: return@withContext Result.failure(Exception("Failed to create file"))

                Result.success(
                    FileItem(
                        name = fileName,
                        uri = newFile.uri,
                        isDirectory = false,
                        size = 0,
                        lastModified = System.currentTimeMillis(),
                        mimeType = mimeType
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating file", e)
                Result.failure(e)
            }
        }

    /**
     * Creates a new directory in the specified directory.
     */
    suspend fun createDirectory(directoryUri: Uri, dirName: String): Result<FileItem> =
        withContext(Dispatchers.IO) {
            try {
                val parentDoc = DocumentFile.fromTreeUri(context, directoryUri)
                    ?: return@withContext Result.failure(Exception("Invalid directory URI"))

                val existing = parentDoc.findFile(dirName)
                if (existing != null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Directory '$dirName' already exists")
                    )
                }

                val newDir = parentDoc.createDirectory(dirName)
                    ?: return@withContext Result.failure(Exception("Failed to create directory"))

                Result.success(
                    FileItem(
                        name = dirName,
                        uri = newDir.uri,
                        isDirectory = true,
                        lastModified = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating directory", e)
                Result.failure(e)
            }
        }

    /**
     * Deletes a file or directory.
     */
    suspend fun deleteFile(fileUri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val docFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: return@withContext Result.failure(Exception("File not found"))

            val deleted = docFile.delete()
            Result.success(deleted)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            Result.failure(e)
        }
    }

    /**
     * Renames a file or directory.
     */
    suspend fun renameFile(fileUri: Uri, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val docFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: return@withContext Result.failure(Exception("File not found"))

            val renamed = docFile.renameTo(newName)
            Result.success(renamed)
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file", e)
            Result.failure(e)
        }
    }

    /**
     * Determines the MIME type based on file extension.
     */
    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt" -> "text/plain"
            "js", "jsx" -> "text/javascript"
            "ts", "tsx" -> "text/typescript"
            "html", "htm" -> "text/html"
            "css", "scss", "less" -> "text/css"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md", "mdx" -> "text/markdown"
            "py" -> "text/x-python"
            "java" -> "text/x-java"
            "kt", "kts" -> "text/x-kotlin"
            "go" -> "text/x-go"
            "rs" -> "text/x-rust"
            "rb" -> "text/x-ruby"
            "php" -> "text/x-php"
            "c", "h" -> "text/x-c"
            "cpp", "hpp", "cc" -> "text/x-c++"
            "cs" -> "text/x-csharp"
            "swift" -> "text/x-swift"
            "sh", "bash" -> "application/x-sh"
            "sql" -> "text/x-sql"
            "yaml", "yml" -> "text/yaml"
            "toml" -> "text/toml"
            "gradle" -> "text/groovy"
            "gitignore" -> "text/plain"
            "env" -> "text/plain"
            "conf", "cfg", "ini" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "text/plain"
        }
    }

    /**
     * Checks if a file type is viewable in the editor (text-based).
     */
    fun isTextFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return !setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "webp",
            "mp3", "wav", "ogg", "flac", "aac",
            "mp4", "avi", "mkv", "mov", "wmv",
            "pdf", "doc", "docx", "xls", "xlsx",
            "zip", "rar", "7z", "tar", "gz", "bz2",
            "apk", "dex", "so",
            "ttf", "otf", "woff", "woff2",
            "ico",
        ).contains(ext)
    }
}
