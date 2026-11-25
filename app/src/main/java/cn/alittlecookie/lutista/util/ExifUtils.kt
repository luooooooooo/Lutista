package cn.alittlecookie.lutista.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

data class ExifResolution(
    val exif: ExifInterface,
    val app1Segment: ByteArray?
)

object ExifUtils {

    private const val TAG = "ExifUtils"

    // ── Orientation ──────────────────────────────────────────────

    fun correctOrientation(bitmap: Bitmap, path: String): Bitmap {
        val exif = try {
            ExifInterface(path)
        } catch (e: Exception) {
            Log.e(TAG, "correctOrientation(path) failed: $path | $e")
            return bitmap
        }
        return applyOrientation(bitmap, exif)
    }

    fun correctOrientation(bitmap: Bitmap, context: Context, uri: Uri): Bitmap {
        val exif = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            Log.e(TAG, "correctOrientation(uri) failed: $uri | $e")
            null
        }
        return if (exif != null) applyOrientation(bitmap, exif) else bitmap
    }

    fun applyOrientation(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        // EXIF 的 8 种方向都要处理：2/4 是镜像，5/7 是镜像 + 旋转，
        // 只处理 3/6/8 会漏掉自拍镜像等场景，导出后画面会左右/上下翻。
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
            else -> return bitmap
        }
        Log.d(TAG, "applyOrientation: ${bitmap.width}x${bitmap.height} orient=$orientation")
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented != bitmap) bitmap.recycle()
        return oriented
    }

    // ── 输出 EXIF 归一化（导出照片用） ──────────────────────────

    /**
     * 结构性标签：描述的是源文件的物理布局（缩略图 / IFD1、条带、原始像素尺寸等）。
     * 导出图是重新编码的新像素，这些值搬过去就是过期数据；尤其是 IFD1 里那份
     * 缩略图方向标记（Orientation），部分看图软件（如微信）会把它当成整图方向，
     * 导致「相册看是正的，发出去横着」。
     */
    private val UNSAFE_TAGS = setOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_THUMBNAIL_ORIENTATION,
        ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
        ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ExifInterface.TAG_STRIP_OFFSETS,
        ExifInterface.TAG_STRIP_BYTE_COUNTS,
        ExifInterface.TAG_ROWS_PER_STRIP,
        ExifInterface.TAG_SAMPLES_PER_PIXEL,
        ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_COMPRESSION,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_PIXEL_X_DIMENSION,
        ExifInterface.TAG_PIXEL_Y_DIMENSION,
        ExifInterface.TAG_MAKER_NOTE,
        ExifInterface.TAG_XMP,
        ExifInterface.TAG_OECF,
        ExifInterface.TAG_ORF_ASPECT_FRAME,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
        ExifInterface.TAG_ORF_THUMBNAIL_IMAGE,
        ExifInterface.TAG_DNG_VERSION,
        ExifInterface.TAG_CFA_PATTERN,
        ExifInterface.TAG_TRANSFER_FUNCTION,
        ExifInterface.TAG_WHITE_POINT,
        ExifInterface.TAG_PRIMARY_CHROMATICITIES,
        ExifInterface.TAG_Y_CB_CR_COEFFICIENTS,
        ExifInterface.TAG_Y_CB_CR_POSITIONING,
        ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING,
    )

    /**
     * 给重新编码的 JPEG 写一份「干净」的 EXIF：
     * - 方向固定 1（像素已由 [applyOrientation] 转正）
     * - 不搬运缩略图 / IFD1 / 条带等结构标签，文件里只剩一处方向标记
     * - 尺寸写成实际输出的宽高，不再保留源图的旧尺寸
     * 相机型号、拍摄参数、GPS 等描述性标签仍然保留。
     */
    fun buildCleanJpegExif(
        jpeg: ByteArray,
        sourceExif: ExifInterface?,
        tempFile: File,
        width: Int,
        height: Int
    ): ByteArray {
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(jpeg)
        try {
            val dest = ExifInterface(tempFile.absolutePath)
            if (sourceExif != null) {
                for (tag in ALL_TAGS) {
                    if (tag in UNSAFE_TAGS) continue
                    val value = try { sourceExif.getAttribute(tag) } catch (_: Exception) { null } ?: continue
                    try { dest.setAttribute(tag, value) } catch (_: Exception) { /* 个别标签格式不受支持，跳过 */ }
                }
            }
            dest.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dest.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            dest.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            dest.saveAttributes()
            return tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    // ── EXIF resolution for PhotoProcessScreen ───────────────────

    /**
     * Read JPEG bytes from URI, extract APP1 segment, fall back to MediaStore
     * if the URI doesn't contain usable EXIF.
     */
    fun resolveExifSource(context: Context, uri: Uri): ExifResolution? {
        val (app1, exif) = readApp1AndExif(context, uri)
        if (app1 != null) {
            val gpsLat = exif?.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val tagCount = countTags(exif)
            Log.d(TAG, "resolveExifSource(uri): hasApp1 tagCount=$tagCount gpsLat=$gpsLat")
            // If GPS is missing but other tags exist, try again with setRequireOriginal
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && gpsLat == null && tagCount > 2) {
                try {
                    val originalUri = MediaStore.setRequireOriginal(uri)
                    val (rawApp1, rawExif) = readApp1AndExif(context, originalUri)
                    if (rawApp1 != null) {
                        val rawGps = rawExif?.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                        Log.d(TAG, "resolveExifSource: setRequireOriginal gpsLat=$rawGps")
                        if (rawGps != null) return ExifResolution(rawExif ?: exif!!, rawApp1)
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "resolveExifSource: setRequireOriginal threw, using original")
                }
            }
            return ExifResolution(exif ?: return null, app1)
        }

        // Try MediaStore via content:// URI (HyperOS PhotoPicker cache has stripped EXIF)
        val uriStr = uri.toString()
        val match = Regex("""/(\d{10,13})\.jpg""").find(uriStr)
        if (match != null) {
            val timeMs = match.groupValues[1].toLongOrNull() ?: 0L
            val timeSec = if (timeMs > 100000000000L) timeMs / 1000 else timeMs
            Log.d(TAG, "resolveExifSource: MediaStore fallback, window=${timeSec - 10}..${timeSec + 10}")
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DATE_ADDED} BETWEEN ? AND ?",
                arrayOf((timeSec - 10).toString(), (timeSec + 10).toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try { MediaStore.setRequireOriginal(contentUri) } catch (_: Exception) { contentUri }
                    } else contentUri
                    val (msApp1, msExif) = readApp1AndExif(context, originalUri)
                    if (msApp1 != null) {
                        val msGps = msExif?.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                        Log.d(TAG, "resolveExifSource: MediaStore hit id=$id tagCount=${countTags(msExif)} gpsLat=$msGps")
                        return ExifResolution(msExif ?: return@use, msApp1)
                    }
                }
            }
        }
        return null
    }

    private fun readApp1AndExif(context: Context, uri: Uri): Pair<ByteArray?, ExifInterface?> {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "readApp1AndExif: failed $uri | $e")
            null
        } ?: return Pair(null, null)
        val app1 = extractApp1FromJpeg(bytes)
        val exif = try { ExifInterface(bytes.inputStream()) } catch (_: Exception) { null }
        return Pair(app1, exif)
    }

    private fun countTags(exif: ExifInterface?): Int {
        return exif?.let { ALL_TAGS.count { t -> it.getAttribute(t) != null } } ?: 0
    }

    // ── Byte-level JPEG/EXIF manipulation ────────────────────────

    /** Extract APP1 (0xFFE1) marker segment from JPEG bytes. */
    fun extractApp1FromJpeg(jpegBytes: ByteArray): ByteArray? {
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null
        var pos = 2
        while (pos < jpegBytes.size - 3) {
            if (jpegBytes[pos] != 0xFF.toByte()) { pos++; continue }
            val marker = jpegBytes[pos + 1].toInt() and 0xFF
            if (marker == 0xE1) {
                val length = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 3].toInt() and 0xFF)
                return if (pos + 2 + length <= jpegBytes.size) jpegBytes.copyOfRange(pos, pos + 2 + length) else null
            }
            if (marker == 0xD9 || marker == 0xDA) break
            if (marker == 0xD8) { pos += 2; continue }
            if (pos + 3 >= jpegBytes.size) break
            val length = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 3].toInt() and 0xFF)
            pos += 2 + length
        }
        return null
    }

    // ── Tag utilities ────────────────────────────────────────────

    fun copyAllTagsTo(src: ExifInterface, dest: ExifInterface) {
        for (tag in ALL_TAGS) {
            src.getAttribute(tag)?.let { dest.setAttribute(tag, it) }
        }
    }

    fun readTag(path: String, tag: String): String? {
        return try { ExifInterface(path).getAttribute(tag) } catch (_: Exception) { null }
    }

    fun readTag(context: Context, uri: Uri, tag: String): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(tag) }
        } catch (_: Exception) { null }
    }

    fun readAllTags(path: String): Map<String, String> = buildMap {
        try {
            val exif = ExifInterface(path)
            ALL_TAGS.forEach { tag -> exif.getAttribute(tag)?.let { put(tag, it) } }
        } catch (_: Exception) {}
    }

    fun readAllTags(context: Context, uri: Uri): Map<String, String> = buildMap {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                ALL_TAGS.forEach { tag -> exif.getAttribute(tag)?.let { put(tag, it) } }
            }
        } catch (_: Exception) {}
    }

    // ── Tag registry ─────────────────────────────────────────────

    private val ALL_TAGS = listOf(
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_ARTIST, ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_BRIGHTNESS_VALUE, ExifInterface.TAG_CFA_PATTERN, ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_COMPONENTS_CONFIGURATION, ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL,
        ExifInterface.TAG_COMPRESSION, ExifInterface.TAG_CONTRAST, ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_CUSTOM_RENDERED, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_EXIF_VERSION,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_EXPOSURE_INDEX, ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_FILE_SOURCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_FLASHPIX_VERSION,
        ExifInterface.TAG_FLASH_ENERGY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
        ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF, ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, ExifInterface.TAG_GPS_DIFFERENTIAL, ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_INTEROPERABILITY_INDEX, ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_LIGHT_SOURCE, ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_MAX_APERTURE_VALUE, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_MODEL, ExifInterface.TAG_NEW_SUBFILE_TYPE, ExifInterface.TAG_OECF,
        ExifInterface.TAG_ORF_ASPECT_FRAME, ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_START, ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, ExifInterface.TAG_PIXEL_X_DIMENSION,
        ExifInterface.TAG_PIXEL_Y_DIMENSION, ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_PRIMARY_CHROMATICITIES, ExifInterface.TAG_REFERENCE_BLACK_WHITE,
        ExifInterface.TAG_RELATED_SOUND_FILE, ExifInterface.TAG_RESOLUTION_UNIT,
        ExifInterface.TAG_ROWS_PER_STRIP, ExifInterface.TAG_SAMPLES_PER_PIXEL,
        ExifInterface.TAG_SATURATION, ExifInterface.TAG_SCENE_CAPTURE_TYPE, ExifInterface.TAG_SCENE_TYPE,
        ExifInterface.TAG_SENSING_METHOD, ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, ExifInterface.TAG_SPECTRAL_SENSITIVITY,
        ExifInterface.TAG_STRIP_BYTE_COUNTS, ExifInterface.TAG_STRIP_OFFSETS, ExifInterface.TAG_SUBFILE_TYPE,
        ExifInterface.TAG_SUBJECT_AREA, ExifInterface.TAG_SUBJECT_DISTANCE,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
        ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH, ExifInterface.TAG_THUMBNAIL_ORIENTATION,
        ExifInterface.TAG_TRANSFER_FUNCTION, ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_WHITE_POINT, ExifInterface.TAG_X_RESOLUTION,
        ExifInterface.TAG_Y_CB_CR_COEFFICIENTS, ExifInterface.TAG_Y_CB_CR_POSITIONING,
        ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING, ExifInterface.TAG_Y_RESOLUTION,
        ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_DEFAULT_CROP_SIZE, ExifInterface.TAG_DNG_VERSION,
        ExifInterface.TAG_GAMMA, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_ORF_THUMBNAIL_IMAGE, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, ExifInterface.TAG_SENSITIVITY_TYPE,
        ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, ExifInterface.TAG_XMP,
    )
}
