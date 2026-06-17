package cn.alittlecookie.lutista.data.model

data class FilterConfig(
    val id: Long = 0,
    val name: String = "",
    // LUT 配置
    val lutPath1: String? = null,
    val lutConcentration1: Float = 1.0f,
    val lutPath2: String? = null,
    val lutConcentration2: Float = 1.0f,
    val lutGlobalStrength: Float = 1.0f,
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
    // 全局控制
    val globalStrength: Float = 0f,       // 0~1, 全局颗粒强度
    val grainSize: Float = 1f,            // 0.1~10.0, 基础颗粒大小
    // 影调阈值（0~255 亮度值）
    val shadowThreshold: Float = 85f,     // 阴影/中间调分界
    val highlightThreshold: Float = 170f, // 中间调/高光分界
    // 影调分区颗粒强度（以中间调为基准 1.0）
    val shadowGrainRatio: Float = 0.6f,
    val midtoneGrainRatio: Float = 1.0f,
    val highlightGrainRatio: Float = 0.5f,
    // 影调分区尺寸系数（以中间调为基准 1.0）
    val shadowSizeRatio: Float = 1.5f,
    val midtoneSizeRatio: Float = 1.0f,
    val highlightSizeRatio: Float = 0.6f,
    // 色彩通道差异（绿色通道固定 1.0）
    val redChannelRatio: Float = 0.9f,
    val blueChannelRatio: Float = 1.2f,
    val channelCorrelation: Float = 0.9f,  // 0.8~0.95
    val colorPreservation: Float = 0.95f   // 0.9~1.0
)
