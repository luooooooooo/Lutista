package cn.alittlecookie.lut2photo_n.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.model.UnifiedLut
import cn.alittlecookie.lut2photo_n.gpu.VulkanGpuEngine
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.LutParser
import cn.alittlecookie.lut2photo_n.watermark.model.Scene
import cn.alittlecookie.lut2photo_n.watermark.renderer.WatermarkProcessor
import org.opencv.android.Utils
import org.opencv.core.CvType
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
    private val watermarkProcessor: WatermarkProcessor,
    private val vulkanGpuEngine: VulkanGpuEngine
) {
    companion object {
        private const val TAG = "ImageProcessingEngine"
    }

    // LUT upload cache to avoid redundant uploads
    private var lastLutPath1: String? = null
    private var lastLutPath2: String? = null
    private var lut1Uploaded = false
    private var lut2Uploaded = false

    /**
     * GPU path from pre-decoded RGBA8 bytes. Avoids Mat conversion.
     * GPU path from pre-decoded RGBA8 bytes. Avoids Mat conversion.
     * Input bytes are NOT modified (defensive copy before GPU upload).
     */
    fun processImageGpuFromBytes(
        rgbaBytes: ByteArray, width: Int, height: Int, filter: FilterConfig
    ): Bitmap? {
        if (!vulkanGpuEngine.isAvailable) return null
        try {
            val t0 = System.nanoTime()
            uploadLutIfNeeded(filter)
            val t1 = System.nanoTime()
            val params = buildGpuParams(filter)
            val inputCopy = rgbaBytes.copyOf()
            val t2 = System.nanoTime()
            val resultBytes = vulkanGpuEngine.processImage(inputCopy, width, height, params)
                ?: return null
            val t3 = System.nanoTime()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(resultBytes))
            val t4 = System.nanoTime()
            logger.d(TAG, "GPU preview ${width}x${height}: " +
                "LUT=${(t1-t0)/1_000_000}ms, copyOf=${(t2-t1)/1_000_000}ms, " +
                "gpu=${(t3-t2)/1_000_000}ms, bitmap=${(t4-t3)/1_000_000}ms, " +
                "total=${(t4-t0)/1_000_000}ms")
            return bitmap
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w(TAG, "GPU processing from bytes failed", e)
            return null
        }
    }

    /**
     * GPU path from URI. Loads Bitmap, extracts RGBA8 bytes, processes via GPU.
     * Returns Bitmap result. No Mat conversion.
     */
    fun processImageGpuFromUri(
        context: Context, uri: String, filter: FilterConfig
    ): Bitmap? {
        if (!vulkanGpuEngine.isAvailable) return null
        try {
            val inputBitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val width = inputBitmap.width
            val height = inputBitmap.height
            val rgbaBytes = ByteArray(width * height * 4)
            val buffer = java.nio.ByteBuffer.wrap(rgbaBytes)
            inputBitmap.copyPixelsToBuffer(buffer)

            uploadLutIfNeeded(filter)
            val params = buildGpuParams(filter)
            val resultBytes = vulkanGpuEngine.processImage(rgbaBytes, width, height, params)
            inputBitmap.recycle()

            if (resultBytes == null) return null
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(resultBytes))
            return result
        } catch (e: Exception) {
            logger.w(TAG, "GPU processing from URI failed", e)
            return null
        }
    }

    private fun uploadLutIfNeeded(filter: FilterConfig) {
        if (filter.lutPath1 != lastLutPath1) {
            lut1Uploaded = false
            if (filter.lutPath1 != null) {
                val t0 = System.nanoTime()
                val lut = lutProcessor.loadLutForGpu(filter.lutPath1)
                val t1 = System.nanoTime()
                if (lut != null) {
                    vulkanGpuEngine.uploadLut(lut, 0)
                    val t2 = System.nanoTime()
                    lut1Uploaded = true
                    logger.d(TAG, "LUT1: parse=${(t1-t0)/1_000_000}ms upload=${(t2-t1)/1_000_000}ms path=${filter.lutPath1}")
                } else {
                    logger.w(TAG, "LUT1: load failed path=${filter.lutPath1}")
                }
            }
            lastLutPath1 = filter.lutPath1
        }
        if (filter.lutPath2 != lastLutPath2) {
            lut2Uploaded = false
            if (filter.lutPath2 != null) {
                val t0 = System.nanoTime()
                val lut = lutProcessor.loadLutForGpu(filter.lutPath2)
                val t1 = System.nanoTime()
                if (lut != null) {
                    vulkanGpuEngine.uploadLut(lut, 1)
                    val t2 = System.nanoTime()
                    lut2Uploaded = true
                    logger.d(TAG, "LUT2: parse=${(t1-t0)/1_000_000}ms upload=${(t2-t1)/1_000_000}ms path=${filter.lutPath2}")
                } else {
                    logger.w(TAG, "LUT2: load failed path=${filter.lutPath2}")
                }
            }
            lastLutPath2 = filter.lutPath2
        }
    }

    fun processImagePreview(input: Mat, filter: FilterConfig): Mat {
        if (vulkanGpuEngine.isAvailable) {
            try {
                val result = processImageGpu(input, filter)
                if (result != null) return result
            } catch (e: Exception) {
                logger.w(TAG, "GPU processing failed, falling back to CPU", e)
            }
        }
        return processImageCpu(input, filter)
    }

    private fun processImageGpu(input: Mat, filter: FilterConfig): Mat? {
        val width = input.cols()
        val height = input.rows()

        // Convert BGR Mat to RGBA8 byte array
        val rgbaMat = Mat()
        Imgproc.cvtColor(input, rgbaMat, Imgproc.COLOR_BGR2RGBA)
        val rgbaData = ByteArray(width * height * 4)
        rgbaMat.get(0, 0, rgbaData)
        rgbaMat.release()

        // Upload LUTs if needed
        if (filter.lutPath1 != null) {
            val lut1 = lutProcessor.loadLutForGpu(filter.lutPath1)
            if (lut1 != null) vulkanGpuEngine.uploadLut(lut1, 0)
        }
        if (filter.lutPath2 != null) {
            val lut2 = lutProcessor.loadLutForGpu(filter.lutPath2)
            if (lut2 != null) vulkanGpuEngine.uploadLut(lut2, 1)
        }

        // Build params array
        val params = buildGpuParams(filter)
        val resultData = vulkanGpuEngine.processImage(rgbaData, width, height, params) ?: return null

        // Convert RGBA8 result back to BGR Mat
        val resultRgbaMat = Mat(height, width, CvType.CV_8UC4)
        resultRgbaMat.put(0, 0, resultData)
        val output = Mat()
        Imgproc.cvtColor(resultRgbaMat, output, Imgproc.COLOR_RGBA2BGR)
        resultRgbaMat.release()
        return output
    }

    private fun buildGpuParams(filter: FilterConfig): FloatArray {
        // FilterParams layout: 28 floats total (24 from here + width/height/padding from JNI)
        val params = FloatArray(24)
        var idx = 0
        params[idx++] = if (lut1Uploaded) filter.lutConcentration1 else 0f  // lutStrength (0 if no LUT)
        params[idx++] = if (lut2Uploaded) filter.lutConcentration2 else 0f  // lut2Strength (0 if no LUT)
        params[idx++] = 33f                                // lutSize (default 33)
        params[idx++] = 33f                                // lut2Size (default 33)
        params[idx++] = filter.exposure
        params[idx++] = filter.contrast
        params[idx++] = filter.highlights
        params[idx++] = filter.shadows
        params[idx++] = filter.saturation
        params[idx++] = filter.colorTemperature
        params[idx++] = filter.tint
        params[idx++] = filter.vignetteStrength
        // Grain: use highlight density as overall grain strength
        params[idx++] = filter.grainConfig.highlightDensity  // grainStrength
        params[idx++] = System.currentTimeMillis().toFloat() // grainSeed
        params[idx++] = 0.33f                                // shadowThreshold
        params[idx++] = 0.66f                                // highlightThreshold
        params[idx++] = filter.grainConfig.shadowDensity     // shadowGrainRatio
        params[idx++] = filter.grainConfig.midtoneDensity    // midtoneGrainRatio
        params[idx++] = filter.grainConfig.highlightDensity  // highlightGrainRatio
        params[idx++] = filter.grainConfig.shadowSize        // shadowSizeRatio
        params[idx++] = filter.grainConfig.highlightSize     // highlightSizeRatio
        params[idx++] = if (lut1Uploaded) 1f else 0f  // enableLut
        params[idx++] = if (lut2Uploaded) 1f else 0f  // enableLut2
        params[idx++] = if (filter.grainConfig.highlightDensity > 0f ||
            filter.grainConfig.midtoneDensity > 0f ||
            filter.grainConfig.shadowDensity > 0f) 1f else 0f    // enableGrain
        return params
    }

    private fun processImageCpu(input: Mat, filter: FilterConfig): Mat {
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
