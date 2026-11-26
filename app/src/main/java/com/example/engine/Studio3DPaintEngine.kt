package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import android.util.LruCache
import com.example.model.DrawingStroke
import com.example.model.Estudio3DPaintTool
import com.example.model.ObjectTexturePaintLayer
import com.example.model.ObjectUVMapSlot
import com.example.model.SceneObject3D
import com.example.model.StrokePoint
import com.example.model.ToolType
import com.example.model.UvUnwrapProjection
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TrianglePickResult(
    val hit: Boolean,
    val u: Float,
    val v: Float,
    val subMeshIndex: Int,
    val depth: Float,
    val objectId: String
)

data class DecodedTextureBuffer(
    val pixels: IntArray,
    val width: Int,
    val height: Int
)

/**
 * Native 3D Texture Painting, UV Projection, Layer Compositing & Ray-Triangle Picking Engine
 * for Kodanima Estúdio 3D ("3 Rodas").
 */
object Studio3DPaintEngine {

    private val decodedTexCache = object : LruCache<Int, DecodedTextureBuffer>(40) {}
    private val compositeTexCache = object : LruCache<Int, DecodedTextureBuffer>(24) {}

    fun clearCaches() {
        decodedTexCache.evictAll()
        compositeTexCache.evictAll()
    }

    /**
     * Decodes a Base64-encoded PNG/JPG texture into cached ARGB IntArray pixels.
     */
    fun decodePixelsFromBase64(base64: String?): DecodedTextureBuffer? {
        if (base64.isNullOrBlank()) return null
        val key = base64.hashCode() xor (base64.length * 31)
        decodedTexCache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            if (!bmp.isRecycled) bmp.recycle()
            DecodedTextureBuffer(px, w, h).also { decodedTexCache.put(key, it) }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBitmapFromBase64(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBitmapToPngBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Generates effective UV coordinates for a submesh given an ObjectUVMapSlot projection & tiling.
     */
    fun generateUVsForProjection(
        vertices: FloatArray,
        existingUvs: FloatArray,
        slot: ObjectUVMapSlot
    ): FloatArray {
        val vCount = vertices.size / 3
        if (vCount <= 0) return FloatArray(0)
        val out = FloatArray(vCount * 2)

        if (slot.projection == UvUnwrapProjection.ORIGINAL) {
            val hasOrig = existingUvs.size >= vCount * 2
            for (i in 0 until vCount) {
                val u0 = if (hasOrig) existingUvs[i * 2] else 0.5f
                val v0 = if (hasOrig) existingUvs[i * 2 + 1] else 0.5f
                out[i * 2] = u0 * slot.scaleU + slot.offsetU
                out[i * 2 + 1] = v0 * slot.scaleV + slot.offsetV
            }
            return out
        }

        // Compute normalized local bounds for projection unwrap
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until vCount) {
            val x = vertices[i * 3]
            val y = vertices[i * 3 + 1]
            val z = vertices[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val spanX = (maxX - minX).coerceAtLeast(1e-4f)
        val spanY = (maxY - minY).coerceAtLeast(1e-4f)
        val spanZ = (maxZ - minZ).coerceAtLeast(1e-4f)
        val maxSpan = max(spanX, max(spanY, spanZ)).coerceAtLeast(1e-4f)

        for (i in 0 until vCount) {
            val x = vertices[i * 3]
            val y = vertices[i * 3 + 1]
            val z = vertices[i * 3 + 2]
            val nx = (x - cx) / maxSpan
            val ny = (y - cy) / maxSpan
            val nz = (z - cz) / maxSpan

            var u = 0f
            var v = 0f
            when (slot.projection) {
                UvUnwrapProjection.PLANAR -> {
                    u = (x - minX) / spanX
                    v = (y - minY) / spanY
                }
                UvUnwrapProjection.BOX -> {
                    val ax = abs(nx)
                    val ay = abs(ny)
                    val az = abs(nz)
                    when {
                        ax >= ay && ax >= az -> {
                            u = (z - minZ) / spanZ
                            v = (y - minY) / spanY
                        }
                        ay >= ax && ay >= az -> {
                            u = (x - minX) / spanX
                            v = (z - minZ) / spanZ
                        }
                        else -> {
                            u = (x - minX) / spanX
                            v = (y - minY) / spanY
                        }
                    }
                }
                UvUnwrapProjection.ESFERICO -> {
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
                    val ux = nx / len
                    val uy = ny / len
                    val uz = nz / len
                    u = 0.5f + atan2(uz.toDouble(), ux.toDouble()).toFloat() / (2f * PI.toFloat())
                    v = 0.5f + kotlin.math.asin(uy.coerceIn(-1f, 1f).toDouble()).toFloat() / PI.toFloat()
                }
                UvUnwrapProjection.CILINDRICO -> {
                    u = 0.5f + atan2(nz.toDouble(), nx.toDouble()).toFloat() / (2f * PI.toFloat())
                    v = (y - minY) / spanY
                }
                UvUnwrapProjection.ORIGINAL -> Unit
            }
            out[i * 2] = u * slot.scaleU + slot.offsetU
            out[i * 2 + 1] = v * slot.scaleV + slot.offsetV
        }
        return out
    }

    /**
     * Exact ray / screen-point triangle intersection against a SceneObject3D using the same
     * vertex transform and camera projection pipeline as Camera3DEngine.renderSceneObjectsSoftware3D.
     */
    fun pickObjectTriangleUV(
        obj: SceneObject3D,
        frameIndex: Int,
        autoInterpolate: Boolean,
        screenX: Float,
        screenY: Float,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        vpCx: Float,
        vpCy: Float,
        worldUnitScale: Float,
        tickFloat: Float = frameIndex.toFloat(),
        animTimeSec: Float = 0f
    ): TrianglePickResult? {
        if (!obj.isVisible || obj.meshAssetPath.isBlank()) return null
        val meshData = Mesh3DImporter.getOrLoadMesh(obj.meshAssetPath) ?: return null
        val objTf = Camera3DEngine.evaluateObjectTransformAtTick(obj.keyframes, tickFloat, autoInterpolate)
        if (objTf.opacity <= 0.01f) return null

        val oxRad = Math.toRadians(objTf.rotX.toDouble())
        val oyRad = Math.toRadians(objTf.rotY.toDouble())
        val ozRad = Math.toRadians(objTf.rotZ.toDouble())
        val cox = cos(oxRad).toFloat(); val sox = sin(oxRad).toFloat()
        val coy = cos(oyRad).toFloat(); val soy = sin(oyRad).toFloat()
        val coz = cos(ozRad).toFloat(); val soz = sin(ozRad).toFloat()

        val posX = objTf.x * worldUnitScale
        val posY = objTf.y * worldUnitScale
        val posZ = objTf.z * worldUnitScale
        val sx = objTf.scaleX * worldUnitScale
        val sy = objTf.scaleY * worldUnitScale
        val sz = objTf.scaleZ * worldUnitScale

        val yawRad = Math.toRadians(camYawDeg.toDouble())
        val pitchRad = Math.toRadians(camPitchDeg.toDouble())
        val rollRad = Math.toRadians(camRollDeg.toDouble())
        val cosY = cos(yawRad).toFloat(); val sinY = sin(yawRad).toFloat()
        val cosP = cos(pitchRad).toFloat(); val sinP = sin(pitchRad).toFloat()
        val cosR = cos(rollRad).toFloat(); val sinR = sin(rollRad).toFloat()
        val camDist = focalPx.coerceAtLeast(240f)

        val activeSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex) ?: obj.uvSlots.firstOrNull() ?: ObjectUVMapSlot()
        val activeSubMeshes = Mesh3DImporter.evaluateSceneAtTime(meshData, animTimeSec)

        var bestDepth = Float.MAX_VALUE
        var bestResult: TrianglePickResult? = null

        for (smIdx in activeSubMeshes.indices) {
            val sm = activeSubMeshes[smIdx]
            val vCount = sm.vertices.size / 3
            if (vCount < 3 || sm.indices.size < 3) continue
            val verts = sm.vertices
            val projX = FloatArray(vCount)
            val projY = FloatArray(vCount)
            val invZ = FloatArray(vCount)
            val clipZ = FloatArray(vCount)

            for (vi in 0 until vCount) {
                val off = vi * 3
                val vx0 = verts[off] * sx
                val vy0 = verts[off + 1] * sy
                val vz0 = verts[off + 2] * sz

                val vy1 = vy0 * cox - vz0 * sox
                val vz1 = vy0 * sox + vz0 * cox
                val vx2 = vx0 * coy + vz1 * soy
                val vz2 = -vx0 * soy + vz1 * coy
                val vx3 = vx2 * coz - vy1 * soz
                val vy3 = vx2 * soz + vy1 * coz

                val wx = vx3 + posX - camPanX
                val wy = vy3 + posY - camPanY
                val wz = vz2 + posZ

                val rx = wx * cosY - wz * sinY
                val rz = wx * sinY + wz * cosY
                val ry = wy * cosP - rz * sinP
                val rz2 = wy * sinP + rz * cosP

                val cz = camDist + rz2
                clipZ[vi] = cz
                val depth = cz.coerceAtLeast(24f)
                val iz = 1f / depth
                val pScale = camDist * iz
                val sx2d = rx * pScale
                val sy2d = ry * pScale
                projX[vi] = vpCx + (sx2d * cosR - sy2d * sinR)
                projY[vi] = vpCy + (sx2d * sinR + sy2d * cosR)
                invZ[vi] = iz
            }

            val effUvs = generateUVsForProjection(verts, sm.uvs, activeSlot)
            val indices = sm.indices
            var tri = 0
            while (tri + 2 < indices.size) {
                val i0 = indices[tri]
                val i1 = indices[tri + 1]
                val i2 = indices[tri + 2]
                tri += 3
                if (i0 !in 0 until vCount || i1 !in 0 until vCount || i2 !in 0 until vCount) continue
                if (clipZ[i0] < 24f || clipZ[i1] < 24f || clipZ[i2] < 24f) continue

                val x0 = projX[i0]; val y0 = projY[i0]
                val x1 = projX[i1]; val y1 = projY[i1]
                val x2 = projX[i2]; val y2 = projY[i2]

                val minX = min(x0, min(x1, x2))
                val maxX = max(x0, max(x1, x2))
                if (screenX < minX || screenX > maxX) continue
                val minY = min(y0, min(y1, y2))
                val maxY = max(y0, max(y1, y2))
                if (screenY < minY || screenY > maxY) continue

                val area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
                if (abs(area) < 0.25f) continue
                val sgn = if (area < 0f) -1f else 1f
                val w0 = ((x1 - screenX) * (y2 - screenY) - (y1 - screenY) * (x2 - screenX)) * sgn
                val w1 = ((x2 - screenX) * (y0 - screenY) - (y2 - screenY) * (x0 - screenX)) * sgn
                val w2 = ((x0 - screenX) * (y1 - screenY) - (y0 - screenY) * (x1 - screenX)) * sgn
                if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                    val absInv = 1f / abs(area)
                    val b0 = w0 * absInv
                    val b1 = w1 * absInv
                    val b2 = w2 * absInv
                    val iz = b0 * invZ[i0] + b1 * invZ[i1] + b2 * invZ[i2]
                    if (iz > 1e-6f) {
                        val depth = 1f / iz
                        if (depth < bestDepth) {
                            val u0 = effUvs[i0 * 2] * invZ[i0]
                            val v0 = effUvs[i0 * 2 + 1] * invZ[i0]
                            val u1 = effUvs[i1 * 2] * invZ[i1]
                            val v1 = effUvs[i1 * 2 + 1] * invZ[i1]
                            val u2 = effUvs[i2 * 2] * invZ[i2]
                            val v2 = effUvs[i2 * 2 + 1] * invZ[i2]
                            val rawU = (b0 * u0 + b1 * u1 + b2 * u2) * depth
                            val rawV = (b0 * v0 + b1 * v1 + b2 * v2) * depth
                            var wu = rawU - floor(rawU)
                            var wv = rawV - floor(rawV)
                            if (wu < 0f) wu += 1f
                            if (wv < 0f) wv += 1f
                            bestDepth = depth
                            bestResult = TrianglePickResult(
                                hit = true,
                                u = wu.coerceIn(0f, 1f),
                                v = wv.coerceIn(0f, 1f),
                                subMeshIndex = smIdx,
                                depth = depth,
                                objectId = obj.id
                            )
                        }
                    }
                }
            }
        }
        return bestResult
    }

    /**
     * Paints a brush, eraser, or wet-smudge (Molhador) stroke segment onto [layer] in normalized [0..1] UV space.
     * Supports all 12 native brushes and imported Krita/Kodanima brushes seamlessly.
     */
    fun paintStrokeSegmentOnLayer(
        layer: ObjectTexturePaintLayer,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        tool: Estudio3DPaintTool,
        colorArgb: Long,
        brushSizePx: Float,
        opacity: Float,
        brushPresetId: Int = 1,
        customBrushParams: String? = null
    ): ObjectTexturePaintLayer {
        val w = layer.texWidth.coerceIn(64, 1024)
        val h = layer.texHeight.coerceIn(64, 1024)
        val bmp = decodeBitmapFromBase64(layer.pixelsBase64)?.let { decoded ->
            if (decoded.width == w && decoded.height == h && decoded.isMutable) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, w, h, true).copy(Bitmap.Config.ARGB_8888, true).also {
                    if (!decoded.isRecycled) decoded.recycle()
                }
            }
        } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val x0 = u0.coerceIn(0f, 1f) * (w - 1)
        val y0 = v0.coerceIn(0f, 1f) * (h - 1)
        val x1 = u1.coerceIn(0f, 1f) * (w - 1)
        val y1 = v1.coerceIn(0f, 1f) * (h - 1)

        when (tool) {
            Estudio3DPaintTool.MOLHADOR -> {
                applyWetSmudgeOnBitmap(
                    bitmap = bmp,
                    x0 = x0,
                    y0 = y0,
                    x1 = x1,
                    y1 = y1,
                    radiusPx = (brushSizePx * 0.65f).coerceIn(3f, 52f),
                    strength = (opacity * 0.82f).coerceIn(0.15f, 0.92f)
                )
            }
            Estudio3DPaintTool.PINCEL, Estudio3DPaintTool.BORRACHA -> {
                val canvas = Canvas(bmp)
                val toolType = if (tool == Estudio3DPaintTool.BORRACHA) ToolType.ERASER else ToolType.BRUSH
                // Avoid long jumps across UV seam edges (e.g. u wrapping 0.98 -> 0.02)
                val isSeamJump = hypot(u1 - u0, v1 - v0) > 0.42f
                val pts = if (isSeamJump) {
                    listOf(StrokePoint(x1, y1, 1.0f))
                } else {
                    listOf(StrokePoint(x0, y0, 1.0f), StrokePoint(x1, y1, 1.0f))
                }
                val stroke = DrawingStroke(
                    tool = toolType,
                    color = colorArgb,
                    brushSize = brushSizePx.coerceIn(2f, 96f),
                    opacity = opacity.coerceIn(0.05f, 1f),
                    points = pts,
                    brushPresetId = brushPresetId,
                    customBrushParams = customBrushParams
                )
                KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, canvas, 1.0f, null, 1.0f)

                // Mirror stamp lightly across horizontal UV seam boundary when painting near u=0 or u=1
                if (u1 < 0.06f || u1 > 0.94f) {
                    val shift = if (u1 < 0.06f) w.toFloat() else -w.toFloat()
                    canvas.save()
                    canvas.translate(shift, 0f)
                    KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, canvas, 1.0f, null, 1.0f)
                    canvas.restore()
                }
            }
        }

        val updatedB64 = encodeBitmapToPngBase64(bmp)
        if (!bmp.isRecycled) bmp.recycle()
        return layer.copy(pixelsBase64 = updatedB64, texWidth = w, texHeight = h)
    }

    /**
     * Wet smudge / blend tool ("Molhador"): drags and softens surrounding pigment along the stroke vector.
     */
    private fun applyWetSmudgeOnBitmap(
        bitmap: Bitmap,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPx: Float,
        strength: Float
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = hypot(dx, dy)
        val steps = max(1, (dist / max(1.5f, radiusPx * 0.35f)).roundToInt().coerceAtMost(24))
        val dirX = if (dist > 0.1f) dx / dist else 0f
        val dirY = if (dist > 0.1f) dy / dist else 0f
        val pullDist = max(2f, radiusPx * 0.45f)
        val rInt = radiusPx.roundToInt().coerceIn(2, 48)
        val rSq = radiusPx * radiusPx

        val work = pixels.clone()
        for (s in 0..steps) {
            val t = if (steps == 0) 1f else s.toFloat() / steps.toFloat()
            val cx = x0 + dx * t
            val cy = y0 + dy * t
            val minX = max(0, (cx - rInt).toInt())
            val maxX = min(w - 1, (cx + rInt).toInt())
            val minY = max(0, (cy - rInt).toInt())
            val maxY = min(h - 1, (cy + rInt).toInt())
            val sampleBaseX = cx - dirX * pullDist
            val sampleBaseY = cy - dirY * pullDist

            for (py in minY..maxY) {
                val oy = py - cy
                val oy2 = oy * oy
                val rowOff = py * w
                for (px in minX..maxX) {
                    val ox = px - cx
                    val d2 = ox * ox + oy2
                    if (d2 > rSq) continue
                    val falloff = (1f - sqrt(d2) / radiusPx).coerceIn(0f, 1f)
                    val mix = (falloff * falloff * strength).coerceIn(0f, 0.90f)
                    if (mix <= 0.01f) continue

                    val sx = (sampleBaseX + ox * 0.85f).roundToInt().coerceIn(0, w - 1)
                    val sy = (sampleBaseY + oy * 0.85f).roundToInt().coerceIn(0, h - 1)
                    // 4-tap neighbor soften around source coordinate
                    val c0 = work[sy * w + sx]
                    val c1 = work[sy * w + min(w - 1, sx + 1)]
                    val c2 = work[min(h - 1, sy + 1) * w + sx]
                    val cDst = work[rowOff + px]

                    val sA = (((c0 ushr 24) and 0xFF) * 2 + ((c1 ushr 24) and 0xFF) + ((c2 ushr 24) and 0xFF)) / 4f
                    val sR = (((c0 ushr 16) and 0xFF) * 2 + ((c1 ushr 16) and 0xFF) + ((c2 ushr 16) and 0xFF)) / 4f
                    val sG = (((c0 ushr 8) and 0xFF) * 2 + ((c1 ushr 8) and 0xFF) + ((c2 ushr 8) and 0xFF)) / 4f
                    val sB = ((c0 and 0xFF) * 2 + (c1 and 0xFF) + (c2 and 0xFF)) / 4f

                    val dA = ((cDst ushr 24) and 0xFF).toFloat()
                    val dR = ((cDst ushr 16) and 0xFF).toFloat()
                    val dG = ((cDst ushr 8) and 0xFF).toFloat()
                    val dB = (cDst and 0xFF).toFloat()

                    val outA = (dA + (sA - dA) * mix).roundToInt().coerceIn(0, 255)
                    val outR = (dR + (sR - dR) * mix).roundToInt().coerceIn(0, 255)
                    val outG = (dG + (sG - dG) * mix).roundToInt().coerceIn(0, 255)
                    val outB = (dB + (sB - dB) * mix).roundToInt().coerceIn(0, 255)
                    work[rowOff + px] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }
        bitmap.setPixels(work, 0, w, 0, 0, w, h)
    }

    /**
     * Merges base UV texture/color + baked UV + all visible paint layers (honoring opacity & blendEffect)
     * into a single flattened PNG Base64 texture ("Mesclar camadas em um UV só").
     */
    fun mergePaintLayersIntoSingleUv(
        obj: SceneObject3D,
        baseSubMesh: Mesh3DSubMesh? = null,
        texSize: Int = 512
    ): String {
        val outSize = texSize.coerceIn(128, 1024)
        val outBmp = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        val baseColor = baseSubMesh?.baseColorArgb ?: obj.colorTint.toInt()
        val activeSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex) ?: obj.uvSlots.firstOrNull()
        val bakedBuf = decodePixelsFromBase64(obj.bakedTextureBase64)
        val slotBuf = decodePixelsFromBase64(activeSlot?.textureBase64)

        when {
            bakedBuf != null -> {
                val bBmp = Bitmap.createBitmap(bakedBuf.width, bakedBuf.height, Bitmap.Config.ARGB_8888)
                bBmp.setPixels(bakedBuf.pixels, 0, bakedBuf.width, 0, 0, bakedBuf.width, bakedBuf.height)
                canvas.drawBitmap(bBmp, null, Rect(0, 0, outSize, outSize), drawPaint)
                bBmp.recycle()
            }
            slotBuf != null -> {
                val sBmp = Bitmap.createBitmap(slotBuf.width, slotBuf.height, Bitmap.Config.ARGB_8888)
                sBmp.setPixels(slotBuf.pixels, 0, slotBuf.width, 0, 0, slotBuf.width, slotBuf.height)
                canvas.drawBitmap(sBmp, null, Rect(0, 0, outSize, outSize), drawPaint)
                sBmp.recycle()
            }
            baseSubMesh?.texturePixels != null && baseSubMesh.textureWidth > 0 && baseSubMesh.textureHeight > 0 -> {
                val bBmp = Bitmap.createBitmap(baseSubMesh.textureWidth, baseSubMesh.textureHeight, Bitmap.Config.ARGB_8888)
                bBmp.setPixels(baseSubMesh.texturePixels, 0, baseSubMesh.textureWidth, 0, 0, baseSubMesh.textureWidth, baseSubMesh.textureHeight)
                canvas.drawBitmap(bBmp, null, Rect(0, 0, outSize, outSize), drawPaint)
                bBmp.recycle()
            }
            else -> {
                canvas.drawColor(baseColor)
            }
        }

        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        for (layer in obj.paintLayers) {
            if (!layer.isVisible || layer.opacity <= 0.01f || layer.pixelsBase64.isNullOrBlank()) continue
            val buf = decodePixelsFromBase64(layer.pixelsBase64) ?: continue
            val lBmp = Bitmap.createBitmap(buf.width, buf.height, Bitmap.Config.ARGB_8888)
            lBmp.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
            layerPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            KodanimaGraphicsEngine.applyLayerBlendMode(layerPaint, layer.blendEffect)
            canvas.drawBitmap(lBmp, null, Rect(0, 0, outSize, outSize), layerPaint)
            layerPaint.xfermode = null
            lBmp.recycle()
        }

        val b64 = encodeBitmapToPngBase64(outBmp)
        if (!outBmp.isRecycled) outBmp.recycle()
        clearCaches()
        return b64
    }

    /**
     * Resolves a fast cached composite texture pixel buffer for software 3D rasterization
     * combining base mesh texture/color, active UV slot custom texture, baked merged UV, and live paint layers.
     * Returns null when the submesh has no custom UV texture or visible paint overlays (zero overhead fast path).
     */
    fun resolveCompositeObjectTexture(
        obj: SceneObject3D,
        basePixels: IntArray?,
        baseW: Int,
        baseH: Int,
        baseColorArgb: Int
    ): DecodedTextureBuffer? {
        val activeSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex) ?: obj.uvSlots.firstOrNull()
        val hasSlotTex = !activeSlot?.textureBase64.isNullOrBlank()
        val hasBaked = !obj.bakedTextureBase64.isNullOrBlank()
        val hasVisiblePaint = obj.paintLayers.any { it.isVisible && it.opacity > 0.01f && !it.pixelsBase64.isNullOrBlank() }

        if (!hasSlotTex && !hasBaked && !hasVisiblePaint) {
            return null
        }

        var cacheKey = obj.id.hashCode()
        cacheKey = cacheKey * 31 + (obj.bakedTextureBase64?.hashCode() ?: 0)
        cacheKey = cacheKey * 31 + (activeSlot?.textureBase64?.hashCode() ?: 0)
        cacheKey = cacheKey * 31 + baseColorArgb
        cacheKey = cacheKey * 31 + (basePixels?.size ?: 0)
        for (layer in obj.paintLayers) {
            if (!layer.isVisible || layer.opacity <= 0.01f) continue
            cacheKey = cacheKey * 31 + (layer.pixelsBase64?.hashCode() ?: 0)
            cacheKey = cacheKey * 31 + layer.blendEffect.ordinal
            cacheKey = cacheKey * 31 + (layer.opacity * 100f).roundToInt()
        }

        compositeTexCache.get(cacheKey)?.let { return it }

        val slotBuf = decodePixelsFromBase64(activeSlot?.textureBase64)
        val bakedBuf = decodePixelsFromBase64(obj.bakedTextureBase64)
        val outW = bakedBuf?.width ?: slotBuf?.width ?: (if (basePixels != null && baseW > 0) baseW else 256)
        val outH = bakedBuf?.height ?: slotBuf?.height ?: (if (basePixels != null && baseH > 0) baseH else 256)
        val targetW = outW.coerceIn(64, 512)
        val targetH = outH.coerceIn(64, 512)

        val compBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        when {
            bakedBuf != null -> {
                val bBmp = Bitmap.createBitmap(bakedBuf.width, bakedBuf.height, Bitmap.Config.ARGB_8888)
                bBmp.setPixels(bakedBuf.pixels, 0, bakedBuf.width, 0, 0, bakedBuf.width, bakedBuf.height)
                canvas.drawBitmap(bBmp, null, Rect(0, 0, targetW, targetH), paint)
                bBmp.recycle()
            }
            slotBuf != null -> {
                val sBmp = Bitmap.createBitmap(slotBuf.width, slotBuf.height, Bitmap.Config.ARGB_8888)
                sBmp.setPixels(slotBuf.pixels, 0, slotBuf.width, 0, 0, slotBuf.width, slotBuf.height)
                canvas.drawBitmap(sBmp, null, Rect(0, 0, targetW, targetH), paint)
                sBmp.recycle()
            }
            basePixels != null && baseW > 0 && baseH > 0 -> {
                val bBmp = Bitmap.createBitmap(baseW, baseH, Bitmap.Config.ARGB_8888)
                bBmp.setPixels(basePixels, 0, baseW, 0, 0, baseW, baseH)
                canvas.drawBitmap(bBmp, null, Rect(0, 0, targetW, targetH), paint)
                bBmp.recycle()
            }
            else -> {
                val r = (baseColorArgb ushr 16) and 0xFF
                val g = (baseColorArgb ushr 8) and 0xFF
                val b = baseColorArgb and 0xFF
                canvas.drawColor(Color.argb(255, r, g, b))
            }
        }

        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        for (layer in obj.paintLayers) {
            if (!layer.isVisible || layer.opacity <= 0.01f || layer.pixelsBase64.isNullOrBlank()) continue
            val lBuf = decodePixelsFromBase64(layer.pixelsBase64) ?: continue
            val lBmp = Bitmap.createBitmap(lBuf.width, lBuf.height, Bitmap.Config.ARGB_8888)
            lBmp.setPixels(lBuf.pixels, 0, lBuf.width, 0, 0, lBuf.width, lBuf.height)
            layerPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            KodanimaGraphicsEngine.applyLayerBlendMode(layerPaint, layer.blendEffect)
            canvas.drawBitmap(lBmp, null, Rect(0, 0, targetW, targetH), layerPaint)
            layerPaint.xfermode = null
            lBmp.recycle()
        }

        val outPixels = IntArray(targetW * targetH)
        compBmp.getPixels(outPixels, 0, targetW, 0, 0, targetW, targetH)
        if (!compBmp.isRecycled) compBmp.recycle()

        return DecodedTextureBuffer(outPixels, targetW, targetH).also {
            compositeTexCache.put(cacheKey, it)
        }
    }
}
