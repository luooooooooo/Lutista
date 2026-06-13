package cn.alittlecookie.lut2photo_n.util

import cn.alittlecookie.lut2photo_n.data.model.LutData
import cn.alittlecookie.lut2photo_n.manager.LogManager
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
        private val DATA_LINE_REGEX = Regex("[\\d.\\-+eE]+\\s+[\\d.\\-+eE]+\\s+[\\d.\\-+eE]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
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
        val dataPoints = mutableListOf<Float>()

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#") || trimmed.isEmpty() -> {}
                    trimmed.startsWith("TITLE") -> {
                        title = trimmed.substringAfter("TITLE").trim().removeSurrounding("\"")
                    }
                    trimmed.startsWith("LUT_3D_SIZE") -> {
                        size = trimmed.substringAfter("LUT_3D_SIZE").trim().toInt()
                    }
                    trimmed.startsWith("DOMAIN_MIN") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MIN").trim().split(WHITESPACE_REGEX)
                        domainMin = parts.map { it.toFloat() }.toFloatArray()
                    }
                    trimmed.startsWith("DOMAIN_MAX") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MAX").trim().split(WHITESPACE_REGEX)
                        domainMax = parts.map { it.toFloat() }.toFloatArray()
                    }
                    trimmed.matches(DATA_LINE_REGEX) -> {
                        val parts = trimmed.split(WHITESPACE_REGEX)
                        dataPoints.addAll(parts.map { it.toFloat() })
                    }
                }
            }
        }

        if (size == 0) {
            size = DEFAULT_LUT_SIZE
            logger.w(TAG, "LUT size not specified, defaulting to $DEFAULT_LUT_SIZE")
        }

        if (dataPoints.isEmpty()) {
            throw IllegalArgumentException("No LUT data found in Cube file")
        }

        return LutData(
            size = size,
            data = dataPoints.toFloatArray(),
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
        val dataPoints = mutableListOf<Float>()

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#") || trimmed.isEmpty() -> {}
                    trimmed.startsWith("Title") -> {
                        title = trimmed.substringAfter("Title").trim().removeSurrounding("\"")
                    }
                    trimmed.startsWith("3DLUT_Size") || trimmed.startsWith("LUT_3D_SIZE") -> {
                        size = trimmed.substringAfter(":").trim().substringAfter("3DLUT_Size").trim().toIntOrNull()
                            ?: trimmed.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                    }
                    trimmed.matches(DATA_LINE_REGEX) -> {
                        val parts = trimmed.split(WHITESPACE_REGEX)
                        dataPoints.addAll(parts.map { it.toFloat() })
                    }
                }
            }
        }

        if (size == 0) {
            size = DEFAULT_LUT_SIZE
            logger.w(TAG, "VLT size not specified, defaulting to $DEFAULT_LUT_SIZE")
        }

        if (dataPoints.isEmpty()) {
            throw IllegalArgumentException("No LUT data found in VLT file")
        }

        return normalizeVltToCube(LutData(size = size, data = dataPoints.toFloatArray(), title = title))
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

        val result = FloatArray(3)
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

            result[ch] = c0 * (1 - fb) + c1 * fb
        }

        return result
    }
}
