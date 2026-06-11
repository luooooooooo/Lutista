package cn.alittlecookie.lut2photo_n.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.UnifiedLut
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
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
            val inputMat = Imgcodecs.imread(sourcePath, Imgcodecs.IMREAD_COLOR)
            if (inputMat.empty()) {
                return@withContext ProcessResult.Error("无法读取图像: $sourcePath")
            }

            val outputMat = when {
                unifiedLut != null -> engine.processImageWithUnifiedLut(inputMat, unifiedLut)
                filter != null -> engine.processImagePreview(inputMat, filter)
                else -> inputMat
            }

            val outputDir = File(outputPath).parentFile
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs()
            }

            val success = Imgcodecs.imwrite(outputPath, outputMat)

            inputMat.release()
            outputMat.release()

            if (success) {
                logger.i(TAG, "图像处理成功: $outputPath")
                ProcessResult.Success(outputPath)
            } else {
                ProcessResult.Error("保存图像失败")
            }
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
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return ProcessResult.Error("无法读取文件")

            val outputFormat = settingsManager.defaultOutputFormat.toString().lowercase()
            val outputFile = File(outputDir, "processed_${System.currentTimeMillis()}.$outputFormat")

            invoke(tempFile.absolutePath, outputFile.absolutePath, filter, unifiedLut)
        } finally {
            tempFile.delete()
        }
    }

    fun loadBitmapAsMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        return mat
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }
}
