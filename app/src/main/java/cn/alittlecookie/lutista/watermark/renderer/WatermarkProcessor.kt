package cn.alittlecookie.lutista.watermark.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import cn.alittlecookie.lutista.watermark.cache.BitmapCache
import cn.alittlecookie.lutista.watermark.engine.LayoutEngine
import cn.alittlecookie.lutista.watermark.engine.UnitConverter
import cn.alittlecookie.lutista.watermark.model.Scene

class WatermarkProcessor(private val bitmapCache: BitmapCache) {

    fun applyWatermark(input: Bitmap, scene: Scene?): Bitmap {
        if (scene == null) return input

        val converter = UnitConverter(input.width, input.height)
        val layoutEngine = LayoutEngine(converter)
        val layout = layoutEngine.layout(scene)

        val canvasW = layout.canvasWidth
        val canvasH = layout.canvasHeight

        val output = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val borderPx = with(converter) { scene.baseImage.borderWidth.toPx() }

        canvas.drawColor(scene.baseImage.borderColor)
        canvas.save()
        canvas.translate(borderPx.left, borderPx.top)
        canvas.drawBitmap(input, 0f, 0f, null)
        canvas.restore()

        val renderer = OutputRenderer(bitmapCache)
        scene.elements.forEach { element ->
            renderer.drawElementOnCanvas(canvas, element, layout, converter, 1f)
        }

        return output
    }
}
