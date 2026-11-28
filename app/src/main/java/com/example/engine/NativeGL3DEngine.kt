package com.example.engine

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.example.model.ObjectShaderMode
import com.example.model.SceneObject3D
import com.example.model.ShaderNodeKind
import com.example.model.StageShadingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hardware-accelerated OpenGL ES 2.0 / EGL14 3D renderer running on a persistent offscreen GL context.
 *
 * Automatically probes native EGL/GLES20 support once and falls back cleanly when unavailable
 * (e.g. headless JVM unit tests), keeping projection, shader modes, ShaderNodeGraph parameters,
 * UV textures, transparent alpha cutout/blending, and Contorno outlines in exact parity with
 * [Camera3DEngine.renderSceneObjectsSoftware3D].
 */
object NativeGL3DEngine {

    private const val MAX_CACHED_TEXTURES = 24
    private const val DEFAULT_CONTORNO_COLOR = 0xFF10131CL

    private val glExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Kodanima-GL3D").apply { isDaemon = true }
    }

    @Volatile
    private var probed = false

    @Volatile
    private var hardwareSupported = false

    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private var meshProgram = 0
    private var maskProgram = 0
    private var outlineProgram = 0

    private var mainFbo = 0
    private var mainColorTex = 0
    private var mainDepthRb = 0
    private var fboWidth = 0
    private var fboHeight = 0

    private var maskFbo = 0
    private var maskColorTex = 0
    private var maskDepthRb = 0

    private var quadVbo = 0
    private var readbackByteBuf: ByteBuffer? = null
    private var readbackCapBytes = 0
    private var texUploadBytes = ByteArray(0)
    private var texUploadBuf: ByteBuffer? = null

    private data class CachedGlTexture(val texId: Int, val w: Int, val h: Int)
    private val textureCache = object : LinkedHashMap<String, CachedGlTexture>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedGlTexture>?): Boolean {
            if (size > MAX_CACHED_TEXTURES && eldest != null) {
                GLES20.glDeleteTextures(1, intArrayOf(eldest.value.texId), 0)
                return true
            }
            return false
        }
    }

    private data class GlOutlineJob(
        val radiusPx: Int,
        val colorInt: Int,
        val drawCalls: List<GlSubMeshCall>
    )

    private data class GlSubMeshCall(
        val vboData: FloatArray,
        val indices: ShortArray,
        val vCount: Int,
        val effShader: Int,
        val hasTex: Boolean,
        val texId: Int,
        val baseColorR: Float,
        val baseColorG: Float,
        val baseColorB: Float,
        val tintR: Float,
        val tintG: Float,
        val tintB: Float,
        val tintA: Float,
        val lightR: Float,
        val lightG: Float,
        val lightB: Float,
        val effLightIntensity: Float,
        val effAmbient: Float,
        val effRim: Float,
        val effEmissive: Float,
        val celSteps: Float,
        val brushStrength: Float,
        val brushDensity: Float,
        val noiseAmt: Float,
        val noiseScale: Float,
        val isSelected: Boolean,
        val isWireframeDots: Boolean
    )

    fun isHardwareAvailable(): Boolean {
        if (probed) return hardwareSupported
        synchronized(this) {
            if (probed) return hardwareSupported
            hardwareSupported = try {
                glExecutor.submit(Callable { ensureGlInitializedOnGlThread() }).get(3, TimeUnit.SECONDS)
            } catch (_: Throwable) {
                false
            }
            probed = true
            return hardwareSupported
        }
    }

    fun renderSceneObjects(
        outW: Int,
        outH: Int,
        sceneObjects: List<SceneObject3D>,
        autoInterpolate: Boolean,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        vpCx: Float,
        vpCy: Float,
        worldUnitScale: Float,
        zNearLimit: Float,
        zFarLimit: Float,
        selectedObjectId: String?,
        tickFloat: Float,
        animTimeSec: Float,
        reuseBitmap: Bitmap?,
        shadingMode: StageShadingMode
    ): Pair<Bitmap, Map<String, ProjectedObjectBounds>>? {
        if (!isHardwareAvailable() || outW <= 0 || outH <= 0) return null
        val visibleObjs = sceneObjects.filter { it.isVisible && it.meshAssetPath.isNotBlank() }
        if (visibleObjs.isEmpty()) return null

        return try {
            glExecutor.submit(Callable {
                renderOnGlThread(
                    outW = outW,
                    outH = outH,
                    visibleObjs = visibleObjs,
                    autoInterpolate = autoInterpolate,
                    camYawDeg = camYawDeg,
                    camPitchDeg = camPitchDeg,
                    camRollDeg = camRollDeg,
                    camPanX = camPanX,
                    camPanY = camPanY,
                    focalPx = focalPx,
                    vpCx = vpCx,
                    vpCy = vpCy,
                    worldUnitScale = worldUnitScale,
                    zNearLimit = zNearLimit,
                    zFarLimit = zFarLimit,
                    selectedObjectId = selectedObjectId,
                    tickFloat = tickFloat,
                    animTimeSec = animTimeSec,
                    reuseBitmap = reuseBitmap,
                    shadingMode = shadingMode
                )
            }).get(5, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            null
        }
    }

    private fun renderOnGlThread(
        outW: Int,
        outH: Int,
        visibleObjs: List<SceneObject3D>,
        autoInterpolate: Boolean,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        vpCx: Float,
        vpCy: Float,
        worldUnitScale: Float,
        zNearLimit: Float,
        zFarLimit: Float,
        selectedObjectId: String?,
        tickFloat: Float,
        animTimeSec: Float,
        reuseBitmap: Bitmap?,
        shadingMode: StageShadingMode
    ): Pair<Bitmap, Map<String, ProjectedObjectBounds>>? {
        if (!ensureGlInitializedOnGlThread()) return null
        if (!ensureFboSize(outW, outH)) return null

        val yawRad = Math.toRadians(camYawDeg.toDouble())
        val pitchRad = Math.toRadians(camPitchDeg.toDouble())
        val rollRad = Math.toRadians(camRollDeg.toDouble())
        val cosY = cos(yawRad).toFloat(); val sinY = sin(yawRad).toFloat()
        val cosP = cos(pitchRad).toFloat(); val sinP = sin(pitchRad).toFloat()
        val cosR = cos(rollRad).toFloat(); val sinR = sin(rollRad).toFloat()
        val camDist = focalPx.coerceAtLeast(240f)

        val boundsMap = HashMap<String, ProjectedObjectBounds>(visibleObjs.size)
        val allDrawCalls = ArrayList<GlSubMeshCall>(visibleObjs.size * 2)
        val outlineJobs = ArrayList<GlOutlineJob>(visibleObjs.size)
        val zFilterActive = zNearLimit > -1e20f || zFarLimit < 1e20f

        for (obj in visibleObjs) {
            val meshData = Mesh3DImporter.getOrLoadMesh(obj.meshAssetPath) ?: continue
            val objTf = Camera3DEngine.evaluateObjectTransformAtTick(obj.keyframes, tickFloat, autoInterpolate)
            if (objTf.opacity <= 0.01f) continue

            val ng = obj.nodeGraph
            val ngNodes = if (ng.enabled) ng.nodes else emptyList()
            fun findNode(k: ShaderNodeKind) = ngNodes.firstOrNull { it.kind == k }
            val uvNode = findNode(ShaderNodeKind.UV_COORD)
            val lightNode = findNode(ShaderNodeKind.LUZ_OBJETO)
            val celNode = findNode(ShaderNodeKind.CEL_RAMP)
            val brushNode = findNode(ShaderNodeKind.BRUSH_GRAIN)
            val outlineNode = findNode(ShaderNodeKind.CONTORNO)
            val bsdfNode = findNode(ShaderNodeKind.PRINCIPLED_BSDF)
            val fresnelNode = findNode(ShaderNodeKind.FRESNEL)
            val mixRgbNode = findNode(ShaderNodeKind.MIX_RGB)
            val noiseNode = findNode(ShaderNodeKind.NOISE_PROC)
            val emissaoNode = findNode(ShaderNodeKind.EMISSAO)

            val effShaderEnum = when {
                shadingMode == StageShadingMode.WIREFRAME -> ObjectShaderMode.ARAMADO
                shadingMode == StageShadingMode.TEXTURE -> ObjectShaderMode.SEM_LUZ
                ng.enabled && brushNode != null && brushNode.paramA > 0.08f -> ObjectShaderMode.CARTOON_BRUSH
                ng.enabled && celNode != null -> ObjectShaderMode.ANIME_CEL
                else -> obj.shaderMode
            }
            val effShaderInt = effShaderEnum.ordinal
            val celSteps = (celNode?.paramA?.roundToInt() ?: 3).coerceIn(2, 6).toFloat()
            val brushStrength = (brushNode?.paramA ?: if (effShaderEnum == ObjectShaderMode.CARTOON_BRUSH) 0.42f else 0f).coerceIn(0f, 1f)
            val brushDensity = (brushNode?.paramB ?: 0.55f).coerceIn(0.15f, 1f)
            val bsdfRoughness = (bsdfNode?.paramA ?: 0.5f).coerceIn(0.05f, 1f)
            val bsdfMetallic = (bsdfNode?.paramB ?: 0f).coerceIn(0f, 1f)
            val fresnelBoost = (fresnelNode?.paramA ?: 0f).coerceIn(0f, 1.2f)
            val mixFactor = (mixRgbNode?.paramA ?: 0f).coerceIn(0f, 1f)
            val mixColor = mixRgbNode?.colorArgb ?: 0xFFFFFFFFL
            val noiseAmt = (noiseNode?.paramA ?: 0f).coerceIn(0f, 0.8f)
            val noiseScale = (noiseNode?.paramB ?: 0.5f).coerceIn(0.1f, 1f)
            val nodeEmissive = emissaoNode?.paramA?.coerceIn(0f, 1.5f) ?: 0f
            val nodeUvScaleMul = (uvNode?.paramA ?: 1f).coerceIn(0.25f, 6f)
            val nodeUvOffsetShift = uvNode?.paramB ?: 0f

            val effLightIntensity = (obj.light.intensity * (if (lightNode != null) lightNode.paramA * 1.4f else 1f) * (1f + (1f - bsdfRoughness) * 0.35f)).coerceIn(0.1f, 3f)
            val effAmbient = (obj.light.ambient + (lightNode?.paramB?.let { (it - 0.3f) * 0.6f } ?: 0f) - bsdfMetallic * 0.12f).coerceIn(0.05f, 0.9f)
            val effRim = (obj.light.rimStrength + (celNode?.paramB?.let { it * 0.45f } ?: 0f) + fresnelBoost * 0.65f + bsdfMetallic * 0.2f).coerceIn(0f, 1.5f)
            val effEmissive = (obj.light.emissiveGlow + nodeEmissive).coerceIn(0f, 1.5f)

            val lAzRad = Math.toRadians(obj.light.azimuthDeg.toDouble())
            val lElRad = Math.toRadians(obj.light.elevationDeg.toDouble())
            val lHoriz = cos(lElRad).toFloat()
            val lightDirX = (sin(lAzRad) * lHoriz).toFloat()
            val lightDirY = (-sin(lElRad)).toFloat()
            val lightDirZ = (-cos(lAzRad) * lHoriz).toFloat()

            val mixColR = ((mixColor ushr 16) and 0xFF) / 255f
            val mixColG = ((mixColor ushr 8) and 0xFF) / 255f
            val mixColB = (mixColor and 0xFF) / 255f
            val lightR = ((obj.light.lightColorArgb ushr 16) and 0xFF) / 255f * (1f - mixFactor) + mixColR * mixFactor
            val lightG = ((obj.light.lightColorArgb ushr 8) and 0xFF) / 255f * (1f - mixFactor) + mixColG * mixFactor
            val lightB = (obj.light.lightColorArgb and 0xFF) / 255f * (1f - mixFactor) + mixColB * mixFactor

            val baseSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex) ?: obj.uvSlots.firstOrNull() ?: com.example.model.ObjectUVMapSlot()
            val effSlot = if (ng.enabled && uvNode != null) {
                baseSlot.copy(
                    scaleU = baseSlot.scaleU * nodeUvScaleMul,
                    scaleV = baseSlot.scaleV * nodeUvScaleMul,
                    offsetU = baseSlot.offsetU + nodeUvOffsetShift * 0.1f,
                    offsetV = baseSlot.offsetV + nodeUvOffsetShift * 0.1f
                )
            } else baseSlot

            val effOutlineEnabled = if (shadingMode == StageShadingMode.RENDERED) {
                if (ng.enabled && outlineNode != null) (outlineNode.paramB > 0.15f) || obj.outline.enabled else obj.outline.enabled
            } else false
            val effOutlineWidth = if (ng.enabled && outlineNode != null) {
                if (obj.outline.enabled && outlineNode.paramA == 2.0f && obj.outline.width > 2.0f) obj.outline.width else outlineNode.paramA
            } else obj.outline.width
            val effOutlineColor = if (ng.enabled && outlineNode != null) {
                if (obj.outline.enabled && outlineNode.colorArgb == DEFAULT_CONTORNO_COLOR && obj.outline.colorArgb != DEFAULT_CONTORNO_COLOR) {
                    obj.outline.colorArgb.toInt()
                } else {
                    outlineNode.colorArgb.toInt()
                }
            } else obj.outline.colorArgb.toInt()

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

            val tintA = objTf.opacity.coerceIn(0f, 1f)
            val tintR = ((obj.colorTint ushr 16) and 0xFF) / 255f
            val tintG = ((obj.colorTint ushr 8) and 0xFF) / 255f
            val tintB = (obj.colorTint and 0xFF) / 255f
            val isSel = obj.id == selectedObjectId

            var bMinX = Float.MAX_VALUE; var bMinY = Float.MAX_VALUE
            var bMaxX = -Float.MAX_VALUE; var bMaxY = -Float.MAX_VALUE
            var depthSum = 0f; var depthCount = 0

            val activeSubMeshes = Mesh3DImporter.evaluateSceneAtTime(meshData, animTimeSec)
            var subMeshVertOffset = 0
            val objDrawCalls = ArrayList<GlSubMeshCall>(activeSubMeshes.size)

            for ((smIdx, sm) in activeSubMeshes.withIndex()) {
                val vCount = sm.vertices.size / 3
                if (vCount < 3 || sm.indices.size < 3) {
                    subMeshVertOffset += vCount
                    continue
                }
                val verts = sm.vertices
                val norms = sm.normals
                val hasNorms = norms.size >= vCount * 3
                val uvs = Studio3DPaintEngine.generateUVsForProjection(verts, sm.uvs, effSlot, subMeshVertOffset)
                subMeshVertOffset += vCount
                val uvsSize = uvs.size

                // Transform vertices into world & camera space on CPU so exact bounds + GL clip positions match 1:1
                // Layout per vertex (11 floats): clipX, clipY, clipZOut, depth, rawClipZ, uvU, uvV, shade, rim, bary (packed in tri mode if ARAMADO)
                // Simpler: always expand indexed triangles only for ARAMADO or zFilter; otherwise use indexed ShortArray directly!
                val useWire = effShaderEnum == ObjectShaderMode.ARAMADO
                val triIndicesList = ArrayList<Short>(sm.indices.size)
                val worldZArr = if (zFilterActive) FloatArray(vCount) else null
                val clipZArr = FloatArray(vCount)

                // Interleaved vertex buffer: posClip(4), rawClipZ(1), uv(2), shade(1), rim(1), bary(3) = 12 floats if expanded or 9 floats indexed
                val strideFloats = if (useWire) 12 else 9
                val tempVerts = FloatArray(vCount * 9)

                for (vi in 0 until vCount) {
                    val vOff = vi * 3
                    val vx0 = verts[vOff] * sx
                    val vy0 = verts[vOff + 1] * sy
                    val vz0 = verts[vOff + 2] * sz

                    val vy1 = vy0 * cox - vz0 * sox
                    val vz1 = vy0 * sox + vz0 * cox
                    val vx2 = vx0 * coy + vz1 * soy
                    val vz2 = -vx0 * soy + vz1 * coy
                    val vx3 = vx2 * coz - vy1 * soz
                    val vy3 = vx2 * soz + vy1 * coz

                    val wx = vx3 + posX - camPanX
                    val wy = vy3 + posY - camPanY
                    val wz = vz2 + posZ
                    if (worldZArr != null) worldZArr[vi] = wz

                    val nx0 = if (hasNorms) norms[vOff] else 0f
                    val ny0 = if (hasNorms) norms[vOff + 1] else -1f
                    val nz0 = if (hasNorms) norms[vOff + 2] else 0f
                    val ny1 = ny0 * cox - nz0 * sox
                    val nz1 = ny0 * sox + nz0 * cox
                    val nx2 = nx0 * coy + nz1 * soy
                    val nz2 = -nx0 * soy + nz1 * coy
                    val nx3 = nx2 * coz - ny1 * soz
                    val ny3 = nx2 * soz + ny1 * coz

                    val dotKey = nx3 * lightDirX + ny3 * lightDirY + nz2 * lightDirZ
                    val viewRz = nx3 * sinY + nz2 * cosY
                    val viewNz = (ny3 * sinP + viewRz * cosP).coerceIn(-1f, 1f)
                    val rimVal = (1f - abs(viewNz)).coerceIn(0f, 1f).pow(2.2f) * effRim
                    val hemi = ((-ny3) * 0.5f + 0.5f).coerceIn(0f, 1f)
                    val shadeVal = (effAmbient + hemi * 0.14f + max(0f, dotKey) * 0.62f * effLightIntensity + effEmissive * 0.55f)
                        .coerceIn(0.12f, 1.35f)

                    val rx = wx * cosY - wz * sinY
                    val rz = wx * sinY + wz * cosY
                    val ry = wy * cosP - rz * sinP
                    val rz2 = wy * sinP + rz * cosP

                    val rawClipZ = camDist + rz2
                    clipZArr[vi] = rawClipZ
                    val depth = rawClipZ.coerceAtLeast(24f)
                    val invZ = 1f / depth
                    val pScale = camDist * invZ
                    val sx2d = rx * pScale
                    val sy2d = ry * pScale
                    val finalX = vpCx + (sx2d * cosR - sy2d * sinR)
                    val finalY = vpCy + (sx2d * sinR + sy2d * cosR)

                    if (rawClipZ >= 24f) {
                        if (finalX < bMinX) bMinX = finalX; if (finalX > bMaxX) bMaxX = finalX
                        if (finalY < bMinY) bMinY = finalY; if (finalY > bMaxY) bMaxY = finalY
                        depthSum += depth
                        depthCount++
                    }

                    // Perspective-correct clip-space coordinates (w = depth)
                    val ndcX = (finalX * 2f / outW) - 1f
                    val ndcY = (finalY * 2f / outH) - 1f
                    val near = 24f
                    val far = 24000f
                    val clipZOut = (depth * (far + near) - (2f * far * near)) / (far - near)

                    val uvOff = vi * 2
                    val u = if (uvOff < uvsSize) uvs[uvOff] else 0f
                    val v = if (uvOff + 1 < uvsSize) uvs[uvOff + 1] else 0f

                    val o = vi * 9
                    tempVerts[o] = ndcX * depth
                    tempVerts[o + 1] = ndcY * depth
                    tempVerts[o + 2] = clipZOut
                    tempVerts[o + 3] = depth
                    tempVerts[o + 4] = rawClipZ
                    tempVerts[o + 5] = u
                    tempVerts[o + 6] = v
                    tempVerts[o + 7] = shadeVal
                    tempVerts[o + 8] = rimVal
                }

                val indices = sm.indices
                var t = 0
                while (t + 2 < indices.size) {
                    val i0 = indices[t]; val i1 = indices[t + 1]; val i2 = indices[t + 2]
                    t += 3
                    if (i0 < 0 || i0 >= vCount || i1 < 0 || i1 >= vCount || i2 < 0 || i2 >= vCount) continue
                    if (clipZArr[i0] < 24f || clipZArr[i1] < 24f || clipZArr[i2] < 24f) continue
                    if (worldZArr != null) {
                        val avgWorldZ = (worldZArr[i0] + worldZArr[i1] + worldZArr[i2]) * (1f / 3f)
                        if (avgWorldZ < zNearLimit || avgWorldZ > zFarLimit) continue
                    }
                    triIndicesList.add(i0.toShort())
                    triIndicesList.add(i1.toShort())
                    triIndicesList.add(i2.toShort())
                }
                if (triIndicesList.isEmpty()) continue

                val finalVerts: FloatArray
                val finalIndices: ShortArray
                val finalVCount: Int
                if (useWire) {
                    val triCount = triIndicesList.size / 3
                    finalVCount = triCount * 3
                    finalVerts = FloatArray(finalVCount * strideFloats)
                    finalIndices = ShortArray(finalVCount) { it.toShort() }
                    for (k in 0 until finalVCount) {
                        val srcIdx = triIndicesList[k].toInt() * 9
                        val dstIdx = k * 12
                        System.arraycopy(tempVerts, srcIdx, finalVerts, dstIdx, 9)
                        val corner = k % 3
                        finalVerts[dstIdx + 9] = if (corner == 0) 1f else 0f
                        finalVerts[dstIdx + 10] = if (corner == 1) 1f else 0f
                        finalVerts[dstIdx + 11] = if (corner == 2) 1f else 0f
                    }
                } else {
                    finalVerts = tempVerts
                    finalIndices = triIndicesList.toShortArray()
                    finalVCount = vCount
                }

                val useTexture = shadingMode == StageShadingMode.TEXTURE || shadingMode == StageShadingMode.RENDERED
                val compTex = if (useTexture) {
                    Studio3DPaintEngine.resolveCompositeObjectTexture(
                        obj = obj,
                        basePixels = sm.texturePixels,
                        baseW = sm.textureWidth,
                        baseH = sm.textureHeight,
                        baseColorArgb = sm.baseColorArgb
                    )
                } else null
                val texPix = if (useTexture) (compTex?.pixels ?: sm.texturePixels) else null
                val texW = if (useTexture) (compTex?.width ?: sm.textureWidth) else 0
                val texH = if (useTexture) (compTex?.height ?: sm.textureHeight) else 0
                val hasTex = useTexture && texPix != null && texW > 0 && texH > 0
                val glTexId = if (hasTex) {
                    val pixArr = texPix!!
                    val pSig = if (pixArr.isNotEmpty()) {
                        pixArr[0] xor pixArr[pixArr.size / 2] xor pixArr[pixArr.size - 1]
                    } else 0
                    val cacheKey = "${obj.id}:$smIdx:$texW:$texH:${System.identityHashCode(pixArr)}:$pSig"
                    getOrUploadTexture(cacheKey, pixArr, texW, texH)
                } else 0

                val baseR = ((sm.baseColorArgb ushr 16) and 0xFF) / 255f
                val baseG = ((sm.baseColorArgb ushr 8) and 0xFF) / 255f
                val baseB = (sm.baseColorArgb and 0xFF) / 255f

                val call = GlSubMeshCall(
                    vboData = finalVerts,
                    indices = finalIndices,
                    vCount = finalVCount,
                    effShader = effShaderInt,
                    hasTex = hasTex && glTexId != 0,
                    texId = glTexId,
                    baseColorR = baseR,
                    baseColorG = baseG,
                    baseColorB = baseB,
                    tintR = tintR,
                    tintG = tintG,
                    tintB = tintB,
                    tintA = tintA,
                    lightR = lightR,
                    lightG = lightG,
                    lightB = lightB,
                    effLightIntensity = effLightIntensity,
                    effAmbient = effAmbient,
                    effRim = effRim,
                    effEmissive = effEmissive,
                    celSteps = celSteps,
                    brushStrength = brushStrength,
                    brushDensity = brushDensity,
                    noiseAmt = noiseAmt,
                    noiseScale = noiseScale,
                    isSelected = isSel,
                    isWireframeDots = shadingMode == StageShadingMode.WIREFRAME
                )
                allDrawCalls.add(call)
                objDrawCalls.add(call)
            }

            if (depthCount > 0 && bMinX <= bMaxX && bMinY <= bMaxY) {
                boundsMap[obj.id] = ProjectedObjectBounds(
                    id = obj.id,
                    centerX = (bMinX + bMaxX) * 0.5f,
                    centerY = (bMinY + bMaxY) * 0.5f,
                    minX = bMinX,
                    minY = bMinY,
                    maxX = bMaxX,
                    maxY = bMaxY,
                    avgDepth = depthSum / depthCount.toFloat()
                )
                if (effOutlineEnabled && effOutlineWidth >= 0.5f && objDrawCalls.isNotEmpty()) {
                    val aByte = (objTf.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(24, 255)
                    val col = (aByte shl 24) or (effOutlineColor and 0x00FFFFFF)
                    outlineJobs.add(
                        GlOutlineJob(
                            radiusPx = effOutlineWidth.roundToInt().coerceIn(1, 5),
                            colorInt = col,
                            drawCalls = objDrawCalls
                        )
                    )
                }
            }
        }

        // Pass 1: Render 3D Geometry to mainFbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mainFbo)
        GLES20.glViewport(0, 0, outW, outH)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(meshProgram)
        for (call in allDrawCalls) {
            drawSubMeshOnGl(call, isMaskPass = false)
        }

        // Pass 2: GPU Silhouette Contorno (Outline) pass per outlined object
        if (outlineJobs.isNotEmpty()) {
            for (job in outlineJobs) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, maskFbo)
                GLES20.glViewport(0, 0, outW, outH)
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                GLES20.glDepthMask(true)
                GLES20.glDisable(GLES20.GL_BLEND)
                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                GLES20.glUseProgram(maskProgram)
                for (call in job.drawCalls) {
                    drawSubMeshOnGl(call, isMaskPass = true)
                }

                // Composite dilated outline behind existing geometry on mainFbo
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mainFbo)
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                GLES20.glDepthMask(false)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE_MINUS_DST_ALPHA, GLES20.GL_ONE)
                drawOutlineQuad(outW, outH, job.radiusPx, job.colorInt)
            }
            GLES20.glDepthMask(true)
        }

        // Readback pixels cleanly into Bitmap
        val neededBytes = outW * outH * 4
        var buf = readbackByteBuf
        if (buf == null || readbackCapBytes < neededBytes) {
            buf = ByteBuffer.allocateDirect(neededBytes).order(ByteOrder.nativeOrder())
            readbackByteBuf = buf
            readbackCapBytes = neededBytes
        }
        buf.position(0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mainFbo)
        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.position(0)

        val outBmp = if (reuseBitmap != null && !reuseBitmap.isRecycled && reuseBitmap.isMutable &&
            reuseBitmap.width == outW && reuseBitmap.height == outH
        ) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        }
        outBmp.copyPixelsFromBuffer(buf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return outBmp to boundsMap
    }

    private fun drawSubMeshOnGl(call: GlSubMeshCall, isMaskPass: Boolean) {
        val prog = if (isMaskPass) maskProgram else meshProgram
        val useWire = call.effShader == ObjectShaderMode.ARAMADO.ordinal
        val strideFloats = if (useWire) 12 else 9
        val strideBytes = strideFloats * 4

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(call.vboData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(call.vboData)
                position(0)
            }
        val iBuf: ShortBuffer = ByteBuffer.allocateDirect(call.indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(call.indices)
                position(0)
            }

        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        vBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 4, GLES20.GL_FLOAT, false, strideBytes, vBuf)

        val aClipZ = GLES20.glGetAttribLocation(prog, "aRawClipZ")
        if (aClipZ >= 0) {
            GLES20.glEnableVertexAttribArray(aClipZ)
            vBuf.position(4)
            GLES20.glVertexAttribPointer(aClipZ, 1, GLES20.GL_FLOAT, false, strideBytes, vBuf)
        }

        val aUv = GLES20.glGetAttribLocation(prog, "aUv")
        if (aUv >= 0) {
            GLES20.glEnableVertexAttribArray(aUv)
            vBuf.position(5)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, strideBytes, vBuf)
        }

        if (!isMaskPass) {
            val aShade = GLES20.glGetAttribLocation(prog, "aShade")
            val aRim = GLES20.glGetAttribLocation(prog, "aRim")
            val aBary = GLES20.glGetAttribLocation(prog, "aBary")
            if (aShade >= 0) {
                GLES20.glEnableVertexAttribArray(aShade)
                vBuf.position(7)
                GLES20.glVertexAttribPointer(aShade, 1, GLES20.GL_FLOAT, false, strideBytes, vBuf)
            }
            if (aRim >= 0) {
                GLES20.glEnableVertexAttribArray(aRim)
                vBuf.position(8)
                GLES20.glVertexAttribPointer(aRim, 1, GLES20.GL_FLOAT, false, strideBytes, vBuf)
            }
            if (aBary >= 0) {
                if (useWire) {
                    GLES20.glEnableVertexAttribArray(aBary)
                    vBuf.position(9)
                    GLES20.glVertexAttribPointer(aBary, 3, GLES20.GL_FLOAT, false, strideBytes, vBuf)
                } else {
                    GLES20.glDisableVertexAttribArray(aBary)
                    GLES20.glVertexAttrib3f(aBary, 0.5f, 0.5f, 0.5f)
                }
            }
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uShaderMode"), call.effShader)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uHasTex"), if (call.hasTex) 1 else 0)
            GLES20.glUniform3f(GLES20.glGetUniformLocation(prog, "uSolidColor"),
                call.baseColorR * call.tintR * call.lightR,
                call.baseColorG * call.tintG * call.lightG,
                call.baseColorB * call.tintB * call.lightB)
            GLES20.glUniform3f(GLES20.glGetUniformLocation(prog, "uTintLight"),
                call.tintR * call.lightR,
                call.tintG * call.lightG,
                call.tintB * call.lightB)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uTintAlpha"), call.tintA)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uEmissive"), call.effEmissive)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uCelSteps"), call.celSteps)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uBrushStrength"), call.brushStrength)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uBrushStepPx"), max(3.5f, 11f - call.brushDensity * 7f))
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uNoiseAmt"), call.noiseAmt)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uNoiseScale"), call.noiseScale)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uSelected"), if (call.isSelected) 1 else 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uWireDots"), if (call.isWireframeDots) 1 else 0)
            if (call.hasTex) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, call.texId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTexture"), 0)
            }
        } else {
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uHasTex"), if (call.hasTex) 1 else 0)
            if (call.hasTex) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, call.texId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTexture"), 0)
            }
        }

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, call.indices.size, GLES20.GL_UNSIGNED_SHORT, iBuf)
    }

    private fun drawOutlineQuad(outW: Int, outH: Int, radiusPx: Int, colorInt: Int) {
        GLES20.glUseProgram(outlineProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskColorTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(outlineProgram, "uMaskTex"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(outlineProgram, "uTexelSize"), 1f / outW, 1f / outH)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(outlineProgram, "uRadius"), radiusPx)
        val a = ((colorInt ushr 24) and 0xFF) / 255f
        val r = ((colorInt ushr 16) and 0xFF) / 255f
        val g = ((colorInt ushr 8) and 0xFF) / 255f
        val b = (colorInt and 0xFF) / 255f
        GLES20.glUniform4f(GLES20.glGetUniformLocation(outlineProgram, "uOutlineColor"), r * a, g * a, b * a, a)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        val aPos = GLES20.glGetAttribLocation(outlineProgram, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun getOrUploadTexture(key: String, pixels: IntArray, w: Int, h: Int): Int {
        textureCache[key]?.let { return it.texId }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texId = ids[0]
        if (texId == 0) return 0

        val needed = w * h * 4
        if (texUploadBytes.size < needed) texUploadBytes = ByteArray(needed)
        if (texUploadBuf == null || texUploadBuf!!.capacity() < needed) {
            texUploadBuf = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder())
        }
        val arr = texUploadBytes
        var bi = 0
        for (i in 0 until w * h) {
            val px = pixels[i]
            arr[bi++] = ((px ushr 16) and 0xFF).toByte()
            arr[bi++] = ((px ushr 8) and 0xFF).toByte()
            arr[bi++] = (px and 0xFF).toByte()
            arr[bi++] = ((px ushr 24) and 0xFF).toByte()
        }
        val buf = texUploadBuf!!
        buf.position(0)
        buf.put(arr, 0, needed)
        buf.position(0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

        textureCache[key] = CachedGlTexture(texId, w, h)
        return texId
    }

    private fun ensureFboSize(w: Int, h: Int): Boolean {
        if (mainFbo != 0 && maskFbo != 0 && fboWidth == w && fboHeight == h) return true
        if (mainFbo != 0) {
            GLES20.glDeleteFramebuffers(2, intArrayOf(mainFbo, maskFbo), 0)
            GLES20.glDeleteTextures(2, intArrayOf(mainColorTex, maskColorTex), 0)
            GLES20.glDeleteRenderbuffers(2, intArrayOf(mainDepthRb, maskDepthRb), 0)
        }
        val fbos = IntArray(2); val texs = IntArray(2); val rbs = IntArray(2)
        GLES20.glGenFramebuffers(2, fbos, 0)
        GLES20.glGenTextures(2, texs, 0)
        GLES20.glGenRenderbuffers(2, rbs, 0)
        mainFbo = fbos[0]; maskFbo = fbos[1]
        mainColorTex = texs[0]; maskColorTex = texs[1]
        mainDepthRb = rbs[0]; maskDepthRb = rbs[1]

        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texs[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)

            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rbs[i])
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texs[i], 0)
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, rbs[i])
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                fboWidth = 0; fboHeight = 0
                return false
            }
        }
        fboWidth = w; fboHeight = h
        return true
    }

    private fun ensureGlInitializedOnGlThread(): Boolean {
        if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT && meshProgram != 0) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            return true
        }
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (dpy == null || dpy == EGL14.EGL_NO_DISPLAY) return false
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return false
        val cfgAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val numCfg = IntArray(1)
        if (!EGL14.eglChooseConfig(dpy, cfgAttribs, 0, cfgs, 0, 1, numCfg, 0) || numCfg[0] <= 0 || cfgs[0] == null) {
            return false
        }
        val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (surf == null || surf == EGL14.EGL_NO_SURFACE) return false
        var ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) {
            ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        }
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT || !EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
            return false
        }
        val mp = compileProgram(MESH_VERT_SHADER, MESH_FRAG_SHADER)
        val mkp = compileProgram(MASK_VERT_SHADER, MASK_FRAG_SHADER)
        val op = compileProgram(OUTLINE_VERT_SHADER, OUTLINE_FRAG_SHADER)
        if (mp == 0 || mkp == 0 || op == 0) return false

        val vbos = IntArray(1)
        GLES20.glGenBuffers(1, vbos, 0)
        quadVbo = vbos[0]
        val quadData = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val qBuf = ByteBuffer.allocateDirect(quadData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(quadData); position(0)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quadData.size * 4, qBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        eglDisplay = dpy; eglSurface = surf; eglContext = ctx
        meshProgram = mp; maskProgram = mkp; outlineProgram = op
        return true
    }

    private fun compileProgram(vsSrc: String, fsSrc: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            if (s == 0) return 0
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val st = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
            if (st[0] == 0) {
                GLES20.glDeleteShader(s)
                return 0
            }
            return s
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES20.glCreateProgram()
        if (prog == 0) return 0
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val st = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, st, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (st[0] == 0) {
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private const val MESH_VERT_SHADER = """
        attribute vec4 aPos;
        attribute float aRawClipZ;
        attribute vec2 aUv;
        attribute float aShade;
        attribute float aRim;
        attribute vec3 aBary;
        varying float vClipZ;
        varying vec2 vUv;
        varying float vShade;
        varying float vRim;
        varying vec3 vBary;
        void main() {
            gl_Position = aPos;
            vClipZ = aRawClipZ;
            vUv = aUv;
            vShade = aShade;
            vRim = aRim;
            vBary = aBary;
        }
    """

    private const val MESH_FRAG_SHADER = """
        precision mediump float;
        varying float vClipZ;
        varying vec2 vUv;
        varying float vShade;
        varying float vRim;
        varying vec3 vBary;
        uniform int uShaderMode;
        uniform int uHasTex;
        uniform sampler2D uTexture;
        uniform vec3 uSolidColor;
        uniform vec3 uTintLight;
        uniform float uTintAlpha;
        uniform float uEmissive;
        uniform float uCelSteps;
        uniform float uBrushStrength;
        uniform float uBrushStepPx;
        uniform float uNoiseAmt;
        uniform float uNoiseScale;
        uniform int uSelected;
        uniform int uWireDots;
        void main() {
            if (vClipZ < 24.0) discard;
            float shadeVal = 1.0;
            if (uShaderMode == 1) {
                shadeVal = 1.0;
            } else if (uShaderMode == 4) {
                shadeVal = clamp(vShade * 0.35 + 0.15, 0.15, 0.55);
            } else if (uShaderMode == 2) {
                float q = floor(clamp(vShade, 0.0, 1.15) * uCelSteps) / uCelSteps;
                shadeVal = clamp(0.32 + q * 0.72 + (vRim > 0.32 ? 0.24 : 0.0), 0.28, 1.25);
            } else if (uShaderMode == 3) {
                float q = floor(clamp(vShade, 0.0, 1.15) * uCelSteps) / uCelSteps;
                float diag = (gl_FragCoord.x + gl_FragCoord.y * 0.65) / uBrushStepPx;
                float strokeWave = abs(diag - floor(diag) - 0.5) * 2.0;
                float shadowBoost = (q < 0.55) ? (1.0 - strokeWave * uBrushStrength * 0.52) : (1.0 - strokeWave * uBrushStrength * 0.12);
                shadeVal = clamp((0.34 + q * 0.72) * shadowBoost + (vRim > 0.30 ? 0.22 : 0.0), 0.22, 1.25);
            } else {
                shadeVal = clamp(vShade + vRim * 0.35, 0.18, 1.25);
            }
            if (uNoiseAmt > 0.01) {
                float nx = floor(gl_FragCoord.x * uNoiseScale * 0.45);
                float ny = floor(gl_FragCoord.y * uNoiseScale * 0.45);
                float n = fract(sin(nx * 12.9898 + ny * 78.233) * 43758.5453);
                shadeVal = clamp(shadeVal * (1.0 - uNoiseAmt * 0.35 + n * uNoiseAmt * 0.5), 0.15, 1.4);
            }
            bool isWireEdge = (uShaderMode == 4) && (vBary.x < 0.05 || vBary.y < 0.05 || vBary.z < 0.05);
            bool isVertexDot = (uWireDots == 1) && (vBary.x > 0.90 || vBary.y > 0.90 || vBary.z > 0.90);
            if (isVertexDot) {
                gl_FragColor = vec4(vec3(1.0, 0.666, 0.0) * uTintAlpha, uTintAlpha);
                return;
            }
            if (isWireEdge) {
                gl_FragColor = vec4(vec3(0.0, 0.898, 1.0) * uTintAlpha, uTintAlpha);
                return;
            }
            vec3 outRgb;
            float outA = uTintAlpha;
            if (uHasTex == 1) {
                vec4 texel = texture2D(uTexture, fract(vUv));
                if (texel.a * 255.0 <= 5.0) discard;
                outRgb = texel.rgb * uTintLight * shadeVal;
                if (uEmissive > 0.02) {
                    outRgb += texel.rgb * uEmissive * 0.45;
                }
                outA *= texel.a;
            } else {
                outRgb = uSolidColor * shadeVal;
            }
            if (uSelected == 1) {
                outRgb = outRgb * 0.88 + vec3(0.12, 0.08, 0.0);
            }
            outRgb = clamp(outRgb, 0.0, 1.0);
            gl_FragColor = vec4(outRgb * outA, outA);
        }
    """

    private const val MASK_VERT_SHADER = """
        attribute vec4 aPos;
        attribute float aRawClipZ;
        attribute vec2 aUv;
        varying float vClipZ;
        varying vec2 vUv;
        void main() {
            gl_Position = aPos;
            vClipZ = aRawClipZ;
            vUv = aUv;
        }
    """

    private const val MASK_FRAG_SHADER = """
        precision mediump float;
        varying float vClipZ;
        varying vec2 vUv;
        uniform int uHasTex;
        uniform sampler2D uTexture;
        void main() {
            if (vClipZ < 24.0) discard;
            if (uHasTex == 1) {
                if (texture2D(uTexture, fract(vUv)).a * 255.0 <= 5.0) discard;
            }
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
    """

    private const val OUTLINE_VERT_SHADER = """
        attribute vec2 aPos;
        varying vec2 vScreenUv;
        void main() {
            gl_Position = vec4(aPos, 0.0, 1.0);
            vScreenUv = aPos * 0.5 + 0.5;
        }
    """

    private const val OUTLINE_FRAG_SHADER = """
        precision mediump float;
        varying vec2 vScreenUv;
        uniform sampler2D uMaskTex;
        uniform vec2 uTexelSize;
        uniform int uRadius;
        uniform vec4 uOutlineColor;
        void main() {
            if (texture2D(uMaskTex, vScreenUv).a > 0.5) discard;
            int r2 = uRadius * uRadius + 1;
            bool hit = false;
            for (int oy = -5; oy <= 5; oy++) {
                if (oy < -uRadius || oy > uRadius) continue;
                for (int ox = -5; ox <= 5; ox++) {
                    if (ox < -uRadius || ox > uRadius) continue;
                    if (ox * ox + oy * oy <= r2) {
                        vec2 sampleUv = clamp(vScreenUv + vec2(float(ox), float(oy)) * uTexelSize, 0.0, 1.0);
                        if (texture2D(uMaskTex, sampleUv).a > 0.5) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (hit) break;
            }
            if (!hit) discard;
            gl_FragColor = uOutlineColor;
        }
    """
}
