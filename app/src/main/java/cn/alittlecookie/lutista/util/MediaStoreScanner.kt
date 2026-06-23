package cn.alittlecookie.lutista.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import cn.alittlecookie.lutista.manager.LogManager
import java.util.concurrent.ConcurrentHashMap

class MediaStoreScanner(
    private val context: Context,
    private val logger: LogManager
) {
    companion object {
        private const val TAG = "MediaStoreScanner"
        private val IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp")
    }

    private val seenUris = ConcurrentHashMap.newKeySet<String>()

    data class ScannedImage(
        val uri: Uri,
        val displayName: String,
        val dateAdded: Long,
        val size: Long
    )

    /**
     * Query MediaStore for the latest image in a given folder path.
     * Returns null if SecurityException or no results.
     *
     * @param folderPath  filesystem path like "/storage/emulated/0/DCIM/Camera"
     *                     or relative path like "DCIM/Camera"
     * @param sinceTimestamp  only return images added after this timestamp (epoch seconds)
     */
    fun queryLatestImages(
        folderPath: String,
        sinceTimestamp: Long = 0L
    ): List<ScannedImage> {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val selection: String
            val selectionArgs: Array<String>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = normalizeToRelativePath(folderPath)
                if (relativePath != null) {
                    selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                    selectionArgs = arrayOf("$relativePath%")
                } else {
                    selection = "${MediaStore.Images.Media.RELATIVE_PATH} IS NOT NULL"
                    selectionArgs = emptyArray()
                }
            } else {
                @Suppress("Deprecation")
                selection = "${MediaStore.Images.Media.DATA} LIKE ?"
                selectionArgs = arrayOf("$folderPath%")
            }

            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val results = mutableListOf<ScannedImage>()

            context.contentResolver.query(
                queryUri, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val dateAdded = cursor.getLong(dateCol)
                    if (dateAdded <= sinceTimestamp) break // sorted DESC, stop at old entries

                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol)
                    val size = cursor.getLong(sizeCol)

                    if (!isImageFile(displayName)) continue

                    val uri = Uri.withAppendedPath(queryUri, id.toString())
                    results.add(ScannedImage(uri, displayName, dateAdded, size))
                }
            }

            logger.d(TAG, "MediaStore query: folder=$folderPath, found=${results.size}, since=$sinceTimestamp")
            results
        } catch (e: SecurityException) {
            logger.w(TAG, "MediaStore query denied by SecurityException for $folderPath", e)
            emptyList()
        } catch (e: Exception) {
            logger.w(TAG, "MediaStore query failed for $folderPath", e)
            emptyList()
        }
    }

    /**
     * Query for new images since last check, deduplicating against seenUris.
     * Returns only previously unseen images.
     */
    fun queryNewImages(
        folderPath: String,
        sinceTimestamp: Long = System.currentTimeMillis() / 1000 - 60
    ): List<ScannedImage> {
        val allImages = queryLatestImages(folderPath, sinceTimestamp)
        val newImages = allImages.filter { seenUris.add(it.uri.toString()) }
        logger.d(TAG, "New images detected: ${newImages.size} of ${allImages.size} total")
        return newImages
    }

    fun clearSeenUris() {
        seenUris.clear()
    }

    fun markAllAsSeen(uris: List<String>) {
        seenUris.addAll(uris)
    }

    fun forgetUri(uriString: String) {
        seenUris.remove(uriString)
    }

    /**
     * Check if a filesystem path is likely a public media directory accessible via MediaStore.
     */
    fun isMediaStoreAccessible(folderPath: String): Boolean {
        val lower = folderPath.lowercase()
        val publicRoots = listOf(
            "/storage/emulated/0/dcim",
            "/storage/emulated/0/pictures",
            "/storage/emulated/0/movies",
            "/storage/emulated/0/download",
            "/storage/emulated/0/music",
            "/sdcard/dcim",
            "/sdcard/pictures",
            "/sdcard/download",
            "dcim",
            "pictures",
            "movies",
            "download"
        )
        return publicRoots.any { lower.startsWith(it) || lower.contains("/$it/") || lower.contains("\\$it\\") }
    }

    private fun normalizeToRelativePath(folderPath: String): String? {
        val patterns = listOf(
            "/storage/emulated/0/",
            "/storage/sdcard0/",
            "/sdcard/",
            "/storage/self/primary/"
        )
        for (pattern in patterns) {
            val idx = folderPath.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) {
                return folderPath.substring(idx + pattern.length).trimEnd('/', '\\')
            }
        }
        if (folderPath.matches(Regex("^[A-Za-z]+/"))) {
            return folderPath.trimEnd('/', '\\')
        }
        return null
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "tiff", "dng")
    }
}
