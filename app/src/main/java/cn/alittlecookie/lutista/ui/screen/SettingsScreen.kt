package cn.alittlecookie.lutista.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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

    val outputFormat by settingsManager.defaultOutputFormat.collectAsState(OutputFormat.JPG)
    val jpgQuality by settingsManager.jpgQuality.collectAsState(95)
    val pngCompression by settingsManager.pngCompression.collectAsState(6)
    val lutPrecision by settingsManager.lutPrecision.collectAsState(LutPrecision.STANDARD)
    val logEnabled by settingsManager.logEnabled.collectAsState(true)

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SmallTitle(
                text = "图片处理",
                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Card {
                val formatOptions = remember {
                    listOf(DropdownItem(text = "JPG"), DropdownItem(text = "PNG"))
                }
                OverlaySpinnerPreference(
                    title = "输出格式",
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
                    valueText = qualityValue.roundToInt().toString(),
                    valueRange = qualityRange,
                    steps = qualitySteps
                )

                val precisionOptions = remember {
                    listOf(DropdownItem(text = "33位"), DropdownItem(text = "65位"))
                }
                OverlaySpinnerPreference(
                    title = "LUT精度",
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
                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Card {
                ArrowPreference(
                    title = "主题设置",
                    onClick = { /* TODO: 主题设置页面 */ }
                )

                SwitchPreference(
                    title = "Logcat日志",
                    checked = logEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsManager.setLogEnabled(enabled)
                        }
                    }
                )

                ArrowPreference(
                    title = "导出并分享Logcat",
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
                    summary = versionName,
                    onClick = { /* TODO: 关于页面 */ }
                )
            }
        }
    }
}
