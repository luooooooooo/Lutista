package cn.alittlecookie.lut2photo_n.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cn.alittlecookie.lut2photo_n.MainActivity
import cn.alittlecookie.lut2photo_n.R
import cn.alittlecookie.lut2photo_n.data.database.entity.TaskSource
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FileMonitorService : LifecycleService(), KoinComponent {

    private val taskSubmissionManager: TaskSubmissionManager by inject()
    private val logger: LogManager by inject()
    private val settingsManager: SettingsManager by inject()

    private val scannedFiles = ConcurrentHashMap.newKeySet<String>()
    private val skippedFiles = ConcurrentHashMap.newKeySet<String>()
    private var monitorPath: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                monitorPath = intent.getStringExtra(EXTRA_PATH) ?: ""
                if (monitorPath.isNotEmpty()) {
                    startMonitoring()
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startMonitoring() {
        logger.i(TAG, "开始监控文件夹: $monitorPath")

        lifecycleScope.launch {
            while (isActive) {
                try {
                    scanAndProcessFiles()
                } catch (e: Exception) {
                    logger.e(TAG, "扫描文件夹异常", e)
                }
                delay(SCAN_INTERVAL)
            }
        }
    }

    private suspend fun scanAndProcessFiles() = withContext(Dispatchers.IO) {
        val dir = File(monitorPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext

        val imageFiles = dir.listFiles { file ->
            file.isFile && isImageFile(file.name)
        } ?: return@withContext

        for (file in imageFiles) {
            val path = file.absolutePath
            if (path in scannedFiles || path in skippedFiles) continue

            try {
                if (!file.canRead()) {
                    logger.w(TAG, "文件不可读，跳过: $path")
                    skippedFiles.add(path)
                    continue
                }

                if (isFileStable(file)) {
                    scannedFiles.add(path)
                    taskSubmissionManager.submitTask(
                        sourcePath = path,
                        source = TaskSource.MONITOR
                    )
                    logger.i(TAG, "发现新文件: $path")
                }
            } catch (e: Exception) {
                logger.e(TAG, "处理文件异常: $path", e)
                skippedFiles.add(path)
            }
        }
    }

    private suspend fun isFileStable(file: File): Boolean = withContext(Dispatchers.IO) {
        val size1 = file.length()
        delay(2000)
        val size2 = file.length()
        size1 == size2 && size1 > 0
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun retrySkippedFiles() {
        val files = skippedFiles.toList()
        skippedFiles.clear()
        files.forEach { path ->
            scannedFiles.remove(path)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监控文件夹中的新图片"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lut2Photo-N")
            .setContentText("正在监控文件夹: $monitorPath")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.i(TAG, "文件监控服务已停止")
    }

    companion object {
        private const val TAG = "FileMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "file_monitor_channel"
        private const val SCAN_INTERVAL = 3000L
        const val ACTION_START = "cn.alittlecookie.lut2photo_n.START_MONITOR"
        const val ACTION_STOP = "cn.alittlecookie.lut2photo_n.STOP_MONITOR"
        const val EXTRA_PATH = "monitor_path"

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "tiff", "dng")
    }
}
