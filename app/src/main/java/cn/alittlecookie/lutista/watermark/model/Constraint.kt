package cn.alittlecookie.lutista.watermark.model

/**
 * 尺寸约束，自顶向下传递
 */
data class Constraint(
    val minWidth: Float = 0f,
    val maxWidth: Float = Float.MAX_VALUE,
    val minHeight: Float = 0f,
    val maxHeight: Float = Float.MAX_VALUE
) {
    val hasFixedWidth: Boolean get() = maxWidth < Float.MAX_VALUE
    val hasFixedHeight: Boolean get() = maxHeight < Float.MAX_VALUE

    companion object {
        val UNBOUNDED = Constraint()
    }
}
