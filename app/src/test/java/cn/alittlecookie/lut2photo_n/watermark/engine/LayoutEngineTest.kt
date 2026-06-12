package cn.alittlecookie.lut2photo_n.watermark.engine

import cn.alittlecookie.lut2photo_n.watermark.model.BaseImageElement
import cn.alittlecookie.lut2photo_n.watermark.model.BorderWidth
import cn.alittlecookie.lut2photo_n.watermark.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {

    @Test
    fun `layout with empty scene returns canvas size`() {
        val converter = UnitConverter(4000, 3000)
        val engine = LayoutEngine(converter)
        val scene = Scene(
            baseImage = BaseImageElement(imageUri = "/test.jpg")
        )
        val result = engine.layout(scene)

        assertEquals(4000, result.canvasWidth)
        assertEquals(3000, result.canvasHeight)
        assertTrue(result.elementBounds.isEmpty())
    }

    @Test
    fun `layout with border increases canvas size`() {
        val converter = UnitConverter(4000, 3000)
        val engine = LayoutEngine(converter)
        val border = BorderWidth(10f, 10f, 10f, 10f)
        val scene = Scene(
            baseImage = BaseImageElement(
                imageUri = "/test.jpg",
                borderWidth = border
            )
        )
        val result = engine.layout(scene)

        val borderPx = converter.gmToPx(10f)
        val expectedW = (4000 + borderPx * 2).toInt()
        val expectedH = (3000 + borderPx * 2).toInt()

        assertEquals(expectedW, result.canvasWidth)
        assertEquals(expectedH, result.canvasHeight)
    }

    @Test
    fun `layout with zero border equals image size`() {
        val converter = UnitConverter(4000, 3000)
        val engine = LayoutEngine(converter)
        val scene = Scene(
            baseImage = BaseImageElement(imageUri = "/test.jpg")
        )
        val result = engine.layout(scene)

        assertEquals(4000, result.canvasWidth)
        assertEquals(3000, result.canvasHeight)
    }

    @Test
    fun `canvas size calculation is consistent with UnitConverter`() {
        val converter = UnitConverter(4000, 3000)
        val engine = LayoutEngine(converter)
        val border = BorderWidth(5f, 10f, 5f, 10f)
        val scene = Scene(
            baseImage = BaseImageElement(
                imageUri = "/test.jpg",
                borderWidth = border
            )
        )
        val result = engine.layout(scene)
        val (expectedW, expectedH) = converter.canvasSize(border)

        assertEquals(expectedW, result.canvasWidth)
        assertEquals(expectedH, result.canvasHeight)
    }
}
