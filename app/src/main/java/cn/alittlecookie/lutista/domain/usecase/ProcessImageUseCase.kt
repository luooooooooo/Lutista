package cn.alittlecookie.lutista.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.data.model.UnifiedLut
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.util.ExifUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProcessImageUseCase(
    private val context: Context,
    private val engine: ImageProcessingEngine,
    private val logger: LogManager,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "ProcessImageUseCase"
    }

    sealed class ProcessResult {
        data class Success(val outputPath: String) : ProcessResult()
        data class Error(val message: String) : ProcessResult()
    }

    suspend operator fun invoke(
        sourcePath: String,
        outputPath: String,
        filter: FilterConfig?,
        unifiedLut: UnifiedLut? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        try {
            val inputBitmap = BitmapFactory.decodeFile(sourcePath)
                ?.let { ExifUtils.correctOrientation(it, sourcePath) }
            if (inputBitmap == null) {
                return@withContext ProcessResult.Error("无法读取图像: $sourcePath")
            }

            val outputBitmap = when {
                unifiedLut != null -> engine.processImageWithUnifiedLut(inputBitmap, unifiedLut)
                filter != null -> engine.processImagePreview(inputBitmap, filter)
                else -> inputBitmap
            }

            if (outputBitmap != inputBitmap) inputBitmap.recycle()

            val outputDir = File(outputPath).parentFile
            if (outputDir != null && !outputDir.exists()) outputDir.mkdirs()

            val ext = outputPath.substringAfterLast('.').lowercase()
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(outputPath).use { outputBitmap.compress(format, 95, it) }
            if (format == Bitmap.CompressFormat.JPEG) {
                // 重建 EXIF：方向固定 1，丢掉源图缩略图 / IFD1 方向标记，尺寸写成实际输出
                try {
                    val sourceExif = androidx.exifinterface.media.ExifInterface(sourcePath)
                    val outputFile = File(outputPath)
                    val clean = ExifUtils.buildCleanJpegExif(
                        jpeg = outputFile.readBytes(),
                        sourceExif = sourceExif,
                        tempFile = File(context.cacheDir, "lutista_exif_${System.nanoTime()}.jpg"),
                        width = outputBitmap.width,
                        height = outputBitmap.height
                    )
                    outputFile.writeBytes(clean)
                } catch (e: Exception) {
                    logger.e(TAG, "写入 EXIF 失败，保留原始输出", e)
                }
            }
            outputBitmap.recycle()

            logger.i(TAG, "图像处理成功: $outputPath")
            ProcessResult.Success(outputPath)
        } catch (e: Exception) {
            logger.e(TAG, "图像处理失败", e)
            ProcessResult.Error(e.message ?: "未知错误")
        }
    }

    suspend fun processFromUri(
        sourceUri: Uri,
        outputDir: File,
        filter: FilterConfig?,
        unifiedLut: UnifiedLut? = null
    ): ProcessResult {
        val tempFile = File(context.cacheDir, "temp_input_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return ProcessResult.Error("无法读取文件")

            val outputFormat = settingsManager.defaultOutputFormat.toString().lowercase()
            val outputFile = File(outputDir, "processed_${System.currentTimeMillis()}.$outputFormat")
            invoke(tempFile.absolutePath, outputFile.absolutePath, filter, unifiedLut)
        } finally {
            tempFile.delete()
        }
    }
}
