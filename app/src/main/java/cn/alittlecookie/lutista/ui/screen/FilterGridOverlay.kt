package cn.alittlecookie.lutista.ui.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

/** 整页平铺的三档密度 */
enum class FilterGridDensity(
    val key: String,
    val label: String,
    val columns: Int,
    val rows: Int,
    /** 图片区宽高比（宽 / 高） */
    val imageAspect: Float,
    val gap: Dp,
    val corner: Dp,
    val labelSize: TextUnit,
    val labelHeight: Dp
) {
    LARGE("large", "大", 2, 4, 4f / 3f, 10.dp, 14.dp, 12.sp, 20.dp),
    MEDIUM("medium", "中", 3, 4, 4f / 5f, 8.dp, 12.dp, 11.sp, 18.dp),
    SMALL("small", "小", 4, 6, 1f, 6.dp, 10.dp, 10.sp, 16.dp);

    val perPage: Int get() = columns * rows

    companion object {
        fun fromKey(key: String?): FilterGridDensity =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

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
    onVisibleIdsChanged: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val perPage = density.perPage
    val pageCount = maxOf(1, ceil(items.size.toDouble() / perPage).toInt())
    val initialPage = remember {
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
    LaunchedEffect(pagerState.currentPage, density) {
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
            onDensityChange = onDensityChange,
            onDismiss = onDismiss
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val start = page * perPage
            val slice = (start until minOf(start + perPage, items.size)).map { items[it] }
            FilterGridPage(
                items = slice,
                density = density,
                selectedId = selectedId,
                thumbnails = thumbnails,
                loadingIds = loadingIds,
                original = original,
                onSelect = onSelect
            )
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
                text = "共 $count 个 · ${density.columns}×${density.rows}",
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
    items: List<FilterGridItem>,
    density: FilterGridDensity,
    selectedId: Long,
    thumbnails: Map<Long, Bitmap>,
    loadingIds: Map<Long, Boolean>,
    original: Bitmap?,
    onSelect: (Long) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val rows = density.rows
        val columns = density.columns
        val gap = density.gap
        val labelHeight = density.labelHeight

        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        val rowHeight = (maxHeight - gap * (rows - 1)) / rows
        val imageHeight = minOf(cellWidth / density.imageAspect, rowHeight - labelHeight)

        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            repeat(rows) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(columns) { columnIndex ->
                        val item = items.getOrNull(rowIndex * columns + columnIndex)
                        if (item == null) {
                            Spacer(Modifier.width(cellWidth))
                        } else {
                            FilterGridCell(
                                item = item,
                                width = cellWidth,
                                imageHeight = imageHeight,
                                density = density,
                                isSelected = item.id == selectedId,
                                bitmap = if (item.id == 0L) original else thumbnails[item.id],
                                isLoading = loadingIds[item.id] == true,
                                onClick = { onSelect(item.id) }
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
    density: FilterGridDensity,
    isSelected: Boolean,
    bitmap: Bitmap?,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
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
                    contentScale = ContentScale.Crop,
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
    }
}
