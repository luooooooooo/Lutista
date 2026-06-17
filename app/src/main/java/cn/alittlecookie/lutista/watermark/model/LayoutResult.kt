package cn.alittlecookie.lutista.watermark.model

/**
 * 布局计算结果
 */
data class LayoutResult(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val elementBounds: Map<String, ElementBounds>
)

/**
 * 元素边界（含 margin 和不含 margin 两种）
 */
data class ElementBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val marginLeft: Float = 0f,
    val marginTop: Float = 0f,
    val marginRight: Float = 0f,
    val marginBottom: Float = 0f
) {
    val totalX: Float get() = x - marginLeft
    val totalY: Float get() = y - marginTop
    val totalWidth: Float get() = width + marginLeft + marginRight
    val totalHeight: Float get() = height + marginTop + marginBottom
}

/**
 * 元素布局信息（内部使用）
 */
data class ElementLayoutInfo(
    val contentSize: Size,
    val layoutSize: Size
)

data class Size(val width: Float, val height: Float) {
    operator fun plus(margin: MarginPx) = Size(
        width + margin.left + margin.right,
        height + margin.top + margin.bottom
    )
}

data class Offset(val x: Float, val y: Float)

data class MarginPx(val top: Float, val right: Float, val bottom: Float, val left: Float)
data class PaddingPx(val top: Float, val right: Float, val bottom: Float, val left: Float)
data class BorderPx(val top: Float, val right: Float, val bottom: Float, val left: Float)
