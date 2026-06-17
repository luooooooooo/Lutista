package cn.alittlecookie.lutista.domain.usecase

import android.graphics.Bitmap
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.manager.LogManager

class ApplyFilterUseCase(
    private val logger: LogManager,
    private val engine: ImageProcessingEngine
) {
    companion object {
        private const val TAG = "ApplyFilterUseCase"
    }

    operator fun invoke(input: Bitmap, filter: FilterConfig): Bitmap {
        logger.d(TAG, "Applying filter: ${filter.name}")
        return engine.processImagePreview(input, filter)
    }
}
