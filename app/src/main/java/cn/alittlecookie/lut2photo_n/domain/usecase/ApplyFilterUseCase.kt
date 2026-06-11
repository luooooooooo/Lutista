package cn.alittlecookie.lut2photo_n.domain.usecase

import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.manager.LogManager
import org.opencv.core.Mat

class ApplyFilterUseCase(
    private val logger: LogManager,
    private val engine: ImageProcessingEngine
) {
    companion object {
        private const val TAG = "ApplyFilterUseCase"
    }

    operator fun invoke(input: Mat, filter: FilterConfig): Mat {
        logger.d(TAG, "Applying filter: ${filter.name}")
        return engine.processImagePreview(input, filter)
    }
}
