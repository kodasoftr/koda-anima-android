package com.example.engine

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.max

/**
 * High-performance animated GIF encoder implemented in zero-boxing Kotlin for Android.
 * Reuses pixel buffers, primitive open-addressed color lookup tables, and LZW hash tables
 * across frames to prevent GC pauses on ARMv7-a / ARM64-v8a devices.
 */
class GifEncoder {
    private var width: Int = 0
    private var height: Int = 0
    private var delayMs: Int = 100
    private var repeatCount: Int = 0
    private var isFirstFrame: Boolean = true

    // Reusable LZW tables
    private val hTab = IntArray(5003)
    private val codeTab = IntArray(5003)
    private val packetBuf = ByteArray(256)

    fun setDelay(fps: Int) {
        val delayHundredths = max(1, 100 / max(1, fps))
        this.delayMs = delayHundredths * 10
    }

    fun setRepeat(count: Int) {
        this.repeatCount = count
    }

    fun encode(bitmaps: List<Bitmap>, fps: Int): ByteArray {
        val out = ByteArrayOutputStream()
        setDelay(fps)
        isFirstFrame = true
        if (bitmaps.isEmpty()) return ByteArray(0)

        val w = bitmaps[0].width
        val h = bitmaps[0].height
        val pixels = IntArray(w * h)
        val indexedPixels = ByteArray(w * h)
        val colorTable = ByteArray(256 * 3)

        for (bitmap in bitmaps) {
            addFrame(bitmap, out, pixels, indexedPixels, colorTable)
        }
        finish(out)
        return out.toByteArray()
    }

    /**
     * Streams frames with hold counts without duplicating Bitmap allocations in memory.
     */
    fun encodeFramesWithHolds(frames: List<Pair<Bitmap, Int>>, fps: Int): ByteArray {
        val out = ByteArrayOutputStream()
        setDelay(fps)
        isFirstFrame = true
        if (frames.isEmpty()) return ByteArray(0)

        val w = frames[0].first.width
        val h = frames[0].first.height
        val pixels = IntArray(w * h)
        val indexedPixels = ByteArray(w * h)
        val colorTable = ByteArray(256 * 3)

        for ((bitmap, holdCount) in frames) {
            val baseDelay = delayMs
            delayMs = max(10, baseDelay * max(1, holdCount))
            addFrame(bitmap, out, pixels, indexedPixels, colorTable)
            delayMs = baseDelay
        }
        finish(out)
        return out.toByteArray()
    }

    private fun addFrame(
        bitmap: Bitmap,
        out: OutputStream,
        reusablePixels: IntArray,
        reusableIndexed: ByteArray,
        reusableColorTable: ByteArray
    ) {
        val w = bitmap.width
        val h = bitmap.height

        if (isFirstFrame) {
            this.width = w
            this.height = h
            writeHeader(out)
            writeLogicalScreenDescriptor(out)
            writeNetscapeExtension(out)
        }

        val pixels = if (reusablePixels.size >= w * h) reusablePixels else IntArray(w * h)
        val indexed = if (reusableIndexed.size >= w * h) reusableIndexed else ByteArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val transparentIndex = quantizePixels(pixels, w * h, indexed, reusableColorTable)
        writeGraphicControlExtension(out, transparentIndex)
        writeImageDescriptor(out, w, h, reusableColorTable)
        writeLzwData(indexed, w * h, 256, out)

        isFirstFrame = false
    }

    private fun writeHeader(out: OutputStream) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(out: OutputStream) {
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x70)
        out.write(0)
        out.write(0)
    }

    private fun writeNetscapeExtension(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, repeatCount)
        out.write(0)
    }

    private fun writeGraphicControlExtension(out: OutputStream, transparentIndex: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)

        val delayHundredths = max(1, delayMs / 10)
        var packed = 0x04
        if (transparentIndex >= 0) {
            packed = packed or 0x01
        }
        out.write(packed)
        writeShort(out, delayHundredths)
        out.write(if (transparentIndex >= 0) transparentIndex else 0)
        out.write(0)
    }

    private fun writeImageDescriptor(out: OutputStream, w: Int, h: Int, colorTable: ByteArray) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, w)
        writeShort(out, h)
        out.write(0x87)
        out.write(colorTable)
    }

    private fun writeLzwData(pixels: ByteArray, pixelCount: Int, colorCount: Int, out: OutputStream) {
        val initCodeSize = max(2, colorCountToBits(colorCount))
        out.write(initCodeSize)
        compressLzw(pixels, pixelCount, initCodeSize, out)
        out.write(0)
    }

    private fun finish(out: OutputStream) {
        out.write(0x3B)
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun colorCountToBits(count: Int): Int {
        var bits = 1
        while ((1 shl bits) < count) bits++
        return bits
    }

    /**
     * Zero-boxing primitive color quantizer using a 15-bit RGB LUT (32KB) for instant O(1) mapping.
     */
    private fun quantizePixels(
        pixels: IntArray,
        pixelCount: Int,
        outIndexed: ByteArray,
        outColorTable: ByteArray
    ): Int {
        outColorTable.fill(0)
        var hasTransparency = false
        for (i in 0 until pixelCount) {
            if (((pixels[i] ushr 24) and 0xFF) < 128) {
                hasTransparency = true
                break
            }
        }

        val startIdx = if (hasTransparency) 1 else 0
        val transparentIndex = if (hasTransparency) 0 else -1

        // 15-bit quantized color -> palette index (-1 = unmapped)
        val lut15 = IntArray(32768) { -1 }
        val uniqueColors = IntArray(256)
        var uniqueCount = 0

        val step = max(1, pixelCount / 2500)
        var sampleIdx = 0
        while (sampleIdx < pixelCount) {
            val p = pixels[sampleIdx]
            if (((p ushr 24) and 0xFF) >= 128) {
                val r5 = (p ushr 19) and 0x1F
                val g5 = (p ushr 11) and 0x1F
                val b5 = (p ushr 3) and 0x1F
                val key = (r5 shl 10) or (g5 shl 5) or b5
                if (lut15[key] == -1 && uniqueCount < (256 - startIdx)) {
                    val paletteIdx = uniqueCount + startIdx
                    lut15[key] = paletteIdx
                    uniqueColors[uniqueCount] = ((r5 shl 3) shl 16) or ((g5 shl 3) shl 8) or (b5 shl 3)
                    uniqueCount++
                }
            }
            sampleIdx += step
        }

        if (uniqueCount == 0) {
            uniqueColors[0] = 0xFFFFFF
            lut15[(0x1F shl 10) or (0x1F shl 5) or 0x1F] = startIdx
            uniqueCount = 1
        }

        for (i in 0 until uniqueCount) {
            val c = uniqueColors[i]
            val idx = (i + startIdx) * 3
            outColorTable[idx] = ((c ushr 16) and 0xFF).toByte()
            outColorTable[idx + 1] = ((c ushr 8) and 0xFF).toByte()
            outColorTable[idx + 2] = (c and 0xFF).toByte()
        }

        for (i in 0 until pixelCount) {
            val p = pixels[i]
            if (hasTransparency && ((p ushr 24) and 0xFF) < 128) {
                outIndexed[i] = 0
                continue
            }
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val key = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
            var mapped = lut15[key]
            if (mapped == -1) {
                var closestDist = Int.MAX_VALUE
                var closestIdx = startIdx
                val endIdx = startIdx + uniqueCount
                for (cIdx in startIdx until endIdx) {
                    val tBase = cIdx * 3
                    val dr = r - (outColorTable[tBase].toInt() and 0xFF)
                    val dg = g - (outColorTable[tBase + 1].toInt() and 0xFF)
                    val db = b - (outColorTable[tBase + 2].toInt() and 0xFF)
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < closestDist) {
                        closestDist = dist
                        closestIdx = cIdx
                    }
                }
                lut15[key] = closestIdx
                mapped = closestIdx
            }
            outIndexed[i] = mapped.toByte()
        }

        return transparentIndex
    }

    private fun compressLzw(pixels: ByteArray, pixelCount: Int, initCodeSize: Int, out: OutputStream) {
        val clearCode = 1 shl initCodeSize
        val eofCode = clearCode + 1
        var codeSize = initCodeSize + 1
        var maxCode = (1 shl codeSize) - 1
        var bufIndex = 0
        var curAccum = 0
        var curBits = 0

        fun flushPacket() {
            if (bufIndex > 0) {
                out.write(bufIndex)
                out.write(packetBuf, 0, bufIndex)
                bufIndex = 0
            }
        }

        fun charOut(c: Byte) {
            packetBuf[bufIndex++] = c
            if (bufIndex >= 254) flushPacket()
        }

        fun output(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte())
                curAccum = curAccum ushr 8
                curBits -= 8
            }
            if (code > maxCode && codeSize < 12) {
                codeSize++
                maxCode = (1 shl codeSize) - 1
            }
        }

        hTab.fill(-1)
        output(clearCode)
        var ent = if (pixelCount > 0) pixels[0].toInt() and 0xFF else -1
        var freeEnt = clearCode + 2

        for (i in 1 until pixelCount) {
            val c = pixels[i].toInt() and 0xFF
            val fcode = (c shl 12) + ent
            var idx = (c shl 4) xor ent
            if (idx >= 5003) idx %= 5003

            if (hTab[idx] == fcode) {
                ent = codeTab[idx]
                continue
            } else if (hTab[idx] >= 0) {
                val disp = if (idx == 0) 1 else 5003 - idx
                var found = false
                while (true) {
                    idx -= disp
                    if (idx < 0) idx += 5003
                    if (hTab[idx] == fcode) {
                        ent = codeTab[idx]
                        found = true
                        break
                    }
                    if (hTab[idx] < 0) break
                }
                if (found) continue
            }

            output(ent)
            ent = c
            if (freeEnt < 4096) {
                codeTab[idx] = freeEnt++
                hTab[idx] = fcode
            } else {
                hTab.fill(-1)
                freeEnt = clearCode + 2
                output(clearCode)
                codeSize = initCodeSize + 1
                maxCode = (1 shl codeSize) - 1
            }
        }

        if (ent != -1) output(ent)
        output(eofCode)
        if (curBits > 0) {
            charOut((curAccum and 0xFF).toByte())
        }
        flushPacket()
    }
}
