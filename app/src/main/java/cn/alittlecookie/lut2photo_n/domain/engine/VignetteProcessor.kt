package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.manager.LogManager
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.sqrt

class VignetteProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "VignetteProcessor"
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        if (filter.vignetteStrength <= 0f) return input

        val output = input.clone()
        val width = input.cols()
        val height = input.rows()
        val centerX = width.toDouble() / 2.0
        val centerY = height.toDouble() / 2.0
        val maxDist = sqrt(centerX * centerX + centerY * centerY)

        val data = FloatArray(input.rows() * input.cols() * input.channels())
        input.convertTo(input, CvType.CV_32FC3)
        input.get(0, 0, data)

        val strength = filter.vignetteStrength / 100.0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val dist = sqrt(dx * dx + dy * dy)
                val vignette = (1.0 - (dist / maxDist * strength)).coerceIn(0.0, 1.0).toFloat()

                val idx = (y * width + x) * 3
                data[idx] = (data[idx] * vignette).coerceIn(0f, 255f)
                data[idx + 1] = (data[idx + 1] * vignette).coerceIn(0f, 255f)
                data[idx + 2] = (data[idx + 2] * vignette).coerceIn(0f, 255f)
            }
        }

        val result = Mat(height, width, CvType.CV_32FC3)
        result.put(0, 0, data)
        result.convertTo(result, CvType.CV_8UC3)

        return result
    }
}
