package cn.alittlecookie.lut2photo_n.domain.usecase

import android.content.Context
import android.net.Uri
import cn.alittlecookie.lut2photo_n.data.database.entity.FilterEntity
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.util.LutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImportLutUseCase(
    private val context: Context,
    private val filterRepository: FilterRepository,
    private val lutParser: LutParser,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "ImportLutUseCase"
    }

    sealed class ImportResult {
        data class Success(val filterName: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
        data class DuplicateExists(val filterName: String) : ImportResult()
    }

    enum class DuplicateAction { OVERWRITE, RENAME, ASK }

    suspend operator fun invoke(
        uri: Uri,
        duplicateAction: DuplicateAction = DuplicateAction.ASK
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri)
            val fileExt = fileName.substringAfterLast('.').lowercase()

            if (fileExt !in listOf("cube", "vlt")) {
                return@withContext ImportResult.Error("不支持的文件格式: $fileExt")
            }

            val lutsDir = File(context.filesDir, "luts")
            lutsDir.mkdirs()

            val filterName = fileName.substringBeforeLast('.')
            val existingFilter = filterRepository.getFilterByName(filterName)

            if (existingFilter != null && duplicateAction == DuplicateAction.ASK) {
                return@withContext ImportResult.DuplicateExists(filterName)
            }

            val destFile = File(lutsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ImportResult.Error("无法读取文件")

            val targetName = when {
                existingFilter != null && duplicateAction == DuplicateAction.RENAME -> {
                    "${filterName}_${System.currentTimeMillis()}"
                }
                else -> filterName
            }

            if (existingFilter != null && duplicateAction == DuplicateAction.OVERWRITE) {
                filterRepository.update(
                    existingFilter.copy(
                        lutPath1 = destFile.absolutePath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val entity = FilterEntity(
                    name = targetName,
                    lutPath1 = destFile.absolutePath,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                filterRepository.insert(entity)
            }

            logger.i(TAG, "成功导入 LUT: $targetName")
            ImportResult.Success(targetName)
        } catch (e: Exception) {
            logger.e(TAG, "导入 LUT 失败", e)
            ImportResult.Error(e.message ?: "未知错误")
        }
    }

    suspend fun importBatch(
        uris: List<Uri>,
        duplicateAction: DuplicateAction = DuplicateAction.RENAME
    ): List<ImportResult> {
        return uris.map { invoke(it, duplicateAction) }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
