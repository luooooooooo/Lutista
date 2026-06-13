package cn.alittlecookie.lut2photo_n.util

import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Process an image in chunks to avoid OOM on large images.
 * Converts to float, processes each chunk through the given function, converts back to byte.
 * The processor function receives float data normalized to [0,255] range (matching CV_32FC3).
 */
fun processImageInChunks(
    input: Mat,
    maxArrayBytes: Int = 16 * 1024 * 1024, // 16MB default
    processChunk: (FloatArray) -> FloatArray
): Mat {
    val width = input.cols()
    val height = input.rows()
    val channels = input.channels()
    val chunkRows = (maxArrayBytes / (width * channels * 4)).coerceIn(1, height)

    val inputFloat = Mat()
    input.convertTo(inputFloat, CvType.CV_32FC3)
    val output = Mat(height, width, CvType.CV_8UC3)

    for (startRow in 0 until height step chunkRows) {
        val endRow = minOf(startRow + chunkRows, height)
        val rowCount = endRow - startRow
        val rowData = FloatArray(rowCount * width * channels)

        inputFloat.rowRange(startRow, endRow).get(0, 0, rowData)
        val outData = processChunk(rowData)

        val chunkMat = Mat(rowCount, width, CvType.CV_32FC3)
        chunkMat.put(0, 0, outData)
        val chunkByte = Mat()
        chunkMat.convertTo(chunkByte, CvType.CV_8UC3)
        chunkMat.release()
        chunkByte.copyTo(output.rowRange(startRow, endRow))
        chunkByte.release()
    }

    inputFloat.release()
    return output
}
