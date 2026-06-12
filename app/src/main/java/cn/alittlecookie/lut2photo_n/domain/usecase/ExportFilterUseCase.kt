package cn.alittlecookie.lut2photo_n.domain.usecase

import android.content.Context
import cn.alittlecookie.lut2photo_n.data.database.entity.FilterEntity
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.LutData
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.LutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportFilterUseCase(
    private val context: Context,
    private val filterRepository: FilterRepository,
    private val lutParser: LutParser,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "ExportFilterUseCase"
        private const val DEFAULT_UNIFIED_LUT_SIZE = 33
    }

    sealed class ExportResult {
        data class Success(val filePath: String, val exportType: ExportType) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    enum class ExportType {
        UNIFIED_LUT,    // 统一 LUT (.cube)
        FILTER_CONFIG   // 滤镜配置 (.zip)
    }

    suspend fun exportUnifiedLut(filterId: Long): ExportResult = withContext(Dispatchers.IO) {
        try {
            val filter = filterRepository.getFilterById(filterId)
                ?: return@withContext ExportResult.Error("滤镜不存在")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(context.cacheDir, "export")
            exportDir.mkdirs()
            val outputFile = File(exportDir, "${filter.name}_unified_$timestamp.cube")

            val unifiedLutData = computeUnifiedLut(filter)
            writeCubeFile(outputFile, unifiedLutData, filter.name)

            logger.i(TAG, "成功导出统一 LUT: ${outputFile.absolutePath}")
            ExportResult.Success(outputFile.absolutePath, ExportType.UNIFIED_LUT)
        } catch (e: Exception) {
            logger.e(TAG, "导出统一 LUT 失败", e)
            ExportResult.Error(e.message ?: "未知错误")
        }
    }

    suspend fun exportFilterConfig(filterId: Long): ExportResult = withContext(Dispatchers.IO) {
        try {
            val filter = filterRepository.getFilterById(filterId)
                ?: return@withContext ExportResult.Error("滤镜不存在")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(context.cacheDir, "export")
            exportDir.mkdirs()
            val zipFile = File(exportDir, "${filter.name}_config_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                filter.lutPath1?.let { lutPath ->
                    val lutFile = File(lutPath)
                    if (lutFile.exists()) {
                        val entryName = "${filter.name}1.cube"
                        zos.putNextEntry(ZipEntry(entryName))
                        lutFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        logger.d(TAG, "添加 LUT1: $entryName")
                    }
                }

                filter.lutPath2?.let { lutPath ->
                    val lutFile = File(lutPath)
                    if (lutFile.exists()) {
                        val entryName = "${filter.name}2.cube"
                        zos.putNextEntry(ZipEntry(entryName))
                        lutFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        logger.d(TAG, "添加 LUT2: $entryName")
                    }
                }

                val configJson = createFilterConfigJson(filter)
                zos.putNextEntry(ZipEntry("${filter.name}.json"))
                zos.write(configJson.toString(2).toByteArray())
                zos.closeEntry()
                logger.d(TAG, "添加配置文件: ${filter.name}.json")
            }

            logger.i(TAG, "成功导出滤镜配置: ${zipFile.absolutePath}")
            ExportResult.Success(zipFile.absolutePath, ExportType.FILTER_CONFIG)
        } catch (e: Exception) {
            logger.e(TAG, "导出滤镜配置失败", e)
            ExportResult.Error(e.message ?: "未知错误")
        }
    }

    private fun computeUnifiedLut(filter: FilterEntity): LutData {
        val size = filter.lutPrecision.coerceIn(2, 128)
        val totalPoints = size * size * size
        val data = FloatArray(totalPoints * 3)

        val lut1 = filter.lutPath1?.let { loadLutData(it) }
        val lut2 = filter.lutPath2?.let { loadLutData(it) }

        val filterConfig = FilterConfig(
            exposure = filter.exposure,
            contrast = filter.contrast,
            highlights = filter.highlights,
            shadows = filter.shadows,
            saturation = filter.saturation,
            colorTemperature = filter.colorTemperature,
            tint = filter.tint,
            lutConcentration1 = filter.lutConcentration1,
            lutConcentration2 = filter.lutConcentration2
        )

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val idx = (b * size * size + g * size + r) * 3
                    val red = r.toFloat() / (size - 1)
                    val green = g.toFloat() / (size - 1)
                    val blue = b.toFloat() / (size - 1)

                    var color = floatArrayOf(red, green, blue)

                    if (lut1 != null) {
                        val applied = lutParser.trilinearInterpolation(lut1, color[0], color[1], color[2])
                        color = floatArrayOf(
                            color[0] * (1 - filter.lutConcentration1) + applied[0] * filter.lutConcentration1,
                            color[1] * (1 - filter.lutConcentration1) + applied[1] * filter.lutConcentration1,
                            color[2] * (1 - filter.lutConcentration1) + applied[2] * filter.lutConcentration1
                        )
                    }

                    if (lut2 != null) {
                        val applied = lutParser.trilinearInterpolation(lut2, color[0], color[1], color[2])
                        color = floatArrayOf(
                            color[0] * (1 - filter.lutConcentration2) + applied[0] * filter.lutConcentration2,
                            color[1] * (1 - filter.lutConcentration2) + applied[1] * filter.lutConcentration2,
                            color[2] * (1 - filter.lutConcentration2) + applied[2] * filter.lutConcentration2
                        )
                    }

                    if (filterConfig.exposure != 0f) {
                        val factor = Math.pow(2.0, filterConfig.exposure.toDouble()).toFloat()
                        color = floatArrayOf(color[0] * factor, color[1] * factor, color[2] * factor)
                    }

                    if (filterConfig.contrast != 0f) {
                        val factor = (100f + filterConfig.contrast) / 100f
                        color = floatArrayOf(
                            ((color[0] - 0.5f) * factor + 0.5f),
                            ((color[1] - 0.5f) * factor + 0.5f),
                            ((color[2] - 0.5f) * factor + 0.5f)
                        )
                    }

                    if (filterConfig.saturation != 0f) {
                        val gray = color[0] * 0.299f + color[1] * 0.587f + color[2] * 0.114f
                        val factor = 1f + filterConfig.saturation / 100f
                        color = floatArrayOf(
                            (gray + (color[0] - gray) * factor),
                            (gray + (color[1] - gray) * factor),
                            (gray + (color[2] - gray) * factor)
                        )
                    }

                    if (filterConfig.colorTemperature != 0f) {
                        val adjustment = filterConfig.colorTemperature / 200f
                        color = floatArrayOf(
                            (color[0] + adjustment),
                            color[1],
                            (color[2] - adjustment)
                        )
                    }

                    if (filterConfig.tint != 0f) {
                        val adjustment = filterConfig.tint / 200f
                        color = floatArrayOf(
                            color[0],
                            (color[1] + adjustment),
                            color[2]
                        )
                    }

                    data[idx] = color[0].coerceIn(0f, 1f)
                    data[idx + 1] = color[1].coerceIn(0f, 1f)
                    data[idx + 2] = color[2].coerceIn(0f, 1f)
                }
            }
        }

        return LutData(size = size, data = data, title = filter.name)
    }

    private fun loadLutData(path: String): LutData? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> null
            }
        } catch (e: Exception) {
            logger.e(TAG, "加载 LUT 失败: $path", e)
            null
        }
    }

    private fun writeCubeFile(file: File, lutData: LutData, title: String) {
        file.bufferedWriter().use { writer ->
            writer.write("# Generated by Lut2Photo")
            writer.newLine()
            writer.write("TITLE \"$title\"")
            writer.newLine()
            writer.write("LUT_3D_SIZE ${lutData.size}")
            writer.newLine()
            writer.write("DOMAIN_MIN 0.0 0.0 0.0")
            writer.newLine()
            writer.write("DOMAIN_MAX 1.0 1.0 1.0")
            writer.newLine()

            val size = lutData.size
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val idx = (b * size * size + g * size + r) * 3
                        val red = "%.6f".format(lutData.data[idx].coerceIn(0f, 1f))
                        val green = "%.6f".format(lutData.data[idx + 1].coerceIn(0f, 1f))
                        val blue = "%.6f".format(lutData.data[idx + 2].coerceIn(0f, 1f))
                        writer.write("$red $green $blue")
                        writer.newLine()
                    }
                }
            }
        }
    }

    private fun createFilterConfigJson(filter: FilterEntity): JSONObject {
        return JSONObject().apply {
            put("name", filter.name)
            put("version", 1)
            put("lutConcentration1", filter.lutConcentration1.toDouble())
            put("lutConcentration2", filter.lutConcentration2.toDouble())
            put("lutPrecision", filter.lutPrecision)
            put("exposure", filter.exposure.toDouble())
            put("contrast", filter.contrast.toDouble())
            put("highlights", filter.highlights.toDouble())
            put("shadows", filter.shadows.toDouble())
            put("saturation", filter.saturation.toDouble())
            put("colorTemperature", filter.colorTemperature.toDouble())
            put("tint", filter.tint.toDouble())
            put("curveJson", filter.curveJson)
            put("lchJson", filter.lchJson)
            put("vignetteStrength", filter.vignetteStrength.toDouble())
            put("grainJson", filter.grainJson)
        }
    }
}
