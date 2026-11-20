package cn.alittlecookie.lutista.manager

import android.content.Context
import cn.alittlecookie.lutista.data.database.entity.FilterEntity
import cn.alittlecookie.lutista.data.repository.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class BuiltinFilterManager(
    private val context: Context,
    private val filterRepository: FilterRepository,
    private val settingsManager: SettingsManager,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "BuiltinFilterManager"

        /**
         * 内置滤镜包版本。v2 起支持「cube + 同名 json」的完整配方（曲线 / LCH / 各项参数），
         * 并新增内置滤镜「NC 人像优化」。老用户升级时靠这个版本号触发一次重新释放。
         * v3：NC 人像优化曝光改为 +0.5 EV，并固定排在默认顺序的第一位。
         */
        const val CURRENT_BUILTIN_FILTER_VERSION = 3
        private const val FILTERS_DIR = "filters"
        private const val ASSETS_FILTERS_DIR = "filters"

        /** 默认顺序里固定排在最前面的内置滤镜（按列表顺序），其余仍按名称字母序。 */
        private val PINNED_FIRST = listOf("NC 人像优化")
    }

    data class ReleasedFilter(
        val name: String,
        val filterDir: File,
        val lutFile: File,
        /** 与 cube 同名的配方 json（可选），用于内置完整的曲线 / LCH / 参数 */
        val configJson: File? = null
    )

    suspend fun initializeBuiltinFilters() = withContext(Dispatchers.IO) {
        try {
            val savedVersion = settingsManager.builtinFilterVersion.first()
            logger.i(TAG, "内置滤镜版本检查: 保存版本=$savedVersion, 当前版本=$CURRENT_BUILTIN_FILTER_VERSION")

            if (savedVersion < CURRENT_BUILTIN_FILTER_VERSION) {
                logger.i(TAG, "需要更新内置滤镜，开始初始化...")
                deleteAllBuiltinFilters()
                val releasedFilters = releaseFilterFiles()
                createBuiltinFilterEntities(releasedFilters)
                settingsManager.setBuiltinFilterVersion(CURRENT_BUILTIN_FILTER_VERSION)
                logger.i(TAG, "内置滤镜初始化完成，共释放 ${releasedFilters.size} 个滤镜")
            } else {
                logger.i(TAG, "内置滤镜版本已是最新，跳过初始化")
            }
        } catch (e: Exception) {
            logger.e(TAG, "初始化内置滤镜失败", e)
        }
    }

    private suspend fun releaseFilterFiles(): List<ReleasedFilter> = withContext(Dispatchers.IO) {
        val releasedFilters = mutableListOf<ReleasedFilter>()
        val filtersBaseDir = File(context.filesDir, FILTERS_DIR)
        filtersBaseDir.mkdirs()

        try {
            val assetFiles = context.assets.list(ASSETS_FILTERS_DIR) ?: emptyArray()
            logger.d(TAG, "Assets中的滤镜文件: ${assetFiles.joinToString()}")

            // 与 cube 同名的 .json 是完整配方（曲线 / LCH / 各项参数），一起释放
            val jsonAssets = assetFiles.filter { it.endsWith(".json", ignoreCase = true) }

            for (fileName in assetFiles) {
                if (!fileName.endsWith(".cube", ignoreCase = true)) continue

                val filterName = fileName.substringBeforeLast(".")
                val filterDir = File(filtersBaseDir, filterName)
                filterDir.mkdirs()

                val destFile = File(filterDir, "${filterName}.cube")
                context.assets.open("$ASSETS_FILTERS_DIR/$fileName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val jsonAsset = jsonAssets.firstOrNull { it.substringBeforeLast(".") == filterName }
                val configFile = jsonAsset?.let { asset ->
                    val dest = File(filterDir, asset)
                    context.assets.open("$ASSETS_FILTERS_DIR/$asset").use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    dest
                }

                releasedFilters.add(
                    ReleasedFilter(
                        name = filterName,
                        filterDir = filterDir,
                        lutFile = destFile,
                        configJson = configFile
                    )
                )
                logger.d(TAG, "释放滤镜: $filterName -> ${destFile.absolutePath}${if (configFile != null) " (+配置)" else ""}")
            }
        } catch (e: Exception) {
            logger.e(TAG, "释放滤镜文件失败", e)
        }

        releasedFilters
    }

    /**
     * 默认顺序：先放 [PINNED_FIRST] 里点名的滤镜（按点名的先后），其余按名称字母序。
     * 这样「NC 人像优化」在配方库、单图底部横排、整页平铺里都是第一个。
     */
    private fun defaultOrder(releasedFilters: List<ReleasedFilter>): List<ReleasedFilter> {
        val pinned = PINNED_FIRST.mapNotNull { name ->
            releasedFilters.firstOrNull { it.name == name }
        }
        val rest = releasedFilters
            .filterNot { it.name in PINNED_FIRST }
            .sortedBy { it.name.lowercase() }
        return pinned + rest
    }

    private suspend fun createBuiltinFilterEntities(releasedFilters: List<ReleasedFilter>) {
        val sorted = defaultOrder(releasedFilters)
        for ((index, releasedFilter) in sorted.withIndex()) {
            try {
                val existingFilter = filterRepository.getFilterByName(releasedFilter.name)
                if (existingFilter != null) {
                    logger.w(TAG, "滤镜已存在，跳过: ${releasedFilter.name}")
                    continue
                }

                val entity = FilterEntity(
                    name = releasedFilter.name,
                    lutPath1 = releasedFilter.lutFile.absolutePath,
                    lutConcentration1 = 1.0f,
                    isBuiltIn = true,
                    filterDirPath = releasedFilter.filterDir.absolutePath,
                    sortOrder = index,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // 有同名 json 的滤镜带着完整配方（曲线 / LCH / 颗粒 / 基础参数），
                // 不是只有一个 LUT，否则「内置」和用户导入后的效果会不一样。
                val entityWithConfig = releasedFilter.configJson?.let { jsonFile ->
                    runCatching {
                        val json = JSONObject(jsonFile.readText())
                        entity.copy(
                            lutConcentration1 = json.optDouble("lutConcentration1", 1.0).toFloat(),
                            lutConcentration2 = json.optDouble("lutConcentration2", 1.0).toFloat(),
                            lutGlobalStrength = json.optDouble("lutGlobalStrength", 1.0).toFloat(),
                            lutPrecision = json.optInt("lutPrecision", 33),
                            exposure = json.optDouble("exposure", 0.0).toFloat(),
                            contrast = json.optDouble("contrast", 0.0).toFloat(),
                            highlights = json.optDouble("highlights", 0.0).toFloat(),
                            shadows = json.optDouble("shadows", 0.0).toFloat(),
                            saturation = json.optDouble("saturation", 0.0).toFloat(),
                            colorTemperature = json.optDouble("colorTemperature", 0.0).toFloat(),
                            tint = json.optDouble("tint", 0.0).toFloat(),
                            curveJson = json.optString("curveJson", "{}"),
                            lchJson = json.optString("lchJson", "{}"),
                            vignetteStrength = json.optDouble("vignetteStrength", 0.0).toFloat(),
                            grainJson = json.optString("grainJson", "{}")
                        )
                    }.getOrElse {
                        logger.w(TAG, "内置滤镜配置解析失败，按纯 LUT 释放: ${releasedFilter.name}")
                        entity
                    }
                } ?: entity

                filterRepository.insert(entityWithConfig)
                logger.d(TAG, "创建内置滤镜实体: ${releasedFilter.name}")
            } catch (e: Exception) {
                logger.e(TAG, "创建滤镜实体失败: ${releasedFilter.name}", e)
            }
        }
    }

    private suspend fun deleteAllBuiltinFilters() {
        try {
            val allFilters = filterRepository.getAllFilters().first()

            // 非内置配方（用户的精修备份 / 拷贝）可能仍然引用 filters 目录里的同一个 LUT 文件，
            // 以前这里会把整个 filters 目录删掉，升级内置版本时这些配方就直接读不到 LUT 了。
            // 现在只删没有任何非内置配方引用的目录。
            val sharedDirs = allFilters
                .filter { !it.isBuiltIn }
                .flatMap { filter ->
                    listOfNotNull(
                        filter.filterDirPath,
                        filter.lutPath1?.let { File(it).parent },
                        filter.lutPath2?.let { File(it).parent }
                    )
                }
                .map { File(it).absolutePath }
                .toSet()

            for (filter in allFilters.filter { it.isBuiltIn }) {
                filter.filterDirPath?.let { dirPath ->
                    val dir = File(dirPath)
                    if (sharedDirs.contains(dir.absolutePath)) {
                        logger.d(TAG, "目录仍被非内置配方引用，保留: $dirPath")
                    } else if (dir.exists()) {
                        dir.deleteRecursively()
                        logger.d(TAG, "删除滤镜目录: $dirPath")
                    }
                }
                filterRepository.delete(filter)
            }

            logger.i(TAG, "已删除所有内置滤镜（保留被引用目录）")
        } catch (e: Exception) {
            logger.e(TAG, "删除内置滤镜失败", e)
        }
    }

    suspend fun getBuiltinFilters(): List<FilterEntity> {
        return filterRepository.getAllFilters().first().filter { it.isBuiltIn }
    }

    suspend fun copyBuiltinFilter(filterId: Long): FilterEntity? {
        val filter = filterRepository.getFilterById(filterId) ?: return null
        if (!filter.isBuiltIn) return null

        val baseName = filter.name
        val allFilters = filterRepository.getAllFilters().first()
        val copyNum = allFilters
            .mapNotNull { e ->
                val prefix = "${baseName}-拷贝"
                if (e.name.startsWith(prefix))
                    e.name.removePrefix(prefix).toIntOrNull()
                else null
            }
            .maxOrNull()
            ?.plus(1) ?: 1
        val newName = "${baseName}-拷贝$copyNum"

        val filtersBaseDir = File(context.filesDir, FILTERS_DIR)
        val newDir = File(filtersBaseDir, newName)
        newDir.mkdirs()

        filter.filterDirPath?.let { oldDirPath ->
            val oldDir = File(oldDirPath)
            if (oldDir.exists()) {
                oldDir.copyRecursively(newDir, overwrite = true)
            }
        }

        val newLutPath1 = filter.lutPath1?.let { oldPath ->
            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                val newFile = File(newDir, oldFile.name)
                oldFile.copyTo(newFile, overwrite = true)
                newFile.absolutePath
            } else null
        }

        val newEntity = FilterEntity(
            name = newName,
            lutPath1 = newLutPath1,
            lutConcentration1 = filter.lutConcentration1,
            lutPath2 = filter.lutPath2,
            lutConcentration2 = filter.lutConcentration2,
            lutGlobalStrength = filter.lutGlobalStrength,
            lutPrecision = filter.lutPrecision,
            exposure = filter.exposure,
            contrast = filter.contrast,
            highlights = filter.highlights,
            shadows = filter.shadows,
            saturation = filter.saturation,
            colorTemperature = filter.colorTemperature,
            tint = filter.tint,
            curveJson = filter.curveJson,
            lchJson = filter.lchJson,
            vignetteStrength = filter.vignetteStrength,
            grainJson = filter.grainJson,
            isBuiltIn = false,
            filterDirPath = newDir.absolutePath,
            sortOrder = -1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val newId = filterRepository.insert(newEntity)
        logger.i(TAG, "拷贝内置滤镜: ${filter.name} -> $newName")
        return filterRepository.getFilterById(newId)
    }
}
