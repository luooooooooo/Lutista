package cn.alittlecookie.lut2photo_n.domain.engine

import android.graphics.Bitmap
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.UnifiedLut
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.LutParser
import cn.alittlecookie.lut2photo_n.watermark.model.Scene
import cn.alittlecookie.lut2photo_n.watermark.renderer.WatermarkProcessor
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class ImageProcessingEngine(
    private val logger: LogManager,
    private val lutParser: LutParser,
    private val lutProcessor: LutProcessor,
    private val brightnessProcessor: BrightnessProcessor,
    private val colorProcessor: ColorProcessor,
    private val curveProcessor: CurveProcessor,
    private val lchProcessor: LchProcessor,
    private val vignetteProcessor: VignetteProcessor,
    private val grainProcessor: GrainProcessor,
    private val watermarkProcessor: WatermarkProcessor
) {
    companion object {
        private const val TAG = "ImageProcessingEngine"
    }

    fun processImagePreview(input: Mat, filter: FilterConfig): Mat {
        logger.d(TAG, "Processing image preview")
        var current = input.clone()

        try {
            current = lutProcessor.process(current, filter)
            current = brightnessProcessor.process(current, filter)
            current = colorProcessor.process(current, filter)
            current = curveProcessor.process(current, filter)
            current = lchProcessor.process(current, filter)
            current = vignetteProcessor.process(current, filter)
            current = grainProcessor.process(current, filter)
        } catch (e: Exception) {
            logger.e(TAG, "Error processing image preview", e)
        }

        return current
    }

    fun processImageWithUnifiedLut(input: Mat, unifiedLut: UnifiedLut): Mat {
        logger.d(TAG, "Processing image with unified LUT")
        return lutProcessor.processWithUnifiedLut(input, unifiedLut)
    }

    fun applyWatermark(input: Mat, scene: Scene?): Mat {
        if (scene == null) return input
        logger.d(TAG, "Applying watermark")

        val bitmap = matToBitmap(input)
        val result = watermarkProcessor.applyWatermark(bitmap, scene)
        bitmap.recycle()

        return bitmapToMat(result)
    }

    suspend fun computeUnifiedLut(filter: FilterConfig): UnifiedLut {
        logger.d(TAG, "Computing unified LUT for filter: ${filter.name}")
        val size = filter.lutPrecision
        val data = FloatArray(size * size * size * 3)

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val rNorm = r.toFloat() / (size - 1)
                    val gNorm = g.toFloat() / (size - 1)
                    val bNorm = b.toFloat() / (size - 1)

                    var color = floatArrayOf(rNorm, gNorm, bNorm)

                    color = lutProcessor.applyLutToColor(color, filter)
                    color = brightnessProcessor.applyToColor(color, filter)
                    color = colorProcessor.applyToColor(color, filter)
                    color = curveProcessor.applyToColor(color, filter)
                    color = lchProcessor.applyToColor(color, filter)

                    val idx = (b * size * size + g * size + r) * 3
                    data[idx] = color[0].coerceIn(0f, 1f)
                    data[idx + 1] = color[1].coerceIn(0f, 1f)
                    data[idx + 2] = color[2].coerceIn(0f, 1f)
                }
            }
        }

        return UnifiedLut(size = size, data = data)
    }

    fun computeFilterHash(filter: FilterConfig): String {
        val sb = StringBuilder()
        sb.append(filter.lutPath1)
        sb.append(filter.lutConcentration1)
        sb.append(filter.lutPath2)
        sb.append(filter.lutConcentration2)
        sb.append(filter.lutPrecision)
        sb.append(filter.exposure)
        sb.append(filter.contrast)
        sb.append(filter.highlights)
        sb.append(filter.shadows)
        sb.append(filter.saturation)
        sb.append(filter.colorTemperature)
        sb.append(filter.tint)
        return sb.toString().hashCode().toString()
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        return mat
    }
}
