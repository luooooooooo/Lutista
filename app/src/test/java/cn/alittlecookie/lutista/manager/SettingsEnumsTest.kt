package cn.alittlecookie.lutista.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEnumsTest {

    @Test
    fun `OutputFormat should have correct values`() {
        assertEquals(2, OutputFormat.values().size)
        assertEquals("JPG", OutputFormat.JPG.name)
        assertEquals("PNG", OutputFormat.PNG.name)
    }

    @Test
    fun `LutPrecision should have correct sizes`() {
        assertEquals(33, LutPrecision.STANDARD.size)
        assertEquals(65, LutPrecision.HIGH.size)
    }

    @Test
    fun `LutPrecision should have correct values`() {
        assertEquals(2, LutPrecision.values().size)
        assertEquals("STANDARD", LutPrecision.STANDARD.name)
        assertEquals("HIGH", LutPrecision.HIGH.name)
    }

    @Test
    fun `ThemeMode should have correct values`() {
        assertEquals(3, ThemeMode.values().size)
        assertEquals("SYSTEM", ThemeMode.SYSTEM.name)
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)
    }
}
