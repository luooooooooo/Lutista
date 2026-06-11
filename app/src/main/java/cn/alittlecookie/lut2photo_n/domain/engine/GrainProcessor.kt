package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.GrainConfig
import cn.alittlecookie.lut2photo_n.manager.LogManager
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.random.Random

class GrainProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "GrainProcessor"
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        if (filter.grainConfig == GrainConfig()) return input

        val config = filter.grainConfig
        if (config.highlightDensity == 0f && config.midtoneDensity == 0f && config.shadowDensity == 0f) {
            return input
        }

        val output = input.clone()
        val data = FloatArray(input.rows() * input.cols() * input.channels())
        input.convertTo(input, CvType.CV_32FC3)
        input.get(0, 0, data)

        val random = Random(System.currentTimeMillis())

        for (i in data.indices step 3) {
            val luminance = (data[i] * 0.299f + data[i + 1] * 0.587f + data[i + 2] * 0.114f) / 255f

            val grainAmount = when {
                luminance > 0.66f -> config.highlightDensity / 100f
                luminance > 0.33f -> config.midtoneDensity / 100f
                else -> config.shadowDensity / 100f
            }

            val grainSize = when {
                luminance > 0.66f -> config.highlightSize / 100f
                luminance > 0.33f -> config.midtoneSize / 100f
                else -> config.shadowSize / 100f
            }

            if (grainAmount > 0f) {
                val noise = (random.nextFloat() - 0.5f) * grainAmount * 50f * (1f + grainSize)
                data[i] = (data[i] + noise).coerceIn(0f, 255f)
                data[i + 1] = (data[i + 1] + noise).coerceIn(0f, 255f)
                data[i + 2] = (data[i + 2] + noise).coerceIn(0f, 255f)
            }
        }

        val result = Mat(input.rows(), input.cols(), CvType.CV_32FC3)
        result.put(0, 0, data)
        result.convertTo(result, CvType.CV_8UC3)

        return result
    }
}
