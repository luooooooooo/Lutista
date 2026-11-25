package cn.alittlecookie.lutista.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cn.alittlecookie.lutista.data.database.entity.TaskEntity
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.data.repository.FilterRepository
import cn.alittlecookie.lutista.data.repository.WatermarkRepository
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.util.ExifResolution
import cn.alittlecookie.lutista.util.ExifUtils
import cn.alittlecookie.lutista.util.FileUtils
import cn.alittlecookie.lutista.util.FilenameExpander
import cn.alittlecookie.lutista.util.FilterConfigJson.toConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ImageProcessingService(
    private val context: Context,
    private val engine: ImageProcessingEngine,
    private val filterRepository: FilterRepository,
    private val watermarkRepository: WatermarkRepository,
    private val settingsManager: SettingsManager,
    private val fileUtils: FileUtils,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "ImageProcessingService"
    }

    suspend fun processImage(
        task: TaskEntity,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val sourcePath = task.sourcePath
        val isCacheFile = sourcePath.startsWith(context.cacheDir.absolutePath)
        var inputBitmap: Bitmap? = null
        var outputBitmap: Bitmap? = null

        try {
            logger.i(TAG, "开始处理图像: $sourcePath")

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                throw IllegalArgumentException("源文件不存在: $sourcePath")
            }

            onProgress(0.05f)

            // Read EXIF + APP1 from source file before processing
            val sourceBytes = sourceFile.readBytes()
            val app1Segment = ExifUtils.extractApp1FromJpeg(sourceBytes)
            val exifResolution = if (app1Segment != null) {
                val exif = try {
                    androidx.exifinterface.media.ExifInterface(sourceFile.absolutePath)
                } catch (_: Exception) { null }
                if (exif != null) ExifResolution(exif, app1Segment) else null
            } else null
            logger.d(TAG, "EXIF: hasApp1=${app1Segment != null}")

            onProgress(0.1f)

            val filterConfig = resolveFilterConfig(task)
            onProgress(0.15f)

            inputBitmap = BitmapFactory.decodeFile(sourcePath)
                ?.let { ExifUtils.correctOrientation(it, sourcePath) }
                ?: throw IllegalArgumentException("无法读取图像: $sourcePath")

            onProgress(0.2f)

            outputBitmap = if (filterConfig != null) {
                engine.processImagePreview(inputBitmap, filterConfig)
            } else {
                inputBitmap
            }

            onProgress(0.5f)

            val watermarkScene = resolveWatermarkScene(task)
            if (watermarkScene != null) {
                outputBitmap = engine.applyWatermark(outputBitmap, watermarkScene)
            }

            onProgress(0.7f)

            val outputFormat = settingsManager.defaultOutputFormat.first()
            val ext = when (outputFormat) {
                cn.alittlecookie.lutista.manager.OutputFormat.PNG -> "png"
                else -> "jpg"
            }
            val quality = when (outputFormat) {
                cn.alittlecookie.lutista.manager.OutputFormat.PNG -> settingsManager.pngCompression.first()
                else -> settingsManager.jpgQuality.first()
            }
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }

            val outputPath = resolveOutputPath(task, ext, filterConfig?.name)
            saveBitmapWithExif(outputBitmap, outputPath, format, quality, exifResolution)

            onProgress(1.0f)
            logger.i(TAG, "图像处理完成: $outputPath")
        } finally {
            if (outputBitmap != inputBitmap) {
                inputBitmap?.recycle()
            }
            outputBitmap?.recycle()

            if (isCacheFile) {
                try { File(sourcePath).delete() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun resolveFilterConfig(task: TaskEntity): FilterConfig? {
        val filterId = task.filterId ?: run {
            val globalId = settingsManager.globalFilterId.first()
            if (globalId > 0L) globalId else return null
        }
        val entity = filterRepository.getFilterById(filterId) ?: return null
        return entity.toConfig()
    }

    private suspend fun resolveWatermarkScene(task: TaskEntity): cn.alittlecookie.lutista.watermark.model.Scene? {
        val watermarkId = if (task.watermarkId != null) {
            task.watermarkId
        } else {
            val globalId = settingsManager.globalWatermarkId.first()
            if (globalId == "none" || globalId.isEmpty()) return null
            globalId.toLongOrNull() ?: return null
        }
        if (watermarkId <= 0L) return null
        return watermarkRepository.getSceneById(watermarkId)
    }

    private suspend fun resolveOutputPath(task: TaskEntity, ext: String, filterName: String?): String {
        if (task.outputPath.isNotEmpty()) return task.outputPath

        val name = generateOutputFileName(task.sourcePath, ext, filterName)
        val customPath = settingsManager.outputPath.first()
        if (customPath.isEmpty()) {
            val dir = fileUtils.getDefaultOutputDir()
            return fileUtils.buildOutputPath(dir, name)
        }

        if (customPath.startsWith("content://")) {
            val baseUri = Uri.parse(customPath)
            val fileUri = fileUtils.createOutputFileInSafDir(baseUri, name, "image/$ext")
                ?: return fallbackToDefaultFile(name)
            return fileUri.toString()
        }

        val dir = fileUtils.getOutputDir(customPath)
        return fileUtils.buildOutputPath(dir, name)
    }

    private fun fallbackToDefaultFile(name: String): String {
        val dir = fileUtils.getDefaultOutputDir()
        return fileUtils.buildOutputPath(dir, name)
    }

    private suspend fun generateOutputFileName(sourcePath: String, ext: String, filterName: String?): String {
        val fname = FileUtils.extractBaseName(sourcePath)
        val filter = filterName ?: ""
        val template = settingsManager.outputFilenameTemplate.first()
        val daily = settingsManager.getDailyCounter()
        val monthly = settingsManager.getMonthlyCounter()
        val yearly = settingsManager.getYearlyCounter()
        return "${FilenameExpander.expand(template, fname, filter, daily, monthly, yearly)}.$ext"
    }

    private fun saveBitmapWithExif(bitmap: Bitmap, outputPath: String, format: Bitmap.CompressFormat, quality: Int, exifResolution: ExifResolution?) {
        val bos = ByteArrayOutputStream()
        bitmap.compress(format, quality, bos)
        var outputBytes = bos.toByteArray()

        // 重新生成 EXIF：方向固定 1（像素已转正），不带源图缩略图 / IFD1 方向标记
        if (format == Bitmap.CompressFormat.JPEG) {
            outputBytes = try {
                ExifUtils.buildCleanJpegExif(
                    jpeg = outputBytes,
                    sourceExif = exifResolution?.exif,
                    tempFile = File(context.cacheDir, "lutista_exif_${System.nanoTime()}.jpg"),
                    width = bitmap.width,
                    height = bitmap.height
                )
            } catch (e: Exception) {
                logger.e(TAG, "写入 EXIF 失败，保留原始输出", e)
                outputBytes
            }
        }

        if (outputPath.startsWith("content://")) {
            context.contentResolver.openOutputStream(Uri.parse(outputPath))?.use { stream ->
                stream.write(outputBytes)
            } ?: throw IllegalStateException("无法打开输出流: $outputPath")
        } else {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            file.writeBytes(outputBytes)
        }
    }
}
