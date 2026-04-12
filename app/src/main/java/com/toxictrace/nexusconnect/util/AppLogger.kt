package com.toxictrace.nexusconnect.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val LOG_FILE_NAME = "nexus_current.log"
    private const val MAX_SIZE_BYTES = 1 * 1024 * 1024 // 1 MB

    private var logFile: File? = null
    private val timestampFmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private val exportNameFmt = SimpleDateFormat("dd-MM-yyyy-HH-mm", Locale.getDefault())

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, LOG_FILE_NAME)
        rotateIfNeeded()
        i("AppLogger", "Logger initialized. File: ${logFile?.absolutePath}")
    }

    fun i(tag: String, message: String) = write("I", tag, message)
    fun w(tag: String, message: String) = write("W", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        write("E", tag, message)
        throwable?.let { write("E", tag, it.stackTraceToString()) }
    }

    private fun write(level: String, tag: String, message: String) {
        try {
            val file = logFile ?: return
            val line = "${timestampFmt.format(Date())} [$level] $tag: $message\n"
            file.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_SIZE_BYTES) {
            val backup = File(file.parent, "nexus_old.log")
            backup.delete()
            file.renameTo(backup)
        }
    }

    /**
     * Copies the current log to the user-selected backup folder.
     * Returns the name of the saved file on success.
     */
    fun exportToFolder(context: Context, folderUri: Uri): String {
        val src = logFile ?: throw IllegalStateException("Logger not initialized")
        if (!src.exists() || src.length() == 0L) throw IllegalStateException("Log file is empty")

        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalStateException("Cannot open backup folder")

        val fileName = "${exportNameFmt.format(Date())}.log"
        val destDoc = folder.createFile("text/plain", fileName)
            ?: throw IllegalStateException("Cannot create file in backup folder")

        context.contentResolver.openOutputStream(destDoc.uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Cannot write to backup folder")

        return fileName
    }
}
