package cn.alittlecookie.lutista.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.LutPrecision
import cn.alittlecookie.lutista.manager.OutputFormat
import cn.alittlecookie.lutista.manager.SettingsManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null
) {
    val context = LocalContext.current
    val settingsManager: SettingsManager = koinInject()
    val logManager: LogManager = koinInject()
    val scope = rememberCoroutineScope()

    var outputFormat by remember { mutableStateOf(settingsManager.getDefaultOutputFormatSync()) }
    var jpgQuality by remember { mutableIntStateOf(settingsManager.getJpgQualitySync()) }
    var pngCompression by remember { mutableIntStateOf(settingsManager.getPngCompressionSync()) }
    var lutPrecision by remember { mutableStateOf(if (settingsManager.getLutPrecisionSync() == 65) LutPrecision.HIGH else LutPrecision.STANDARD) }
    var logEnabled by remember { mutableStateOf(settingsManager.isLogEnabledSync()) }

    LaunchedEffect(Unit) {
        settingsManager.defaultOutputFormat.collect { outputFormat = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.jpgQuality.collect { jpgQuality = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.pngCompression.collect { pngCompression = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.lutPrecision.collect { lutPrecision = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.logEnabled.collect { logEnabled = it }
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        } catch (_: Exception) {
            "未知"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SmallTitle(
                text = "图片处理",
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Card {
                val formatOptions = remember {
                    listOf(DropdownItem(text = "JPG"), DropdownItem(text = "PNG"))
                }
                OverlaySpinnerPreference(
                    title = "输出格式",
                    summary = "选择全局输出格式",
                    items = formatOptions,
                    selectedIndex = outputFormat.ordinal,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsManager.setDefaultOutputFormat(
                                if (index == 0) OutputFormat.JPG else OutputFormat.PNG
                            )
                        }
                    }
                )

                val qualityValue =
                    if (outputFormat == OutputFormat.JPG) jpgQuality.toFloat() else pngCompression.toFloat()
                val qualityRange =
                    if (outputFormat == OutputFormat.JPG) 60f..100f else 0f..9f
                val qualitySteps =
                    if (outputFormat == OutputFormat.JPG) 40 else 9

                SliderPreference(
                    value = qualityValue,
                    onValueChange = { value ->
                        scope.launch {
                            if (outputFormat == OutputFormat.JPG) {
                                settingsManager.setJpgQuality(value.roundToInt())
                            } else {
                                settingsManager.setPngCompression(value.roundToInt())
                            }
                        }
                    },
                    title = "输出质量",
                    summary = "PNG格式始终为无损，滑块仅控制压缩等级",
                    valueText = qualityValue.roundToInt().toString(),
                    valueRange = qualityRange,
                    steps = qualitySteps
                )

                val precisionOptions = remember {
                    listOf(DropdownItem(text = "33位"), DropdownItem(text = "65位"))
                }
                OverlaySpinnerPreference(
                    title = "LUT精度",
                    summary = "控制LUT上/下采样的精度，同时对统一LUT生效",
                    items = precisionOptions,
                    selectedIndex = if (lutPrecision == LutPrecision.STANDARD) 0 else 1,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsManager.setLutPrecision(
                                if (index == 0) LutPrecision.STANDARD else LutPrecision.HIGH
                            )
                        }
                    }
                )
            }
        }

        item {
            SmallTitle(
                text = "应用",
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Card {
                ArrowPreference(
                    title = "主题设置",
                    onClick = { /* TODO: 主题设置页面 */ }
                )

                SwitchPreference(
                    title = "Logcat日志",
                    summary = "开启后打印Logcat到文件",
                    checked = logEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsManager.setLogEnabled(enabled)
                        }
                    }
                )

                ArrowPreference(
                    title = "导出并分享Logcat",
                    summary = "仅包含应用级别Logcat",
                    onClick = {
                        scope.launch {
                            val zipFile = logManager.exportLogs()
                            if (zipFile != null) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    zipFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                            } else {
                                Toast.makeText(context, "暂无日志可导出", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                ArrowPreference(
                    title = "关于应用",
                    summary = "Version: $versionName",
                    onClick = { /* TODO: 关于页面 */ }
                )
            }
        }
    }
}
