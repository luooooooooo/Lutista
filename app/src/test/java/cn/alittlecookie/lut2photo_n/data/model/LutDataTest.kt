package cn.alittlecookie.lut2photo_n.data.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LutDataTest {

    @Test
    fun `LutData should store values correctly`() {
        val data = floatArrayOf(0.0f, 0.5f, 1.0f)
        val lut = LutData(size = 2, data = data, title = "Test")

        assertEquals(2, lut.size)
        assertArrayEquals(data, lut.data, 0.0001f)
        assertEquals("Test", lut.title)
    }

    @Test
    fun `LutData equality should work`() {
        val data1 = floatArrayOf(0.0f, 0.5f, 1.0f)
        val data2 = floatArrayOf(0.0f, 0.5f, 1.0f)
        val data3 = floatArrayOf(0.0f, 0.3f, 1.0f)

        val lut1 = LutData(size = 2, data = data1, title = "Test")
        val lut2 = LutData(size = 2, data = data2, title = "Test")
        val lut3 = LutData(size = 2, data = data3, title = "Test")

        assertEquals(lut1, lut2)
        assertNotEquals(lut1, lut3)
    }

    @Test
    fun `LutData hashCode should be consistent`() {
        val data1 = floatArrayOf(0.0f, 0.5f, 1.0f)
        val data2 = floatArrayOf(0.0f, 0.5f, 1.0f)

        val lut1 = LutData(size = 2, data = data1, title = "Test")
        val lut2 = LutData(size = 2, data = data2, title = "Test")

        assertEquals(lut1.hashCode(), lut2.hashCode())
    }

    @Test
    fun `UnifiedLut should store values correctly`() {
        val data = floatArrayOf(0.0f, 0.5f, 1.0f)
        val lut = UnifiedLut(size = 2, data = data)

        assertEquals(2, lut.size)
        assertArrayEquals(data, lut.data, 0.0001f)
    }

    @Test
    fun `UnifiedLut equality should work`() {
        val data1 = floatArrayOf(0.0f, 0.5f, 1.0f)
        val data2 = floatArrayOf(0.0f, 0.5f, 1.0f)

        val lut1 = UnifiedLut(size = 2, data = data1)
        val lut2 = UnifiedLut(size = 2, data = data2)

        assertEquals(lut1, lut2)
    }
}
