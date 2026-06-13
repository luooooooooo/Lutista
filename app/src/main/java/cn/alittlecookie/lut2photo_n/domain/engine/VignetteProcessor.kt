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
        private const val MAX_ARRAY_BYTES = 16 * 1024 * 1024 // 16MB per chunk
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        if (filter.vignetteStrength <= 0f) return input

        val width = input.cols()
        val height = input.rows()
        val channels = input.channels()
        val centerX = width.toDouble() / 2.0
        val centerY = height.toDouble() / 2.0
        val maxDist = sqrt(centerX * centerX + centerY * centerY)
        val strength = filter.vignetteStrength / 100.0

        val inputFloat = Mat()
        input.convertTo(inputFloat, CvType.CV_32FC3)
        val output = Mat(height, width, CvType.CV_8UC3)

        val chunkRows = (MAX_ARRAY_BYTES / (width * channels * 4)).coerceIn(1, height)

        for (startRow in 0 until height step chunkRows) {
            val endRow = minOf(startRow + chunkRows, height)
            val rowCount = endRow - startRow
            val data = FloatArray(rowCount * width * channels)

            inputFloat.rowRange(startRow, endRow).get(0, 0, data)

            for (y in 0 until rowCount) {
                val absY = startRow + y
                val dy = absY - centerY
                for (x in 0 until width) {
                    val dx = x - centerX
                    val dist = sqrt(dx * dx + dy * dy)
                    val vignette = (1.0 - (dist / maxDist * strength)).coerceIn(0.0, 1.0).toFloat()

                    val idx = (y * width + x) * 3
                    data[idx] = (data[idx] * vignette).coerceIn(0f, 255f)
                    data[idx + 1] = (data[idx + 1] * vignette).coerceIn(0f, 255f)
                    data[idx + 2] = (data[idx + 2] * vignette).coerceIn(0f, 255f)
                }
            }

            val chunkMat = Mat(rowCount, width, CvType.CV_32FC3)
            chunkMat.put(0, 0, data)
            val chunkByte = Mat()
            chunkMat.convertTo(chunkByte, CvType.CV_8UC3)
            chunkMat.release()
            chunkByte.copyTo(output.rowRange(startRow, endRow))
            chunkByte.release()
        }

        inputFloat.release()
        return output
    }
}
