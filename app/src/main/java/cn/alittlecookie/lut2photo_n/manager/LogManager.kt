package cn.alittlecookie.lut2photo_n.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val logDir = File(context.filesDir, "logs")
    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    init {
        logDir.mkdirs()
        setupTimber()
    }

    private fun setupTimber() {
        if (settingsManager.isLogEnabledSync()) {
            Timber.plant(FileLogTree(logDir))
        }
    }

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
        addLogEntry(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
        addLogEntry(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag).w(throwable, message)
        } else {
            Timber.tag(tag).w(message)
        }
        addLogEntry(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.tag(tag).e(message)
        }
        addLogEntry(LogLevel.ERROR, tag, message, throwable)
    }

    private fun addLogEntry(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        val current = _logEntries.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_MEMORY_ENTRIES) {
            current.removeFirst()
        }
        _logEntries.value = current
    }

    suspend fun exportLogs(): File? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "log_export")
            exportDir.mkdirs()
            val zipFile = File(exportDir, "logs_${System.currentTimeMillis()}.zip")

            val logFiles = logDir.listFiles() ?: emptyArray()
            if (logFiles.isEmpty()) return@withContext null

            ZipOutputStream(zipFile.outputStream()).use { zos ->
                logFiles.forEach { file ->
                    if (file.isFile) {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            zipFile
        } catch (e: Exception) {
            Timber.e(e, "导出日志失败")
            null
        }
    }

    suspend fun cleanOldLogs(keepDays: Int = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        logDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    companion object {
        private const val MAX_MEMORY_ENTRIES = 1000
    }
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class FileLogTree(private val logDir: File) : Timber.Tree() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return

        val date = dateFormat.format(Date())
        val logFile = File(logDir, "app_$date.log")

        val timestamp = timeFormat.format(Date())
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        val throwableStr = t?.let { "\n${it.stackTraceToString()}" } ?: ""
        val logLine = "$timestamp $level/$tag: $message$throwableStr\n"

        try {
            logFile.appendText(logLine)
        } catch (_: Exception) {
        }
    }
}
