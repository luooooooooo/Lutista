package cn.alittlecookie.lutista.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * APP1（EXIF 段）提取的回归测试。
 *
 * 导出照片时不再直接搬运源文件的 APP1（那份带缩略图与 IFD1 方向标记，
 * 会导致「相册正常、微信横着」），但读取源 EXIF 仍依赖这段定位逻辑。
 */
class ExifUtilsApp1Test {

    @Test
    fun extractsFirstApp1Segment() {
        val app0 = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00)
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x06, 0x45, 0x78, 0x69, 0x66)
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val jpeg = soi + app0 + app1 + byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02)

        assertArrayEquals(app1, ExifUtils.extractApp1FromJpeg(jpeg))
    }

    @Test
    fun returnsNullForNonJpeg() {
        assertNull(ExifUtils.extractApp1FromJpeg(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
    }

    @Test
    fun returnsNullWhenNoApp1() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02
        )
        assertNull(ExifUtils.extractApp1FromJpeg(jpeg))
    }
}
