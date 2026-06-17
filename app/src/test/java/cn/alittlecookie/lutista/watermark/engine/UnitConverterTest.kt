package cn.alittlecookie.lutista.watermark.engine

import cn.alittlecookie.lutista.watermark.model.BorderWidth
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class UnitConverterTest {

    @Test
    fun `gmBase calculation is correct`() {
        val converter = UnitConverter(4000, 3000)
        val expected = sqrt(4000f * 3000f) / 1000f
        assertEquals(expected, converter.gmBase, 0.001f)
    }

    @Test
    fun `gmToPx conversion is correct`() {
        val converter = UnitConverter(4000, 3000)
        val gm = 10f
        val expected = gm * converter.gmBase
        assertEquals(expected, converter.gmToPx(gm), 0.001f)
    }

    @Test
    fun `pxToGm conversion is correct`() {
        val converter = UnitConverter(4000, 3000)
        val px = 100f
        val expected = px / converter.gmBase
        assertEquals(expected, converter.pxToGm(px), 0.001f)
    }

    @Test
    fun `canvasSize calculation is correct`() {
        val converter = UnitConverter(4000, 3000)
        val border = BorderWidth(10f, 20f, 10f, 20f)
        val (w, h) = converter.canvasSize(border)

        val borderLeftPx = converter.gmToPx(20f)
        val borderTopPx = converter.gmToPx(10f)
        val borderRightPx = converter.gmToPx(20f)
        val borderBottomPx = converter.gmToPx(10f)

        val expectedW = (4000 + borderLeftPx + borderRightPx).toInt()
        val expectedH = (3000 + borderTopPx + borderBottomPx).toInt()

        assertEquals(expectedW, w)
        assertEquals(expectedH, h)
    }

    @Test
    fun `canvasSize with zero border equals image size`() {
        val converter = UnitConverter(4000, 3000)
        val (w, h) = converter.canvasSize(BorderWidth.ZERO)
        assertEquals(4000, w)
        assertEquals(3000, h)
    }

    @Test
    fun `roundtrip conversion preserves value`() {
        val converter = UnitConverter(4000, 3000)
        val originalGm = 42f
        val px = converter.gmToPx(originalGm)
        val backToGm = converter.pxToGm(px)
        assertEquals(originalGm, backToGm, 0.001f)
    }
}
