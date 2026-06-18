package cn.alittlecookie.lutista.gpu

import android.content.Context
import cn.alittlecookie.lutista.data.model.LutData
import cn.alittlecookie.lutista.manager.LogManager

class VulkanGpuEngine(private val logger: LogManager) {
    companion object {
        private const val TAG = "VulkanGpuEngine"
        private val SHADER_NAMES = listOf(
            "unified.comp.spv"
        )
    }

    private var nativeHandle: Long = 0L

    /** true after init() completes (success or failure) */
    @Volatile
    var initialized = false
        private set

    val isAvailable: Boolean
        get() = initialized && nativeHandle != 0L && nativeIsAvailable(nativeHandle)

    fun init(context: Context) {
        try {
            val shaderDataMap = loadShaderData(context)
            if (shaderDataMap.isEmpty()) {
                logger.w(TAG, "No shader data loaded, Vulkan disabled")
                return
            }
            nativeHandle = nativeInit(shaderDataMap)
            initialized = true
            if (nativeHandle != 0L) {
                logger.i(TAG, "Vulkan GPU engine initialized successfully")
            } else {
                logger.w(TAG, "Vulkan GPU engine init returned null handle")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to initialize Vulkan GPU engine", e)
        }
    }

    private fun loadShaderData(context: Context): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        try {
            for (name in SHADER_NAMES) {
                val data = context.assets.open("shaders/$name").use { it.readBytes() }
                result[name] = data
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load shader data from assets", e)
            return emptyMap()
        }
        return result
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun uploadLut(lutData: LutData, slot: Int = 0) {
        if (!isAvailable) return
        try {
            nativeUploadLut(nativeHandle, lutData.data, lutData.size, slot)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to upload LUT", e)
        }
    }

    fun uploadCurves(curveData: FloatArray) {
        if (!isAvailable || curveData.size != 1028) return
        try {
            nativeUploadCurves(nativeHandle, curveData)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to upload curves", e)
        }
    }

    /**
     * Process RGBA8 image data through the GPU pipeline.
     * Native engine has its own mutex for thread safety.
     */
    fun processImage(
        imageData: ByteArray,
        width: Int,
        height: Int,
        params: FloatArray
    ): ByteArray? = synchronized(this) {
        if (!isAvailable) return@synchronized null
        try {
            nativeProcessImage(nativeHandle, imageData, width, height, params)
        } catch (e: Exception) {
            logger.e(TAG, "GPU processing failed", e)
            null
        }
    }

    private external fun nativeInit(shaderData: Map<String, ByteArray>): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeIsAvailable(handle: Long): Boolean
    private external fun nativeUploadLut(handle: Long, lutData: FloatArray, lutSize: Int, lutSlot: Int)
    private external fun nativeUploadCurves(handle: Long, curveData: FloatArray)
    private external fun nativeProcessImage(
        handle: Long,
        imageData: ByteArray,
        width: Int,
        height: Int,
        params: FloatArray
    ): ByteArray?
}
