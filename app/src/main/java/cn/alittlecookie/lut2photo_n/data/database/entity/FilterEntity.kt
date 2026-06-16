package cn.alittlecookie.lut2photo_n.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
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
    // 曲线配置 (JSON)
    val curveJson: String = "{}",
    // LCH 配置 (JSON)
    val lchJson: String = "{}",
    // 暗角配置
    val vignetteStrength: Float = 0f,
    // 颗粒配置 (JSON)
    val grainJson: String = "{}",
    // 预计算缓存
    val unifiedLutPath: String? = null,
    val unifiedLutHash: String? = null,
    val isDefault: Boolean = false,
    // 内置滤镜标识
    val isBuiltIn: Boolean = false,
    // 滤镜文件夹路径 (应用私有目录/filters/滤镜名称/)
    val filterDirPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
