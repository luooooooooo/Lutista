package cn.alittlecookie.lutista.ui.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 整页平铺的三档密度。
 *
 * 参照 Google 官方 Compose 的做法：
 * - `GridCells.Adaptive(minSize)`：列数由可用宽度算出，而不是写死；
 * - `Modifier.aspectRatio` + `ContentScale.Fit`：格子高度由照片真实宽高比算出，永不裁切。
 * 密度只描述"目标格宽"和"最多几行"，实际列数、行数、每页个数都在运行时按屏幕与图片比例算出，
 * 所以竖图、横图、方图、全景图都走同一套逻辑，换图不需要改代码。
 */
enum class FilterGridDensity(
    val key: String,
    val label: String,
    /** 最小列数（窄屏也至少这么多列） */
    val columns: Int,
    /** 最多行数上限（行数实际由可用高度 ÷ 格子高度算出，这里只做上限） */
    val maxRows: Int,
    /** 目标格宽：按它算列数，手机上正好是 2/3/4 列，横屏或平板会自动增加列数 */
    val minCellWidth: Dp,
    val gap: Dp,
    val corner: Dp,
    val labelSize: TextUnit,
    val labelHeight: Dp
) {
    LARGE("large", "大", 2, 6, 190.dp, 10.dp, 14.dp, 12.sp, 20.dp),
    MEDIUM("medium", "中", 3, 8, 126.dp, 8.dp, 12.dp, 11.sp, 18.dp),
    SMALL("small", "小", 4, 10, 92.dp, 8.dp, 10.dp, 10.sp, 16.dp);

    /**
     * 按真实可用空间 + 当前照片的真实宽高比，算出这一档的实际行列与格子尺寸。
     *
     * 1. 列数 = max(最小列数, 按目标格宽能放下的列数)；
     * 2. 格子宽 = 列宽，格子高 = 宽 ÷ 照片比例（等价于 `Modifier.aspectRatio`）；
     * 3. 行数 = 可用高度能放下的行数（四舍五入，受 [maxRows] 限制）；
     * 4. 行高不够时整格等比缩小，所以永远是"完整图片 + 不裁切"。
     */
    fun metrics(
        availWidth: Dp,
        availHeight: Dp,
        photoAspect: Float,
        itemCount: Int = 0
    ): FilterGridMetrics {
        val aspect = if (photoAspect.isFinite() && photoAspect > 0f) photoAspect else 1f
        val columnsFit = floor(((availWidth + gap) / (minCellWidth + gap)).toDouble()).toInt()
        val actualColumns = maxOf(columns, columnsFit)
        val cellWidth = ((availWidth - gap * (actualColumns - 1)) / actualColumns).coerceAtLeast(1.dp)
        // 按列宽铺满时的自然高度，行数由"这个高度能在可用高度里放几个"决定
        val naturalHeight = cellWidth / aspect
        val slotHeight = naturalHeight + labelHeight + gap
        val rowsFit = kotlin.math.round(((availHeight + gap) / slotHeight).toDouble()).toInt()
        // 行数取"能放下的行数"与"装完当前列表需要的行数"的较小值，
        // 否则列表很短时会算出一堆空行，整块被压到屏幕中间、上下大片留白
        val rowsNeeded = if (itemCount > 0) {
            ceil(itemCount.toDouble() / actualColumns).toInt()
        } else {
            rowsFit
        }
        val actualRows = minOf(rowsFit, rowsNeeded).coerceIn(1, maxRows)
        val rowHeight = ((availHeight - gap * (actualRows - 1)) / actualRows).coerceAtLeast(1.dp)
        val maxImageHeight = (rowHeight - labelHeight).coerceAtLeast(24.dp)
        val imageHeight = minOf(naturalHeight, maxImageHeight)
        val imageWidth = (imageHeight * aspect).coerceAtMost(cellWidth)
        return FilterGridMetrics(
            columns = actualColumns,
            rows = actualRows,
            perPage = actualColumns * actualRows,
            cellWidth = cellWidth,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            gap = gap,
            labelHeight = labelHeight
        )
    }

    companion object {
        fun fromKey(key: String?): FilterGridDensity =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** 一屏网格的实测几何：列/行、每页个数、格子与图片尺寸 */
data class FilterGridMetrics(
    val columns: Int,
    val rows: Int,
    val perPage: Int,
    val cellWidth: Dp,
    val imageWidth: Dp,
    val imageHeight: Dp,
    val gap: Dp,
    val labelHeight: Dp
)

data class FilterGridItem(
    val id: Long,
    val name: String,
    val isGlobalDefault: Boolean = false
)

/**
 * 分页器在首次组合时就把 pageCount 的取值方式固定下来了，
 * 这里通过 State 间接读取，保证切换密度、滤镜列表加载完成后页数能立即跟上，而不是停在首次组合时的旧值。
 */
@Composable
private fun rememberPagerStateBox(
    initialPage: Int,
    itemsCount: Int,
    perPage: Int
): PagerState {
    val countState = remember { mutableStateOf(itemsCount) }
    val perPageState = remember { mutableStateOf(perPage) }
    LaunchedEffect(itemsCount) { countState.value = itemsCount }
    LaunchedEffect(perPage) { perPageState.value = perPage }
    return rememberPagerState(initialPage = initialPage, pageCount = {
        maxOf(1, ceil(countState.value.toDouble() / perPageState.value).toInt())
    })
}

/**
 * 整页平铺滤镜选择器。
 *
 * 一屏就是一页：2×4 / 3×4 / 4×6 三档，左右滑动翻页，点击即选中并关闭。
 * 缩略图全部来自 [FilterThumbnailStore]，与大图预览共用同一份缓存，切换密度不重渲。
 */
@Composable
fun FilterGridOverlay(
    items: List<FilterGridItem>,
    selectedId: Long,
    density: FilterGridDensity,
    onDensityChange: (FilterGridDensity) -> Unit,
    thumbnails: Map<Long, Bitmap>,
    loadingIds: Map<Long, Boolean>,
    original: Bitmap?,
    onSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit = onSelect,
    onVisibleIdsChanged: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val photoAspect = remember(original) {
        original?.let { it.width.toFloat() / it.height.toFloat() }?.takeIf { it > 0f } ?: 1f
    }
    // 真实几何要等量到可用空间才能算出来（依赖屏幕尺寸 + 照片比例），先按密度的默认值占位
    var metrics by remember(density) { mutableStateOf<FilterGridMetrics?>(null) }
    val perPage = metrics?.perPage ?: (density.columns * 2)
    val pageCount = maxOf(1, ceil(items.size.toDouble() / perPage).toInt())
    val initialPage = remember(density, perPage) {
        val index = items.indexOfFirst { it.id == selectedId }
        (if (index >= 0) index else 0) / perPage
    }
    val pagerState = rememberPagerStateBox(initialPage = initialPage, itemsCount = items.size, perPage = perPage)

    // 切密度后回到当前选中项所在的那一页
    LaunchedEffect(density, pageCount) {
        val index = items.indexOfFirst { it.id == selectedId }
        val target = (if (index >= 0) index else 0) / perPage
        pagerState.scrollToPage(target.coerceIn(0, pageCount - 1))
    }

    // 当前页可见项优先渲染
    LaunchedEffect(pagerState.currentPage, perPage) {
        val start = pagerState.currentPage * perPage
        onVisibleIdsChanged(items.drop(start).take(perPage).map { it.id }.filter { it != 0L })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        FilterGridTopBar(
            count = items.size,
            density = density,
            columns = metrics?.columns ?: density.columns,
            rows = metrics?.rows ?: density.maxRows,
            onDensityChange = onDensityChange,
            onDismiss = onDismiss
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val current = density.metrics(maxWidth, maxHeight, photoAspect, items.size)
            LaunchedEffect(current) { if (metrics != current) metrics = current }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val start = page * current.perPage
                val slice = (start until minOf(start + current.perPage, items.size)).map { items[it] }
                FilterGridPage(
                    metrics = current,
                    items = slice,
                    density = density,
                    selectedId = selectedId,
                    thumbnails = thumbnails,
                    loadingIds = loadingIds,
                    original = original,
                    onSelect = onSelect,
                    onLongSelect = onLongSelect
                )
            }
        }

        FilterGridBottomBar(
            currentPage = pagerState.currentPage,
            pageCount = pageCount,
            perPage = perPage
        )
    }
}

@Composable
private fun FilterGridTopBar(
    count: Int,
    density: FilterGridDensity,
    columns: Int,
    rows: Int,
    onDensityChange: (FilterGridDensity) -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(imageVector = MiuixIcons.Back, contentDescription = "关闭")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "全部滤镜",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "共 $count 个 · ${columns}×${rows}",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterGridDensity.entries.forEach { option ->
                DensityPill(
                    label = option.label,
                    active = option == density,
                    onClick = { onDensityChange(option) }
                )
            }
        }
    }
}

@Composable
private fun DensityPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Color.White else MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FilterGridPage(
    metrics: FilterGridMetrics,
    items: List<FilterGridItem>,
    density: FilterGridDensity,
    selectedId: Long,
    thumbnails: Map<Long, Bitmap>,
    loadingIds: Map<Long, Boolean>,
    original: Bitmap?,
    onSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit
) {
    val rows = metrics.rows
    val columns = metrics.columns
    val gap = metrics.gap
    val imageWidth = metrics.imageWidth
    val imageHeight = metrics.imageHeight

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            repeat(rows) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(columns) { columnIndex ->
                        val item = items.getOrNull(rowIndex * columns + columnIndex)
                        if (item == null) {
                            // 空格子也占位，保证每行宽度一致、列与列对得齐
                            Spacer(Modifier.width(imageWidth))
                        } else {
                            FilterGridCell(
                                item = item,
                                width = imageWidth,
                                imageHeight = imageHeight,
                                labelWidth = imageWidth,
                                density = density,
                                isSelected = item.id == selectedId,
                                bitmap = if (item.id == 0L) original else thumbnails[item.id],
                                isLoading = loadingIds[item.id] == true,
                                onClick = { onSelect(item.id) },
                                onLongClick = { onLongSelect(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGridCell(
    item: FilterGridItem,
    width: Dp,
    imageHeight: Dp,
    labelWidth: Dp,
    density: FilterGridDensity,
    isSelected: Boolean,
    bitmap: Bitmap?,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(labelWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(imageHeight)
                .clip(RoundedCornerShape(density.corner))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            2.dp,
                            MiuixTheme.colorScheme.primary,
                            RoundedCornerShape(density.corner)
                        )
                    } else Modifier
                )
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = item.name.firstOrNull()?.toString() ?: "?",
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            if (isLoading && bitmap == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(14.dp)
                )
            }

            if (item.isGlobalDefault) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(text = "默认", fontSize = 9.sp, color = Color.White)
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        if (density != FilterGridDensity.SMALL || isSelected) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.name,
                fontSize = density.labelSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(labelWidth),
                color = if (isSelected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun FilterGridBottomBar(currentPage: Int, pageCount: Int, perPage: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentPage) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentPage) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${currentPage + 1} / $pageCount · 每页 $perPage 个",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "轻点套用 · 长按套用并精调",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
        )
    }
}
