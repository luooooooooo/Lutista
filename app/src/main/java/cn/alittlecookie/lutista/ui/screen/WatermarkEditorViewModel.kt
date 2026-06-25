package cn.alittlecookie.lutista.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cn.alittlecookie.lutista.R
import cn.alittlecookie.lutista.data.database.entity.WatermarkEntity
import cn.alittlecookie.lutista.data.repository.WatermarkRepository
import cn.alittlecookie.lutista.util.ExifUtils
import cn.alittlecookie.lutista.watermark.engine.LayoutEngine
import cn.alittlecookie.lutista.watermark.engine.UnitConverter
import cn.alittlecookie.lutista.watermark.manager.HistoryManager
import cn.alittlecookie.lutista.watermark.model.BaseImageElement
import cn.alittlecookie.lutista.watermark.model.ContainerElement
import cn.alittlecookie.lutista.watermark.model.ContainerMode
import cn.alittlecookie.lutista.watermark.model.Element
import cn.alittlecookie.lutista.watermark.model.ElementBounds
import cn.alittlecookie.lutista.watermark.model.FontWeight
import cn.alittlecookie.lutista.watermark.model.ImageElement
import cn.alittlecookie.lutista.watermark.model.LayoutResult
import cn.alittlecookie.lutista.watermark.model.Scene
import cn.alittlecookie.lutista.watermark.model.TextAlign
import cn.alittlecookie.lutista.watermark.model.TextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import android.graphics.Canvas as AndroidCanvas

class WatermarkEditorViewModel(
    private val watermarkId: Long,
    private val context: Context,
    private val watermarkRepository: WatermarkRepository,
    private val historyManager: HistoryManager,
    private val scope: CoroutineScope
) {
    var scene by mutableStateOf(Scene(baseImage = BaseImageElement(imageUri = "")))
        private set
    var watermarkName by mutableStateOf("未命名水印")
    var hasChanges by mutableStateOf(false)
        private set

    var baseImageBitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    var layoutResult by mutableStateOf<LayoutResult?>(null)
        private set
    var converter by mutableStateOf<UnitConverter?>(null)
        private set

    val navigationStack = mutableStateListOf<String>()
    var selectedElementId by mutableStateOf<String?>(null)

    private var existingEntity: WatermarkEntity? = null
    private var initialSceneJson: String = ""
    private var renderJob: Job? = null
    private var baseImageWidth = 0
    private var baseImageHeight = 0

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val entity = watermarkRepository.getWatermarkById(watermarkId)
            if (entity != null) {
                existingEntity = entity
                watermarkName = entity.name
                val loadedScene = watermarkRepository.deserializeScene(entity)
                scene = loadedScene
                initialSceneJson = watermarkRepository.serializeScene(loadedScene)
                historyManager.saveState(loadedScene)
                hasChanges = false
            } else {
                val newEntity = createInitialEntity()
                watermarkName = newEntity.name
                initialSceneJson = newEntity.configJson
                hasChanges = false
            }
        }
        loadDefaultPreview()
    }

    private suspend fun createInitialEntity(): WatermarkEntity {
        val defaultScene = Scene(baseImage = BaseImageElement(imageUri = ""))
        scene = defaultScene
        val entity = WatermarkEntity(
            name = "未命名水印",
            configJson = watermarkRepository.serializeScene(defaultScene)
        )
        val id = watermarkRepository.insert(entity)
        existingEntity = entity.copy(id = id)
        return entity
    }

    private fun loadDefaultPreview() {
        scope.launch(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options()
                val bitmap = BitmapFactory.decodeResource(context.resources, R.raw.example, options)
                if (bitmap != null) {
                    baseImageWidth = bitmap.width
                    baseImageHeight = bitmap.height
                    baseImageBitmap = bitmap.asImageBitmap()
                    computeLayout()
                }
            } catch (_: Exception) {}
        }
    }

    fun loadPreviewImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val localPath = copyToLocal(uri)
                val bitmap = BitmapFactory.decodeFile(localPath)
                    ?.let { ExifUtils.correctOrientation(it, localPath) }
                if (bitmap != null) {
                    baseImageWidth = bitmap.width
                    baseImageHeight = bitmap.height
                    baseImageBitmap = bitmap.asImageBitmap()
                    computeLayout()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无法加载图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importElementImage(uri: Uri): String? {
        try {
            return copyToLocal(uri)
        } catch (_: Exception) {
            return null
        }
    }

    fun importFont(uri: Uri): String? {
        try {
            return copyToLocal(uri)
        } catch (_: Exception) {
            return null
        }
    }

    private fun copyToLocal(sourceUri: Uri): String {
        val dir = File(context.filesDir, ASSETS_DIR)
        if (!dir.exists()) dir.mkdirs()

        val originalName = getFileName(sourceUri) ?: "file_${System.currentTimeMillis()}"
        val ext = originalName.substringAfterLast('.', "")
        val baseName = originalName.substringBeforeLast('.')

        // Get source size and sample bytes for dedup
        var sourceSize = -1L
        val sampleSize = 4096
        var sampleBytes: ByteArray? = null
        context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0) sourceSize = cursor.getLong(idx)
        }
        context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            val buf = ByteArray(sampleSize)
            val read = stream.read(buf)
            if (read > 0) sampleBytes = buf.copyOf(read)
        } ?: throw IllegalStateException("Cannot open: $sourceUri")

        // Check existing files for same content
        val mainName = "$baseName.$ext"
        val candidates = dir.listFiles()?.filter { f ->
            f.name == mainName || f.name.matches(Regex("^\\Q$baseName\\E-\\d+\\.\\Q$ext\\E$"))
        } ?: emptyList()

        for (existing in candidates) {
            if (sourceSize > 0 && existing.length() != sourceSize) continue
            if (sampleBytes != null) {
                val buf = ByteArray(sampleBytes.size)
                existing.inputStream().use { it.read(buf) }
                if (buf.contentEquals(sampleBytes)) return existing.absolutePath
            }
        }

        // Find next available filename
        val mainFile = File(dir, mainName)
        val destFile = if (!mainFile.exists()) {
            mainFile
        } else {
            var num = 2
            var f: File
            do { f = File(dir, "$baseName-$num.$ext"); num++ } while (f.exists())
            f
        }

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile.absolutePath
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun computeLayout() {
        if (baseImageWidth <= 0 || baseImageHeight <= 0) return
        val cvt = UnitConverter(baseImageWidth, baseImageHeight)
        converter = cvt
        val engine = LayoutEngine(cvt)
        layoutResult = engine.layout(scene)
    }

    private fun triggerComputeLayout() {
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.IO) {
            delay(50)
            computeLayout()
        }
    }

    fun getCurrentLevelElements(): List<Element> {
        if (navigationStack.isEmpty()) {
            return scene.elements
        }
        val container = findElementById(navigationStack.last()) as? ContainerElement ?: return emptyList()
        return container.children
    }

    fun navigateInto(elementId: String) {
        selectedElementId = null
        navigationStack.add(elementId)
    }

    fun navigateBack() {
        selectedElementId = null
        if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(navigationStack.size - 1)
        }
    }

    fun selectElement(elementId: String) {
        selectedElementId = elementId
    }

    fun deselectElement() {
        selectedElementId = null
    }

    fun findElementById(id: String): Element? {
        fun find(list: List<Element>): Element? {
            for (el in list) {
                if (el.id == id) return el
                if (el is ContainerElement) {
                    find(el.children)?.let { return it }
                }
            }
            return null
        }
        return find(scene.elements)
    }

    fun getDisplayName(element: Element): String {
        if (element !is ContainerElement) {
            return when (element) {
                is TextElement -> element.content.take(10).ifEmpty { "文字" }
                is ImageElement -> element.imageUri.substringAfterLast('/').ifEmpty { "图片" }
                else -> "元素"
            }
        }
        val modePrefix = when (element.mode) {
            ContainerMode.LINEAR_H -> "横排容器"
            ContainerMode.LINEAR_V -> "竖排容器"
            ContainerMode.FREE -> "自由容器"
        }
        val path = findReversePath(element.id)
        if (path.isEmpty()) return modePrefix
        return "$modePrefix ${path.joinToString("-")}"
    }

    private fun findReversePath(id: String): List<Int> {
        fun resolve(list: List<Element>, acc: List<Int>): List<Int>? {
            val n = list.size
            list.forEachIndexed { index, el ->
                val rev = n - index
                if (el.id == id) return acc + rev
                if (el is ContainerElement) {
                    resolve(el.children, acc + rev)?.let { return it }
                }
            }
            return null
        }
        return resolve(scene.elements, emptyList()) ?: emptyList()
    }

    fun findParentOf(id: String): ContainerElement? {
        fun findParent(list: List<Element>, parent: ContainerElement?): ContainerElement? {
            for (el in list) {
                if (el.id == id) return parent
                if (el is ContainerElement) {
                    findParent(el.children, el)?.let { return it }
                }
            }
            return null
        }
        return findParent(scene.elements, null)
    }

    fun addTextElement() {
        val id = "t_${UUID.randomUUID().toString().take(8)}"
        val newElement = TextElement(
            id = id, content = "文字", fontSize = 10f,
            color = android.graphics.Color.BLACK
        )
        addElementToList(newElement)
    }

    fun addImageElement() {
        val id = "i_${UUID.randomUUID().toString().take(8)}"
        val newElement = ImageElement(id = id, imageUri = "", imageSizeRef = 2, width = 200f)
        addElementToList(newElement)
    }

    fun addContainerElement() {
        val id = "c_${UUID.randomUUID().toString().take(8)}"
        val newElement = ContainerElement(id = id, children = emptyList(), mode = ContainerMode.LINEAR_H)
        addElementToList(newElement)
    }

    private fun addElementToList(element: Element) {
        val currentContainerId = navigationStack.lastOrNull()
        val newScene = if (currentContainerId != null) {
            scene.copy(elements = addToContainer(scene.elements, currentContainerId, element))
        } else {
            scene.copy(elements = listOf(element) + scene.elements)
        }
        applyNewScene(newScene)
        selectedElementId = element.id
    }

    private fun addToContainer(list: List<Element>, containerId: String, newElement: Element): List<Element> {
        return list.map { el ->
            if (el.id == containerId && el is ContainerElement) {
                el.copy(children = listOf(newElement) + el.children)
            } else if (el is ContainerElement) {
                el.copy(children = addToContainer(el.children, containerId, newElement))
            } else el
        }
    }

    fun deleteElement(elementId: String) {
        val newScene = scene.copy(
            elements = removeElementRecursive(scene.elements, elementId)
        )
        applyNewScene(newScene)
        if (selectedElementId == elementId) {
            selectedElementId = null
        }
    }

    private fun removeElementRecursive(list: List<Element>, id: String): List<Element> {
        return list.filter { it.id != id }.map { el ->
            if (el is ContainerElement) {
                el.copy(children = removeElementRecursive(el.children, id))
            } else el
        }
    }

    fun moveElement(fromIndex: Int, toIndex: Int) {
        val newScene = if (navigationStack.isEmpty()) {
            val mutable = scene.elements.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            scene.copy(elements = mutable)
        } else {
            val currentContainerId = navigationStack.last()
            scene.copy(elements = moveInElements(scene.elements, currentContainerId, fromIndex, toIndex))
        }
        applyNewScene(newScene)
    }

    private fun moveInElements(list: List<Element>, containerId: String, from: Int, to: Int): List<Element> {
        return list.map { el ->
            if (el.id == containerId && el is ContainerElement) {
                val mutable = el.children.toMutableList()
                val item = mutable.removeAt(from)
                mutable.add(to, item)
                el.copy(children = mutable)
            } else el
        }
    }

    fun updateElement(elementId: String, transform: (Element) -> Element) {
        val newScene = updateElementInScene(elementId, transform)
        if (newScene != null) applyNewScene(newScene)
    }

    fun updateTextElement(elementId: String, transform: (TextElement) -> TextElement) {
        val newScene = updateElementInScene(elementId) { el ->
            if (el is TextElement) transform(el) else el
        }
        if (newScene != null) applyNewScene(newScene)
    }

    fun updateImageElement(elementId: String, transform: (ImageElement) -> ImageElement) {
        val newScene = updateElementInScene(elementId) { el ->
            if (el is ImageElement) transform(el) else el
        }
        if (newScene != null) applyNewScene(newScene)
    }

    fun updateContainerElement(elementId: String, transform: (ContainerElement) -> ContainerElement) {
        val newScene = updateElementInScene(elementId) { el ->
            if (el is ContainerElement) transform(el) else el
        }
        if (newScene != null) applyNewScene(newScene)
    }

    fun updateBaseImage(transform: (BaseImageElement) -> BaseImageElement) {
        val newScene = scene.copy(baseImage = transform(scene.baseImage))
        applyNewScene(newScene)
    }

    private fun updateElementInScene(elementId: String, transform: (Element) -> Element): Scene {
        fun update(list: List<Element>): List<Element> {
            return list.map { el ->
                if (el.id == elementId) transform(el)
                else if (el is ContainerElement) el.copy(children = update(el.children))
                else el
            }
        }
        return scene.copy(elements = update(scene.elements))
    }

    private fun applyNewScene(newScene: Scene) {
        scene = newScene
        hasChanges = true
        historyManager.saveState(newScene)
        updateUndoRedoState()
        triggerComputeLayout()
    }

    fun undo() {
        val previous = historyManager.undo() ?: return
        scene = previous
        hasChanges = true
        updateUndoRedoState()
        triggerComputeLayout()
    }

    fun redo() {
        val next = historyManager.redo() ?: return
        scene = next
        hasChanges = true
        updateUndoRedoState()
        triggerComputeLayout()
    }

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun updateUndoRedoState() {
        canUndo = historyManager.canUndo()
        canRedo = historyManager.canRedo()
    }

    fun updateName(name: String) {
        watermarkName = name
        hasChanges = true
    }

    suspend fun save(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val configJson = watermarkRepository.serializeScene(scene)
                val finalName = watermarkName.ifEmpty { "未命名水印" }
                if (existingEntity != null) {
                    watermarkRepository.update(
                        existingEntity!!.copy(name = finalName, configJson = configJson)
                    )
                } else {
                    val id = watermarkRepository.insert(
                        WatermarkEntity(name = finalName, configJson = configJson)
                    )
                    existingEntity = WatermarkEntity(id = id, name = finalName, configJson = configJson)
                }
                initialSceneJson = configJson
                hasChanges = false
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun discardChanges() {
        try {
            val originalScene = watermarkRepository.deserializeScene(
                WatermarkEntity(name = "", configJson = initialSceneJson)
            )
            scene = originalScene
            watermarkName = existingEntity?.name ?: "未命名水印"
            hasChanges = false
            historyManager.clear()
            updateUndoRedoState()
            triggerComputeLayout()
        } catch (_: Exception) {
            historyManager.clear()
            updateUndoRedoState()
            triggerComputeLayout()
        }
    }

    fun release() {
        renderJob?.cancel()
        baseImageBitmap = null
        historyManager.clear()
    }

    companion object {
        private const val ASSETS_DIR = "watermark_assets"

        fun renderTextToBitmap(
            element: TextElement,
            converter: UnitConverter,
            bounds: ElementBounds
        ): Bitmap {
            val paint = TextPaint().apply {
                textSize = converter.gmToPx(element.fontSize)
                color = element.color
                isAntiAlias = true
                typeface = when {
                    !element.fontUri.isNullOrEmpty() -> Typeface.createFromFile(element.fontUri)
                    element.fontWeight == FontWeight.BOLD -> Typeface.DEFAULT_BOLD
                    else -> Typeface.DEFAULT
                }
                letterSpacing = converter.gmToPx(element.letterSpacing) / textSize
            }

            val layout = StaticLayout.Builder.obtain(
                element.content, 0, element.content.length, paint,
                ceil(bounds.width).toInt()
            ).apply {
                setLineSpacing(0f, element.lineHeight)
                setIncludePad(false)
                setAlignment(
                    when (element.textAlign) {
                        TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                        TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                        TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    }
                )
            }.build()

            val w = ceil(bounds.width).toInt()
            val h = ceil(bounds.height).toInt()
            if (w <= 0 || h <= 0) {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            layout.draw(canvas)
            return bitmap
        }
    }
}
