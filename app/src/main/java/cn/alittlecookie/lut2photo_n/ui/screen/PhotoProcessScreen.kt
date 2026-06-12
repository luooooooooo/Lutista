package cn.alittlecookie.lut2photo_n.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.alittlecookie.lut2photo_n.data.model.FilterConfig
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.manager.SettingsManager
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

data class PhotoItem(
    val uri: String,
    val index: Int
)

data class PhotoEffectSettings(
    val filterId: Long = 0,
    val watermarkId: String = "none",
    val isCustomized: Boolean = false
)

@Composable
fun PhotoProcessScreen(
    photoUris: List<String>,
    onBackClick: () -> Unit,
    onProcessComplete: (ProcessResult) -> Unit
) {
    val context = LocalContext.current
    val filterRepository: FilterRepository = koinInject()
    val engine: ImageProcessingEngine = koinInject()
    val settingsManager: SettingsManager = koinInject()
    val logger: LogManager = koinInject()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()

    val photos = remember {
        photoUris.mapIndexed { index, uri -> PhotoItem(uri, index) }
    }

    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showProcessingDialog by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStatusText by remember { mutableStateOf("准备中...") }
    var isCancelled by remember { mutableStateOf(false) }
    var showResultScreen by remember { mutableStateOf(false) }
    var resultPhotoCount by remember { mutableIntStateOf(0) }

    var globalFilterId by remember { mutableStateOf(0L) }
    var globalWatermarkId by remember { mutableStateOf("none") }

    val perImageSettings = remember { mutableStateListOf<PhotoEffectSettings>() }
    repeat(photos.size) { index ->
        if (perImageSettings.size <= index) {
            perImageSettings.add(PhotoEffectSettings())
        }
    }

    fun getCurrentSettings(): PhotoEffectSettings {
        val settings = perImageSettings[selectedPhotoIndex]
        return if (settings.isCustomized) settings else PhotoEffectSettings(
            filterId = globalFilterId,
            watermarkId = globalWatermarkId
        )
    }

    fun updateCurrentSettings(filterId: Long? = null, watermarkId: String? = null) {
        val current = perImageSettings[selectedPhotoIndex]
        perImageSettings[selectedPhotoIndex] = current.copy(
            filterId = filterId ?: current.filterId,
            watermarkId = watermarkId ?: current.watermarkId,
            isCustomized = true
        )
    }

    val filtersFromDb by filterRepository.getAllFilters().collectAsState(initial = emptyList())

    val filterOptions = remember(filtersFromDb) {
        buildList {
            add(FilterOption(0, "默认", null))
            addAll(filtersFromDb.map { filter ->
                FilterOption(filter.id, filter.name, null)
            })
        }
    }

    val watermarkOptions = remember {
        listOf(
            WatermarkOption("none", "无水印", null),
            WatermarkOption("white", "白色边框", null),
            WatermarkOption("black", "黑色边框", null),
            WatermarkOption("custom", "自定义水印", null)
        )
    }

    val globalSettingsEntries = listOf(
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "当前选择滤镜",
                    summary = filterOptions.find { it.id == globalFilterId }?.name ?: "默认",
                    children = filterOptions.map { option ->
                        DropdownItem(
                            text = option.name,
                            selected = globalFilterId == option.id,
                            onClick = {
                                globalFilterId = option.id
                                for (i in perImageSettings.indices) {
                                    if (!perImageSettings[i].isCustomized) {
                                        perImageSettings[i] = PhotoEffectSettings(
                                            filterId = option.id,
                                            watermarkId = globalWatermarkId
                                        )
                                    }
                                }
                            }
                        )
                    }
                ),
                DropdownItem(
                    text = "默认边框水印",
                    summary = watermarkOptions.find { it.id == globalWatermarkId }?.name ?: "无水印",
                    children = watermarkOptions.map { option ->
                        DropdownItem(
                            text = option.name,
                            selected = globalWatermarkId == option.id,
                            onClick = {
                                globalWatermarkId = option.id
                                for (i in perImageSettings.indices) {
                                    if (!perImageSettings[i].isCustomized) {
                                        perImageSettings[i] = PhotoEffectSettings(
                                            filterId = globalFilterId,
                                            watermarkId = option.id
                                        )
                                    }
                                }
                            }
                        )
                    }
                )
            )
        )
    )

    fun buildFilterConfig(filterId: Long): FilterConfig? {
        if (filterId == 0L) return null
        val entity = filtersFromDb.find { it.id == filterId } ?: return null
        return FilterConfig(
            id = entity.id,
            name = entity.name,
            lutPath1 = entity.lutPath1,
            lutConcentration1 = entity.lutConcentration1,
            lutPath2 = entity.lutPath2,
            lutConcentration2 = entity.lutConcentration2,
            lutPrecision = entity.lutPrecision,
            exposure = entity.exposure,
            contrast = entity.contrast,
            highlights = entity.highlights,
            shadows = entity.shadows,
            saturation = entity.saturation,
            colorTemperature = entity.colorTemperature,
            tint = entity.tint,
            vignetteStrength = entity.vignetteStrength
        )
    }

    suspend fun copyUriToTempFile(context: Context, uri: String): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lut2Photo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
        return uri
    }

    fun startProcessing() {
        showProcessingDialog = true
        isCancelled = false
        processingProgress = 0f
        processingStatusText = "准备处理..."

        scope.launch {
            var processedCount = 0
            val totalPhotos = photos.size

            for ((photoIdx, photo) in photos.withIndex()) {
                if (isCancelled) break

                val settings = perImageSettings.getOrNull(photoIdx) ?: PhotoEffectSettings()
                val effectiveSettings = if (settings.isCustomized) settings else PhotoEffectSettings(
                    filterId = globalFilterId,
                    watermarkId = globalWatermarkId
                )

                try {
                    processingStatusText = "处理第 ${photoIdx + 1}/$totalPhotos 张..."
                    processingProgress = photoIdx.toFloat() / totalPhotos

                    val resultBitmap = withContext(Dispatchers.IO) {
                        ensureActive()
                        val tempFile = copyUriToTempFile(context, photo.uri)

                        try {
                            ensureActive()
                            val inputMat = Imgcodecs.imread(tempFile.absolutePath, Imgcodecs.IMREAD_COLOR)
                            tempFile.delete()

                            if (inputMat.empty()) {
                                logger.e("PhotoProcessScreen", "无法读取图片: ${photo.uri}")
                                return@withContext null
                            }

                            ensureActive()
                            val filterConfig = buildFilterConfig(effectiveSettings.filterId)
                            val outputMat = if (filterConfig != null) {
                                engine.processImagePreview(inputMat, filterConfig)
                            } else {
                                inputMat.clone()
                            }
                            inputMat.release()

                            ensureActive()
                            val rgbaMat = Mat()
                            Imgproc.cvtColor(outputMat, rgbaMat, Imgproc.COLOR_BGR2RGBA)
                            val resultBitmap = Bitmap.createBitmap(
                                rgbaMat.cols(),
                                rgbaMat.rows(),
                                Bitmap.Config.ARGB_8888
                            )
                            Utils.matToBitmap(rgbaMat, resultBitmap)
                            rgbaMat.release()
                            outputMat.release()

                            resultBitmap
                        } catch (e: Exception) {
                            tempFile.delete()
                            throw e
                        }
                    }

                    if (resultBitmap != null) {
                        ensureActive()
                        withContext(Dispatchers.IO) {
                            val displayName = "Lut2Photo_${System.currentTimeMillis()}_${photoIdx + 1}.jpg"
                            saveBitmapToGallery(context, resultBitmap, displayName)
                            resultBitmap.recycle()
                        }
                        processedCount++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.e("PhotoProcessScreen", "处理图片失败: ${photo.uri}", e)
                }
            }

            showProcessingDialog = false
            resultPhotoCount = processedCount
            showResultScreen = true
        }
    }

    if (showResultScreen) {
        ResultScreen(
            photoCount = resultPhotoCount,
            filterCount = perImageSettings.map { it.filterId }.distinct().size,
            watermarkCount = perImageSettings.map { it.watermarkId }.distinct().size,
            onContinueProcessing = {
                showResultScreen = false
                onBackClick()
            },
            onBackToHome = onBackClick
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "图片处理",
                largeTitle = "图片处理",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    Text(
                        text = "长按预览对比原图",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .background(
                                color = MiuixTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OverlayIconCascadingDropdownMenu(
                        entries = globalSettingsEntries
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "全局设置"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomActionBar(
                photoCount = photos.size,
                onStartProcess = { startProcessing() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PhotoThumbnailBar(
                photos = photos,
                selectedIndex = selectedPhotoIndex,
                perImageSettings = perImageSettings,
                onPhotoSelected = { selectedPhotoIndex = it }
            )

            MainPreviewArea(
                photoUri = photos[selectedPhotoIndex].uri,
                currentSettings = getCurrentSettings(),
                filterOptions = filterOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            val tabs = listOf("滤镜", "边框水印")
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (selectedTabIndex == 0) {
                EffectOptionsList(
                    options = filterOptions.map { EffectItem(it.id.toString(), it.name, it.thumbnail) },
                    selectedId = getCurrentSettings().filterId.toString(),
                    onOptionSelected = { updateCurrentSettings(filterId = it.toLongOrNull() ?: 0L) }
                )
            } else {
                EffectOptionsList(
                    options = watermarkOptions.map { EffectItem(it.id, it.name, it.thumbnail) },
                    selectedId = getCurrentSettings().watermarkId,
                    onOptionSelected = { updateCurrentSettings(watermarkId = it) }
                )
            }
        }
    }

    if (showProcessingDialog) {
        ProcessingDialog(
            progress = processingProgress,
            statusText = processingStatusText,
            onCancel = {
                isCancelled = true
                showProcessingDialog = false
            }
        )
    }
}

@Composable
private fun PhotoThumbnailBar(
    photos: List<PhotoItem>,
    selectedIndex: Int,
    perImageSettings: List<PhotoEffectSettings>,
    onPhotoSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(photos) { index, photo ->
            PhotoThumbnail(
                photo = photo,
                isSelected = index == selectedIndex,
                isCustomized = perImageSettings.getOrNull(index)?.isCustomized == true,
                onClick = { onPhotoSelected(index) }
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PhotoItem,
    isSelected: Boolean,
    isCustomized: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = "Photo ${photo.index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${photo.index + 1}",
                fontSize = 10.sp,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(16.dp)
                .background(
                    color = if (isCustomized) Color(0xFFFF9800) else Color(0xFF4CAF50),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCustomized) MiuixIcons.Create else MiuixIcons.Ok,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun MainPreviewArea(
    photoUri: String,
    currentSettings: PhotoEffectSettings,
    filterOptions: List<FilterOption>,
    modifier: Modifier = Modifier
) {
    var isLongPressed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(4f / 5f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isLongPressed = true
                        }
                    )
                }
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            val filterName = filterOptions.find { it.id == currentSettings.filterId }?.name ?: "默认"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = filterName,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(
                        color = if (currentSettings.isCustomized) {
                            Color(0xFFFF9800).copy(alpha = 0.8f)
                        } else {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (currentSettings.isCustomized) MiuixIcons.Create else MiuixIcons.Ok,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (currentSettings.isCustomized) "已单独设置效果" else "已应用全局效果",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            if (isLongPressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "原图",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectOptionsList(
    options: List<EffectItem>,
    selectedId: String,
    onOptionSelected: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(options) { _, item ->
            EffectOptionCard(
                item = item,
                isSelected = item.id == selectedId,
                onClick = { onOptionSelected(item.id) }
            )
        }
    }
}

@Composable
private fun EffectOptionCard(
    item: EffectItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.first().toString(),
                        fontSize = 24.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            if (item.id == "0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "默认",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.name,
            fontSize = 12.sp,
            color = if (isSelected) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomActionBar(
    photoCount: Int,
    onStartProcess: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        cornerRadius = 16.dp
    ) {
        Button(
            onClick = onStartProcess,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始处理",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$photoCount 张",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingDialog(
    progress: Float,
    statusText: String,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .padding(40.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    progress = progress
                )

                Text(
                    text = statusText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "取消处理",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(
    photoCount: Int,
    filterCount: Int,
    watermarkCount: Int,
    onContinueProcessing: () -> Unit,
    onBackToHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFF4CAF50),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "处理完成",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "已成功处理 $photoCount 张图片",
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticItem("处理图片", photoCount)
            StatisticItem("滤镜效果", filterCount)
            StatisticItem("边框水印", watermarkCount)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinueProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "继续处理图片",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBackToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "返回主页",
                fontSize = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

data class FilterOption(
    val id: Long,
    val name: String,
    val thumbnail: String?
)

data class WatermarkOption(
    val id: String,
    val name: String,
    val thumbnail: String?
)

data class EffectItem(
    val id: String,
    val name: String,
    val thumbnail: String?
)

data class ProcessResult(
    val photoCount: Int,
    val filterCount: Int,
    val watermarkCount: Int
)
