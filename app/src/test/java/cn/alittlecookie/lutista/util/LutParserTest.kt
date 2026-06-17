package cn.alittlecookie.lutista.util

import cn.alittlecookie.lutista.manager.LogManager
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class LutParserTest {

    @MockK
    private lateinit var logger: LogManager

    private lateinit var lutParser: LutParser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        lutParser = LutParser(logger)
    }

    @Test
    fun `parseCube should parse valid cube file`() {
        val cubeContent = """
            TITLE "Test LUT"
            LUT_3D_SIZE 3
            0.0 0.0 0.0
            0.5 0.0 0.0
            1.0 0.0 0.0
            0.0 0.5 0.0
            0.5 0.5 0.0
            1.0 0.5 0.0
            0.0 1.0 0.0
            0.5 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 0.5
            0.5 0.0 0.5
            1.0 0.0 0.5
            0.0 0.5 0.5
            0.5 0.5 0.5
            1.0 0.5 0.5
            0.0 1.0 0.5
            0.5 1.0 0.5
            1.0 1.0 0.5
            0.0 0.0 1.0
            0.5 0.0 1.0
            1.0 0.0 1.0
            0.0 0.5 1.0
            0.5 0.5 1.0
            1.0 0.5 1.0
            0.0 1.0 1.0
            0.5 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val inputStream = ByteArrayInputStream(cubeContent.toByteArray())
        val result = lutParser.parseCube(inputStream)

        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals("Test LUT", result.title)
        assertEquals(81, result.data.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCube should throw exception for empty data`() {
        val cubeContent = """
            TITLE "Empty LUT"
            LUT_3D_SIZE 3
        """.trimIndent()

        val inputStream = ByteArrayInputStream(cubeContent.toByteArray())
        lutParser.parseCube(inputStream)
    }

    @Test
    fun `trilinearInterpolation should return correct values`() {
        val cubeContent = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val inputStream = ByteArrayInputStream(cubeContent.toByteArray())
        val lut = lutParser.parseCube(inputStream)

        val result = lutParser.trilinearInterpolation(lut, 0.5f, 0.5f, 0.5f)
        assertNotNull(result)
        assertEquals(3, result.size)
    }

    @Test
    fun `trilinearInterpolation should handle edge values`() {
        val cubeContent = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val inputStream = ByteArrayInputStream(cubeContent.toByteArray())
        val lut = lutParser.parseCube(inputStream)

        val result1 = lutParser.trilinearInterpolation(lut, 0.0f, 0.0f, 0.0f)
        val result2 = lutParser.trilinearInterpolation(lut, 1.0f, 1.0f, 1.0f)

        assertNotNull(result1)
        assertNotNull(result2)
    }

    @Test
    fun `quantizeLut should maintain size if already target size`() {
        val cubeContent = """
            LUT_3D_SIZE 33
            ${generateCubeData(33)}
        """.trimIndent()

        val inputStream = ByteArrayInputStream(cubeContent.toByteArray())
        val lut = lutParser.parseCube(inputStream)

        val result = lutParser.quantizeLut(lut, 33)
        assertEquals(33, result.size)
    }

    private fun generateCubeData(size: Int): String {
        val sb = StringBuilder()
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    sb.appendLine("${r.toFloat() / (size - 1)} ${g.toFloat() / (size - 1)} ${b.toFloat() / (size - 1)}")
                }
            }
        }
        return sb.toString()
    }
}
