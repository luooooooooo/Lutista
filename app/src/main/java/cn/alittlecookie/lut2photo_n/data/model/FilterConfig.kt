package cn.alittlecookie.lut2photo_n.data.model

data class FilterConfig(
    val id: Long = 0,
    val name: String = "",
    // LUT 配置
    val lutPath1: String? = null,
    val lutConcentration1: Float = 1.0f,
    val lutPath2: String? = null,
    val lutConcentration2: Float = 1.0f,
    val lutPrecision: Int = 33,
    // 亮度配置
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    // 颜色配置
    val saturation: Float = 0f,
    val colorTemperature: Float = 0f,
    val tint: Float = 0f,
    // 曲线配置
    val curveConfig: CurveConfig = CurveConfig(),
    // LCH 配置
    val lchConfig: LchConfig = LchConfig(),
    // 暗角配置
    val vignetteStrength: Float = 0f,
    // 颗粒配置
    val grainConfig: GrainConfig = GrainConfig(),
    // 预计算缓存
    val unifiedLutPath: String? = null,
    val unifiedLutHash: String? = null
)

data class CurveConfig(
    val whiteChannel: List<FloatPoint> = emptyList(),
    val redChannel: List<FloatPoint> = emptyList(),
    val greenChannel: List<FloatPoint> = emptyList(),
    val blueChannel: List<FloatPoint> = emptyList()
)

data class FloatPoint(
    val x: Float,
    val y: Float
)

data class LchConfig(
    val red: LchAdjustment = LchAdjustment(),
    val orange: LchAdjustment = LchAdjustment(),
    val yellow: LchAdjustment = LchAdjustment(),
    val green: LchAdjustment = LchAdjustment(),
    val cyan: LchAdjustment = LchAdjustment(),
    val blue: LchAdjustment = LchAdjustment(),
    val purple: LchAdjustment = LchAdjustment(),
    val skin: LchAdjustment = LchAdjustment()
)

data class LchAdjustment(
    val lightness: Float = 0f,
    val chroma: Float = 0f,
    val hue: Float = 0f
)

data class GrainConfig(
    val highlightSize: Float = 0f,
    val highlightDensity: Float = 0f,
    val midtoneSize: Float = 0f,
    val midtoneDensity: Float = 0f,
    val shadowSize: Float = 0f,
    val shadowDensity: Float = 0f
)
