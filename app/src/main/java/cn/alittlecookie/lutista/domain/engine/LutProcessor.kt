package cn.alittlecookie.lutista.domain.engine

import android.graphics.Bitmap
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.data.model.LutData
import cn.alittlecookie.lutista.data.model.UnifiedLut
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.util.LutParser
import java.nio.ByteBuffer

class LutProcessor(
    private val logger: LogManager,
    private val lutParser: LutParser
) {
    companion object {
        private const val TAG = "LutProcessor"
    }

    private val lutCache = mutableMapOf<String, LutData>()

    fun processWithUnifiedLut(bitmap: Bitmap, unifiedLut: UnifiedLut): Bitmap {
        val lutData = LutData(size = unifiedLut.size, data = unifiedLut.data)
        return applyLut(bitmap, lutData)
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
            if (!file.exists()) { logger.w(TAG, "LUT file not found: $path"); return null }
            val lut = when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> { logger.w(TAG, "Unsupported LUT format: $path"); return null }
            }
            val quantized = lutParser.quantizeLut(lut)
            lutCache[path] = quantized
            quantized
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load LUT: $path", e)
            null
        }
    }

    private fun applyLut(bitmap: Bitmap, lut: LutData): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height

        val rgbaBytes = ByteArray(pixelCount * 4)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(rgbaBytes))

        val resultBytes = ByteArray(pixelCount * 4)
        val inv255 = 1f / 255f

        for (i in 0 until pixelCount) {
            val base = i * 4
            val r = (rgbaBytes[base].toInt() and 0xFF) * inv255
            val g = (rgbaBytes[base + 1].toInt() and 0xFF) * inv255
            val b = (rgbaBytes[base + 2].toInt() and 0xFF) * inv255
            val result = lutParser.trilinearInterpolation(lut, r, g, b)
            resultBytes[base] = (result[0].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 1] = (result[1].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 2] = (result[2].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 3] = rgbaBytes[base + 3] // preserve alpha
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.copyPixelsFromBuffer(ByteBuffer.wrap(resultBytes))
        return result
    }

    fun clearCache() {
        lutCache.clear()
    }

    fun loadLutForGpu(path: String, targetSize: Int): LutData? {
        val cacheKey = "$path@$targetSize"
        lutCache[cacheKey]?.let { return it }
        return try {
            val file = java.io.File(path)
            if (!file.exists()) { logger.w(TAG, "LUT file not found: $path"); return null }
            val parsed = when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> { logger.w(TAG, "Unsupported LUT format: $path"); return null }
            }
            val quantized = lutParser.quantizeLut(parsed, targetSize)
            lutCache[cacheKey] = quantized
            quantized
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load LUT: $path", e)
            null
        }
    }
}
