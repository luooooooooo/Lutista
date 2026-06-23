package cn.alittlecookie.lutista.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

class FileUtils(
    private val context: Context
) {
    fun getAppDir(): File = context.filesDir

    fun getLutsDir(): File = File(context.filesDir, "luts").apply { mkdirs() }

    fun getDefaultOutputDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lutista").apply { mkdirs() }

    fun getCacheDir(): File = context.cacheDir

    fun getLogsDir(): File = File(context.filesDir, "logs").apply { mkdirs() }

    fun getUnifiedLutsDir(): File = File(context.filesDir, "unified_luts").apply { mkdirs() }

    fun getOutputDir(customPath: String? = null): File {
        if (!customPath.isNullOrEmpty() && !customPath.startsWith("content://")) {
            val dir = File(customPath)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        return getDefaultOutputDir()
    }

    fun getOutputDirUri(customPath: String?): Uri? {
        return if (!customPath.isNullOrEmpty() && customPath.startsWith("content://")) {
            Uri.parse(customPath)
        } else {
            null
        }
    }

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
        val dir = outputDir ?: getDefaultOutputDir()
        val name = requireNotNull(sourcePath.substringBeforeLast('/')
            .substringBeforeLast('\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')) { "Invalid sourcePath: $sourcePath" }
        return File(dir, "${name}_processed_${System.currentTimeMillis()}.jpg").absolutePath
    }

    fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create an output file in a SAF-backed (content://) output directory.
     */
    fun createOutputFileInSafDir(
        outputDirUri: Uri,
        displayName: String,
        mimeType: String = "image/jpeg"
    ): Uri? {
        val docDir = DocumentFile.fromTreeUri(context, outputDirUri) ?: return null
        val docFile = docDir.createFile(mimeType, displayName) ?: return null
        return docFile.uri
    }
}
