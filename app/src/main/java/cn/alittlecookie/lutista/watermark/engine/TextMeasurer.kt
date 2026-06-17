package cn.alittlecookie.lutista.watermark.engine

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cn.alittlecookie.lutista.watermark.model.FontWeight
import cn.alittlecookie.lutista.watermark.model.Size
import cn.alittlecookie.lutista.watermark.model.TextAlign
import cn.alittlecookie.lutista.watermark.model.TextElement

class TextMeasurer(private val converter: UnitConverter) {
    private val unconstrainedCache = HashMap<String, Size>()
    private val constrainedCache = HashMap<String, Size>()

    fun measure(element: TextElement): Size {
        val key = buildKey(element, null)
        unconstrainedCache[key]?.let { return it }

        val paint = createPaint(element)
        val layout = createLayout(paint, element.content, Int.MAX_VALUE, element)
        val width = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val height = layout.height.toFloat()
        val size = Size(width, height)
        unconstrainedCache[key] = size
        return size
    }

    fun measureWithConstraint(element: TextElement, maxWidth: Float): Size {
        val key = buildKey(element, maxWidth)
        constrainedCache[key]?.let { return it }

        val paint = createPaint(element)
        val layout = createLayout(paint, element.content, maxWidth.toInt(), element)
        val width = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val height = layout.height.toFloat()
        val size = Size(width, height)
        constrainedCache[key] = size
        return size
    }

    private fun createPaint(element: TextElement): TextPaint {
        return TextPaint().apply {
            textSize = converter.gmToPx(element.fontSize)
            typeface = element.fontUri?.let { Typeface.createFromFile(it) }
                ?: when (element.fontWeight) {
                    FontWeight.NORMAL -> Typeface.DEFAULT
                    FontWeight.BOLD -> Typeface.DEFAULT_BOLD
                }
            letterSpacing = converter.gmToPx(element.letterSpacing) / textSize
            isAntiAlias = true
        }
    }

    private fun createLayout(
        paint: TextPaint,
        text: String,
        maxWidth: Int,
        element: TextElement
    ): StaticLayout {
        return StaticLayout.Builder.obtain(
            text, 0, text.length, paint, maxWidth
        ).apply {
            setLineSpacing(0f, element.lineHeight)
            setIncludePad(false)
            setAlignment(
                when (element.textAlign) {
                    TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                }
            )
        }.build()
    }

    private fun buildKey(element: TextElement, maxWidth: Float?): String {
        return "${element.content}|${element.fontSize}|${element.letterSpacing}|${element.lineHeight}|${element.fontUri}|${element.fontWeight}|${element.textAlign}|$maxWidth"
    }

    fun clearCache() {
        unconstrainedCache.clear()
        constrainedCache.clear()
    }
}
