package cn.alittlecookie.lutista.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(
    private val context: Context
) {
    private val dataStore = context.dataStore

    // 输出格式
    val defaultOutputFormat: Flow<OutputFormat> = dataStore.data.map { prefs ->
        prefs[OUTPUT_FORMAT_KEY]?.let { OutputFormat.valueOf(it) } ?: OutputFormat.JPG
    }

    suspend fun setDefaultOutputFormat(format: OutputFormat) {
        dataStore.edit { prefs -> prefs[OUTPUT_FORMAT_KEY] = format.name }
    }

    fun getDefaultOutputFormatSync(): OutputFormat = runBlocking {
        dataStore.data.first()[OUTPUT_FORMAT_KEY]?.let { OutputFormat.valueOf(it) } ?: OutputFormat.JPG
    }

    // JPG 质量
    val jpgQuality: Flow<Int> = dataStore.data.map { prefs ->
        prefs[JPG_QUALITY_KEY] ?: 95
    }

    suspend fun setJpgQuality(quality: Int) {
        dataStore.edit { prefs -> prefs[JPG_QUALITY_KEY] = quality.coerceIn(60, 100) }
    }

    fun getJpgQualitySync(): Int = runBlocking {
        dataStore.data.first()[JPG_QUALITY_KEY] ?: 95
    }

    // PNG 压缩等级
    val pngCompression: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PNG_COMPRESSION_KEY] ?: 6
    }

    suspend fun setPngCompression(level: Int) {
        dataStore.edit { prefs -> prefs[PNG_COMPRESSION_KEY] = level.coerceIn(0, 9) }
    }

    fun getPngCompressionSync(): Int = runBlocking {
        dataStore.data.first()[PNG_COMPRESSION_KEY] ?: 6
    }

    // LUT 精度
    val lutPrecision: Flow<LutPrecision> = dataStore.data.map { prefs ->
        prefs[LUT_PRECISION_KEY]?.let { LutPrecision.valueOf(it) } ?: LutPrecision.STANDARD
    }

    suspend fun setLutPrecision(precision: LutPrecision) {
        dataStore.edit { prefs -> prefs[LUT_PRECISION_KEY] = precision.name }
    }

    fun getLutPrecisionSync(): Int = runBlocking {
        dataStore.data.first()[LUT_PRECISION_KEY]?.let { LutPrecision.valueOf(it).size } ?: 33
    }

    // 主题模式
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }

    // 日志开关
    val logEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LOG_ENABLED_KEY] ?: true
    }

    fun isLogEnabledSync(): Boolean = runBlocking {
        dataStore.data.first()[LOG_ENABLED_KEY] ?: true
    }

    suspend fun setLogEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[LOG_ENABLED_KEY] = enabled }
    }

    // 监控路径
    val monitorPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[MONITOR_PATH_KEY] ?: ""
    }

    suspend fun setMonitorPath(path: String) {
        dataStore.edit { prefs -> prefs[MONITOR_PATH_KEY] = path }
    }

    fun getMonitorPathSync(): String = runBlocking {
        dataStore.data.first()[MONITOR_PATH_KEY] ?: ""
    }

    // 内置滤镜版本号
    val builtinFilterVersion: Flow<Int> = dataStore.data.map { prefs ->
        prefs[BUILTIN_FILTER_VERSION_KEY] ?: 0
    }

    suspend fun setBuiltinFilterVersion(version: Int) {
        dataStore.edit { prefs -> prefs[BUILTIN_FILTER_VERSION_KEY] = version }
    }

    // 全局滤镜 ID
    val globalFilterId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[GLOBAL_FILTER_ID_KEY]?.toLongOrNull() ?: 0L
    }

    suspend fun setGlobalFilterId(id: Long) {
        dataStore.edit { prefs -> prefs[GLOBAL_FILTER_ID_KEY] = id.toString() }
    }

    fun getGlobalFilterIdSync(): Long = runBlocking {
        dataStore.data.first()[GLOBAL_FILTER_ID_KEY]?.toLongOrNull() ?: 0L
    }

    // 全局边框水印 ID
    val globalWatermarkId: Flow<String> = dataStore.data.map { prefs ->
        prefs[GLOBAL_WATERMARK_ID_KEY] ?: "none"
    }

    suspend fun setGlobalWatermarkId(id: String) {
        dataStore.edit { prefs -> prefs[GLOBAL_WATERMARK_ID_KEY] = id }
    }

    fun getGlobalWatermarkIdSync(): String = runBlocking {
        dataStore.data.first()[GLOBAL_WATERMARK_ID_KEY] ?: "none"
    }

    // 自定义输出文件夹 (SAF URI 字符串或文件系统路径)
    val outputPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[OUTPUT_PATH_KEY] ?: ""
    }

    fun getOutputPathSync(): String = runBlocking {
        dataStore.data.first()[OUTPUT_PATH_KEY] ?: ""
    }

    suspend fun setOutputPath(path: String) {
        dataStore.edit { prefs -> prefs[OUTPUT_PATH_KEY] = path }
    }

    // 服务运行状态
    val monitorRunning: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MONITOR_RUNNING_KEY] ?: false
    }

    suspend fun setMonitorRunning(running: Boolean) {
        dataStore.edit { prefs -> prefs[MONITOR_RUNNING_KEY] = running }
    }

    fun getMonitorRunningSync(): Boolean = runBlocking {
        dataStore.data.first()[MONITOR_RUNNING_KEY] ?: false
    }

    // 仅处理新增文件开关
    val onlyNewFiles: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONLY_NEW_FILES_KEY] ?: true
    }

    suspend fun setOnlyNewFiles(value: Boolean) {
        dataStore.edit { prefs -> prefs[ONLY_NEW_FILES_KEY] = value }
    }

    fun getOnlyNewFilesSync(): Boolean = runBlocking {
        dataStore.data.first()[ONLY_NEW_FILES_KEY] ?: true
    }

    // 已处理图片计数
    val processedCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PROCESSED_COUNT_KEY] ?: 0
    }

    suspend fun incrementProcessedCount() {
        dataStore.edit { prefs ->
            prefs[PROCESSED_COUNT_KEY] = (prefs[PROCESSED_COUNT_KEY] ?: 0) + 1
        }
    }

    suspend fun resetProcessedCount() {
        dataStore.edit { prefs -> prefs[PROCESSED_COUNT_KEY] = 0 }
    }

    fun getProcessedCountSync(): Int = runBlocking {
        dataStore.data.first()[PROCESSED_COUNT_KEY] ?: 0
    }

    fun getThemeModeSync(): ThemeMode = runBlocking {
        dataStore.data.first()[THEME_MODE_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
    }

    // 模糊效果开关
    val useBlur: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_BLUR_KEY] ?: true
    }

    suspend fun setUseBlur(useBlur: Boolean) {
        dataStore.edit { prefs -> prefs[USE_BLUR_KEY] = useBlur }
    }

    fun getUseBlurSync(): Boolean = runBlocking {
        dataStore.data.first()[USE_BLUR_KEY] ?: true
    }

    // Monet 自定义颜色开关
    val useMonet: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_MONET_KEY] ?: false
    }

    suspend fun setUseMonet(useMonet: Boolean) {
        dataStore.edit { prefs -> prefs[USE_MONET_KEY] = useMonet }
    }

    fun getUseMonetSync(): Boolean = runBlocking {
        dataStore.data.first()[USE_MONET_KEY] ?: false
    }

    // 动态取色开关（使用系统/壁纸颜色作为种子色）
    val useDynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR_KEY] ?: true
    }

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { prefs -> prefs[USE_DYNAMIC_COLOR_KEY] = useDynamicColor }
    }

    fun getUseDynamicColorSync(): Boolean = runBlocking {
        dataStore.data.first()[USE_DYNAMIC_COLOR_KEY] ?: true
    }

    // 手动种子颜色 (ARGB hex string)
    val seedColor: Flow<String> = dataStore.data.map { prefs ->
        prefs[SEED_COLOR_KEY] ?: ""
    }

    suspend fun setSeedColor(seedColor: String) {
        dataStore.edit { prefs -> prefs[SEED_COLOR_KEY] = seedColor }
    }

    fun getSeedColorSync(): String = runBlocking {
        dataStore.data.first()[SEED_COLOR_KEY] ?: ""
    }

    // 调色板风格
    val paletteStyle: Flow<String> = dataStore.data.map { prefs ->
        prefs[PALETTE_STYLE_KEY] ?: "TonalSpot"
    }

    suspend fun setPaletteStyle(style: String) {
        dataStore.edit { prefs -> prefs[PALETTE_STYLE_KEY] = style }
    }

    fun getPaletteStyleSync(): String = runBlocking {
        dataStore.data.first()[PALETTE_STYLE_KEY] ?: "TonalSpot"
    }

    // 颜色规范
    val colorSpec: Flow<String> = dataStore.data.map { prefs ->
        prefs[COLOR_SPEC_KEY] ?: "Spec2025"
    }

    suspend fun setColorSpec(spec: String) {
        dataStore.edit { prefs -> prefs[COLOR_SPEC_KEY] = spec }
    }

    fun getColorSpecSync(): String = runBlocking {
        dataStore.data.first()[COLOR_SPEC_KEY] ?: "Spec2025"
    }

    companion object {
        private val USE_BLUR_KEY = booleanPreferencesKey("use_blur")
        private val USE_MONET_KEY = booleanPreferencesKey("use_monet")
        private val USE_DYNAMIC_COLOR_KEY = booleanPreferencesKey("use_dynamic_color")
        private val SEED_COLOR_KEY = stringPreferencesKey("seed_color")
        private val PALETTE_STYLE_KEY = stringPreferencesKey("palette_style")
        private val COLOR_SPEC_KEY = stringPreferencesKey("color_spec")
        private val OUTPUT_FORMAT_KEY = stringPreferencesKey("output_format")
        private val JPG_QUALITY_KEY = intPreferencesKey("jpg_quality")
        private val PNG_COMPRESSION_KEY = intPreferencesKey("png_compression")
        private val LUT_PRECISION_KEY = stringPreferencesKey("lut_precision")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val LOG_ENABLED_KEY = booleanPreferencesKey("log_enabled")
        private val MONITOR_PATH_KEY = stringPreferencesKey("monitor_path")
        private val BUILTIN_FILTER_VERSION_KEY = intPreferencesKey("builtin_filter_version")
        private val GLOBAL_FILTER_ID_KEY = stringPreferencesKey("global_filter_id")
        private val GLOBAL_WATERMARK_ID_KEY = stringPreferencesKey("global_watermark_id")
        private val OUTPUT_PATH_KEY = stringPreferencesKey("output_path")
        private val MONITOR_RUNNING_KEY = booleanPreferencesKey("monitor_running")
        private val ONLY_NEW_FILES_KEY = booleanPreferencesKey("only_new_files")
        private val PROCESSED_COUNT_KEY = intPreferencesKey("processed_count")
    }
}

enum class OutputFormat { JPG, PNG }
enum class LutPrecision(val size: Int) { STANDARD(33), HIGH(65) }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
