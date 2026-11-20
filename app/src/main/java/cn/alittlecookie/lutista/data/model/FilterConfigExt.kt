package cn.alittlecookie.lutista.data.model

import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.util.FilterConfigJson

/**
 * 配方（数据库里的 FilterEntity）→ 渲染用的 FilterConfig。
 *
 * 曲线 / HSL(LCH) / 颗粒 在数据库里各自存成 JSON，这里统一解出来，
 * 保证"单图处理"和"配方室预览"用的是同一份完整参数，不会丢参数。
 *
 * 一次解析三段 JSON，而缩略图/主预览会成批反复要同一批滤镜的配置
 * （换照片 21 次、拖滑杆每次重组都会取），所以按 (id, updatedAt, json 指纹) 记忆化；
 * 配方室保存会刷新 updatedAt，缓存自然失效。
 */
private data class ConfigMemoKey(val id: Long, val updatedAt: Long, val jsonHash: Int)

private const val CONFIG_MEMO_MAX = 96

private val configMemo = object : LinkedHashMap<ConfigMemoKey, FilterConfig>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ConfigMemoKey, FilterConfig>) =
        size > CONFIG_MEMO_MAX
}

fun FilterEntity.toFilterConfig(): FilterConfig {
    val key = ConfigMemoKey(
        id = id,
        updatedAt = updatedAt,
        jsonHash = (curveJson.hashCode() * 31 + lchJson.hashCode()) * 31 + grainJson.hashCode()
    )
    synchronized(configMemo) { configMemo[key] }?.let { return it }
    val config = FilterConfig(
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
    synchronized(configMemo) { configMemo[key] = config }
    return config
}
