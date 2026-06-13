package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.processImageInChunks
import org.opencv.core.Mat

class ColorProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "ColorProcessor"
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        var current = input.clone()

        if (filter.saturation != 0f) {
            current = applySaturation(current, filter.saturation)
        }
        if (filter.colorTemperature != 0f) {
            current = applyColorTemperature(current, filter.colorTemperature)
        }
        if (filter.tint != 0f) {
            current = applyTint(current, filter.tint)
        }

        return current
    }

    fun applyToColor(color: FloatArray, filter: FilterConfig): FloatArray {
        var result = color.copyOf()

        if (filter.saturation != 0f) {
            val gray = result[0] * 0.299f + result[1] * 0.587f + result[2] * 0.114f
            val factor = 1f + filter.saturation / 100f
            result = floatArrayOf(
                (gray + (result[0] - gray) * factor),
                (gray + (result[1] - gray) * factor),
                (gray + (result[2] - gray) * factor)
            )
        }

        if (filter.colorTemperature != 0f) {
            val adjustment = filter.colorTemperature / 200f
            result = floatArrayOf(
                (result[0] + adjustment),
                result[1],
                (result[2] - adjustment)
            )
        }

        if (filter.tint != 0f) {
            val adjustment = filter.tint / 200f
            result = floatArrayOf(
                result[0],
                (result[1] + adjustment),
                result[2]
            )
        }

        return result
    }

    private fun applySaturation(input: Mat, saturation: Float): Mat {
        val factor = 1f + saturation / 100f
        return processImageInChunks(input) { data ->
            for (i in data.indices step 3) {
                val gray = data[i] * 0.299f + data[i + 1] * 0.587f + data[i + 2] * 0.114f
                data[i] = (gray + (data[i] - gray) * factor).coerceIn(0f, 255f)
                data[i + 1] = (gray + (data[i + 1] - gray) * factor).coerceIn(0f, 255f)
                data[i + 2] = (gray + (data[i + 2] - gray) * factor).coerceIn(0f, 255f)
            }
            data
        }
    }

    private fun applyColorTemperature(input: Mat, temperature: Float): Mat {
        val adjustment = temperature / 200f * 255f
        return processImageInChunks(input) { data ->
            for (i in data.indices step 3) {
                data[i] = (data[i] + adjustment).coerceIn(0f, 255f)
                data[i + 2] = (data[i + 2] - adjustment).coerceIn(0f, 255f)
            }
            data
        }
    }

    private fun applyTint(input: Mat, tint: Float): Mat {
        val adjustment = tint / 200f * 255f
        return processImageInChunks(input) { data ->
            for (i in data.indices step 3) {
                data[i + 1] = (data[i + 1] + adjustment).coerceIn(0f, 255f)
            }
            data
        }
    }
}
