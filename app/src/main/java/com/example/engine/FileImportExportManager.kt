package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.AnimationProject
import com.example.model.Camera3DKeyframe
import com.example.model.Camera3DSettings
import com.example.model.CameraEasingCurve
import com.example.model.CameraPostEffectSettings
import com.example.model.CanvasBackgroundSettings
import com.example.model.CanvasBackgroundStyle
import com.example.model.CanvasTexturePattern
import com.example.model.DrawingStroke
import com.example.model.Frame
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.OnionSkinSettings
import com.example.model.ProjectResolution
import com.example.model.SkyBackdropMode
import com.example.model.StrokePoint
import com.example.model.ToolType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * File Import & Export Manager for Kodanima 2D on Android.
 * Handles:
 * - Image decoding from Android content URIs
 * - Project serialization/deserialization (.kodanima format)
 * - GIF / PNG export and sharing via Android Sharesheet
 */
object FileImportExportManager {

    /**
     * Decodes an image from content Uri safely.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1920): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { decodeStream ->
                BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Serializes an AnimationProject to JSON string (.kodanima format).
     */
    fun projectToJson(project: AnimationProject, compact: Boolean = false): String {
        val root = JSONObject()
        root.put("version", "9.2.0-android")
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("fps", project.fps)
        root.put("createdAt", project.createdAt)
        root.put("updatedAt", project.updatedAt)

        root.put("lastActiveFrameIndex", project.lastActiveFrameIndex)

        // Resolution
        val res = JSONObject()
        res.put("width", project.resolution.width)
        res.put("height", project.resolution.height)
        res.put("name", project.resolution.name)
        res.put("isCustom", project.resolution.isCustom)
        root.put("resolution", res)

        // Onion Skin
        val os = JSONObject()
        os.put("isEnabled", project.onionSkin.isEnabled)
        os.put("prevFramesCount", project.onionSkin.prevFramesCount)
        os.put("nextFramesCount", project.onionSkin.nextFramesCount)
        os.put("opacity", project.onionSkin.opacity.toDouble())
        os.put("prevColor", project.onionSkin.prevColor)
        os.put("nextColor", project.onionSkin.nextColor)
        root.put("onionSkin", os)

        // 3D Camera & Post Effects
        root.put("camera3D", camera3DToJson(project.camera3D, includeTextures = true))

        val bg = project.canvasBackground
        root.put("canvasBackground", JSONObject().apply {
            put("style", bg.style.name)
            put("primaryColor", bg.primaryColor)
            put("secondaryColor", bg.secondaryColor)
            put("gradientAngleDeg", bg.gradientAngleDeg.toDouble())
            put("texturePattern", bg.texturePattern.name)
            put("textureIntensity", bg.textureIntensity.toDouble())
            put("skyMode", bg.skyMode.name)
            put("skyTopColor", bg.skyTopColor)
            put("skyBottomColor", bg.skyBottomColor)
            put("skyLightColor", bg.skyLightColor)
            put("skyDarkColor", bg.skyDarkColor)
            put("skyGradientAngleDeg", bg.skyGradientAngleDeg.toDouble())
            put("show3DInCanvas", bg.show3DInCanvas)
            put("canvas3DRenderMode", bg.canvas3DRenderMode.name)
            val stopsArr = JSONArray()
            bg.skyGradientStops.forEach { st ->
                stopsArr.put(JSONObject().apply {
                    put("position", st.position.toDouble())
                    put("color", st.color)
                })
            }
            put("skyGradientStops", stopsArr)
        })

        // Frames
        val framesArray = JSONArray()
        for (frame in project.frames) {
            val frameObj = JSONObject()
            frameObj.put("id", frame.id)
            frameObj.put("holdFrames", frame.holdFrames)

            val layersArray = JSONArray()
            for (layer in frame.layers) {
                layersArray.put(layerToJson(layer))
            }
            frameObj.put("layers", layersArray)
            if (frame.localLayers.isNotEmpty()) {
                val localLayersArray = JSONArray()
                for (localLayer in frame.localLayers) {
                    localLayersArray.put(layerToJson(localLayer))
                }
                frameObj.put("localLayers", localLayersArray)
            }
            framesArray.put(frameObj)
        }
        // Media Assets (Audio & Video / GIF tracks matching desktop Kodanima2D)
        val mediaArray = JSONArray()
        for (asset in project.mediaAssets) {
            val aObj = JSONObject()
            aObj.put("id", asset.id)
            aObj.put("name", asset.name)
            aObj.put("uri", asset.uriString)
            aObj.put("type", asset.type)
            aObj.put("startSeconds", asset.startSeconds)
            aObj.put("durationSeconds", asset.durationSeconds)
            aObj.put("opacityPercent", asset.opacityPercent)
            aObj.put("scalePercent", asset.scalePercent)
            aObj.put("rotationDegrees", asset.rotationDegrees)
            aObj.put("offsetX", asset.offsetX)
            aObj.put("offsetY", asset.offsetY)
            aObj.put("audioVolumePercent", asset.audioVolumePercent)
            aObj.put("audioMuted", asset.audioMuted)
            val peaksArray = JSONArray()
            for (p in asset.waveformPeaks) peaksArray.put(p.toDouble())
            aObj.put("waveformPeaks", peaksArray)
            mediaArray.put(aObj)
        }
        root.put("mediaAssets", mediaArray)

        root.put("frames", framesArray)

        return if (compact) root.toString() else root.toString(2)
    }

    /**
     * Parses an AnimationProject from JSON string (.kodanima format).
     */
    fun projectFromJson(jsonString: String): AnimationProject {
        val root = JSONObject(jsonString)
        val id = root.optString("id", java.util.UUID.randomUUID().toString())
        val name = root.optString("name", "Projeto Importado")
        val fps = root.optInt("fps", 12)
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())

        val resObj = root.optJSONObject("resolution")
        val resolution = if (resObj != null) {
            ProjectResolution(
                width = resObj.optInt("width", 1280),
                height = resObj.optInt("height", 720),
                name = resObj.optString("name", "Personalizado")
            )
        } else {
            ProjectResolution.PRESETS[0]
        }

        val osObj = root.optJSONObject("onionSkin")
        val onionSkin = if (osObj != null) {
            OnionSkinSettings(
                isEnabled = osObj.optBoolean("isEnabled", true),
                prevFramesCount = osObj.optInt("prevFramesCount", 2),
                nextFramesCount = osObj.optInt("nextFramesCount", 1),
                opacity = osObj.optDouble("opacity", 0.35).toFloat(),
                prevColor = osObj.optLong("prevColor", 0xFFFF3366),
                nextColor = osObj.optLong("nextColor", 0xFF00D2FF)
            )
        } else {
            OnionSkinSettings()
        }

        val framesList = ArrayList<Frame>()
        val framesArray = root.optJSONArray("frames")
        if (framesArray != null) {
            for (fIdx in 0 until framesArray.length()) {
                val fObj = framesArray.optJSONObject(fIdx) ?: continue
                val fId = fObj.optString("id", java.util.UUID.randomUUID().toString())
                val hold = fObj.optInt("holdFrames", 1)

                val layersList = ArrayList<Layer>()
                val layersArray = fObj.optJSONArray("layers")
                if (layersArray != null) {
                    for (lIdx in 0 until layersArray.length()) {
                        val lObj = layersArray.optJSONObject(lIdx) ?: continue
                        layersList.add(jsonToLayer(lObj, lIdx))
                    }
                }
                val localLayersList = ArrayList<Layer>()
                val localLayersArray = fObj.optJSONArray("localLayers")
                if (localLayersArray != null) {
                    for (lIdx in 0 until localLayersArray.length()) {
                        val lObj = localLayersArray.optJSONObject(lIdx) ?: continue
                        localLayersList.add(jsonToLayer(lObj, lIdx, defaultNamePrefix = "Subcamada"))
                    }
                }
                framesList.add(
                    Frame(
                        id = fId,
                        layers = if (layersList.isEmpty()) listOf(Layer()) else layersList,
                        holdFrames = hold,
                        localLayers = localLayersList
                    )
                )
            }
        }

        val mediaAssetsList = ArrayList<com.example.model.SpecialMediaAsset>()
        val mediaArray = root.optJSONArray("mediaAssets")
        if (mediaArray != null) {
            for (mIdx in 0 until mediaArray.length()) {
                val mObj = mediaArray.optJSONObject(mIdx) ?: continue
                val peaksList = ArrayList<Float>()
                val peaksArr = mObj.optJSONArray("waveformPeaks")
                if (peaksArr != null) {
                    for (p in 0 until peaksArr.length()) peaksList.add(peaksArr.optDouble(p, 0.0).toFloat())
                }
                mediaAssetsList.add(
                    com.example.model.SpecialMediaAsset(
                        id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = mObj.optString("name", "Mídia"),
                        uriString = mObj.optString("uri", ""),
                        type = mObj.optString("type", "audio"),
                        startSeconds = mObj.optDouble("startSeconds", 0.0),
                        durationSeconds = mObj.optDouble("durationSeconds", 0.0),
                        opacityPercent = mObj.optInt("opacityPercent", 100),
                        scalePercent = mObj.optDouble("scalePercent", 100.0),
                        rotationDegrees = mObj.optDouble("rotationDegrees", 0.0),
                        offsetX = mObj.optDouble("offsetX", 0.0),
                        offsetY = mObj.optDouble("offsetY", 0.0),
                        audioVolumePercent = mObj.optInt("audioVolumePercent", 100),
                        audioMuted = mObj.optBoolean("audioMuted", false),
                        waveformPeaks = peaksList
                    )
                )
            }
        }

        val finalFrames = if (framesList.isEmpty()) listOf(Frame()) else framesList
        val savedActiveFrame = root.optInt("lastActiveFrameIndex", 0).coerceIn(0, finalFrames.lastIndex)
        val resObjParsed = root.optJSONObject("resolution")
        val finalResolution = if (resObjParsed != null) {
            resolution.copy(isCustom = resObjParsed.optBoolean("isCustom", false))
        } else resolution

        val camObjParsed = root.optJSONObject("camera3D")
        val camera3D = if (camObjParsed != null) {
            jsonToCamera3D(camObjParsed, finalResolution.width, finalResolution.height)
        } else {
            Camera3DSettings(
                captureWidth = finalResolution.width,
                captureHeight = finalResolution.height
            )
        }

        val bgObj = root.optJSONObject("canvasBackground")
        val canvasBackground = if (bgObj != null) {
            val bgStyle = try {
                CanvasBackgroundStyle.valueOf(bgObj.optString("style", CanvasBackgroundStyle.SOLIDO.name))
            } catch (_: Exception) { CanvasBackgroundStyle.SOLIDO }
            val texPat = try {
                CanvasTexturePattern.valueOf(bgObj.optString("texturePattern", CanvasTexturePattern.NENHUMA.name))
            } catch (_: Exception) { CanvasTexturePattern.NENHUMA }
            val skyMode = try {
                SkyBackdropMode.valueOf(bgObj.optString("skyMode", SkyBackdropMode.GRADE_ESPACIAL.name))
            } catch (_: Exception) { SkyBackdropMode.GRADE_ESPACIAL }
            val parsedStops = mutableListOf<com.example.model.GradientColorStop>()
            val stArr = bgObj.optJSONArray("skyGradientStops")
            if (stArr != null) {
                for (si in 0 until stArr.length()) {
                    val so = stArr.optJSONObject(si) ?: continue
                    parsedStops.add(
                        com.example.model.GradientColorStop(
                            position = so.optDouble("position", 0.5).toFloat().coerceIn(0f, 1f),
                            color = so.optLong("color", 0xFF3A528CL)
                        )
                    )
                }
            }
            val render3DMode = try {
                com.example.model.Canvas3DBackgroundRenderMode.valueOf(
                    bgObj.optString("canvas3DRenderMode", com.example.model.Canvas3DBackgroundRenderMode.STATIC_2D_CACHE.name)
                )
            } catch (_: Exception) { com.example.model.Canvas3DBackgroundRenderMode.STATIC_2D_CACHE }
            CanvasBackgroundSettings(
                style = bgStyle,
                primaryColor = bgObj.optLong("primaryColor", 0xFFFFFFFFL),
                secondaryColor = bgObj.optLong("secondaryColor", 0xFFE3ECFFL),
                gradientAngleDeg = bgObj.optDouble("gradientAngleDeg", 90.0).toFloat(),
                texturePattern = texPat,
                textureIntensity = bgObj.optDouble("textureIntensity", 0.28).toFloat().coerceIn(0.05f, 1.0f),
                skyMode = skyMode,
                skyTopColor = bgObj.optLong("skyTopColor", 0xFF151926L),
                skyBottomColor = bgObj.optLong("skyBottomColor", 0xFF28314AL),
                skyLightColor = bgObj.optLong("skyLightColor", 0xFF3E4E75L),
                skyDarkColor = bgObj.optLong("skyDarkColor", 0xFF0E1320L),
                skyGradientStops = parsedStops,
                skyGradientAngleDeg = bgObj.optDouble("skyGradientAngleDeg", 90.0).toFloat(),
                show3DInCanvas = bgObj.optBoolean("show3DInCanvas", false),
                canvas3DRenderMode = render3DMode
            )
        } else CanvasBackgroundSettings()

        return AnimationProject(
            id = id,
            name = name,
            resolution = finalResolution,
            fps = fps,
            frames = finalFrames,
            mediaAssets = mediaAssetsList,
            onionSkin = onionSkin,
            camera3D = camera3D,
            canvasBackground = canvasBackground,
            lastActiveFrameIndex = savedActiveFrame,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Saves bytes to application cache and returns share intent.
     */
    fun shareFile(context: Context, filename: String, mimeType: String, data: ByteArray) {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, filename)
            FileOutputStream(file).use { it.write(data) }

            val uri: Uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Animação Kodanima 2D").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun layerToJson(layer: Layer): JSONObject {
        val layerObj = JSONObject()
        layerObj.put("id", layer.id)
        layerObj.put("name", layer.name)
        layerObj.put("isVisible", layer.isVisible)
        layerObj.put("isLocked", layer.isLocked)
        layerObj.put("opacity", layer.opacity.toDouble())
        if (layer.blendEffect != LayerBlendEffect.NORMAL) {
            layerObj.put("blendEffect", layer.blendEffect.name)
        }
        if (layer.isClippingMask) {
            layerObj.put("isClippingMask", true)
        }
        if (layer.isBucketLayer) {
            layerObj.put("isBucketLayer", true)
        }
        if (layer.isBucketReference) {
            layerObj.put("isBucketReference", true)
        }
        layer.celHoldTicks?.let { layerObj.put("celHoldTicks", it) }
        layer.celStartTick?.let { layerObj.put("celStartTick", it) }
        layer.imageAttachment?.let { att ->
            val attObj = JSONObject().apply {
                put("uriString", att.uriString)
                put("scalePercent", att.scalePercent.toDouble())
                put("rotationDegrees", att.rotationDegrees.toDouble())
                put("offsetX", att.offsetX.toDouble())
                put("offsetY", att.offsetY.toDouble())
                put("opacity", att.opacity.toDouble())
            }
            layerObj.put("imageAttachment", attObj)
        }

        val strokesArray = JSONArray()
        for (stroke in layer.strokes) {
            val strokeObj = JSONObject()
            strokeObj.put("id", stroke.id)
            if (stroke.authorId.isNotBlank()) {
                strokeObj.put("authorId", stroke.authorId)
            }
            strokeObj.put("tool", stroke.tool.name)
            strokeObj.put("color", stroke.color)
            strokeObj.put("brushSize", stroke.brushSize.toDouble())
            strokeObj.put("opacity", stroke.opacity.toDouble())
            strokeObj.put("brushPresetId", stroke.brushPresetId)
            if (!stroke.customBrushParams.isNullOrBlank()) {
                strokeObj.put("customBrushParams", stroke.customBrushParams)
            }
            strokeObj.put("isFilled", stroke.isFilled)

            val pointsArray = JSONArray()
            for (p in stroke.points) {
                val pObj = JSONObject()
                pObj.put("x", p.x.toDouble())
                pObj.put("y", p.y.toDouble())
                pObj.put("pressure", p.pressure.toDouble())
                pointsArray.put(pObj)
            }
            strokeObj.put("points", pointsArray)
            if (!stroke.clipPolygon.isNullOrEmpty()) {
                val clipArr = JSONArray()
                for (cp in stroke.clipPolygon) {
                    val cObj = JSONObject()
                    cObj.put("x", cp.x.toDouble())
                    cObj.put("y", cp.y.toDouble())
                    clipArr.put(cObj)
                }
                strokeObj.put("clipPolygon", clipArr)
                if (stroke.clipInverted) {
                    strokeObj.put("clipInverted", true)
                    strokeObj.put("clipBoundsWidth", stroke.clipBoundsWidth.toDouble())
                    strokeObj.put("clipBoundsHeight", stroke.clipBoundsHeight.toDouble())
                }
            }
            strokesArray.put(strokeObj)
        }
        layerObj.put("strokes", strokesArray)
        return layerObj
    }

    fun jsonToLayer(
        lObj: JSONObject,
        lIdx: Int = 0,
        defaultNamePrefix: String = "Camada"
    ): Layer {
        val lId = lObj.optString("id", java.util.UUID.randomUUID().toString())
        val lName = lObj.optString("name", "$defaultNamePrefix ${lIdx + 1}")
        val isVisible = lObj.optBoolean("isVisible", true)
        val isLocked = lObj.optBoolean("isLocked", false)
        val opacity = lObj.optDouble("opacity", 1.0).toFloat()
        val blendStr = lObj.optString("blendEffect", LayerBlendEffect.NORMAL.name)
        val blendEffect = try {
            LayerBlendEffect.valueOf(blendStr)
        } catch (_: Exception) {
            LayerBlendEffect.NORMAL
        }
        val isClippingMask = lObj.optBoolean("isClippingMask", false)
        val isBucketLayer = lObj.optBoolean("isBucketLayer", false)
        val isBucketReference = lObj.optBoolean("isBucketReference", false)

        val strokesList = ArrayList<DrawingStroke>()
        val strokesArray = lObj.optJSONArray("strokes")
        if (strokesArray != null) {
            for (sIdx in 0 until strokesArray.length()) {
                val sObj = strokesArray.optJSONObject(sIdx) ?: continue
                val sId = sObj.optString("id", java.util.UUID.randomUUID().toString())
                val authorId = sObj.optString("authorId", "")
                val toolStr = sObj.optString("tool", "PEN")
                val tool = try { ToolType.valueOf(toolStr) } catch (_: Exception) { ToolType.PEN }
                val color = sObj.optLong("color", 0xFFFFFFFF)
                val brushSize = sObj.optDouble("brushSize", 6.0).toFloat()
                val strokeOpacity = sObj.optDouble("opacity", 1.0).toFloat()
                val brushPresetId = sObj.optInt("brushPresetId", 1)
                val customBrushParams = if (sObj.has("customBrushParams") && !sObj.isNull("customBrushParams")) {
                    sObj.optString("customBrushParams", "").ifBlank { null }
                } else null
                val isFilled = sObj.optBoolean("isFilled", false)

                val pointsList = ArrayList<StrokePoint>()
                val pointsArray = sObj.optJSONArray("points")
                var sMinX = Float.MAX_VALUE
                var sMinY = Float.MAX_VALUE
                var sMaxX = -Float.MAX_VALUE
                var sMaxY = -Float.MAX_VALUE
                if (pointsArray != null) {
                    for (pIdx in 0 until pointsArray.length()) {
                        val pObj = pointsArray.optJSONObject(pIdx) ?: continue
                        val px = pObj.optDouble("x", 0.0).toFloat()
                        val py = pObj.optDouble("y", 0.0).toFloat()
                        if (px < sMinX) sMinX = px
                        if (px > sMaxX) sMaxX = px
                        if (py < sMinY) sMinY = py
                        if (py > sMaxY) sMaxY = py
                        pointsList.add(
                            StrokePoint(
                                x = px,
                                y = py,
                                pressure = pObj.optDouble("pressure", 1.0).toFloat()
                            )
                        )
                    }
                }
                val clipArr = sObj.optJSONArray("clipPolygon")
                val clipPoly = if (clipArr != null && clipArr.length() >= 3) {
                    val poly = ArrayList<StrokePoint>(clipArr.length())
                    for (ci in 0 until clipArr.length()) {
                        val cObj = clipArr.optJSONObject(ci) ?: continue
                        poly.add(
                            StrokePoint(
                                x = cObj.optDouble("x", 0.0).toFloat(),
                                y = cObj.optDouble("y", 0.0).toFloat()
                            )
                        )
                    }
                    if (poly.size >= 3) poly else null
                } else null
                strokesList.add(
                    DrawingStroke(
                        id = sId,
                        authorId = authorId,
                        tool = tool,
                        color = color,
                        brushSize = brushSize,
                        opacity = strokeOpacity,
                        points = pointsList,
                        isFilled = isFilled,
                        brushPresetId = brushPresetId,
                        customBrushParams = customBrushParams,
                        clipPolygon = clipPoly,
                        clipInverted = sObj.optBoolean("clipInverted", false),
                        clipBoundsWidth = sObj.optDouble("clipBoundsWidth", 0.0).toFloat(),
                        clipBoundsHeight = sObj.optDouble("clipBoundsHeight", 0.0).toFloat(),
                        minX = sMinX,
                        minY = sMinY,
                        maxX = sMaxX,
                        maxY = sMaxY
                    )
                )
            }
        }
        val attObj = lObj.optJSONObject("imageAttachment")
        val imageAttachment = if (attObj != null) {
            val u = attObj.optString("uriString", "")
            if (u.isNotBlank()) {
                val rawScale = attObj.optDouble("scalePercent", 1.0).toFloat()
                val normalizedScale = (if (rawScale > 5.0f) rawScale / 100f else rawScale).coerceIn(0.1f, 5.0f)
                com.example.model.LayerImageAttachment(
                    uriString = u,
                    scalePercent = normalizedScale,
                    rotationDegrees = attObj.optDouble("rotationDegrees", 0.0).toFloat(),
                    offsetX = attObj.optDouble("offsetX", 0.0).toFloat(),
                    offsetY = attObj.optDouble("offsetY", 0.0).toFloat(),
                    opacity = attObj.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1.0f)
                )
            } else null
        } else null
        return Layer(
            id = lId,
            name = lName,
            isVisible = isVisible,
            isLocked = isLocked,
            opacity = opacity,
            strokes = strokesList,
            blendEffect = blendEffect,
            isClippingMask = isClippingMask,
            imageAttachment = imageAttachment,
            isBucketLayer = isBucketLayer,
            isBucketReference = isBucketReference,
            celHoldTicks = if (lObj.has("celHoldTicks")) lObj.optInt("celHoldTicks", 1).coerceIn(0, 240) else null,
            celStartTick = if (lObj.has("celStartTick")) lObj.optInt("celStartTick", 0).coerceAtLeast(0) else null
        )
    }

    private fun objectKeyframesToJsonArray(
        keyframes: List<com.example.model.ObjectTransformKeyframe>
    ): JSONArray {
        val arr = JSONArray()
        keyframes.forEach { kf ->
            arr.put(JSONObject().apply {
                put("frameIndex", kf.frameIndex)
                put("x", kf.x.toDouble())
                put("y", kf.y.toDouble())
                put("z", kf.z.toDouble())
                put("rotX", kf.rotX.toDouble())
                put("rotY", kf.rotY.toDouble())
                put("rotZ", kf.rotZ.toDouble())
                put("scaleX", kf.scaleX.toDouble())
                put("scaleY", kf.scaleY.toDouble())
                put("scaleZ", kf.scaleZ.toDouble())
                put("opacity", kf.opacity.toDouble())
                put("easing", kf.easing.name)
                put("holdTicks", kf.holdTicks)
            })
        }
        return arr
    }

    private fun parseObjectKeyframes(arr: JSONArray?): List<com.example.model.ObjectTransformKeyframe> {
        if (arr == null) return emptyList()
        val out = ArrayList<com.example.model.ObjectTransformKeyframe>(arr.length())
        for (i in 0 until arr.length()) {
            val ko = arr.optJSONObject(i) ?: continue
            val easing = try {
                CameraEasingCurve.valueOf(ko.optString("easing", CameraEasingCurve.BEZIER_SUAVE.name))
            } catch (_: Exception) {
                CameraEasingCurve.BEZIER_SUAVE
            }
            out.add(
                com.example.model.ObjectTransformKeyframe(
                    frameIndex = ko.optInt("frameIndex", 0).coerceAtLeast(0),
                    x = ko.optDouble("x", 0.0).toFloat(),
                    y = ko.optDouble("y", 0.0).toFloat(),
                    z = ko.optDouble("z", 0.0).toFloat(),
                    rotX = ko.optDouble("rotX", 0.0).toFloat(),
                    rotY = ko.optDouble("rotY", 0.0).toFloat(),
                    rotZ = ko.optDouble("rotZ", 0.0).toFloat(),
                    scaleX = ko.optDouble("scaleX", 1.0).toFloat().coerceIn(0.05f, 20f),
                    scaleY = ko.optDouble("scaleY", 1.0).toFloat().coerceIn(0.05f, 20f),
                    scaleZ = ko.optDouble("scaleZ", 1.0).toFloat().coerceIn(0.05f, 20f),
                    opacity = ko.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                    easing = easing,
                    holdTicks = ko.optInt("holdTicks", 1).coerceIn(1, 240)
                )
            )
        }
        return out
    }

    fun camera3DToJson(cam: Camera3DSettings, includeTextures: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("isEnabled", cam.isEnabled)
            put("showCaptureFrame", cam.showCaptureFrame)
            put("autoInterpolate", cam.autoInterpolate)
            put("captureWidth", cam.captureWidth)
            put("captureHeight", cam.captureHeight)
            put("layerDepthSpacing", cam.layerDepthSpacing.toDouble())
            put("cameraMode", cam.cameraMode.name)
            val kArr = JSONArray()
            cam.keyframes.forEach { kf ->
                kArr.put(JSONObject().apply {
                    put("frameIndex", kf.frameIndex)
                    put("x", kf.x.toDouble())
                    put("y", kf.y.toDouble())
                    put("zoom", kf.zoom.toDouble())
                    put("rotationZ", kf.rotationZ.toDouble())
                    put("pitchX", kf.pitchX.toDouble())
                    put("yawY", kf.yawY.toDouble())
                    put("depthSeparation", kf.depthSeparation.toDouble())
                    put("fovDegrees", kf.fovDegrees.toDouble())
                    put("easing", kf.easing.name)
                    put("bezierP1X", kf.bezierP1X.toDouble())
                    put("bezierP1Y", kf.bezierP1Y.toDouble())
                    put("bezierP2X", kf.bezierP2X.toDouble())
                    put("bezierP2Y", kf.bezierP2Y.toDouble())
                    put("holdTicks", kf.holdTicks)
                })
            }
            put("keyframes", kArr)
            val spanArr = JSONArray()
            cam.effectSpans.forEach { sp ->
                spanArr.put(JSONObject().apply {
                    put("id", sp.id)
                    put("effect", sp.effect.name)
                    put("startFrame", sp.startFrame)
                    put("endFrame", sp.endFrame)
                    put("intensity", sp.intensity.toDouble())
                })
            }
            put("effectSpans", spanArr)
            put("postEffects", JSONObject().apply {
                put("vignette", cam.postEffects.vignette.toDouble())
                put("bloom", cam.postEffects.bloom.toDouble())
                put("chromaticAberration", cam.postEffects.chromaticAberration.toDouble())
                put("filmGrain", cam.postEffects.filmGrain.toDouble())
                put("sepia", cam.postEffects.sepia.toDouble())
                put("radialBlur", cam.postEffects.radialBlur.toDouble())
                put("contrastBoost", cam.postEffects.contrastBoost.toDouble())
            })
            val layerTrackArr = JSONArray()
            cam.layerTransformTracks.forEach { track ->
                layerTrackArr.put(JSONObject().apply {
                    put("layerId", track.layerId)
                    put("keyframes", objectKeyframesToJsonArray(track.keyframes))
                })
            }
            put("layerTransformTracks", layerTrackArr)
            val objArr = JSONArray()
            cam.scene3DObjects.forEach { obj ->
                objArr.put(sceneObject3DToJson(obj, includeTextures = includeTextures))
            }
            put("scene3DObjects", objArr)
        }
    }

    fun jsonToCamera3D(camObjParsed: JSONObject, defaultWidth: Int = 1280, defaultHeight: Int = 720): Camera3DSettings {
        val kArr = camObjParsed.optJSONArray("keyframes")
        val kfList = ArrayList<Camera3DKeyframe>()
        if (kArr != null) {
            for (k in 0 until kArr.length()) {
                val ko = kArr.optJSONObject(k) ?: continue
                val easing = try {
                    CameraEasingCurve.valueOf(ko.optString("easing", CameraEasingCurve.BEZIER_SUAVE.name))
                } catch (_: Exception) {
                    CameraEasingCurve.BEZIER_SUAVE
                }
                kfList.add(
                    Camera3DKeyframe(
                        frameIndex = ko.optInt("frameIndex", 0),
                        x = ko.optDouble("x", 0.0).toFloat(),
                        y = ko.optDouble("y", 0.0).toFloat(),
                        zoom = ko.optDouble("zoom", 1.0).toFloat(),
                        rotationZ = ko.optDouble("rotationZ", 0.0).toFloat(),
                        pitchX = ko.optDouble("pitchX", 0.0).toFloat(),
                        yawY = ko.optDouble("yawY", 0.0).toFloat(),
                        depthSeparation = ko.optDouble("depthSeparation", 38.0).toFloat(),
                        fovDegrees = ko.optDouble("fovDegrees", 50.0).toFloat().coerceIn(18f, 120f),
                        easing = easing,
                        bezierP1X = ko.optDouble("bezierP1X", 0.25).toFloat().coerceIn(0.01f, 0.99f),
                        bezierP1Y = ko.optDouble("bezierP1Y", 0.1).toFloat().coerceIn(-0.4f, 1.4f),
                        bezierP2X = ko.optDouble("bezierP2X", 0.25).toFloat().coerceIn(0.01f, 0.99f),
                        bezierP2Y = ko.optDouble("bezierP2Y", 1.0).toFloat().coerceIn(-0.4f, 1.4f),
                        holdTicks = ko.optInt("holdTicks", 1).coerceIn(1, 240)
                    )
                )
            }
        }
        val sArr = camObjParsed.optJSONArray("effectSpans")
        val spansList = ArrayList<com.example.model.CameraEffectSpan>()
        if (sArr != null) {
            for (si in 0 until sArr.length()) {
                val so = sArr.optJSONObject(si) ?: continue
                val ef = try {
                    com.example.model.CameraBuiltInEffect.valueOf(so.optString("effect", ""))
                } catch (_: Exception) { continue }
                spansList.add(
                    com.example.model.CameraEffectSpan(
                        id = so.optString("id", java.util.UUID.randomUUID().toString()),
                        effect = ef,
                        startFrame = so.optInt("startFrame", 0).coerceAtLeast(0),
                        endFrame = so.optInt("endFrame", 6).coerceAtLeast(0),
                        intensity = so.optDouble("intensity", 1.0).toFloat()
                    )
                )
            }
        }
        val camMode = try {
            com.example.model.CameraMode.valueOf(camObjParsed.optString("cameraMode", com.example.model.CameraMode.MODE_3D.name))
        } catch (_: Exception) { com.example.model.CameraMode.MODE_3D }
        val po = camObjParsed.optJSONObject("postEffects")
        val postEffects = if (po != null) {
            CameraPostEffectSettings(
                vignette = po.optDouble("vignette", 0.0).toFloat(),
                bloom = po.optDouble("bloom", 0.0).toFloat(),
                chromaticAberration = po.optDouble("chromaticAberration", 0.0).toFloat(),
                filmGrain = po.optDouble("filmGrain", 0.0).toFloat(),
                sepia = po.optDouble("sepia", 0.0).toFloat(),
                radialBlur = po.optDouble("radialBlur", 0.0).toFloat(),
                contrastBoost = po.optDouble("contrastBoost", 0.0).toFloat()
            )
        } else CameraPostEffectSettings()
        val layerTracksParsed = mutableListOf<com.example.model.LayerTransformTrack>()
        val ltArr = camObjParsed.optJSONArray("layerTransformTracks")
        if (ltArr != null) {
            for (i in 0 until ltArr.length()) {
                val to = ltArr.optJSONObject(i) ?: continue
                val lid = to.optString("layerId", "")
                if (lid.isNotBlank()) {
                    layerTracksParsed.add(
                        com.example.model.LayerTransformTrack(
                            layerId = lid,
                            keyframes = parseObjectKeyframes(to.optJSONArray("keyframes"))
                        )
                    )
                }
            }
        }
        val sceneObjsParsed = mutableListOf<com.example.model.SceneObject3D>()
        val soArr = camObjParsed.optJSONArray("scene3DObjects")
        if (soArr != null) {
            for (i in 0 until soArr.length()) {
                val oo = soArr.optJSONObject(i) ?: continue
                sceneObjsParsed.add(jsonToSceneObject3D(oo))
            }
        }
        return Camera3DSettings(
            isEnabled = camObjParsed.optBoolean("isEnabled", false),
            showCaptureFrame = camObjParsed.optBoolean("showCaptureFrame", true),
            autoInterpolate = camObjParsed.optBoolean("autoInterpolate", true),
            captureWidth = camObjParsed.optInt("captureWidth", defaultWidth).coerceIn(160, 4096),
            captureHeight = camObjParsed.optInt("captureHeight", defaultHeight).coerceIn(160, 4096),
            layerDepthSpacing = camObjParsed.optDouble("layerDepthSpacing", 38.0).toFloat(),
            keyframes = if (kfList.isEmpty()) listOf(Camera3DKeyframe(frameIndex = 0)) else kfList,
            postEffects = postEffects,
            cameraMode = camMode,
            effectSpans = spansList,
            layerTransformTracks = layerTracksParsed,
            scene3DObjects = sceneObjsParsed
        )
    }

    fun sceneObject3DToJson(obj: com.example.model.SceneObject3D, includeTextures: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("id", obj.id)
            put("name", obj.name)
            put("meshAssetPath", obj.meshAssetPath)
            put("formatTag", obj.formatTag)
            put("isVisible", obj.isVisible)
            put("colorTint", obj.colorTint)
            put("keyframes", objectKeyframesToJsonArray(obj.keyframes))
            put("shaderMode", obj.shaderMode.name)
            put("outline", JSONObject().apply {
                put("enabled", obj.outline.enabled)
                put("width", obj.outline.width.toDouble())
                put("colorArgb", obj.outline.colorArgb)
            })
            put("light", JSONObject().apply {
                put("azimuthDeg", obj.light.azimuthDeg.toDouble())
                put("elevationDeg", obj.light.elevationDeg.toDouble())
                put("intensity", obj.light.intensity.toDouble())
                put("ambient", obj.light.ambient.toDouble())
                put("rimStrength", obj.light.rimStrength.toDouble())
                put("emissiveGlow", obj.light.emissiveGlow.toDouble())
                put("lightColorArgb", obj.light.lightColorArgb)
            })
            val uvArr = JSONArray()
            obj.uvSlots.forEach { slot ->
                uvArr.put(JSONObject().apply {
                    put("id", slot.id)
                    put("name", slot.name)
                    put("projection", slot.projection.name)
                    put("scaleU", slot.scaleU.toDouble())
                    put("scaleV", slot.scaleV.toDouble())
                    put("offsetU", slot.offsetU.toDouble())
                    put("offsetV", slot.offsetV.toDouble())
                    if (includeTextures) {
                        slot.textureBase64?.let { put("textureBase64", it) }
                    }
                    slot.textureUri?.let { put("textureUri", it) }
                })
            }
            put("uvSlots", uvArr)
            put("activeUvSlotIndex", obj.activeUvSlotIndex)
            val plArr = JSONArray()
            obj.paintLayers.forEach { pl ->
                plArr.put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("isVisible", pl.isVisible)
                    put("opacity", pl.opacity.toDouble())
                    put("blendEffect", pl.blendEffect.name)
                    if (includeTextures) {
                        pl.pixelsBase64?.let { put("pixelsBase64", it) }
                    }
                    put("texWidth", pl.texWidth)
                    put("texHeight", pl.texHeight)
                })
            }
            put("paintLayers", plArr)
            put("activePaintLayerIndex", obj.activePaintLayerIndex)
            put("nodeGraph", JSONObject().apply {
                put("enabled", obj.nodeGraph.enabled)
                put("presetName", obj.nodeGraph.presetName)
                val nArr = JSONArray()
                obj.nodeGraph.nodes.forEach { nd ->
                    nArr.put(JSONObject().apply {
                        put("id", nd.id)
                        put("kind", nd.kind.name)
                        put("x", nd.x.toDouble())
                        put("y", nd.y.toDouble())
                        put("paramA", nd.paramA.toDouble())
                        put("paramB", nd.paramB.toDouble())
                        put("colorArgb", nd.colorArgb)
                    })
                }
                put("nodes", nArr)
                val lArr = JSONArray()
                obj.nodeGraph.links.forEach { lk ->
                    lArr.put(JSONObject().apply {
                        put("id", lk.id)
                        put("fromNodeId", lk.fromNodeId)
                        put("toNodeId", lk.toNodeId)
                    })
                }
                put("links", lArr)
            })
            if (includeTextures) {
                obj.bakedTextureBase64?.let { put("bakedTextureBase64", it) }
            }
        }
    }

    fun jsonToSceneObject3D(oo: JSONObject): com.example.model.SceneObject3D {
        val kfs = parseObjectKeyframes(oo.optJSONArray("keyframes"))
        val shaderMode = try {
            com.example.model.ObjectShaderMode.valueOf(
                oo.optString("shaderMode", com.example.model.ObjectShaderMode.PADRAO.name)
            )
        } catch (_: Exception) {
            com.example.model.ObjectShaderMode.PADRAO
        }
        val outObj = oo.optJSONObject("outline")
        val outline = if (outObj != null) {
            com.example.model.ObjectOutlineSettings(
                enabled = outObj.optBoolean("enabled", false),
                width = outObj.optDouble("width", 2.0).toFloat().coerceIn(0.5f, 8f),
                colorArgb = outObj.optLong("colorArgb", 0xFF10131CL)
            )
        } else com.example.model.ObjectOutlineSettings()

        val lObj = oo.optJSONObject("light")
        val light = if (lObj != null) {
            com.example.model.ObjectLightSettings(
                azimuthDeg = lObj.optDouble("azimuthDeg", 40.0).toFloat(),
                elevationDeg = lObj.optDouble("elevationDeg", 52.0).toFloat(),
                intensity = lObj.optDouble("intensity", 1.0).toFloat().coerceIn(0f, 3f),
                ambient = lObj.optDouble("ambient", 0.28).toFloat().coerceIn(0f, 1f),
                rimStrength = lObj.optDouble("rimStrength", 0.25).toFloat().coerceIn(0f, 1.5f),
                emissiveGlow = lObj.optDouble("emissiveGlow", 0.0).toFloat().coerceIn(0f, 2f),
                lightColorArgb = lObj.optLong("lightColorArgb", 0xFFFFFFFFL)
            )
        } else com.example.model.ObjectLightSettings()

        val uvSlots = mutableListOf<com.example.model.ObjectUVMapSlot>()
        val uvArr = oo.optJSONArray("uvSlots")
        if (uvArr != null) {
            for (u in 0 until uvArr.length()) {
                val uo = uvArr.optJSONObject(u) ?: continue
                val proj = try {
                    com.example.model.UvUnwrapProjection.valueOf(
                        uo.optString("projection", com.example.model.UvUnwrapProjection.ORIGINAL.name)
                    )
                } catch (_: Exception) {
                    com.example.model.UvUnwrapProjection.ORIGINAL
                }
                uvSlots.add(
                    com.example.model.ObjectUVMapSlot(
                        id = uo.optString("id", java.util.UUID.randomUUID().toString()),
                        name = uo.optString("name", "UVMap ${u + 1}"),
                        projection = proj,
                        scaleU = uo.optDouble("scaleU", 1.0).toFloat(),
                        scaleV = uo.optDouble("scaleV", 1.0).toFloat(),
                        offsetU = uo.optDouble("offsetU", 0.0).toFloat(),
                        offsetV = uo.optDouble("offsetV", 0.0).toFloat(),
                        textureBase64 = if (uo.has("textureBase64") && !uo.isNull("textureBase64")) uo.optString("textureBase64", "").ifBlank { null } else null,
                        textureUri = if (uo.has("textureUri") && !uo.isNull("textureUri")) uo.optString("textureUri", "").ifBlank { null } else null
                    )
                )
            }
        }
        if (uvSlots.isEmpty()) uvSlots.add(com.example.model.ObjectUVMapSlot())

        val paintLayers = mutableListOf<com.example.model.ObjectTexturePaintLayer>()
        val plArr = oo.optJSONArray("paintLayers")
        if (plArr != null) {
            for (p in 0 until plArr.length()) {
                val po = plArr.optJSONObject(p) ?: continue
                val blend = try {
                    LayerBlendEffect.valueOf(po.optString("blendEffect", LayerBlendEffect.NORMAL.name))
                } catch (_: Exception) {
                    LayerBlendEffect.NORMAL
                }
                paintLayers.add(
                    com.example.model.ObjectTexturePaintLayer(
                        id = po.optString("id", java.util.UUID.randomUUID().toString()),
                        name = po.optString("name", "Pintura ${p + 1}"),
                        isVisible = po.optBoolean("isVisible", true),
                        opacity = po.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                        blendEffect = blend,
                        pixelsBase64 = if (po.has("pixelsBase64") && !po.isNull("pixelsBase64")) po.optString("pixelsBase64", "").ifBlank { null } else null,
                        texWidth = po.optInt("texWidth", 256).coerceIn(32, 1024),
                        texHeight = po.optInt("texHeight", 256).coerceIn(32, 1024)
                    )
                )
            }
        }

        val ngObj = oo.optJSONObject("nodeGraph")
        val nodeGraph = if (ngObj != null) {
            val nodesList = mutableListOf<com.example.model.ShaderNode>()
            val nArr = ngObj.optJSONArray("nodes")
            if (nArr != null) {
                for (n in 0 until nArr.length()) {
                    val no = nArr.optJSONObject(n) ?: continue
                    val kind = try {
                        com.example.model.ShaderNodeKind.valueOf(no.optString("kind", ""))
                    } catch (_: Exception) { continue }
                    nodesList.add(
                        com.example.model.ShaderNode(
                            id = no.optString("id", java.util.UUID.randomUUID().toString()),
                            kind = kind,
                            x = no.optDouble("x", 60.0).toFloat(),
                            y = no.optDouble("y", 60.0).toFloat(),
                            paramA = no.optDouble("paramA", 0.5).toFloat(),
                            paramB = no.optDouble("paramB", 0.5).toFloat(),
                            colorArgb = no.optLong("colorArgb", 0xFFFFFFFFL)
                        )
                    )
                }
            }
            val linksList = mutableListOf<com.example.model.ShaderNodeLink>()
            val lArr = ngObj.optJSONArray("links")
            if (lArr != null) {
                for (l in 0 until lArr.length()) {
                    val lo = lArr.optJSONObject(l) ?: continue
                    linksList.add(
                        com.example.model.ShaderNodeLink(
                            id = lo.optString("id", java.util.UUID.randomUUID().toString()),
                            fromNodeId = lo.optString("fromNodeId", ""),
                            toNodeId = lo.optString("toNodeId", "")
                        )
                    )
                }
            }
            com.example.model.ShaderNodeGraph(
                enabled = ngObj.optBoolean("enabled", false),
                presetName = ngObj.optString("presetName", "Cartoon Studio"),
                nodes = if (nodesList.isEmpty()) com.example.model.ShaderNodeGraph.defaultNodes() else nodesList,
                links = if (nodesList.isEmpty()) com.example.model.ShaderNodeGraph.defaultLinks() else linksList
            )
        } else com.example.model.ShaderNodeGraph()

        return com.example.model.SceneObject3D(
            id = oo.optString("id", java.util.UUID.randomUUID().toString()),
            name = oo.optString("name", "Objeto 3D"),
            meshAssetPath = oo.optString("meshAssetPath", ""),
            formatTag = oo.optString("formatTag", "obj"),
            isVisible = oo.optBoolean("isVisible", true),
            colorTint = oo.optLong("colorTint", 0xFFFFFFFFL),
            keyframes = if (kfs.isEmpty()) listOf(com.example.model.ObjectTransformKeyframe(0)) else kfs,
            shaderMode = shaderMode,
            outline = outline,
            light = light,
            uvSlots = uvSlots,
            activeUvSlotIndex = oo.optInt("activeUvSlotIndex", 0).coerceIn(0, uvSlots.lastIndex),
            paintLayers = paintLayers,
            activePaintLayerIndex = oo.optInt("activePaintLayerIndex", 0).coerceIn(0, (paintLayers.size - 1).coerceAtLeast(0)),
            nodeGraph = nodeGraph,
            bakedTextureBase64 = if (oo.has("bakedTextureBase64") && !oo.isNull("bakedTextureBase64")) oo.optString("bakedTextureBase64", "").ifBlank { null } else null
        )
    }
}
