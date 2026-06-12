package cn.alittlecookie.lut2photo_n.watermark.model

import android.graphics.Color

/**
 * 场景数据模型（不可变，支持撤销/重做快照）
 */
data class Scene(
    val baseImage: BaseImageElement,
    val elements: List<Element> = emptyList()
)

/**
 * 底图元素（固定最底层）
 */
data class BaseImageElement(
    val imageUri: String,
    val borderWidth: BorderWidth = BorderWidth.ZERO,
    val borderColor: Int = Color.WHITE
)

/**
 * 元素基类
 */
sealed class Element {
    abstract val id: String
    abstract val margin: Margin
    abstract val opacity: Float
    abstract val anchor: Anchor?
    abstract val offsetX: Float?
    abstract val offsetY: Float?
}

/**
 * 文字元素
 */
data class TextElement(
    override val id: String,
    override val margin: Margin = Margin.ZERO,
    override val opacity: Float = 1f,
    override val anchor: Anchor? = null,
    override val offsetX: Float? = null,
    override val offsetY: Float? = null,
    val content: String,
    val fontUri: String? = null,
    val fontWeight: FontWeight = FontWeight.NORMAL,
    val color: Int = Color.BLACK,
    val fontSize: Float,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val textAlign: TextAlign = TextAlign.LEFT
) : Element()

/**
 * 图片元素
 */
data class ImageElement(
    override val id: String,
    override val margin: Margin = Margin.ZERO,
    override val opacity: Float = 1f,
    override val anchor: Anchor? = null,
    override val offsetX: Float? = null,
    override val offsetY: Float? = null,
    val imageUri: String,
    val width: Float? = null,
    val height: Float? = null,
    val maintainAspectRatio: Boolean = true
) : Element()

/**
 * 容器元素
 */
data class ContainerElement(
    override val id: String,
    override val margin: Margin = Margin.ZERO,
    override val opacity: Float = 1f,
    override val anchor: Anchor? = null,
    override val offsetX: Float? = null,
    override val offsetY: Float? = null,
    val children: List<Element>,
    val mode: ContainerMode,
    val mainAxisAlign: MainAxisAlign = MainAxisAlign.START,
    val crossAxisAlign: CrossAxisAlign = CrossAxisAlign.START,
    val widthMode: SizeMode = SizeMode.WRAP_CONTENT,
    val width: Float? = null,
    val heightMode: SizeMode = SizeMode.WRAP_CONTENT,
    val height: Float? = null,
    val padding: Padding = Padding.ZERO
) : Element()

/**
 * 输出配置
 */
data class OutputConfig(
    val outputPath: String,
    val format: OutputFormat,
    val quality: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null
)
