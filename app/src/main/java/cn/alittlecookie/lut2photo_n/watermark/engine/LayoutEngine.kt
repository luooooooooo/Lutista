package cn.alittlecookie.lut2photo_n.watermark.engine

import cn.alittlecookie.lut2photo_n.watermark.model.Anchor
import cn.alittlecookie.lut2photo_n.watermark.model.Constraint
import cn.alittlecookie.lut2photo_n.watermark.model.ContainerElement
import cn.alittlecookie.lut2photo_n.watermark.model.ContainerMode
import cn.alittlecookie.lut2photo_n.watermark.model.CrossAxisAlign
import cn.alittlecookie.lut2photo_n.watermark.model.Element
import cn.alittlecookie.lut2photo_n.watermark.model.ElementBounds
import cn.alittlecookie.lut2photo_n.watermark.model.ElementLayoutInfo
import cn.alittlecookie.lut2photo_n.watermark.model.ImageElement
import cn.alittlecookie.lut2photo_n.watermark.model.LayoutResult
import cn.alittlecookie.lut2photo_n.watermark.model.MainAxisAlign
import cn.alittlecookie.lut2photo_n.watermark.model.Offset
import cn.alittlecookie.lut2photo_n.watermark.model.Scene
import cn.alittlecookie.lut2photo_n.watermark.model.Size
import cn.alittlecookie.lut2photo_n.watermark.model.SizeMode
import cn.alittlecookie.lut2photo_n.watermark.model.TextElement

/**
 * 布局引擎
 */
class LayoutEngine(private val converter: UnitConverter) {
    private val textMeasurer = TextMeasurer(converter)

    fun layout(scene: Scene): LayoutResult {
        val borderPx = scene.baseImage.borderWidth.let {
            with(converter) { it.toPx() }
        }

        val canvasW = converter.imageWidth + borderPx.left + borderPx.right
        val canvasH = converter.imageHeight + borderPx.top + borderPx.bottom
        val contentOrigin = Offset(borderPx.left, borderPx.top)
        val contentSize = Size(converter.imageWidth.toFloat(), converter.imageHeight.toFloat())

        val bounds = mutableMapOf<String, ElementBounds>()
        val constraint = Constraint(maxWidth = contentSize.width, maxHeight = contentSize.height)

        scene.elements.forEach { el ->
            layoutElement(el, contentOrigin, contentSize, constraint, bounds)
        }

        return LayoutResult(canvasW.toInt(), canvasH.toInt(), bounds)
    }

    private fun layoutElement(
        element: Element,
        parentOrigin: Offset,
        parentSize: Size,
        constraint: Constraint,
        bounds: MutableMap<String, ElementBounds>
    ): ElementLayoutInfo {
        val contentSize = measureElement(element, constraint)
        val marginPx = with(converter) { element.margin.toPx() }
        val layoutSize = Size(
            contentSize.width + marginPx.left + marginPx.right,
            contentSize.height + marginPx.top + marginPx.bottom
        )

        val position = calculatePosition(element, parentOrigin, parentSize, contentSize)

        bounds[element.id] = ElementBounds(
            x = position.x,
            y = position.y,
            width = contentSize.width,
            height = contentSize.height,
            marginLeft = marginPx.left,
            marginTop = marginPx.top,
            marginRight = marginPx.right,
            marginBottom = marginPx.bottom
        )

        if (element is ContainerElement) {
            layoutContainerChildren(element, position, contentSize, bounds)
        }

        return ElementLayoutInfo(contentSize, layoutSize)
    }

    private fun measureElement(element: Element, constraint: Constraint): Size {
        return when (element) {
            is TextElement -> {
                if (constraint.hasFixedWidth) {
                    textMeasurer.measureWithConstraint(element, constraint.maxWidth)
                } else {
                    textMeasurer.measure(element)
                }
            }
            is ImageElement -> measureImage(element, constraint)
            is ContainerElement -> measureContainer(element, constraint, mutableMapOf())
        }
    }

    private fun measureImage(element: ImageElement, constraint: Constraint): Size {
        val w = element.width?.let { converter.gmToPx(it) } ?: constraint.maxWidth
        val h = element.height?.let { converter.gmToPx(it) } ?: constraint.maxHeight
        return Size(
            w.coerceIn(constraint.minWidth, constraint.maxWidth),
            h.coerceIn(constraint.minHeight, constraint.maxHeight)
        )
    }

    private fun measureContainer(
        container: ContainerElement,
        constraint: Constraint,
        bounds: MutableMap<String, ElementBounds>
    ): Size {
        val paddingPx = with(converter) { container.padding.toPx() }
        val innerW = when (container.widthMode) {
            SizeMode.FIXED -> converter.gmToPx(container.width!!)
            SizeMode.WRAP_CONTENT -> constraint.maxWidth - paddingPx.left - paddingPx.right
        }
        val innerH = when (container.heightMode) {
            SizeMode.FIXED -> converter.gmToPx(container.height!!)
            SizeMode.WRAP_CONTENT -> constraint.maxHeight - paddingPx.top - paddingPx.bottom
        }
        val innerSize = Size(innerW, innerH)

        val childConstraint = Constraint(maxWidth = innerW, maxHeight = innerH)

        val childrenInfo = container.children.map { child ->
            val childInfo = when (child) {
                is TextElement -> {
                    val cs = if (container.widthMode == SizeMode.FIXED ||
                        container.crossAxisAlign == CrossAxisAlign.STRETCH
                    ) {
                        textMeasurer.measureWithConstraint(child, innerW)
                    } else {
                        textMeasurer.measure(child)
                    }
                    val mPx = with(converter) { child.margin.toPx() }
                    ElementLayoutInfo(cs, Size(cs.width + mPx.left + mPx.right, cs.height + mPx.top + mPx.bottom))
                }
                is ImageElement -> {
                    val cs = measureImage(child, childConstraint)
                    val mPx = with(converter) { child.margin.toPx() }
                    ElementLayoutInfo(cs, Size(cs.width + mPx.left + mPx.right, cs.height + mPx.top + mPx.bottom))
                }
                is ContainerElement -> {
                    val cs = measureContainer(child, childConstraint, bounds)
                    val mPx = with(converter) { child.margin.toPx() }
                    ElementLayoutInfo(cs, Size(cs.width + mPx.left + mPx.right, cs.height + mPx.top + mPx.bottom))
                }
            }
            childInfo
        }

        return when (container.mode) {
            ContainerMode.LINEAR_H -> {
                val totalW = childrenInfo.sumOf { it.layoutSize.width.toDouble() }.toFloat()
                val maxH = childrenInfo.maxOfOrNull { it.layoutSize.height } ?: 0f
                val w = if (container.widthMode == SizeMode.FIXED) innerW else totalW
                val h = if (container.heightMode == SizeMode.FIXED) innerH else maxH
                Size(w + paddingPx.left + paddingPx.right, h + paddingPx.top + paddingPx.bottom)
            }
            ContainerMode.LINEAR_V -> {
                val maxW = childrenInfo.maxOfOrNull { it.layoutSize.width } ?: 0f
                val totalH = childrenInfo.sumOf { it.layoutSize.height.toDouble() }.toFloat()
                val w = if (container.widthMode == SizeMode.FIXED) innerW else maxW
                val h = if (container.heightMode == SizeMode.FIXED) innerH else totalH
                Size(w + paddingPx.left + paddingPx.right, h + paddingPx.top + paddingPx.bottom)
            }
            ContainerMode.FREE -> {
                val maxW = childrenInfo.maxOfOrNull { it.layoutSize.width } ?: 0f
                val maxH = childrenInfo.maxOfOrNull { it.layoutSize.height } ?: 0f
                val w = if (container.widthMode == SizeMode.FIXED) innerW else maxW
                val h = if (container.heightMode == SizeMode.FIXED) innerH else maxH
                Size(w + paddingPx.left + paddingPx.right, h + paddingPx.top + paddingPx.bottom)
            }
        }
    }

    private fun layoutContainerChildren(
        container: ContainerElement,
        containerOrigin: Offset,
        containerContentSize: Size,
        bounds: MutableMap<String, ElementBounds>
    ) {
        val paddingPx = with(converter) { container.padding.toPx() }
        val innerOrigin = Offset(
            containerOrigin.x + paddingPx.left,
            containerOrigin.y + paddingPx.top
        )
        val innerSize = Size(
            containerContentSize.width - paddingPx.left - paddingPx.right,
            containerContentSize.height - paddingPx.top - paddingPx.bottom
        )

        when (container.mode) {
            ContainerMode.LINEAR_H, ContainerMode.LINEAR_V ->
                layoutLinearChildren(container, innerOrigin, innerSize, bounds)
            ContainerMode.FREE ->
                layoutFreeChildren(container, innerOrigin, innerSize, bounds)
        }
    }

    private fun layoutLinearChildren(
        container: ContainerElement,
        innerOrigin: Offset,
        innerSize: Size,
        bounds: MutableMap<String, ElementBounds>
    ) {
        val isH = container.mode == ContainerMode.LINEAR_H

        val childInfos = container.children.map { child ->
            val childConstraint = buildChildConstraint(child, container, innerSize)
            val contentSize = measureElement(child, childConstraint)
            val marginPx = with(converter) { child.margin.toPx() }
            val layoutSize = Size(
                contentSize.width + marginPx.left + marginPx.right,
                contentSize.height + marginPx.top + marginPx.bottom
            )
            Triple(child, contentSize, layoutSize)
        }

        val totalMain = childInfos.sumOf {
            (if (isH) it.third.width else it.third.height).toDouble()
        }.toFloat()

        val mainAxisLen = if (isH) innerSize.width else innerSize.height
        val (startOffset, gap) = calculateMainAxisLayout(
            container.mainAxisAlign, mainAxisLen, totalMain, childInfos.size
        )

        var mainOffset = startOffset
        childInfos.forEach { (child, contentSize, layoutSize) ->
            val marginPx = with(converter) { child.margin.toPx() }

            val crossAxisLen = if (isH) innerSize.height else innerSize.width
            val crossAxisSize = if (isH) layoutSize.height else layoutSize.width
            val crossOffset = calculateCrossAxisOffset(
                container.crossAxisAlign, crossAxisLen, crossAxisSize
            )

            val stretchedSize = if (container.crossAxisAlign == CrossAxisAlign.STRETCH) {
                if (isH) Size(contentSize.width, innerSize.height)
                else Size(innerSize.width, contentSize.height)
            } else {
                contentSize
            }

            val childPos = if (isH) {
                Offset(
                    innerOrigin.x + mainOffset + marginPx.left,
                    innerOrigin.y + crossOffset
                )
            } else {
                Offset(
                    innerOrigin.x + crossOffset,
                    innerOrigin.y + mainOffset + marginPx.top
                )
            }

            bounds[child.id] = ElementBounds(
                x = childPos.x,
                y = childPos.y,
                width = stretchedSize.width,
                height = stretchedSize.height,
                marginLeft = marginPx.left,
                marginTop = marginPx.top,
                marginRight = marginPx.right,
                marginBottom = marginPx.bottom
            )

            if (child is ContainerElement) {
                layoutContainerChildren(child, childPos, stretchedSize, bounds)
            }

            mainOffset += if (isH) layoutSize.width else layoutSize.height
            mainOffset += gap
        }
    }

    private fun buildChildConstraint(
        child: Element,
        container: ContainerElement,
        innerSize: Size
    ): Constraint {
        val isH = container.mode == ContainerMode.LINEAR_H
        val stretchCross = container.crossAxisAlign == CrossAxisAlign.STRETCH

        return Constraint(
            maxWidth = when {
                container.widthMode == SizeMode.FIXED -> innerSize.width
                stretchCross && !isH -> innerSize.width
                else -> Float.MAX_VALUE
            },
            maxHeight = when {
                container.heightMode == SizeMode.FIXED -> innerSize.height
                stretchCross && isH -> innerSize.height
                else -> Float.MAX_VALUE
            }
        )
    }

    private fun calculateMainAxisLayout(
        align: MainAxisAlign,
        availableLen: Float,
        totalChildLen: Float,
        childCount: Int
    ): Pair<Float, Float> {
        return when (align) {
            MainAxisAlign.START -> Pair(0f, 0f)
            MainAxisAlign.CENTER -> Pair((availableLen - totalChildLen) / 2, 0f)
            MainAxisAlign.END -> Pair(availableLen - totalChildLen, 0f)
            MainAxisAlign.SPACE_BETWEEN -> {
                val gap = if (childCount > 1) (availableLen - totalChildLen) / (childCount - 1) else 0f
                Pair(0f, gap)
            }
            MainAxisAlign.SPACE_EVENLY -> {
                val gap = if (childCount > 0) (availableLen - totalChildLen) / (childCount + 1) else 0f
                Pair(gap, gap)
            }
        }
    }

    private fun calculateCrossAxisOffset(
        align: CrossAxisAlign,
        availableLen: Float,
        childLen: Float
    ): Float {
        return when (align) {
            CrossAxisAlign.START -> 0f
            CrossAxisAlign.CENTER -> (availableLen - childLen) / 2
            CrossAxisAlign.END -> availableLen - childLen
            CrossAxisAlign.STRETCH -> 0f
        }
    }

    private fun layoutFreeChildren(
        container: ContainerElement,
        innerOrigin: Offset,
        innerSize: Size,
        bounds: MutableMap<String, ElementBounds>
    ) {
        container.children.forEach { child ->
            val constraint = Constraint(maxWidth = innerSize.width, maxHeight = innerSize.height)
            layoutElement(child, innerOrigin, innerSize, constraint, bounds)
        }
    }

    private fun calculatePosition(
        element: Element,
        parentOrigin: Offset,
        parentSize: Size,
        contentSize: Size
    ): Offset {
        val anchor = element.anchor ?: Anchor.TL
        val offsetXPx = converter.gmToPx(element.offsetX ?: 0f)
        val offsetYPx = converter.gmToPx(element.offsetY ?: 0f)

        val x = when (anchor) {
            Anchor.TL, Anchor.ML, Anchor.BL -> parentOrigin.x + offsetXPx
            Anchor.TC, Anchor.MC, Anchor.BC -> parentOrigin.x + (parentSize.width - contentSize.width) / 2 + offsetXPx
            Anchor.TR, Anchor.MR, Anchor.BR -> parentOrigin.x + parentSize.width - contentSize.width + offsetXPx
        }

        val y = when (anchor) {
            Anchor.TL, Anchor.TC, Anchor.TR -> parentOrigin.y + offsetYPx
            Anchor.ML, Anchor.MC, Anchor.MR -> parentOrigin.y + (parentSize.height - contentSize.height) / 2 + offsetYPx
            Anchor.BL, Anchor.BC, Anchor.BR -> parentOrigin.y + parentSize.height - contentSize.height + offsetYPx
        }

        return Offset(x, y)
    }

    fun clearCache() {
        textMeasurer.clearCache()
    }
}
