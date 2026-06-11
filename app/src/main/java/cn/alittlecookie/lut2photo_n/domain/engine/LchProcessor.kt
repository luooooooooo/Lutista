package cn.alittlecookie.lut2photo_n.domain.engine

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.LchAdjustment
import cn.alittlecookie.lut2photo_n.manager.LogManager
import org.opencv.core.Mat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LchProcessor(
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "LchProcessor"
    }

    fun process(input: Mat, filter: FilterConfig): Mat {
        if (filter.lchConfig.red == LchAdjustment() &&
            filter.lchConfig.orange == LchAdjustment() &&
            filter.lchConfig.yellow == LchAdjustment() &&
            filter.lchConfig.green == LchAdjustment() &&
            filter.lchConfig.cyan == LchAdjustment() &&
            filter.lchConfig.blue == LchAdjustment() &&
            filter.lchConfig.purple == LchAdjustment() &&
            filter.lchConfig.skin == LchAdjustment()
        ) {
            return input
        }

        logger.d(TAG, "LCH processing not yet fully implemented, returning input")
        return input
    }

    fun applyToColor(color: FloatArray, filter: FilterConfig): FloatArray {
        if (filter.lchConfig.red == LchAdjustment() &&
            filter.lchConfig.orange == LchAdjustment() &&
            filter.lchConfig.yellow == LchAdjustment() &&
            filter.lchConfig.green == LchAdjustment() &&
            filter.lchConfig.cyan == LchAdjustment() &&
            filter.lchConfig.blue == LchAdjustment() &&
            filter.lchConfig.purple == LchAdjustment() &&
            filter.lchConfig.skin == LchAdjustment()
        ) {
            return color
        }

        logger.d(TAG, "LCH color processing not yet fully implemented, returning input")
        return color
    }

    private fun rgbToLch(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        val a = r - g
        val bComponent = (r + g) / 2f - b
        val c = sqrt(a * a + bComponent * bComponent)
        val h = atan2(bComponent, a)
        return Triple(l, c, h)
    }

    private fun lchToRgb(l: Float, c: Float, h: Float): Triple<Float, Float, Float> {
        val a = c * cos(h)
        val bComponent = c * sin(h)
        val r = l + a
        val g = l - a / 2f + bComponent / 2f
        val b = l - a / 2f - bComponent / 2f
        return Triple(r, g, b)
    }
}
