package cn.alittlecookie.lutista.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.util.ExifUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 滤镜实时缩略图仓库。
 *
 * 设计要点（对应本项目的 Vulkan 单例引擎，而不是参考项目的 CPU 渲染器）：
 * - 每张照片只解码一次基准图（最长边 [RENDER_EDGE]），之后所有滤镜复用同一份 RGBA。
 * - 每个滤镜只渲染一份最高版本（[RENDER_EDGE]），小格子交给 Compose 绘制时缩放，
 *   所以切"大/中/小"密度不会重渲，也不会出现"放大后变糊"的二次等待。
 * - 渲染串行执行，按可见优先级出图；滚动/翻页时未进入可视区的请求可以被跳过。
 * - 结果按字节做 LRU 上限，换照片或改滤镜参数即整批失效。
 */
class FilterThumbnailStore(
    private val context: Context,
    private val engine: ImageProcessingEngine,
    private val logger: LogManager
) {
    companion object {
        /** 缩略图长边，覆盖 2×4 大格子（约 496px）仍有富余 */
        const val RENDER_EDGE = 512
        private const val MAX_CACHE_BYTES = 32L * 1024 * 1024
        private const val MAX_CACHE_COUNT = 96
        private const val TAG = "FilterThumbnailStore"

        const val PRIORITY_VISIBLE = 0
        const val PRIORITY_IDLE = 1
    }

    private class BaseImage(
        val uri: String,
        val width: Int,
        val height: Int,
        val bytes: ByteArray,
        val bitmap: Bitmap
    )

    /** filterId -> 已渲染缩略图；0 表示"原图"，由 [original] 提供 */
    val thumbnails = mutableStateMapOf<Long, Bitmap>()

    /** filterId -> 渲染中 */
    val loading = mutableStateMapOf<Long, Boolean>()

    /** 基准图（"原图"卡片直接用它） */
    var original by mutableStateOf<Bitmap?>(null)
        private set

    /** 基准图是否已就绪 */
    var baseReady by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private var base: BaseImage? = null
    private var loadJob: Job? = null
    @Volatile private var generation = 0

    private val desired = LinkedHashMap<Long, Int>()
    private val entities = HashMap<Long, FilterEntity>()

    /**
     * filterId -> 生成当前这份缩略图时用的滤镜参数指纹（[FilterEntity.hashCode]）。
     * 在配方室里改完参数保存后，指纹会变，缓存随之作废重渲，
     * 否则底部横排/整页平铺会一直显示改动前的旧图。
     */
    private val signatures = HashMap<Long, Int>()

    private val cache = LinkedHashMap<Long, Bitmap>(16, 0.75f, true)
    private var cacheBytes = 0L

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var workerJob: Job? = null

    init {
        workerJob = scope.launch { workerLoop() }
    }

    /**
     * 切换当前照片。uri 相同则忽略；不同则清空所有缩略图并重新解码基准图。
     *
     * @param uri 照片 Uri
     * @param sourceEdgeHint 已有的高分辨率来源提示（当前未使用，保留给后续接入 1024 预览缓存）
     */
    fun setPhoto(uri: String, sourceEdgeHint: Int = RENDER_EDGE) {
        if (base?.uri == uri) return
        generation++
        synchronized(lock) {
            desired.clear()
        }
        cache.clear()
        cacheBytes = 0L
        // setPhoto 由 Compose 在主线程调用，状态可直接改
        thumbnails.clear()
        loading.clear()
        original = null
        baseReady = false
        base = null
        loadJob?.cancel()
        loadJob = scope.launch {
            val loaded = decodeBase(uri, sourceEdgeHint)
            if (loaded == null) {
                logger.w(TAG, "基准图解码失败: $uri")
                return@launch
            }
            if (base?.uri == uri) return@launch
            base = loaded
            withStateUpdate {
                original = loaded.bitmap
                baseReady = true
            }
            requestAll(loaded)
        }
    }

    /** 更新期望队列：可见的先渲，其余按顺序补；参数变过的滤镜当场作废旧图重渲 */
    fun request(filters: List<FilterEntity>, priority: Int) {
        if (filters.isEmpty()) return
        val toAdd = ArrayList<Long>(filters.size)
        val stale = ArrayList<Long>()
        synchronized(lock) {
            for (f in filters) {
                if (f.id == 0L) continue
                val signature = f.hashCode()
                val previous = signatures.put(f.id, signature)
                if (previous != null && previous != signature) {
                    // 同一个滤镜的参数被改过：丢掉现有缩略图，重新排队
                    stale.add(f.id)
                    val removed = cache.remove(f.id)
                    if (removed != null) cacheBytes -= removed.allocationByteCount.toLong()
                    desired[f.id] = priority
                    toAdd.add(f.id)
                } else {
                    val old = desired[f.id]
                    if (old == null || old > priority) {
                        desired[f.id] = priority
                        toAdd.add(f.id)
                    }
                }
                entities[f.id] = f
            }
        }
        // 状态表在 Compose 侧，放到锁外清，避免持锁改 snapshot state
        if (stale.isNotEmpty()) {
            stale.forEach {
                thumbnails.remove(it)
                loading.remove(it)
            }
        }
        if (toAdd.isNotEmpty()) wake.trySend(Unit)
    }

    /** 只保留这些滤镜的待渲染请求（滚动离开的可以晚点再说） */
    fun trimPending(keep: Collection<Long>) {
        synchronized(lock) {
            val it = desired.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (e.value == PRIORITY_VISIBLE && e.key !in keep) it.remove()
            }
        }
    }

    /** 作废某张缩略图（滤镜参数变化时用） */
    fun invalidate(filterId: Long) {
        thumbnails.remove(filterId)
        loading.remove(filterId)
        synchronized(lock) {
            signatures.remove(filterId)
            val removed = cache.remove(filterId)
            if (removed != null) cacheBytes -= removed.allocationByteCount.toLong()
        }
    }

    fun release() {
        loadJob?.cancel()
        workerJob?.cancel()
        // 释放引用即可，交给 GC；不主动 recycle，避免正在绘制的帧崩溃
        synchronized(lock) {
            cache.clear()
            cacheBytes = 0L
            desired.clear()
        }
        base = null
        thumbnails.clear()
        loading.clear()
        original = null
        scope.cancel()
    }

    // ── 内部实现 ────────────────────────────────────────────────────

    private fun requestAll(baseImage: BaseImage) {
        // 基准图就绪后，把所有登记过的滤镜都排进队列（strip 需要横向滚动随时可见）
        val all = synchronized(lock) { entities.values.toList() }
        if (all.isEmpty()) return
        request(all, PRIORITY_IDLE)
    }

    private suspend fun decodeBase(uri: String, edgeHint: Int): BaseImage? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longEdge / (sample * 2) >= RENDER_EDGE) {
                sample *= 2
            }

            val decoded = context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return@withContext null

            val oriented = ExifUtils.correctOrientation(decoded, context, Uri.parse(uri))
            if (oriented !== decoded) decoded.recycle()

            val scaled = scaleToMaxEdge(oriented, RENDER_EDGE)
            if (scaled !== oriented) oriented.recycle()

            val w = scaled.width
            val h = scaled.height
            val bytes = ByteArray(w * h * 4)
            scaled.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
            BaseImage(uri, w, h, bytes, scaled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            logger.w(TAG, "解码基准图 OOM: $uri")
            null
        } catch (e: Exception) {
            logger.w(TAG, "解码基准图失败", e)
            null
        }
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private suspend fun workerLoop() {
        for (signal in wake) {
            while (true) {
                val id = takeNext() ?: break
                renderOne(id)
            }
        }
    }

    private fun takeNext(): Long? = synchronized(lock) {
        if (entities.isEmpty()) return@synchronized null
        var bestId: Long? = null
        var bestPriority = Int.MAX_VALUE
        for ((id, priority) in desired) {
            if (priority < bestPriority) {
                bestId = id
                bestPriority = priority
            }
        }
        val id = bestId ?: return@synchronized null
        desired.remove(id)
        id
    }

    private suspend fun renderOne(filterId: Long) {
        val baseImage = base ?: return
        if (baseImage.uri != base?.uri) return
        val entity = synchronized(lock) { entities[filterId] } ?: return
        if (synchronized(lock) { cache.containsKey(filterId) }) return

        val gen = generation
        val signature = synchronized(lock) { signatures[filterId] }
        withStateUpdate { loading[filterId] = true }
        try {
            val config = entity.toPreviewFilterConfig()
            val result = engine.processImageGpuFromBytes(
                baseImage.bytes, baseImage.width, baseImage.height, config
            )
            if (result == null) {
                logger.w(TAG, "缩略图渲染失败 filterId=$filterId")
                return
            }
            if (gen != generation) {
                result.recycle()
                return
            }
            // 渲染期间该滤镜又被改过：这份结果是旧参数出来的，直接丢弃
            val latestSignature = synchronized(lock) { signatures[filterId] }
            if (signature != null && latestSignature != signature) {
                result.recycle()
                return
            }
            withStateUpdate {
                putCache(filterId, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            // 低内存机型上连续渲图可能建不出 bitmap：丢掉现有缓存腾地方，跳过这一张，不往下崩
            logger.w(TAG, "缩略图渲染 OOM，清空缓存 filterId=$filterId")
            synchronized(lock) {
                cache.clear()
                cacheBytes = 0L
            }
            withStateUpdate {
                thumbnails.clear()
                loading.clear()
            }
        } catch (e: Exception) {
            logger.w(TAG, "缩略图渲染异常 filterId=$filterId", e)
        } finally {
            withStateUpdate { loading.remove(filterId) }
        }
    }

    private fun putCache(filterId: Long, bitmap: Bitmap) {
        val evicted = ArrayList<Long>(0)
        synchronized(lock) {
            val previous = cache.put(filterId, bitmap)
            if (previous != null) cacheBytes -= previous.allocationByteCount.toLong()
            cacheBytes += bitmap.allocationByteCount.toLong()
            evictIfNeeded(evicted)
        }
        // 淘汰的 key 在锁外从快照状态里摘掉，避免持锁写 Compose 状态
        evicted.forEach { thumbnails.remove(it) }
        thumbnails[filterId] = bitmap
    }

    private fun evictIfNeeded(evicted: MutableList<Long>) {
        val iterator = cache.entries.iterator()
        while ((cacheBytes > MAX_CACHE_BYTES || cache.size > MAX_CACHE_COUNT) && iterator.hasNext()) {
            val entry = iterator.next()
            cacheBytes -= entry.value.allocationByteCount.toLong()
            evicted.add(entry.key)
            iterator.remove()
        }
    }

    private suspend fun withStateUpdate(block: () -> Unit) {
        withContext(Dispatchers.Main) { block() }
    }
}
