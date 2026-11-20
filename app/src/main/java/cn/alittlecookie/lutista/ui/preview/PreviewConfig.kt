package cn.alittlecookie.lutista.ui.preview

import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.data.model.FilterConfig
import cn.alittlecookie.lutista.data.model.toFilterConfig

/**
 * 预览/缩略图共用的一套 FilterConfig 映射。
 *
 * 这里必须和主预览用同一套完整参数（全局 LUT 浓度、曲线、LCH、颗粒、统一 LUT 等），
 * 否则在配方室里调了这些参数保存后，主预览变了、底部缩略图却还是老样子，
 * 看起来就像"缩略图没有刷新"。
 */
fun FilterEntity.toPreviewFilterConfig(): FilterConfig = toFilterConfig()
