package cn.alittlecookie.lut2photo_n.domain.usecase

import android.content.Context
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.manager.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportFilterUseCase(
    private val context: Context,
    private val filterRepository: FilterRepository,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "ExportFilterUseCase"
    }

    sealed class ExportResult {
        data class Success(val filePath: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    suspend operator fun invoke(filterId: Long): ExportResult = withContext(Dispatchers.IO) {
        try {
            val filter = filterRepository.getFilterById(filterId)
                ?: return@withContext ExportResult.Error("滤镜不存在")

            val exportDir = File(context.cacheDir, "export")
            exportDir.mkdirs()
            val zipFile = File(exportDir, "${filter.name}.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                if (filter.lutPath1 != null) {
                    val lutFile = File(filter.lutPath1)
                    if (lutFile.exists()) {
                        zos.putNextEntry(ZipEntry(lutFile.name))
                        lutFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                if (filter.lutPath2 != null) {
                    val lutFile = File(filter.lutPath1!!)
                    if (lutFile.exists()) {
                        zos.putNextEntry(ZipEntry(lutFile.name))
                        lutFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                val configJson = JSONObject().apply {
                    put("name", filter.name)
                    put("lutConcentration1", filter.lutConcentration1)
                    put("lutConcentration2", filter.lutConcentration2)
                    put("lutPrecision", filter.lutPrecision)
                    put("exposure", filter.exposure)
                    put("contrast", filter.contrast)
                    put("highlights", filter.highlights)
                    put("shadows", filter.shadows)
                    put("saturation", filter.saturation)
                    put("colorTemperature", filter.colorTemperature)
                    put("tint", filter.tint)
                    put("curveJson", filter.curveJson)
                    put("lchJson", filter.lchJson)
                    put("vignetteStrength", filter.vignetteStrength)
                    put("grainJson", filter.grainJson)
                }

                zos.putNextEntry(ZipEntry("config.json"))
                zos.write(configJson.toString().toByteArray())
                zos.closeEntry()
            }

            logger.i(TAG, "成功导出滤镜: ${filter.name}")
            ExportResult.Success(zipFile.absolutePath)
        } catch (e: Exception) {
            logger.e(TAG, "导出滤镜失败", e)
            ExportResult.Error(e.message ?: "未知错误")
        }
    }
}
