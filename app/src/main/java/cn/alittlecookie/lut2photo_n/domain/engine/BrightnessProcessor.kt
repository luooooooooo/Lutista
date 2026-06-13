package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.processImageInChunks
import org.opencv.core.Mat

class BrightnessProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "BrightnessProcessor"
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        var current = input.clone()

        if (filter.exposure != 0f) {
            current = applyExposure(current, filter.exposure)
        }
        if (filter.contrast != 0f) {
            current = applyContrast(current, filter.contrast)
        }
        if (filter.highlights != 0f || filter.shadows != 0f) {
            current = applyHighlightsShadows(current, filter.highlights, filter.shadows)
        }

        return current
    }

    fun applyToColor(color: FloatArray, filter: FilterConfig): FloatArray {
        var result = color.copyOf()

        if (filter.exposure != 0f) {
            val factor = Math.pow(2.0, filter.exposure.toDouble()).toFloat()
            result = floatArrayOf(result[0] * factor, result[1] * factor, result[2] * factor)
        }

        if (filter.contrast != 0f) {
            val factor = (100f + filter.contrast) / 100f
            result = floatArrayOf(
                ((result[0] - 0.5f) * factor + 0.5f),
                ((result[1] - 0.5f) * factor + 0.5f),
                ((result[2] - 0.5f) * factor + 0.5f)
            )
        }

        return result
    }

    private fun applyExposure(input: Mat, exposure: Float): Mat {
        val factor = Math.pow(2.0, exposure.toDouble())
        val output = Mat()
        input.convertTo(output, input.type(), factor)
        return output
    }

    private fun applyContrast(input: Mat, contrast: Float): Mat {
        val factor = (100f + contrast) / 100f
        val output = Mat()
        input.convertTo(output, input.type(), factor.toDouble(), (1.0 - factor.toDouble()) * 128.0)
        return output
    }

    private fun applyHighlightsShadows(input: Mat, highlights: Float, shadows: Float): Mat {
        return processImageInChunks(input) { data ->
            for (i in data.indices step 3) {
                val luminance = (data[i] * 0.299f + data[i + 1] * 0.587f + data[i + 2] * 0.114f) / 255f
                for (ch in 0..2) {
                    var value = data[i + ch]
                    if (luminance > 0.5f && highlights != 0f) {
                        value += highlights * luminance * 0.5f
                    } else if (luminance <= 0.5f && shadows != 0f) {
                        value += shadows * (1f - luminance) * 0.5f
                    }
                    data[i + ch] = value.coerceIn(0f, 255f)
                }
            }
            data
        }
    }
}
