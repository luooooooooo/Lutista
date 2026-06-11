package cn.alittlecookie.lut2photo_n.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FilterConfigTest {

    @Test
    fun `default FilterConfig should have correct default values`() {
        val config = FilterConfig()

        assertEquals(0, config.id)
        assertEquals("", config.name)
        assertEquals(null, config.lutPath1)
        assertEquals(1.0f, config.lutConcentration1)
        assertEquals(null, config.lutPath2)
        assertEquals(1.0f, config.lutConcentration2)
        assertEquals(33, config.lutPrecision)
        assertEquals(0f, config.exposure)
        assertEquals(0f, config.contrast)
        assertEquals(0f, config.highlights)
        assertEquals(0f, config.shadows)
        assertEquals(0f, config.saturation)
        assertEquals(0f, config.colorTemperature)
        assertEquals(0f, config.tint)
        assertEquals(0f, config.vignetteStrength)
    }

    @Test
    fun `FilterConfig copy should work correctly`() {
        val original = FilterConfig(
            id = 1,
            name = "Test Filter",
            exposure = 1.5f,
            contrast = 20f
        )

        val copy = original.copy(name = "Updated Filter")

        assertEquals(1, copy.id)
        assertEquals("Updated Filter", copy.name)
        assertEquals(1.5f, copy.exposure)
        assertEquals(20f, copy.contrast)
    }

    @Test
    fun `FilterConfig equality should work`() {
        val config1 = FilterConfig(id = 1, name = "Test")
        val config2 = FilterConfig(id = 1, name = "Test")
        val config3 = FilterConfig(id = 2, name = "Test")

        assertEquals(config1, config2)
        assertNotEquals(config1, config3)
    }

    @Test
    fun `CurveConfig default values should be empty`() {
        val curve = CurveConfig()

        assertEquals(0, curve.whiteChannel.size)
        assertEquals(0, curve.redChannel.size)
        assertEquals(0, curve.greenChannel.size)
        assertEquals(0, curve.blueChannel.size)
    }

    @Test
    fun `LchConfig default values should be empty adjustments`() {
        val lch = LchConfig()

        assertEquals(LchAdjustment(), lch.red)
        assertEquals(LchAdjustment(), lch.orange)
        assertEquals(LchAdjustment(), lch.yellow)
        assertEquals(LchAdjustment(), lch.green)
        assertEquals(LchAdjustment(), lch.cyan)
        assertEquals(LchAdjustment(), lch.blue)
        assertEquals(LchAdjustment(), lch.purple)
        assertEquals(LchAdjustment(), lch.skin)
    }

    @Test
    fun `LchAdjustment default values should be zero`() {
        val adjustment = LchAdjustment()

        assertEquals(0f, adjustment.lightness)
        assertEquals(0f, adjustment.chroma)
        assertEquals(0f, adjustment.hue)
    }

    @Test
    fun `GrainConfig default values should be zero`() {
        val grain = GrainConfig()

        assertEquals(0f, grain.highlightSize)
        assertEquals(0f, grain.highlightDensity)
        assertEquals(0f, grain.midtoneSize)
        assertEquals(0f, grain.midtoneDensity)
        assertEquals(0f, grain.shadowSize)
        assertEquals(0f, grain.shadowDensity)
    }
}
