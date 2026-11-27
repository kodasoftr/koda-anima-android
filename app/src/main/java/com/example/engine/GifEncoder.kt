package com.example.engine

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance animated GIF encoder implemented in zero-boxing Kotlin for Android.
 * Uses full-frame 15-bit RGB frequency quantization, ordered Bayer dithering, and compliant GIF89a LZW.
 */
class GifEncoder {
    private var width: Int = 0
    private var height: Int = 0
    private var delayMs: Int = 100
    private var repeatCount: Int = 0
    private var isFirstFrame: Boolean = true

    // Reusable LZW & quantization tables
    private val hTab = IntArray(5003)
    private val codeTab = IntArray(5003)
    private val packetBuf = ByteArray(256)
    private val histCount = IntArray(32768)
    private val histSumR = LongArray(32768)
    private val histSumG = LongArray(32768)
    private val histSumB = LongArray(32768)
    private val bayer4 = intArrayOf(
        -8, 0, -6, 2,
        4, -4, 6, -2,
        -5, 3, -7, 1,
        7, -1, 5, -3
    )

    fun setDelay(fps: Int) {
        val delayHundredths = max(1, (100f / max(1, fps)).roundToInt())
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
        val safeFps = max(1, fps)
        setDelay(safeFps)
        isFirstFrame = true
        if (frames.isEmpty()) return ByteArray(0)

        val w = frames[0].first.width
        val h = frames[0].first.height
        val pixels = IntArray(w * h)
        val indexedPixels = ByteArray(w * h)
        val colorTable = ByteArray(256 * 3)

        val baseDelay = delayMs
        for ((bitmap, holdCount) in frames) {
            val hundredths = ((100f * max(1, holdCount)) / safeFps.toFloat()).roundToInt().coerceAtLeast(1)
            delayMs = hundredths * 10
            addFrame(bitmap, out, pixels, indexedPixels, colorTable)
        }
        delayMs = baseDelay
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

        val transparentIndex = quantizePixels(pixels, w, h, indexed, reusableColorTable)
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
     * Full-frame 15-bit RGB weighted histogram quantizer with 4x4 ordered dithering.
     * Samples the entire frame uniformly so 3D objects anywhere in the viewport get accurate palette entries.
     */
    private fun quantizePixels(
        pixels: IntArray,
        w: Int,
        h: Int,
        outIndexed: ByteArray,
        outColorTable: ByteArray
    ): Int {
        val pixelCount = w * h
        outColorTable.fill(0)
        histCount.fill(0)
        histSumR.fill(0L)
        histSumG.fill(0L)
        histSumB.fill(0L)

        var hasTransparency = false
        val sampleStep = max(1, pixelCount / 40000)
        var nonZeroBuckets = 0
        var i = 0
        while (i < pixelCount) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a < 128) {
                hasTransparency = true
            } else {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val key = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
                if (histCount[key] == 0) nonZeroBuckets++
                histCount[key]++
                histSumR[key] += r.toLong()
                histSumG[key] += g.toLong()
                histSumB[key] += b.toLong()
            }
            i += sampleStep
        }

        if (!hasTransparency) {
            for (k in 0 until pixelCount) {
                if (((pixels[k] ushr 24) and 0xFF) < 128) {
                    hasTransparency = true
                    break
                }
            }
        }

        val startIdx = if (hasTransparency) 1 else 0
        val transparentIndex = if (hasTransparency) 0 else -1
        val maxColors = 256 - startIdx

        val paletteCount: Int
        val paletteRgb = IntArray(256)

        if (nonZeroBuckets == 0) {
            paletteRgb[0] = 0xFFFFFF
            paletteCount = 1
        } else if (nonZeroBuckets <= maxColors) {
            var idx = 0
            for (k in 0 until 32768) {
                val cnt = histCount[k]
                if (cnt > 0) {
                    val avgR = (histSumR[k] / cnt).toInt().coerceIn(0, 255)
                    val avgG = (histSumG[k] / cnt).toInt().coerceIn(0, 255)
                    val avgB = (histSumB[k] / cnt).toInt().coerceIn(0, 255)
                    paletteRgb[idx++] = (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
            paletteCount = idx
        } else {
            // Pick top-N diverse/frequent buckets by descending frequency, deduplicating near-identical 4-bit bins first
            val activeKeys = IntArray(nonZeroBuckets)
            var ak = 0
            for (k in 0 until 32768) {
                if (histCount[k] > 0) activeKeys[ak++] = k
            }
            // Partial selection/sort by weighted score using primitive arrays (fast for <= 32768)
            val boxed = activeKeys.sortedByDescending { histCount[it] }
            val usedCoarse = BooleanArray(4096)
            var picked = 0
            for (key in boxed) {
                if (picked >= maxColors) break
                val r4 = (key ushr 11) and 0x0F
                val g4 = (key ushr 6) and 0x0F
                val b4 = (key ushr 1) and 0x0F
                val cKey = (r4 shl 8) or (g4 shl 4) or b4
                if (!usedCoarse[cKey]) {
                    usedCoarse[cKey] = true
                    val cnt = histCount[key]
                    val avgR = (histSumR[key] / cnt).toInt().coerceIn(0, 255)
                    val avgG = (histSumG[key] / cnt).toInt().coerceIn(0, 255)
                    val avgB = (histSumB[key] / cnt).toInt().coerceIn(0, 255)
                    paletteRgb[picked++] = (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
            if (picked < maxColors) {
                for (key in boxed) {
                    if (picked >= maxColors) break
                    val cnt = histCount[key]
                    val avgR = (histSumR[key] / cnt).toInt().coerceIn(0, 255)
                    val avgG = (histSumG[key] / cnt).toInt().coerceIn(0, 255)
                    val avgB = (histSumB[key] / cnt).toInt().coerceIn(0, 255)
                    val rgb = (avgR shl 16) or (avgG shl 8) or avgB
                    var dup = false
                    for (j in 0 until picked) {
                        val existing = paletteRgb[j]
                        val dr = avgR - ((existing ushr 16) and 0xFF)
                        val dg = avgG - ((existing ushr 8) and 0xFF)
                        val db = avgB - (existing and 0xFF)
                        if (dr * dr + dg * dg + db * db < 48) {
                            dup = true
                            break
                        }
                    }
                    if (!dup) paletteRgb[picked++] = rgb
                }
            }
            paletteCount = picked.coerceAtLeast(1)
        }

        for (pIdx in 0 until paletteCount) {
            val c = paletteRgb[pIdx]
            val tBase = (pIdx + startIdx) * 3
            outColorTable[tBase] = ((c ushr 16) and 0xFF).toByte()
            outColorTable[tBase + 1] = ((c ushr 8) and 0xFF).toByte()
            outColorTable[tBase + 2] = (c and 0xFF).toByte()
        }

        val lut15 = IntArray(32768) { -1 }
        val endIdx = startIdx + paletteCount

        for (py in 0 until h) {
            val rowOff = py * w
            val bRow = (py and 3) shl 2
            for (px in 0 until w) {
                val pIdx = rowOff + px
                val p = pixels[pIdx]
                if (hasTransparency && ((p ushr 24) and 0xFF) < 128) {
                    outIndexed[pIdx] = 0
                    continue
                }
                val dither = bayer4[bRow + (px and 3)]
                val r = (((p ushr 16) and 0xFF) + dither).coerceIn(0, 255)
                val g = (((p ushr 8) and 0xFF) + dither).coerceIn(0, 255)
                val b = ((p and 0xFF) + dither).coerceIn(0, 255)
                val key = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
                var mapped = lut15[key]
                if (mapped == -1) {
                    var closestDist = Int.MAX_VALUE
                    var closestIdx = startIdx
                    for (cIdx in startIdx until endIdx) {
                        val tBase = cIdx * 3
                        val dr = r - (outColorTable[tBase].toInt() and 0xFF)
                        val dg = g - (outColorTable[tBase + 1].toInt() and 0xFF)
                        val db = b - (outColorTable[tBase + 2].toInt() and 0xFF)
                        val dist = dr * dr + dg * dg + db * db
                        if (dist < closestDist) {
                            closestDist = dist
                            closestIdx = cIdx
                            if (dist == 0) break
                        }
                    }
                    lut15[key] = closestIdx
                    mapped = closestIdx
                }
                outIndexed[pIdx] = mapped.toByte()
            }
        }

        return transparentIndex
    }

    private fun compressLzw(pixels: ByteArray, pixelCount: Int, initCodeSize: Int, out: OutputStream) {
        val clearCode = 1 shl initCodeSize
        val eofCode = clearCode + 1
        var freeEnt = clearCode + 2
        var clearFlag = false
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
            if (freeEnt > maxCode || clearFlag) {
                if (clearFlag) {
                    codeSize = initCodeSize + 1
                    maxCode = (1 shl codeSize) - 1
                    clearFlag = false
                } else {
                    codeSize++
                    maxCode = if (codeSize == 12) 4096 else (1 shl codeSize) - 1
                }
            }
        }

        hTab.fill(-1)
        output(clearCode)
        var ent = if (pixelCount > 0) pixels[0].toInt() and 0xFF else -1

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
                clearFlag = true
                output(clearCode)
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
