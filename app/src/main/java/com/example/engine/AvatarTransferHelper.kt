package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Compresses, encodes, caches, and decodes profile avatar thumbnails for zero-lag
 * peer-to-peer transmission over local Wi-Fi/NSD without relying on device-private URIs.
 */
object AvatarTransferHelper {

    private const val MAX_AVATAR_DIM = 144

    /**
     * Copies a picked photo URI into internal storage and returns a persistent file URI + compact JPEG Base64.
     */
    fun prepareLocalAvatar(context: Context, uriString: String?): Pair<String?, String?> {
        if (uriString.isNullOrBlank()) return null to null
        val decoded = loadScaledBitmapFromUri(context, uriString) ?: return uriString to null
        try {
            val dir = File(context.filesDir, "avatars").apply { mkdirs() }
            val outFile = File(dir, "my_profile_avatar.jpg")
            FileOutputStream(outFile).use { fos ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            val baos = ByteArrayOutputStream()
            decoded.compress(Bitmap.CompressFormat.JPEG, 76, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            return Uri.fromFile(outFile).toString() to base64
        } catch (_: Exception) {
            return uriString to null
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    /**
     * Persists a remote peer's Base64 avatar locally so it remains visible offline and across sessions.
     */
    fun savePeerAvatarFromBase64(context: Context, peerId: String, avatarBase64: String?): String? {
        if (avatarBase64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val safeName = peerId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val dir = File(context.filesDir, "avatars").apply { mkdirs() }
            val outFile = File(dir, "peer_$safeName.jpg")
            FileOutputStream(outFile).use { fos ->
                fos.write(bytes)
            }
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes an avatar Bitmap from Base64 first (for peer devices) and falls back to local URI.
     */
    fun decodeAvatarBitmap(context: Context, avatarUri: String?, avatarBase64: String?): Bitmap? {
        if (!avatarBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                if (bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) return bmp
                }
            } catch (_: Exception) {}
        }
        if (!avatarUri.isNullOrBlank()) {
            return loadScaledBitmapFromUri(context, avatarUri)
        }
        return null
    }

    private fun loadScaledBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" && uri.path != null) {
                val f = File(uri.path!!)
                if (!f.exists()) return null
            }

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            var inSample = 1
            val maxRaw = max(boundsOpts.outWidth, boundsOpts.outHeight)
            if (maxRaw > MAX_AVATAR_DIM) {
                while (maxRaw / (inSample * 2) >= MAX_AVATAR_DIM) {
                    inSample *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val w = raw.width
            val h = raw.height
            if (w <= MAX_AVATAR_DIM && h <= MAX_AVATAR_DIM) {
                return raw
            }
            val scale = min(MAX_AVATAR_DIM.toFloat() / w, MAX_AVATAR_DIM.toFloat() / h)
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
            if (scaled != raw && !raw.isRecycled) raw.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }
}
