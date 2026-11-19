package cn.alittlecookie.lutista.ui.preview

import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.data.model.FilterConfig

/**
 * 预览/缩略图共用的一套 FilterConfig 映射。
 *
 * 只取渲染预览真正会用到的基础字段，与 PhotoProcessScreen 里原有的大图预览保持完全一致，
 * 避免"底部缩略图"和"主预览"出现口径不同的颜色。
 */
fun FilterEntity.toPreviewFilterConfig(): FilterConfig = FilterConfig(
    id = id,
    name = name,
    lutPath1 = lutPath1,
    lutConcentration1 = lutConcentration1,
    lutPath2 = lutPath2,
    lutConcentration2 = lutConcentration2,
    lutPrecision = lutPrecision,
    exposure = exposure,
    contrast = contrast,
    highlights = highlights,
    shadows = shadows,
    saturation = saturation,
    colorTemperature = colorTemperature,
    tint = tint,
    vignetteStrength = vignetteStrength
)
