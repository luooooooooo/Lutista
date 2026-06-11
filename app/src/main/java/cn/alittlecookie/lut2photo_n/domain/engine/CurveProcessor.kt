package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.manager.LogManager
import org.opencv.core.Mat

class CurveProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "CurveProcessor"
        private const val CURVE_SAMPLES = 256
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        if (filter.curveConfig.whiteChannel.isEmpty() &&
            filter.curveConfig.redChannel.isEmpty() &&
            filter.curveConfig.greenChannel.isEmpty() &&
            filter.curveConfig.blueChannel.isEmpty()
        ) {
            return input
        }

        val whiteCurve = buildCurveLut(filter.curveConfig.whiteChannel)
        val redCurve = buildCurveLut(filter.curveConfig.redChannel)
        val greenCurve = buildCurveLut(filter.curveConfig.greenChannel)
        val blueCurve = buildCurveLut(filter.curveConfig.blueChannel)

        val data = ByteArray(input.rows() * input.cols() * input.channels())
        input.get(0, 0, data)

        for (i in data.indices step 3) {
            val b = (data[i].toInt() and 0xFF)
            val g = (data[i + 1].toInt() and 0xFF)
            val r = (data[i + 2].toInt() and 0xFF)

            data[i] = blueCurve[b].toByte()
            data[i + 1] = greenCurve[g].toByte()
            data[i + 2] = redCurve[r].toByte()

            if (whiteCurve.isNotEmpty()) {
                data[i] = whiteCurve[data[i].toInt() and 0xFF].toByte()
                data[i + 1] = whiteCurve[data[i + 1].toInt() and 0xFF].toByte()
                data[i + 2] = whiteCurve[data[i + 2].toInt() and 0xFF].toByte()
            }
        }

        val output = input.clone()
        output.put(0, 0, data)
        return output
    }

    fun applyToColor(color: FloatArray, filter: FilterConfig): FloatArray {
        if (filter.curveConfig.whiteChannel.isEmpty() &&
            filter.curveConfig.redChannel.isEmpty() &&
            filter.curveConfig.greenChannel.isEmpty() &&
            filter.curveConfig.blueChannel.isEmpty()
        ) {
            return color
        }

        val result = color.copyOf()

        if (filter.curveConfig.redChannel.isNotEmpty()) {
            val curve = buildCurveLut(filter.curveConfig.redChannel)
            val idx = (result[0] * 255).toInt().coerceIn(0, 255)
            result[0] = curve[idx] / 255f
        }
        if (filter.curveConfig.greenChannel.isNotEmpty()) {
            val curve = buildCurveLut(filter.curveConfig.greenChannel)
            val idx = (result[1] * 255).toInt().coerceIn(0, 255)
            result[1] = curve[idx] / 255f
        }
        if (filter.curveConfig.blueChannel.isNotEmpty()) {
            val curve = buildCurveLut(filter.curveConfig.blueChannel)
            val idx = (result[2] * 255).toInt().coerceIn(0, 255)
            result[2] = curve[idx] / 255f
        }

        return result
    }

    private fun buildCurveLut(points: List<cn.alittlecookie.lut2photo_n.data.model.FloatPoint>): IntArray {
        val lut = IntArray(CURVE_SAMPLES)

        if (points.isEmpty()) {
            for (i in 0 until CURVE_SAMPLES) lut[i] = i
            return lut
        }

        val sorted = points.sortedBy { it.x }
        for (i in 0 until CURVE_SAMPLES) {
            val x = i.toFloat() / (CURVE_SAMPLES - 1)
            val y = interpolate(sorted, x)
            lut[i] = (y * 255).toInt().coerceIn(0, 255)
        }

        return lut
    }

    private fun interpolate(points: List<cn.alittlecookie.lut2photo_n.data.model.FloatPoint>, x: Float): Float {
        if (x <= points.first().x) return points.first().y
        if (x >= points.last().x) return points.last().y

        for (i in 0 until points.size - 1) {
            if (x >= points[i].x && x <= points[i + 1].x) {
                val t = (x - points[i].x) / (points[i + 1].x - points[i].x)
                return points[i].y + t * (points[i + 1].y - points[i].y)
            }
        }

        return x
    }
}
