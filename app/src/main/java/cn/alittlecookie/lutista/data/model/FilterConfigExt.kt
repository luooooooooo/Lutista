package cn.alittlecookie.lutista.data.model

import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.util.FilterConfigJson

/**
 * 配方（数据库里的 FilterEntity）→ 渲染用的 FilterConfig。
 *
 * 曲线 / HSL(LCH) / 颗粒 在数据库里各自存成 JSON，这里统一解出来，
 * 保证"单图处理"和"配方室预览"用的是同一份完整参数，不会丢参数。
 */
fun FilterEntity.toFilterConfig(): FilterConfig = FilterConfig(
    id = id,
    name = name,
    // LUT：双 LUT 叠加 + 两条浓度 + 全局强度
    lutPath1 = lutPath1,
    lutConcentration1 = lutConcentration1,
    lutPath2 = lutPath2,
    lutConcentration2 = lutConcentration2,
    lutGlobalStrength = lutGlobalStrength,
    lutPrecision = lutPrecision,
    // 亮度
    exposure = exposure,
    contrast = contrast,
    highlights = highlights,
    shadows = shadows,
    // 颜色
    saturation = saturation,
    colorTemperature = colorTemperature,
    tint = tint,
    // 曲线 / HSL / 暗角 / 颗粒
    curveConfig = FilterConfigJson.jsonToCurveConfig(curveJson),
    lchConfig = FilterConfigJson.jsonToLchConfig(lchJson),
    vignetteStrength = vignetteStrength,
    grainConfig = FilterConfigJson.jsonToGrainConfig(grainJson),
    unifiedLutPath = unifiedLutPath,
    unifiedLutHash = unifiedLutHash
)
