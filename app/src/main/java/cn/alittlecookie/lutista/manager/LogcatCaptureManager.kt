package cn.alittlecookie.lutista.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Captures the app's logcat output (including native __android_log_print calls)
 * into logcat_yyyy-MM-dd.log files. Started/stopped by LogManager.
 */
class LogcatCaptureManager(
    private val logDir: File,
    settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "LogcatCapture"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /** Logcat threadtime format: "MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message" */
    private val logcatPattern = Pattern.compile(
        "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+(\\S+)\\s*:\\s*(.*)$"
    )

    fun start() {
        if (captureJob?.isActive == true) return
        logDir.mkdirs()
        captureJob = scope.launch {
            try {
                val pid = android.os.Process.myPid()
                // Use logcat -d in a loop for compatibility (some devices restrict streaming)
                while (isActive) {
                    try {
                        val process = ProcessBuilder(
                            "logcat", "-d", "-v", "threadtime", "--pid", pid.toString(), "*:D"
                        ).redirectErrorStream(true).start()

                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val batch = StringBuilder()
                        reader.forEachLine { line ->
                            batch.append(formatLine(line)).append('\n')
                        }
                        reader.close()
                        process.waitFor()
                        flushBatch(batch)

                        // Clear the logcat buffer for our PID to avoid re-reading same lines
                        try {
                            ProcessBuilder("logcat", "-c", "--pid", pid.toString())
                                .redirectErrorStream(true).start().waitFor()
                        } catch (_: Exception) {}
                    } catch (_: Exception) {
                        // If logcat fails, stop capture
                        break
                    }
                    kotlinx.coroutines.delay(2000L)
                }
            } catch (_: Exception) {}
        }
    }

    private fun formatLine(line: String): String {
        val matcher = logcatPattern.matcher(line)
        if (matcher.matches()) {
            val levelChar = matcher.group(1) ?: ""
            val tag = matcher.group(2) ?: ""
            val message = matcher.group(3) ?: ""
            val level = when (levelChar) {
                "V", "D" -> "D"
                "I" -> "I"
                "W" -> "W"
                else -> "E"
            }
            return "${timeFormat.format(Date())} $level($tag): $message"
        }
        val raw = line.replaceFirst(
            Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+"), ""
        )
        return "${timeFormat.format(Date())} D(logcat): $raw"
    }

    private fun flushBatch(batch: StringBuilder) {
        if (batch.isEmpty()) return
        val logFile = File(logDir, "logcat_${dateFormat.format(Date())}.log")
        try {
            logFile.appendText(batch.toString())
        } catch (_: Exception) {}
    }

    fun destroy() {
        captureJob?.cancel()
        captureJob = null
        scope.cancel()
    }
}
