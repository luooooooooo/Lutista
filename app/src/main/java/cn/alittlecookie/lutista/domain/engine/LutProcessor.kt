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
        private const val MAX_CACHED_LUTS = 8
    }

    /**
     * 解析好的 LUT 按路径缓存，避免来回切滤镜时反复解析 1MB 的 .cube。
     * 但一个 64³ 的 LUT 展开是 3MB float，滤镜多了会一直涨，所以做成有上限的 LRU：
     * 只保留最近用过的 8 份，超出的丢给 GC。
     */
    private val lutCache = object : LinkedHashMap<String, LutData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LutData>) =
            size > MAX_CACHED_LUTS
    }

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
        synchronized(lutCache) { lutCache[path] }?.let { return it }
        return try {
            val file = java.io.File(path)
            if (!file.exists()) { logger.w(TAG, "LUT file not found: $path"); return null }
            val lut = when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> { logger.w(TAG, "Unsupported LUT format: $path"); return null }
            }
            val quantized = lutParser.quantizeLut(lut)
            synchronized(lutCache) { lutCache[path] = quantized }
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
        // 逐像素复用同一块 out[3]，不再每个像素 new FloatArray
        val out = FloatArray(3)

        for (i in 0 until pixelCount) {
            val base = i * 4
            val r = (rgbaBytes[base].toInt() and 0xFF) * inv255
            val g = (rgbaBytes[base + 1].toInt() and 0xFF) * inv255
            val b = (rgbaBytes[base + 2].toInt() and 0xFF) * inv255
            lutParser.trilinearInterpolation(lut, r, g, b, out)
            resultBytes[base] = (out[0].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 1] = (out[1].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 2] = (out[2].coerceIn(0f, 1f) * 255f).toInt().toByte()
            resultBytes[base + 3] = rgbaBytes[base + 3] // preserve alpha
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.copyPixelsFromBuffer(ByteBuffer.wrap(resultBytes))
        return result
    }

    fun clearCache() {
        synchronized(lutCache) { lutCache.clear() }
    }

    fun loadLutForGpu(path: String, targetSize: Int): LutData? {
        val cacheKey = "$path@$targetSize"
        synchronized(lutCache) { lutCache[cacheKey] }?.let { return it }
        return try {
            val file = java.io.File(path)
            if (!file.exists()) { logger.w(TAG, "LUT file not found: $path"); return null }
            val parsed = when {
                path.endsWith(".cube", ignoreCase = true) -> lutParser.parseCube(file)
                path.endsWith(".vlt", ignoreCase = true) -> lutParser.parseVlt(file)
                else -> { logger.w(TAG, "Unsupported LUT format: $path"); return null }
            }
            val quantized = lutParser.quantizeLut(parsed, targetSize)
            synchronized(lutCache) { lutCache[cacheKey] = quantized }
            quantized
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load LUT: $path", e)
            null
        }
    }
}
