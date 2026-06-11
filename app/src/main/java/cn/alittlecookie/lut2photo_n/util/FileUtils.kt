package cn.alittlecookie.lut2photo_n.util

import android.content.Context
import android.net.Uri
import java.io.File

class FileUtils(
    private val context: Context
) {
    fun getAppDir(): File = context.filesDir

    fun getLutsDir(): File = File(context.filesDir, "luts").apply { mkdirs() }

    fun getOutputDir(): File = File(context.filesDir, "output").apply { mkdirs() }

    fun getCacheDir(): File = context.cacheDir

    fun getLogsDir(): File = File(context.filesDir, "logs").apply { mkdirs() }

    fun getUnifiedLutsDir(): File = File(context.filesDir, "unified_luts").apply { mkdirs() }

    fun getFileNameFromUri(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    fun isImageFile(path: String): Boolean {
        val ext = path.substringAfterLast('.').lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "tiff")
    }

    fun isLutFile(path: String): Boolean {
        val ext = path.substringAfterLast('.').lowercase()
        return ext in listOf("cube", "vlt")
    }

    fun generateOutputPath(sourcePath: String, outputDir: File? = null): String {
        val dir = outputDir ?: getOutputDir()
        val ext = sourcePath.substringAfterLast('.').lowercase()
        val name = sourcePath.substringBeforeLast('/').substringBeforeLast('\\')
            .substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
        return File(dir, "${name}_processed_${System.currentTimeMillis()}.$ext").absolutePath
    }

    fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
}
