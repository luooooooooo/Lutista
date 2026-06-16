package cn.alittlecookie.lut2photo_n.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.alittlecookie.lut2photo_n.R
import cn.alittlecookie.lut2photo_n.data.database.entity.FilterEntity
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.FloatPoint
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.FilterConfigJson.toConfig
import cn.alittlecookie.lut2photo_n.util.FilterConfigJson.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilterEditorViewModel(
    private val filterId: Long,  // 0 = 新建, >0 = 编辑已有
    private val context: Context,
    private val filterRepository: FilterRepository,
    private val engine: ImageProcessingEngine,
    private val logger: LogManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FilterEditorViewModel"
        private const val MAX_HISTORY_SIZE = 50
        private const val MAX_PREVIEW_SIZE = 1024
        private const val LUT_DIR_NAME = "luts"

    }

    // ── 工作副本 ──────────────────────────────────────────────────

    var filterConfig by mutableStateOf(FilterConfig())
        private set
    var filterName by mutableStateOf("")

    // ── 预览 ──────────────────────────────────────────────────────

    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var isLongPressed by mutableStateOf(false)

    // ── 预览图片管理 ──────────────────────────────────────────────

    var previewImageUri by mutableStateOf<String?>(null)
    private var cachedRgbaBytes: ByteArray? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var filterGeneration = 0

    // ── 撤销/重做 ─────────────────────────────────────────────────

    private val undoStack = ArrayDeque<FilterConfig>(MAX_HISTORY_SIZE)
    private val redoStack = ArrayDeque<FilterConfig>(MAX_HISTORY_SIZE)
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    // ── 脏标记 ────────────────────────────────────────────────────

    var hasChanges by mutableStateOf(false)
        private set
    private var initialConfig = FilterConfig()
    private var existingEntity: FilterEntity? = null

    // ── 初始化 ────────────────────────────────────────────────────

    suspend fun loadFilter() {
        if (filterId <= 0) return
        withContext(Dispatchers.IO) {
            try {
                val entity = filterRepository.getFilterById(filterId)
                if (entity != null) {
                    existingEntity = entity
                    val config = entity.toConfig()
                    filterConfig = config
                    filterName = config.name
                    initialConfig = config
                    logger.d(TAG, "已加载滤镜: id=$filterId, name=${config.name}")
                } else {
                    logger.w(TAG, "未找到滤镜: id=$filterId")
                }
            } catch (e: Exception) {
                logger.e(TAG, "加载滤镜失败", e)
            }
        }
    }

    /**
     * 初始化预览图片，从 R.raw.example 加载默认示例图
     */
    suspend fun initPreview() {
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeResource(context.resources, R.raw.example, options)

                val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_PREVIEW_SIZE)
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize

                val bitmap = BitmapFactory.decodeResource(
                    context.resources, R.raw.example, options
                ) ?: run {
                    logger.e(TAG, "无法解码默认预览图")
                    return@withContext
                }

                cachedWidth = bitmap.width
                cachedHeight = bitmap.height
                cachedRgbaBytes = ByteArray(cachedWidth * cachedHeight * 4)
                bitmap.copyPixelsToBuffer(ByteBuffer.wrap(cachedRgbaBytes!!))
                bitmap.recycle()

                logger.d(TAG, "默认预览图已加载: ${cachedWidth}x${cachedHeight}")
                renderPreview()
            } catch (e: Exception) {
                logger.e(TAG, "初始化预览图失败", e)
            }
        }
    }

    /**
     * 从用户选择的 URI 加载预览图片
     */
    fun loadPreviewImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取图片尺寸
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val sampleSize = calculateSampleSize(
                    boundsOptions.outWidth, boundsOptions.outHeight, MAX_PREVIEW_SIZE
                )

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }

                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: run {
                    logger.e(TAG, "无法从 URI 解码图片: $uri")
                    return@launch
                }

                cachedWidth = bitmap.width
                cachedHeight = bitmap.height
                cachedRgbaBytes = ByteArray(cachedWidth * cachedHeight * 4)
                bitmap.copyPixelsToBuffer(ByteBuffer.wrap(cachedRgbaBytes!!))
                bitmap.recycle()

                previewImageUri = uri.toString()
                logger.d(TAG, "用户预览图已加载: ${cachedWidth}x${cachedHeight}")
                renderPreview()
            } catch (e: Exception) {
                logger.e(TAG, "加载用户预览图失败", e)
            }
        }
    }

    // ── 配置更新 ──────────────────────────────────────────────────

    /**
     * 更新配置，自动推入撤销栈并触发预览重绘
     */
    fun updateConfig(transform: (FilterConfig) -> FilterConfig) {
        undoStack.addLast(filterConfig)
        if (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }
        redoStack.clear()

        filterConfig = transform(filterConfig)
        hasChanges = true
        triggerRenderPreview()
    }

    /**
     * 更新曲线通道（提交版本，推撤销栈）
     * 直接操作 filterConfig，不依赖闭包捕获的 config 快照
     */
    fun updateCurveChannel(channelIndex: Int, points: List<FloatPoint>) {
        val newCurve = when (channelIndex) {
            0 -> filterConfig.curveConfig.copy(whiteChannel = points)
            1 -> filterConfig.curveConfig.copy(redChannel = points)
            2 -> filterConfig.curveConfig.copy(greenChannel = points)
            3 -> filterConfig.curveConfig.copy(blueChannel = points)
            else -> filterConfig.curveConfig
        }
        updateConfig { it.copy(curveConfig = newCurve) }
    }

    /**
     * 更新曲线通道（预览版本，不推撤销栈）
     */
    fun updateCurveChannelPreview(channelIndex: Int, points: List<FloatPoint>) {
        val newCurve = when (channelIndex) {
            0 -> filterConfig.curveConfig.copy(whiteChannel = points)
            1 -> filterConfig.curveConfig.copy(redChannel = points)
            2 -> filterConfig.curveConfig.copy(greenChannel = points)
            3 -> filterConfig.curveConfig.copy(blueChannel = points)
            else -> filterConfig.curveConfig
        }
        updateConfigPreview { it.copy(curveConfig = newCurve) }
    }

    /**
     * 更新配置但不推入撤销栈（用于拖拽中的实时预览）
     */
    fun updateConfigPreview(transform: (FilterConfig) -> FilterConfig) {
        filterConfig = transform(filterConfig)
        hasChanges = true
        triggerRenderPreview()
    }

    /**
     * 更新滤镜名称
     */
    fun updateName(name: String) {
        filterName = name
        filterConfig = filterConfig.copy(name = name)
        hasChanges = filterConfig != initialConfig
    }

    // ── 撤销/重做 ─────────────────────────────────────────────────

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(filterConfig)
        filterConfig = undoStack.removeLast()
        hasChanges = filterConfig != initialConfig
        triggerRenderPreview()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(filterConfig)
        filterConfig = redoStack.removeLast()
        hasChanges = filterConfig != initialConfig
        triggerRenderPreview()
    }

    // ── 预览渲染 ──────────────────────────────────────────────────

    private var renderJob: Job? = null

    fun toggleLongPress() {
        isLongPressed = !isLongPressed
    }

    /**
     * Debounced render: cancels previous pending render, waits 32ms (~30fps cap),
     * then launches a single render. Prevents OOM from rapid drag updates.
     */
    private fun triggerRenderPreview() {
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.IO) {
            delay(32) // debounce ~30fps
            renderPreview()
        }
    }

    /**
     * 使用 Vulkan GPU 渲染预览图
     */
    private suspend fun renderPreview() {
        val bytes = cachedRgbaBytes ?: run {
            logger.w(TAG, "缓存的 RGBA 字节为空，跳过渲染")
            return
        }
        val width = cachedWidth
        val height = cachedHeight
        if (width <= 0 || height <= 0) {
            logger.w(TAG, "缓存的图片尺寸无效: ${width}x${height}")
            return
        }

        val currentGeneration = ++filterGeneration
        val currentConfig = filterConfig

        withContext(Dispatchers.IO) {
            try {
                val result = engine.processImageGpuFromBytes(
                    bytes, width, height, currentConfig
                )
                // 丢弃过期的渲染结果
                if (currentGeneration == filterGeneration && result != null) {
                    withContext(Dispatchers.Main) {
                        previewBitmap?.recycle()
                        previewBitmap = result
                    }
                } else {
                    result?.recycle()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.e(TAG, "预览渲染失败", e)
            }
        }
    }

    // ── LUT 导入 ──────────────────────────────────────────────────

    /**
     * 通过 SAF 导入 LUT 文件到指定槽位
     * @param slot LUT 槽位 (1 或 2)
     * @param uri  源文件 URI
     */
    fun importLut(slot: Int, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val lutDir = File(context.filesDir, LUT_DIR_NAME)
                if (!lutDir.exists()) lutDir.mkdirs()

                val originalName = getFileName(uri).substringBeforeLast('.')
                val dateFormat = SimpleDateFormat("yyMM", Locale.getDefault())
                val prefix = dateFormat.format(Date())
                val existingFiles = lutDir.listFiles()?.mapNotNull { it.name } ?: emptyList()
                val ext = "cube"
                val pattern = Regex("^\\Q$originalName\\E-\\Q$prefix\\E(\\d{3})\\.$ext$")
                val maxNum = existingFiles
                    .mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    .maxOrNull() ?: 0
                val nextNum = (maxNum + 1).toString().padStart(3, '0')
                val fileName = "$originalName-$prefix$nextNum.$ext"
                val destFile = File(lutDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    logger.e(TAG, "无法打开 LUT 文件输入流: $uri")
                    return@launch
                }

                val path = destFile.absolutePath
                logger.d(TAG, "LUT 已导入到: $path (槽位 $slot)")

                when (slot) {
                    1 -> updateConfig { config ->
                        config.copy(lutPath1 = path)
                    }
                    2 -> updateConfig { config ->
                        config.copy(lutPath2 = path)
                    }
                    else -> logger.w(TAG, "无效的 LUT 槽位: $slot")
                }
            } catch (e: Exception) {
                logger.e(TAG, "导入 LUT 文件失败", e)
            }
        }
    }

    // ── 保存 ──────────────────────────────────────────────────────

    /**
     * 保存滤镜到数据库
     * @return true 保存成功, false 保存失败
     */
    suspend fun save(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val configWithName = filterConfig.copy(name = filterName.ifEmpty { "新滤镜" })
                val entity = configWithName.toEntity(existingEntity)

                if (filterId > 0) {
                    filterRepository.update(entity)
                    logger.d(TAG, "滤镜已更新: id=$filterId")
                } else {
                    val newId = filterRepository.insert(entity)
                    logger.d(TAG, "新滤镜已保存: id=$newId")
                }

                initialConfig = configWithName
                hasChanges = false
                true
            } catch (e: Exception) {
                logger.e(TAG, "保存滤镜失败", e)
                false
            }
        }
    }

    // ── 丢弃更改 ──────────────────────────────────────────────────

    /**
     * 丢弃所有未保存的更改，恢复到初始状态
     */
    fun discardChanges() {
        filterConfig = initialConfig
        filterName = initialConfig.name
        hasChanges = false
        undoStack.clear()
        redoStack.clear()
        triggerRenderPreview()
        logger.d(TAG, "已丢弃所有未保存的更改")
    }

    // ── 资源清理 ──────────────────────────────────────────────────

    /**
     * 释放预览 Bitmap 和缓存资源
     */
    fun release() {
        renderJob?.cancel()
        previewBitmap?.recycle()
        previewBitmap = null
        cachedRgbaBytes = null
        cachedWidth = 0
        cachedHeight = 0
        logger.d(TAG, "资源已释放")
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxSize || height / sampleSize > maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
