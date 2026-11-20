package cn.alittlecookie.lutista.util

import cn.alittlecookie.lutista.data.model.LutData
import cn.alittlecookie.lutista.manager.LogManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

class LutParser(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "LutParser"
        private const val DEFAULT_LUT_SIZE = 33
        private val WHITESPACE_REGEX = Regex("\\s+")
        /** 33³ × 3 约 108k 个 float，预留够一个标准 LUT 的容量，省掉反复扩容 */
        private const val INITIAL_CAPACITY = 33 * 33 * 33 * 3
    }

    fun parseCube(file: File): LutData {
        return parseCube(file.inputStream())
    }

    fun parseCube(inputStream: InputStream): LutData {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var title = ""
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        // 直接写进 FloatArray：原来用 mutableListOf<Float> 会让 10 万级的采样点全部装箱，
        // 一个 33³ 的 LUT 要产生几万个 Float 对象和临时 List，解析一次就是上百 MB 的垃圾。
        var data = FloatArray(INITIAL_CAPACITY)
        var count = 0

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed[0] == '#') continue
                when {
                    trimmed.startsWith("TITLE") -> {
                        title = trimmed.substringAfter("TITLE").trim().removeSurrounding("\"")
                    }
                    trimmed.startsWith("LUT_3D_SIZE") -> {
                        size = trimmed.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                        if (size > 0) {
                            val needed = size * size * size * 3
                            if (data.size < needed) data = FloatArray(needed)
                        }
                    }
                    trimmed.startsWith("DOMAIN_MIN") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MIN").trim().split(WHITESPACE_REGEX)
                        domainMin = parts.map { it.toFloat() }.toFloatArray()
                    }
                    trimmed.startsWith("DOMAIN_MAX") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MAX").trim().split(WHITESPACE_REGEX)
                        domainMax = parts.map { it.toFloat() }.toFloatArray()
                    }
                    isDataLine(trimmed) -> {
                        val parts = trimmed.split(WHITESPACE_REGEX)
                        if (parts.size >= 3) {
                            if (count + 3 > data.size) data = data.copyOf(maxOf(data.size * 2, count + 3))
                            data[count] = parts[0].toFloat()
                            data[count + 1] = parts[1].toFloat()
                            data[count + 2] = parts[2].toFloat()
                            count += 3
                        }
                    }
                }
            }
        }

        if (size == 0) {
            size = DEFAULT_LUT_SIZE
            logger.w(TAG, "LUT size not specified, defaulting to $DEFAULT_LUT_SIZE")
        }

        if (count == 0) {
            throw IllegalArgumentException("No LUT data found in Cube file")
        }

        return LutData(
            size = size,
            data = if (count == data.size) data else data.copyOf(count),
            title = title
        )
    }

    fun parseVlt(file: File): LutData {
        return parseVlt(file.inputStream())
    }

    fun parseVlt(inputStream: InputStream): LutData {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var title = ""
        var size = 0
        var data = FloatArray(INITIAL_CAPACITY)
        var count = 0

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed[0] == '#') continue
                when {
                    trimmed.startsWith("Title") -> {
                        title = trimmed.substringAfter("Title").trim().removeSurrounding("\"")
                    }
                    trimmed.startsWith("3DLUT_Size") || trimmed.startsWith("LUT_3D_SIZE") -> {
                        size = trimmed.substringAfter(":").trim().substringAfter("3DLUT_Size").trim().toIntOrNull()
                            ?: trimmed.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                        if (size > 0) {
                            val needed = size * size * size * 3
                            if (data.size < needed) data = FloatArray(needed)
                        }
                    }
                    isDataLine(trimmed) -> {
                        val parts = trimmed.split(WHITESPACE_REGEX)
                        if (parts.size >= 3) {
                            if (count + 3 > data.size) data = data.copyOf(maxOf(data.size * 2, count + 3))
                            data[count] = parts[0].toFloat()
                            data[count + 1] = parts[1].toFloat()
                            data[count + 2] = parts[2].toFloat()
                            count += 3
                        }
                    }
                }
            }
        }

        if (size == 0) {
            size = DEFAULT_LUT_SIZE
            logger.w(TAG, "VLT size not specified, defaulting to $DEFAULT_LUT_SIZE")
        }

        if (count == 0) {
            throw IllegalArgumentException("No LUT data found in VLT file")
        }

        return normalizeVltToCube(LutData(size = size, data = data.copyOf(count), title = title))
    }

    /** 数据行判定：以数字/正负号/小数点开头，避免对每行都跑一次正则 */
    private fun isDataLine(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        val c = trimmed[0]
        return c.isDigit() || c == '-' || c == '+' || c == '.'
    }

    private fun normalizeVltToCube(vltData: LutData): LutData {
        logger.d(TAG, "Normalizing VLT to Cube format: size=${vltData.size}")
        return vltData
    }

    fun quantizeLut(lutData: LutData, targetSize: Int = DEFAULT_LUT_SIZE): LutData {
        if (lutData.size == targetSize) return lutData

        logger.d(TAG, "Quantizing LUT from ${lutData.size} to $targetSize")
        val newData = FloatArray(targetSize * targetSize * targetSize * 3)
        val scale = (lutData.size - 1).toFloat() / (targetSize - 1).toFloat()

        for (b in 0 until targetSize) {
            for (g in 0 until targetSize) {
                for (r in 0 until targetSize) {
                    val srcR = r * scale
                    val srcG = g * scale
                    val srcB = b * scale

                    val r0 = srcR.toInt().coerceIn(0, lutData.size - 2)
                    val g0 = srcG.toInt().coerceIn(0, lutData.size - 2)
                    val b0 = srcB.toInt().coerceIn(0, lutData.size - 2)
                    val r1 = (r0 + 1).coerceAtMost(lutData.size - 1)
                    val g1 = (g0 + 1).coerceAtMost(lutData.size - 1)
                    val b1 = (b0 + 1).coerceAtMost(lutData.size - 1)

                    val fr = srcR - r0
                    val fg = srcG - g0
                    val fb = srcB - b0

                    val dstIdx = (b * targetSize * targetSize + g * targetSize + r) * 3

                    for (ch in 0..2) {
                        val c000 = getLutValue(lutData, r0, g0, b0, ch)
                        val c100 = getLutValue(lutData, r1, g0, b0, ch)
                        val c010 = getLutValue(lutData, r0, g1, b0, ch)
                        val c110 = getLutValue(lutData, r1, g1, b0, ch)
                        val c001 = getLutValue(lutData, r0, g0, b1, ch)
                        val c101 = getLutValue(lutData, r1, g0, b1, ch)
                        val c011 = getLutValue(lutData, r0, g1, b1, ch)
                        val c111 = getLutValue(lutData, r1, g1, b1, ch)

                        val c00 = c000 * (1 - fr) + c100 * fr
                        val c10 = c010 * (1 - fr) + c110 * fr
                        val c01 = c001 * (1 - fr) + c101 * fr
                        val c11 = c011 * (1 - fr) + c111 * fr

                        val c0 = c00 * (1 - fg) + c10 * fg
                        val c1 = c01 * (1 - fg) + c11 * fg

                        newData[dstIdx + ch] = c0 * (1 - fb) + c1 * fb
                    }
                }
            }
        }

        return LutData(size = targetSize, data = newData, title = lutData.title)
    }

    private fun getLutValue(lut: LutData, r: Int, g: Int, b: Int, channel: Int): Float {
        val idx = (b * lut.size * lut.size + g * lut.size + r) * 3 + channel
        return if (idx < lut.data.size) lut.data[idx] else 0f
    }

    fun trilinearInterpolation(
        lut: LutData,
        r: Float,
        g: Float,
        b: Float
    ): FloatArray {
        val result = FloatArray(3)
        trilinearInterpolation(lut, r, g, b, result)
        return result
    }

    /**
     * 逐像素插值时用这个版本：结果写进调用方复用的 out[3]，
     * 免得整幅图每个像素都新建一个 FloatArray（1024×1365 就是 140 万次分配）。
     */
    fun trilinearInterpolation(
        lut: LutData,
        r: Float,
        g: Float,
        b: Float,
        out: FloatArray
    ) {
        val size = lut.size
        val maxIdx = (size - 1).toFloat()

        val rIdx = (r * maxIdx).coerceIn(0f, maxIdx)
        val gIdx = (g * maxIdx).coerceIn(0f, maxIdx)
        val bIdx = (b * maxIdx).coerceIn(0f, maxIdx)

        val r0 = rIdx.toInt().coerceIn(0, size - 2)
        val g0 = gIdx.toInt().coerceIn(0, size - 2)
        val b0 = bIdx.toInt().coerceIn(0, size - 2)
        val r1 = r0 + 1
        val g1 = g0 + 1
        val b1 = b0 + 1

        val fr = rIdx - r0
        val fg = gIdx - g0
        val fb = bIdx - b0

        for (ch in 0..2) {
            val c000 = getLutValue(lut, r0, g0, b0, ch)
            val c100 = getLutValue(lut, r1, g0, b0, ch)
            val c010 = getLutValue(lut, r0, g1, b0, ch)
            val c110 = getLutValue(lut, r1, g1, b0, ch)
            val c001 = getLutValue(lut, r0, g0, b1, ch)
            val c101 = getLutValue(lut, r1, g0, b1, ch)
            val c011 = getLutValue(lut, r0, g1, b1, ch)
            val c111 = getLutValue(lut, r1, g1, b1, ch)

            val c00 = c000 * (1 - fr) + c100 * fr
            val c10 = c010 * (1 - fr) + c110 * fr
            val c01 = c001 * (1 - fr) + c101 * fr
            val c11 = c011 * (1 - fr) + c111 * fr

            val c0 = c00 * (1 - fg) + c10 * fg
            val c1 = c01 * (1 - fg) + c11 * fg

            out[ch] = c0 * (1 - fb) + c1 * fb
        }
    }
}
