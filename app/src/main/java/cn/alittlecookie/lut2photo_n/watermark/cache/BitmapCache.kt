package cn.alittlecookie.lut2photo_n.watermark.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 图片缓存，使用引用计数确保所有 tile 使用完毕后释放
 */
class BitmapCache(private val maxSizeBytes: Int = 50 * 1024 * 1024) {

    private val cache = object : LruCache<String, CachedBitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: CachedBitmap): Int {
            return value.bitmap.byteCount
        }
    }

    private val refCounts = ConcurrentHashMap<String, AtomicInteger>()

    data class CachedBitmap(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int
    )

    fun get(uri: String, targetWidth: Int, targetHeight: Int): Bitmap {
        val key = buildKey(uri, targetWidth, targetHeight)
        refCounts.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        cache.get(key)?.let { return it.bitmap }

        val bitmap = decodeSampled(uri, targetWidth, targetHeight)
        cache.put(key, CachedBitmap(bitmap, targetWidth, targetHeight))
        return bitmap
    }

    fun release(uri: String, targetWidth: Int, targetHeight: Int) {
        val key = buildKey(uri, targetWidth, targetHeight)
        val count = refCounts[key]?.decrementAndGet() ?: 0
        if (count <= 0) {
            cache.remove(key)
            refCounts.remove(key)
        }
    }

    fun releaseAll(uri: String) {
        refCounts.keys.filter { it.startsWith("$uri|") }.forEach { key ->
            cache.remove(key)
            refCounts.remove(key)
        }
    }

    fun clear() {
        cache.evictAll()
        refCounts.clear()
    }

    private fun buildKey(uri: String, w: Int, h: Int) = "$uri|${w}x$h"

    private fun decodeSampled(uri: String, reqWidth: Int, reqHeight: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(uri, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(uri, options)
            ?: throw IllegalArgumentException("Failed to decode: $uri")
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqW: Int,
        reqH: Int
    ): Int {
        val (w, h) = options.outWidth to options.outHeight
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
