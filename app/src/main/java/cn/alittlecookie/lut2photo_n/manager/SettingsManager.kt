package cn.alittlecookie.lut2photo_n.manager

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

    // JPG 质量
    val jpgQuality: Flow<Int> = dataStore.data.map { prefs ->
        prefs[JPG_QUALITY_KEY] ?: 95
    }

    suspend fun setJpgQuality(quality: Int) {
        dataStore.edit { prefs -> prefs[JPG_QUALITY_KEY] = quality.coerceIn(60, 100) }
    }

    // PNG 压缩等级
    val pngCompression: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PNG_COMPRESSION_KEY] ?: 6
    }

    suspend fun setPngCompression(level: Int) {
        dataStore.edit { prefs -> prefs[PNG_COMPRESSION_KEY] = level.coerceIn(0, 9) }
    }

    // LUT 精度
    val lutPrecision: Flow<LutPrecision> = dataStore.data.map { prefs ->
        prefs[LUT_PRECISION_KEY]?.let { LutPrecision.valueOf(it) } ?: LutPrecision.STANDARD
    }

    suspend fun setLutPrecision(precision: LutPrecision) {
        dataStore.edit { prefs -> prefs[LUT_PRECISION_KEY] = precision.name }
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

    // 全局边框水印 ID
    val globalWatermarkId: Flow<String> = dataStore.data.map { prefs ->
        prefs[GLOBAL_WATERMARK_ID_KEY] ?: "none"
    }

    suspend fun setGlobalWatermarkId(id: String) {
        dataStore.edit { prefs -> prefs[GLOBAL_WATERMARK_ID_KEY] = id }
    }

    companion object {
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
    }
}

enum class OutputFormat { JPG, PNG }
enum class LutPrecision(val size: Int) { STANDARD(33), HIGH(65) }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
