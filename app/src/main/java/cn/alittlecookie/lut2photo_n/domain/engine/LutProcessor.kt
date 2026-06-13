package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.LutData
import cn.alittlecookie.lut2photo_n.data.model.UnifiedLut
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.LutParser
import org.opencv.core.CvType
import org.opencv.core.Mat

class LutProcessor(
    private val logger: LogManager,
    private val lutParser: LutParser
) {
    companion object {
        private const val TAG = "LutProcessor"
        private const val MAX_FLOAT_ARRAY_SIZE = 16 * 1024 * 1024 // 16MB max float array
    }

    private val lutCache = mutableMapOf<String, LutData>()

    fun process(input: Mat, filter: FilterConfig): Mat {
        var current = input.clone()

        if (filter.lutPath1 != null) {
            val lut1 = loadLut(filter.lutPath1)
            if (lut1 != null) {
                val applied = applyLut(current, lut1)
                current = mixImages(current, applied, filter.lutConcentration1)
            }
        }

        if (filter.lutPath2 != null) {
            val lut2 = loadLut(filter.lutPath2)
            if (lut2 != null) {
                val applied = applyLut(current, lut2)
                current = mixImages(current, applied, filter.lutConcentration2)
            }
        }

        return current
    }

    fun processWithUnifiedLut(input: Mat, unifiedLut: UnifiedLut): Mat {
        val lutData = LutData(size = unifiedLut.size, data = unifiedLut.data)
        return applyLut(input, lutData)
    }

    fun applyLutToColor(color: FloatArray, filter: FilterConfig): FloatArray {
        var result = color.copyOf()

        if (filter.lutPath1 != null) {
            val lut1 = loadLut(filter.lutPath1)
            if (lut1 != null) {
                val applied = lutParser.trilinearInterpolation(lut1, result[0], result[1], result[2])
                result = floatArrayOf(
                    result[0] * (1 - filter.lutConcentration1) + applied[0] * filter.lutConcentration1,
                    result[1] * (1 - filter.lutConcentration1) + applied[1] * filter.lutConcentration1,
                    result[2] * (1 - filter.lutConcentration1) + applied[2] * filter.lutConcentration1
                )
            }
        }

        if (filter.lutPath2 != null) {
            val lut2 = loadLut(filter.lutPath2)
            if (lut2 != null) {
                val applied = lutParser.trilinearInterpolation(lut2, result[0], result[1], result[2])
                result = floatArrayOf(
                    result[0] * (1 - filter.lutConcentration2) + applied[0] * filter.lutConcentration2,
                    result[1] * (1 - filter.lutConcentration2) + applied[1] * filter.lutConcentration2,
                    result[2] * (1 - filter.lutConcentration2) + applied[2] * filter.lutConcentration2
                )
            }
        }

        return result
    }

    private fun loadLut(path: String): LutData? {
        lutCache[path]?.let { return it }

        return try {
            val file = java.io.File(path)
            if (!file.exists()) {
                logger.w(TAG, "LUT file not found: $path")
                return null
            }

            val lut = when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> {
                    logger.w(TAG, "Unsupported LUT format: $path")
                    return null
                }
            }

            val quantized = lutParser.quantizeLut(lut)
            lutCache[path] = quantized
            quantized
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load LUT: $path", e)
            null
        }
    }

    private fun applyLut(input: Mat, lut: LutData): Mat {
        val output = Mat(input.rows(), input.cols(), CvType.CV_8UC3)
        val width = input.cols()
        val height = input.rows()
        val channels = input.channels()

        // Process in chunks to avoid OOM (each row = width * channels * 4 bytes float)
        val chunkRows = (MAX_FLOAT_ARRAY_SIZE / (width * channels * 4)).coerceIn(1, height)

        val inputFloat = Mat()
        input.convertTo(inputFloat, CvType.CV_32FC3, 1.0 / 255.0)

        for (startRow in 0 until height step chunkRows) {
            val endRow = minOf(startRow + chunkRows, height)
            val rowCount = endRow - startRow
            val rowData = FloatArray(rowCount * width * channels)
            val outData = FloatArray(rowData.size)

            inputFloat.rowRange(startRow, endRow).get(0, 0, rowData)

            for (i in rowData.indices step 3) {
                val r = rowData[i].coerceIn(0f, 1f)
                val g = rowData[i + 1].coerceIn(0f, 1f)
                val b = rowData[i + 2].coerceIn(0f, 1f)
                val result = lutParser.trilinearInterpolation(lut, r, g, b)
                outData[i] = result[0].coerceIn(0f, 1f)
                outData[i + 1] = result[1].coerceIn(0f, 1f)
                outData[i + 2] = result[2].coerceIn(0f, 1f)
            }

            val chunkMat = Mat(rowCount, width, CvType.CV_32FC3)
            chunkMat.put(0, 0, outData)
            val chunkByte = Mat()
            chunkMat.convertTo(chunkByte, CvType.CV_8UC3, 255.0)
            chunkMat.release()

            chunkByte.copyTo(output.rowRange(startRow, endRow))
            chunkByte.release()
        }

        inputFloat.release()
        return output
    }

    private fun mixImages(original: Mat, processed: Mat, concentration: Float): Mat {
        if (concentration >= 1.0f) return processed
        if (concentration <= 0.0f) return original

        val result = Mat()
        org.opencv.core.Core.addWeighted(
            original, 1.0 - concentration,
            processed, concentration.toDouble(),
            0.0, result
        )
        return result
    }

    fun clearCache() {
        lutCache.clear()
    }

    fun loadLutForGpu(path: String): LutData? = loadLut(path)
}
