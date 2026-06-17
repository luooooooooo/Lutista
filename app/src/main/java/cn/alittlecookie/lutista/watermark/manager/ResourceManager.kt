package cn.alittlecookie.lutista.watermark.manager

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ResourceManager(private val context: Context) {

    private val templatesDir = File(context.filesDir, "watermark_templates")
    private val fontsDir = File(context.filesDir, "watermark_fonts")
    private val imagesDir = File(context.filesDir, "watermark_images")

    init {
        templatesDir.mkdirs()
        fontsDir.mkdirs()
        imagesDir.mkdirs()
    }

    suspend fun importFont(sourceUri: Uri, templateId: String): String =
        withContext(Dispatchers.IO) {
            val templateDir = File(templatesDir, templateId)
            templateDir.mkdirs()

            val fileName = getFileName(sourceUri) ?: "font_${System.currentTimeMillis()}"
            val destFile = File(templateDir, "fonts/$fileName")
            destFile.parentFile?.mkdirs()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            destFile.absolutePath
        }

    suspend fun importImage(sourceUri: Uri, templateId: String): String =
        withContext(Dispatchers.IO) {
            val templateDir = File(templatesDir, templateId)
            templateDir.mkdirs()

            val fileName = getFileName(sourceUri) ?: "img_${System.currentTimeMillis()}"
            val destFile = File(templateDir, "images/$fileName")
            destFile.parentFile?.mkdirs()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            destFile.absolutePath
        }

    suspend fun deleteTemplate(templateId: String) = withContext(Dispatchers.IO) {
        val templateDir = File(templatesDir, templateId)
        templateDir.deleteRecursively()
    }

    suspend fun saveTemplate(templateId: String, sceneJson: String) =
        withContext(Dispatchers.IO) {
            val templateDir = File(templatesDir, templateId)
            templateDir.mkdirs()

            val jsonFile = File(templateDir, "scene.json")
            jsonFile.writeText(sceneJson)
        }

    suspend fun loadTemplate(templateId: String): String? = withContext(Dispatchers.IO) {
        val jsonFile = File(templatesDir, "$templateId/scene.json")
        if (jsonFile.exists()) jsonFile.readText() else null
    }

    suspend fun listTemplates(): List<String> = withContext(Dispatchers.IO) {
        templatesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    fun loadTypeface(fontPath: String): Typeface? {
        val file = File(fontPath)
        return if (file.exists()) Typeface.createFromFile(file) else null
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
