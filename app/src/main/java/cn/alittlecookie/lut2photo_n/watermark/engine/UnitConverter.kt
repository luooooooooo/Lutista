package cn.alittlecookie.lut2photo_n.watermark.engine

import cn.alittlecookie.lut2photo_n.watermark.model.BorderPx
import cn.alittlecookie.lut2photo_n.watermark.model.BorderWidth
import cn.alittlecookie.lut2photo_n.watermark.model.Margin
import cn.alittlecookie.lut2photo_n.watermark.model.MarginPx
import cn.alittlecookie.lut2photo_n.watermark.model.Padding
import cn.alittlecookie.lut2photo_n.watermark.model.PaddingPx
import kotlin.math.sqrt

/**
 * 单位转换器
 * gm = geometric mean milli = √(长边 × 短边) / 1000
 */
class UnitConverter(
    val imageWidth: Int,
    val imageHeight: Int
) {
    val gmBase: Float = sqrt(imageWidth.toFloat() * imageHeight.toFloat()) / 1000f

    fun gmToPx(gm: Float): Float = gm * gmBase
    fun pxToGm(px: Float): Float = px / gmBase

    fun canvasSize(border: BorderWidth): Pair<Int, Int> {
        val w = imageWidth + gmToPx(border.left) + gmToPx(border.right)
        val h = imageHeight + gmToPx(border.top) + gmToPx(border.bottom)
        return Pair(w.toInt(), h.toInt())
    }

    fun Margin.toPx(): MarginPx = MarginPx(
        gmToPx(top), gmToPx(right), gmToPx(bottom), gmToPx(left)
    )

    fun Padding.toPx(): PaddingPx = PaddingPx(
        gmToPx(top), gmToPx(right), gmToPx(bottom), gmToPx(left)
    )

    fun BorderWidth.toPx(): BorderPx = BorderPx(
        gmToPx(top), gmToPx(right), gmToPx(bottom), gmToPx(left)
    )
}
