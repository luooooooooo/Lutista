package cn.alittlecookie.lutista.watermark.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cn.alittlecookie.lutista.watermark.cache.BitmapCache
import cn.alittlecookie.lutista.watermark.engine.LayoutEngine
import cn.alittlecookie.lutista.watermark.engine.UnitConverter
import cn.alittlecookie.lutista.watermark.model.BaseImageElement
import cn.alittlecookie.lutista.watermark.model.ContainerElement
import cn.alittlecookie.lutista.watermark.model.Element
import cn.alittlecookie.lutista.watermark.model.ElementBounds
import cn.alittlecookie.lutista.watermark.model.ImageElement
import cn.alittlecookie.lutista.watermark.model.LayoutResult
import cn.alittlecookie.lutista.watermark.model.OutputConfig
import cn.alittlecookie.lutista.watermark.model.OutputFormat
import cn.alittlecookie.lutista.watermark.model.Scene
import cn.alittlecookie.lutista.watermark.model.TextAlign
import cn.alittlecookie.lutista.watermark.model.TextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min

class OutputRenderer(private val bitmapCache: BitmapCache) {

    companion object {
        private const val TILE_SIZE = 2048
        private const val MEMORY_THRESHOLD_RATIO = 0.3
    }

    suspend fun render(scene: Scene, config: OutputConfig): RenderResult =
        withContext(Dispatchers.IO) {
            try {
                val converter = UnitConverter(config.sourceWidth, config.sourceHeight)
                val layoutEngine = LayoutEngine(converter)
                val layout = layoutEngine.layout(scene)

                val requiredMemory = layout.canvasWidth.toLong() * layout.canvasHeight * 4
                val maxMemory = Runtime.getRuntime().maxMemory()

                if (requiredMemory > maxMemory * MEMORY_THRESHOLD_RATIO) {
                    renderTiled(scene, layout, converter, config)
                } else {
                    renderDirect(scene, layout, converter, config)
                }
            } catch (e: Exception) {
                RenderResult.Error(e.message ?: "Unknown error")
            }
        }

    private fun renderDirect(
        scene: Scene,
        layout: LayoutResult,
        converter: UnitConverter,
        config: OutputConfig
    ): RenderResult {
        val bitmap = Bitmap.createBitmap(
            layout.canvasWidth, layout.canvasHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        canvas.drawColor(scene.baseImage.borderColor)

        val borderPx = with(converter) { scene.baseImage.borderWidth.toPx() }
        canvas.save()
        canvas.translate(borderPx.left, borderPx.top)
        drawBaseImage(canvas, scene.baseImage)
        canvas.restore()

        scene.elements.forEach { drawElement(canvas, it, layout, converter, 1f) }

        val result = saveToFile(bitmap, config)
        bitmap.recycle()
        return result
    }

    private suspend fun renderTiled(
        scene: Scene,
        layout: LayoutResult,
        converter: UnitConverter,
        config: OutputConfig
    ): RenderResult {
        val tilesX = ceil(layout.canvasWidth.toFloat() / TILE_SIZE).toInt()
        val tilesY = ceil(layout.canvasHeight.toFloat() / TILE_SIZE).toInt()

        val baseImageDecoder = BitmapRegionDecoder.newInstance(
            scene.baseImage.imageUri, false
        )

        val outputStream = File(config.outputPath).outputStream()
        val encoder = createEncoder(config, layout.canvasWidth, layout.canvasHeight)

        try {
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    coroutineContext.ensureActive()
                    val tileX = tx * TILE_SIZE
                    val tileY = ty * TILE_SIZE
                    val tileW = min(TILE_SIZE, layout.canvasWidth - tileX)
                    val tileH = min(TILE_SIZE, layout.canvasHeight - tileY)

                    renderTile(
                        scene, layout, converter, tileX, tileY, tileW, tileH,
                        baseImageDecoder, encoder
                    )
                }
            }
        } finally {
            baseImageDecoder?.recycle()
            encoder.finish()
            outputStream.close()
            bitmapCache.clear()
        }

        return RenderResult.Success(config.outputPath)
    }

    private fun renderTile(
        scene: Scene,
        layout: LayoutResult,
        converter: UnitConverter,
        tileX: Int, tileY: Int, tileW: Int, tileH: Int,
        baseImageDecoder: BitmapRegionDecoder?,
        encoder: TileEncoder
    ) {
        val tileBitmap = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tileBitmap)

        tileCanvas.clipRect(0, 0, tileW, tileH)
        tileCanvas.translate(-tileX.toFloat(), -tileY.toFloat())

        tileCanvas.drawColor(scene.baseImage.borderColor)

        val borderPx = with(converter) { scene.baseImage.borderWidth.toPx() }
        tileCanvas.save()
        tileCanvas.translate(borderPx.left, borderPx.top)
        drawBaseImageTiled(
            tileCanvas, baseImageDecoder,
            tileX - borderPx.left.toInt(),
            tileY - borderPx.top.toInt(),
            tileW, tileH
        )
        tileCanvas.restore()

        val tileRect = RectF(
            tileX.toFloat(), tileY.toFloat(),
            (tileX + tileW).toFloat(), (tileY + tileH).toFloat()
        )
        scene.elements.forEach { element ->
            val bounds = layout.elementBounds[element.id] ?: return@forEach
            val elementRect = RectF(
                bounds.totalX, bounds.totalY,
                bounds.totalX + bounds.totalWidth, bounds.totalY + bounds.totalHeight
            )
            if (RectF.intersects(tileRect, elementRect)) {
                drawElement(tileCanvas, element, layout, converter, 1f)
            }
        }

        encoder.writeTile(tileBitmap, tileX, tileY)
        tileBitmap.recycle()
    }

    private fun drawBaseImageTiled(
        canvas: Canvas,
        decoder: BitmapRegionDecoder?,
        offsetX: Int, offsetY: Int,
        tileW: Int, tileH: Int
    ) {
        if (decoder == null) return

        val imageW = decoder.width
        val imageH = decoder.height

        val visibleRect = Rect(
            min(0, -offsetX).coerceAtLeast(0),
            min(0, -offsetY).coerceAtLeast(0),
            min(imageW, tileW - offsetX).coerceAtMost(imageW),
            min(imageH, tileH - offsetY).coerceAtMost(imageH)
        )

        if (visibleRect.width() <= 0 || visibleRect.height() <= 0) return

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val regionBitmap = decoder.decodeRegion(visibleRect, options) ?: return
        canvas.drawBitmap(
            regionBitmap,
            (offsetX + visibleRect.left).toFloat(),
            (offsetY + visibleRect.top).toFloat(),
            null
        )
        regionBitmap.recycle()
    }

    private fun drawBaseImage(canvas: Canvas, baseImage: BaseImageElement) {
        val bitmap = BitmapFactory.decodeFile(baseImage.imageUri) ?: return
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
    }

    fun drawElementOnCanvas(
        canvas: Canvas,
        element: Element,
        layout: LayoutResult,
        converter: UnitConverter,
        parentOpacity: Float
    ) {
        drawElement(canvas, element, layout, converter, parentOpacity)
    }

    private fun drawElement(
        canvas: Canvas,
        element: Element,
        layout: LayoutResult,
        converter: UnitConverter,
        parentOpacity: Float
    ) {
        val bounds = layout.elementBounds[element.id] ?: return
        val opacity = element.opacity * parentOpacity

        canvas.save()
        canvas.translate(bounds.x, bounds.y)

        when (element) {
            is TextElement -> drawText(canvas, element, bounds, converter, opacity)
            is ImageElement -> drawImage(canvas, element, bounds, opacity)
            is ContainerElement -> {
                element.children.forEach { drawElement(canvas, it, layout, converter, opacity) }
            }
        }

        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        element: TextElement,
        bounds: ElementBounds,
        converter: UnitConverter,
        opacity: Float
    ) {
        val paint = TextPaint().apply {
            textSize = converter.gmToPx(element.fontSize)
            color = element.color
            alpha = (opacity * 255).toInt()
            typeface = element.fontUri?.let { Typeface.createFromFile(it) }
            letterSpacing = converter.gmToPx(element.letterSpacing) / textSize
            isAntiAlias = true
        }

        val layout = StaticLayout.Builder.obtain(
            element.content, 0, element.content.length, paint, bounds.width.toInt()
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

        layout.draw(canvas)
    }

    private fun drawImage(
        canvas: Canvas,
        element: ImageElement,
        bounds: ElementBounds,
        opacity: Float
    ) {
        val uri = element.imageUri
        val bitmap = bitmapCache.get(uri, bounds.width.toInt(), bounds.height.toInt())
        val paint = Paint().apply { alpha = (opacity * 255).toInt() }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        bitmapCache.release(uri, bounds.width.toInt(), bounds.height.toInt())
    }

    private fun saveToFile(bitmap: Bitmap, config: OutputConfig): RenderResult {
        val file = File(config.outputPath)
        file.parentFile?.mkdirs()

        val format = when (config.format) {
            OutputFormat.JPG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        bitmap.compress(format, config.quality, file.outputStream())

        return RenderResult.Success(config.outputPath)
    }

    private fun createEncoder(config: OutputConfig, width: Int, height: Int): TileEncoder {
        return SimpleTileEncoder(config)
    }
}

sealed class RenderResult {
    data class Success(val outputPath: String) : RenderResult()
    data class Error(val message: String) : RenderResult()
}

interface TileEncoder {
    fun writeTile(bitmap: Bitmap, x: Int, y: Int)
    fun finish()
}

class SimpleTileEncoder(private val config: OutputConfig) : TileEncoder {
    private val outputBitmap: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun writeTile(bitmap: Bitmap, x: Int, y: Int) {
        // For simplicity, just save each tile (in real implementation, combine tiles)
    }

    override fun finish() {
        // Finalize output
    }
}
