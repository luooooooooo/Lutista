package cn.alittlecookie.lutista.service

import android.graphics.BitmapFactory
import cn.alittlecookie.lutista.data.database.entity.TaskEntity
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.data.repository.FilterRepository
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.util.FileUtils
import cn.alittlecookie.lutista.util.FilterConfigJson.toConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImageProcessingService(
    private val engine: ImageProcessingEngine,
    private val filterRepository: FilterRepository,
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
        logger.i(TAG, "开始处理图像: ${task.sourcePath}")

        val sourceFile = File(task.sourcePath)
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("源文件不存在: ${task.sourcePath}")
        }

        onProgress(0.1f)

        val filterConfig = task.filterId?.let { filterId ->
            val entity = filterRepository.getFilterById(filterId)
            entity?.let { mapToFilterConfig(it) }
        }

        onProgress(0.2f)

        val inputBitmap = BitmapFactory.decodeFile(task.sourcePath)
            ?: throw IllegalArgumentException("无法读取图像: ${task.sourcePath}")

        onProgress(0.3f)

        val outputBitmap = if (filterConfig != null) {
            engine.processImagePreview(inputBitmap, filterConfig)
        } else {
            inputBitmap
        }

        onProgress(0.7f)

        val outputPath = if (task.outputPath.isNotEmpty()) {
            task.outputPath
        } else {
            fileUtils.generateOutputPath(task.sourcePath)
        }

        val outputDir = File(outputPath).parentFile
        if (outputDir != null && !outputDir.exists()) outputDir.mkdirs()

        val ext = outputPath.substringAfterLast('.').lowercase()
        val format = when (ext) {
            "png" -> android.graphics.Bitmap.CompressFormat.PNG
            else -> android.graphics.Bitmap.CompressFormat.JPEG
        }
        FileOutputStream(outputPath).use { outputBitmap.compress(format, 95, it) }

        if (outputBitmap != inputBitmap) inputBitmap.recycle()
        outputBitmap.recycle()

        onProgress(1.0f)
        logger.i(TAG, "图像处理完成: $outputPath")
    }

    private fun mapToFilterConfig(entity: cn.alittlecookie.lutista.data.database.entity.FilterEntity): FilterConfig {
        return entity.toConfig()
    }
}
