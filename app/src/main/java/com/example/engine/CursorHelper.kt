package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Robust loader and LAN transfer encoder for custom tablet cursors:
 * Supports .png, .webp, .jpg, Windows .cur (cursor), and Windows .ani (animated cursor).
 */
object CursorHelper {

    fun loadCursorBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            decodeCursorBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadCursorBitmapFromFile(filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            decodeCursorBytes(file.readBytes())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveCursorToInternalStorage(context: Context, uri: Uri): String? {
        var bmp: Bitmap? = null
        return try {
            bmp = loadCursorBitmap(context, uri) ?: return null
            val cursorFile = File(context.filesDir, "custom_cursor_${System.currentTimeMillis()}.png")
            FileOutputStream(cursorFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            cursorFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }

    /**
     * Encodes a saved custom cursor file (.cur/.ani/.png) to a compact PNG Base64 string
     * (max 64x64 preserving aspect ratio and alpha) for fast LAN peer synchronization.
     */
    fun encodeCursorFileToBase64(filePath: String?, maxDim: Int = 64): String? {
        if (filePath.isNullOrBlank()) return null
        val bmp = loadCursorBitmapFromFile(filePath) ?: return null
        return try {
            encodeCursorBitmapToBase64(bmp, maxDim)
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun encodeCursorBitmapToBase64(bitmap: Bitmap, maxDim: Int = 64): String? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        var scaled: Bitmap? = null
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val targetBmp = if (w > maxDim || h > maxDim) {
                val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
                val tw = (w * scale).toInt().coerceAtLeast(1)
                val th = (h * scale).toInt().coerceAtLeast(1)
                scaled = Bitmap.createScaledBitmap(bitmap, tw, th, true)
                scaled
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            targetBmp.compress(Bitmap.CompressFormat.PNG, 95, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        } finally {
            if (scaled != null && scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    fun decodeCursorFromBase64(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) null else decodeCursorBytes(bytes)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeCursorBytes(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null

        // 1. Direct decode with safe max dimension bounds (prevents 4K cursor OOM)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            var sample = 1
            while (boundsOpts.outWidth / sample > 256 || boundsOpts.outHeight / sample > 256) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val direct = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            if (direct != null) return direct
        }

        // 2. Check for PNG embedded anywhere (e.g. in .cur or .ani)
        val pngIndex = findSequence(bytes, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        if (pngIndex != -1) {
            val pngBmp = BitmapFactory.decodeByteArray(bytes, pngIndex, bytes.size - pngIndex)
            if (pngBmp != null) return pngBmp
        }

        // 3. Parse Windows .cur / .ico header
        // Header: 2 bytes reserved (0), 2 bytes type (2 for cur, 1 for ico), 2 bytes count (>= 1)
        if (bytes.size > 22 && bytes[0].toInt() == 0 && bytes[1].toInt() == 0 &&
            (bytes[2].toInt() == 2 || bytes[2].toInt() == 1)
        ) {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.getShort(4).toInt()
            if (count > 0) {
                // First icon entry starts at offset 6:
                // byte width (offset 6), byte height (offset 7), ...
                val imageSize = buffer.getInt(14)
                val imageOffset = buffer.getInt(18)

                if (imageOffset >= 0 && imageOffset + 40 <= bytes.size) {
                    val candidate = BitmapFactory.decodeByteArray(bytes, imageOffset, bytes.size - imageOffset)
                    if (candidate != null) return candidate

                    // If DIB (BITMAPINFOHEADER): construct BMP by prepending 14-byte BITMAPFILEHEADER
                    try {
                        val dibBmp = decodeDibWithHeader(bytes, imageOffset)
                        if (dibBmp != null) return dibBmp
                    } catch (ignored: Exception) {}
                }
            }
        }

        // 4. Windows .ani parser: searches for 'icon' or DIB chunk inside RIFF
        if (bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            val iconHeader = byteArrayOf('i'.code.toByte(), 'c'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte())
            val iconIndex = findSequence(bytes, iconHeader)
            if (iconIndex != -1 && iconIndex + 8 < bytes.size) {
                val chunkSize = ByteBuffer.wrap(bytes, iconIndex + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val subBytes = bytes.copyOfRange(iconIndex + 8, (iconIndex + 8 + chunkSize).coerceAtMost(bytes.size))
                return decodeCursorBytes(subBytes)
            }
        }

        return null
    }

    private fun decodeDibWithHeader(bytes: ByteArray, dibOffset: Int): Bitmap? {
        val buffer = ByteBuffer.wrap(bytes, dibOffset, 40).order(ByteOrder.LITTLE_ENDIAN)
        val biSize = buffer.getInt(0)
        val biWidth = buffer.getInt(4)
        val biHeight = buffer.getInt(8) / 2 // ICO/CUR DIB masks double the height
        val biPlanes = buffer.getShort(12)
        val biBitCount = buffer.getShort(14)

        val fileHeaderSize = 14
        val totalSize = fileHeaderSize + (bytes.size - dibOffset)
        val bmpData = ByteArray(totalSize)

        // BM header
        bmpData[0] = 'B'.code.toByte()
        bmpData[1] = 'M'.code.toByte()
        val outBuf = ByteBuffer.wrap(bmpData).order(ByteOrder.LITTLE_ENDIAN)
        outBuf.putInt(2, totalSize)
        outBuf.putInt(6, 0) // reserved
        val offsetToBits = fileHeaderSize + biSize
        outBuf.putInt(10, offsetToBits)

        // Copy DIB header with adjusted single height
        System.arraycopy(bytes, dibOffset, bmpData, fileHeaderSize, bytes.size - dibOffset)
        val dibHeaderBuf = ByteBuffer.wrap(bmpData, fileHeaderSize, 40).order(ByteOrder.LITTLE_ENDIAN)
        dibHeaderBuf.putInt(8, biHeight)

        return BitmapFactory.decodeByteArray(bmpData, 0, bmpData.size)
    }

    private fun findSequence(data: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1
        for (i in 0..data.size - pattern.size) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
