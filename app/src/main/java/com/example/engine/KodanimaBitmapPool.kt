package com.example.engine

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Color
import java.util.LinkedHashMap

/**
 * Thread-safe LRU Bitmap Pool to eliminate native heap churn, GC pauses,
 * and virtual address fragmentation (critical on 32-bit ARMv7-a & low-RAM devices).
 */
object KodanimaBitmapPool {

    private data class PoolKey(
        val width: Int,
        val height: Int,
        val config: Bitmap.Config
    )

    private var maxPoolBytes: Int = 32 * 1024 * 1024
    private var currentBytes: Int = 0
    private val pool = LinkedHashMap<PoolKey, ArrayDeque<Bitmap>>(16, 0.75f, true)

    @Synchronized
    fun configureMaxBytes(bytes: Int) {
        maxPoolBytes = bytes.coerceAtLeast(4 * 1024 * 1024)
        trimToSize(maxPoolBytes)
    }

    @Synchronized
    fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        clearColor: Int? = Color.TRANSPARENT
    ): Bitmap {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val key = PoolKey(safeW, safeH, config)
        val bucket = pool[key]
        while (bucket != null && bucket.isNotEmpty()) {
            val candidate = bucket.removeLast()
            val bytes = candidate.allocationByteCount
            currentBytes = (currentBytes - bytes).coerceAtLeast(0)
            if (!candidate.isRecycled && candidate.isMutable) {
                if (clearColor != null) {
                    candidate.eraseColor(clearColor)
                }
                return candidate
            }
        }
        val fresh = Bitmap.createBitmap(safeW, safeH, config)
        if (clearColor != null && clearColor != Color.TRANSPARENT) {
            fresh.eraseColor(clearColor)
        }
        return fresh
    }

    @Synchronized
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (!bitmap.isMutable) {
            bitmap.recycle()
            return
        }
        val byteCount = bitmap.allocationByteCount
        if (byteCount <= 0 || byteCount > maxPoolBytes / 2) {
            bitmap.recycle()
            return
        }
        val cfg = bitmap.config ?: Bitmap.Config.ARGB_8888
        val key = PoolKey(bitmap.width, bitmap.height, cfg)
        val bucket = pool.getOrPut(key) { ArrayDeque(4) }
        if (bucket.size >= 4) {
            bitmap.recycle()
            return
        }
        bucket.addLast(bitmap)
        currentBytes += byteCount
        trimToSize(maxPoolBytes)
    }

    @Synchronized
    fun trimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> trimToSize(maxPoolBytes / 4)
            else -> trimToSize(maxPoolBytes / 2)
        }
    }

    @Synchronized
    fun clear() {
        for (bucket in pool.values) {
            while (bucket.isNotEmpty()) {
                val bmp = bucket.removeFirst()
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        pool.clear()
        currentBytes = 0
    }

    private fun trimToSize(targetBytes: Int) {
        if (currentBytes <= targetBytes) return
        val iterator = pool.entries.iterator()
        while (currentBytes > targetBytes && iterator.hasNext()) {
            val entry = iterator.next()
            val bucket = entry.value
            while (currentBytes > targetBytes && bucket.isNotEmpty()) {
                val victim = bucket.removeFirst()
                currentBytes = (currentBytes - victim.allocationByteCount).coerceAtLeast(0)
                if (!victim.isRecycled) victim.recycle()
            }
            if (bucket.isEmpty()) {
                iterator.remove()
            }
        }
    }
}
