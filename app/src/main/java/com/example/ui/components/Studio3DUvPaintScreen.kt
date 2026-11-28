package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Camera3DEngine
import com.example.engine.NativeGL3DEngine
import com.example.engine.Studio3DPaintEngine
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.Estudio3DPaintTool
import com.example.model.KodanimaTheme
import com.example.model.LayerBlendEffect
import com.example.model.ObjectTexturePaintLayer
import com.example.model.SceneObject3D
import com.example.model.StageShadingMode
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fullscreen UV Texture Painting Studio ("Pintura UV") for 3D objects:
 * - Fullscreen interactive 3D viewport (1-finger Paint or Orbit + 2-finger Pinch Zoom/Pan)
 * - Reuses Mobile 2D Brush Picker (`ProcreateBrushPickerSheet` + `BrushStudioDrawer`)
 * - Paint tools: Pincel, Borracha, Molhador + Color Picker
 * - Paint layers with Blend Modes + "Mesclar Camadas em 1 UV só"
 * - Unfolded 2D UV window toggle + Export Selected 3D Object (.zip with .obj/.mtl/texture)
 */
@Composable
fun Studio3DUvPaintScreen(
    sceneObjects: List<SceneObject3D>,
    selectedObjectId: String?,
    onSelectObject: (String) -> Unit,
    onUpdateObject: (String, Boolean, (SceneObject3D) -> SceneObject3D) -> Unit,
    onPaintUVSegment: (String, Float, Float, Float, Float) -> Unit,
    onMergePaintLayers: (String) -> Unit,
    onExportObjectZip: (SceneObject3D) -> Unit,
    paintTool: Estudio3DPaintTool,
    onSelectPaintTool: (Estudio3DPaintTool) -> Unit,
    paintSize: Float,
    onChangePaintSize: (Float) -> Unit,
    paintOpacity: Float,
    onChangePaintOpacity: (Float) -> Unit,
    activeBrushPreset: BrushPreset,
    nativeBrushPresets: List<BrushPreset>,
    importedBrushes: List<BrushPreset>,
    onSelectBrushPreset: (BrushPreset) -> Unit,
    onSaveBrushCustomization: (BrushPreset, Float, Float, BrushDynamicParams) -> Unit,
    onResetBrushCustomization: (BrushPreset) -> BrushPreset,
    onRequestImportKrita: () -> Unit,
    onDeleteImportedBrush: (Int) -> Unit,
    currentColorArgb: Long,
    onOpenColorPicker: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenUvVertexEditor: () -> Unit = {},
    theme: KodanimaTheme,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)

    val selectedObj = remember(sceneObjects, selectedObjectId) {
        sceneObjects.firstOrNull { it.id == selectedObjectId } ?: sceneObjects.firstOrNull()
    }

    var isOrbitMode by remember { mutableStateOf(false) }
    var useRenderedLighting by remember { mutableStateOf(true) }
    var showBrushPicker by remember { mutableStateOf(false) }
    var showBrushStudioDrawer by remember { mutableStateOf(false) }
    var showLayersDrawer by remember { mutableStateOf(false) }
    var showUvUnfold by remember { mutableStateOf(false) }

    var camYaw by remember { mutableFloatStateOf(24f) }
    var camPitch by remember { mutableFloatStateOf(-14f) }
    var viewZoom by remember { mutableFloatStateOf(1.15f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var isInteracting by remember { mutableStateOf(false) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF202738), Color(0xFF0E1119))
                )
            )
            .testTag("studio3d_uv_paint_screen")
    ) {
        val density = LocalDensity.current
        val viewWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewHPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val hasGl3D = remember { NativeGL3DEngine.isHardwareAvailable() }
        val renderW = if (hasGl3D) {
            if (isInteracting) 720 else 1080
        } else {
            if (isInteracting) 420 else 640
        }
        val renderH = ((renderW.toFloat() * (viewHPx / viewWPx)).toInt()).coerceIn(280, if (hasGl3D) 1440 else 900)
        val vpScaleX = renderW.toFloat() / viewWPx
        val vpScaleY = renderH.toFloat() / viewHPx
        val baseUnitScale = (minOf(renderW, renderH) / 560f).coerceIn(0.45f, 1.35f)
        val worldUnitScale = baseUnitScale * viewZoom
        val focalPx = 440f * viewZoom
        val vpCx = renderW * 0.5f + panX * vpScaleX
        val vpCy = renderH * 0.5f + panY * vpScaleY

        val shading = if (useRenderedLighting) StageShadingMode.RENDERED else StageShadingMode.TEXTURE

        LaunchedEffect(
            selectedObj,
            camYaw,
            camPitch,
            viewZoom,
            panX,
            panY,
            renderW,
            renderH,
            shading
        ) {
            val obj = selectedObj
            if (obj == null) {
                previewBitmap = null
                return@LaunchedEffect
            }
            val result = withContext(Dispatchers.Default) {
                Camera3DEngine.renderSceneObjectsSoftware3D(
                    outW = renderW,
                    outH = renderH,
                    sceneObjects = listOf(obj),
                    frameIndex = 0,
                    autoInterpolate = true,
                    camYawDeg = camYaw,
                    camPitchDeg = camPitch,
                    camRollDeg = 0f,
                    camPanX = 0f,
                    camPanY = 0f,
                    focalPx = focalPx,
                    vpCx = vpCx,
                    vpCy = vpCy,
                    worldUnitScale = worldUnitScale,
                    selectedObjectId = null,
                    shadingMode = shading
                )
            }
            previewBitmap = result?.first
        }

        // Fullscreen 3D Viewport Image
        previewBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Pintura UV 3D em tela cheia",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (selectedObj == null) "Nenhum objeto 3D no palco. Importe ou adicione um objeto." else "Carregando malha 3D...",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
        }

        // Interactive 1-finger (Paint / Orbit) + 2-finger (Pinch Zoom / Pan) Gesture Surface
        val curObjState by rememberUpdatedState(selectedObj)
        val curOrbitState by rememberUpdatedState(isOrbitMode)
        val curYawState by rememberUpdatedState(camYaw)
        val curPitchState by rememberUpdatedState(camPitch)
        val curZoomState by rememberUpdatedState(viewZoom)
        val curFocalState by rememberUpdatedState(focalPx)
        val curWorldScaleState by rememberUpdatedState(worldUnitScale)
        val curVpCxState by rememberUpdatedState(vpCx)
        val curVpCyState by rememberUpdatedState(vpCy)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(renderW, renderH, paintTool, paintSize, paintOpacity, activeBrushPreset.id, currentColorArgb) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()
                        isInteracting = true
                        var prevPos = firstDown.position
                        var prevUv: Pair<Float, Float>? = null
                        var isMultiTouch = false

                        val obj0 = curObjState
                        if (!curOrbitState && obj0 != null) {
                            val sx = firstDown.position.x * (renderW.toFloat() / size.width.coerceAtLeast(1))
                            val sy = firstDown.position.y * (renderH.toFloat() / size.height.coerceAtLeast(1))
                            val hit = Studio3DPaintEngine.pickObjectTriangleUV(
                                obj = obj0,
                                frameIndex = 0,
                                autoInterpolate = true,
                                screenX = sx,
                                screenY = sy,
                                camYawDeg = curYawState,
                                camPitchDeg = curPitchState,
                                camRollDeg = 0f,
                                camPanX = 0f,
                                camPanY = 0f,
                                focalPx = curFocalState,
                                vpCx = curVpCxState,
                                vpCy = curVpCyState,
                                worldUnitScale = curWorldScaleState
                            )
                            if (hit != null) {
                                prevUv = hit.u to hit.v
                                onPaintUVSegment(obj0.id, hit.u, hit.v, hit.u, hit.v)
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break

                            if (activeChanges.size >= 2) {
                                isMultiTouch = true
                                val zoomFactor = event.calculateZoom()
                                val panDelta = event.calculatePan()
                                if (kotlin.math.abs(zoomFactor - 1f) > 0.002f) {
                                    viewZoom = (curZoomState * zoomFactor).coerceIn(0.35f, 4.0f)
                                }
                                panX = (panX + panDelta.x).coerceIn(-viewWPx * 0.6f, viewWPx * 0.6f)
                                panY = (panY + panDelta.y).coerceIn(-viewHPx * 0.6f, viewHPx * 0.6f)
                                event.changes.forEach { it.consume() }
                            } else if (!isMultiTouch) {
                                val ch = activeChanges.first()
                                ch.consume()
                                val objNow = curObjState
                                if (!curOrbitState && objNow != null) {
                                    val sx = ch.position.x * (renderW.toFloat() / size.width.coerceAtLeast(1))
                                    val sy = ch.position.y * (renderH.toFloat() / size.height.coerceAtLeast(1))
                                    val hit = Studio3DPaintEngine.pickObjectTriangleUV(
                                        obj = objNow,
                                        frameIndex = 0,
                                        autoInterpolate = true,
                                        screenX = sx,
                                        screenY = sy,
                                        camYawDeg = curYawState,
                                        camPitchDeg = curPitchState,
                                        camRollDeg = 0f,
                                        camPanX = 0f,
                                        camPanY = 0f,
                                        focalPx = curFocalState,
                                        vpCx = curVpCxState,
                                        vpCy = curVpCyState,
                                        worldUnitScale = curWorldScaleState
                                    )
                                    if (hit != null) {
                                        val last = prevUv ?: (hit.u to hit.v)
                                        onPaintUVSegment(objNow.id, last.first, last.second, hit.u, hit.v)
                                        prevUv = hit.u to hit.v
                                    }
                                } else {
                                    val dx = ch.position.x - prevPos.x
                                    val dy = ch.position.y - prevPos.y
                                    camYaw += dx * 0.42f
                                    camPitch = (camPitch + dy * 0.42f).coerceIn(-85f, 85f)
                                }
                                prevPos = ch.position
                            }
                        }
                        isInteracting = false
                    }
                }
        )

        // TOP BAR: Back, Object Name, Quick Angle Presets, Undo/Redo, Exportar Objeto
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = panelColor.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.55f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("uv_paint_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar ao Estúdio 3D", tint = textColor)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pintura UV • ${selectedObj?.name ?: "Sem Objeto"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonPink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isOrbitMode) "Modo Orbitar (1 dedo gira • 2 dedos zoom)" else "Modo Pintar (1 dedo pinta • 2 dedos zoom/mover)",
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { if (canUndo) onUndo() },
                        enabled = canUndo,
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("uv_paint_undo_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Desfazer",
                            tint = if (canUndo) textColor else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (canRedo) onRedo() },
                        enabled = canRedo,
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("uv_paint_redo_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Refazer",
                            tint = if (canRedo) textColor else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (selectedObj != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onExportObjectZip(selectedObj) }
                                .border(1.dp, NeonGreen.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                                .testTag("uv_paint_export_obj_btn"),
                            color = NeonGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Exportar Objeto 3D", tint = NeonGreen, modifier = Modifier.size(15.dp))
                                Text("Exportar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                            }
                        }
                    }
                }
            }

            // Quick Camera Angles & Object Switcher strip (easy reach to back/sides/top/bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sceneObjects.size > 1) {
                    sceneObjects.forEach { obj ->
                        UvPaintChip(
                            label = obj.name.take(14),
                            selected = obj.id == selectedObj?.id,
                            accent = OrangeAccent,
                            panelColor = panelColor,
                            textColor = textColor
                        ) { onSelectObject(obj.id) }
                    }
                }
                UvPaintChip("Frente", false, CyanAccent, panelColor, textColor) {
                    camYaw = 0f; camPitch = 0f
                }
                UvPaintChip("Costas", false, CyanAccent, panelColor, textColor) {
                    camYaw = 180f; camPitch = 0f
                }
                UvPaintChip("Lado", false, CyanAccent, panelColor, textColor) {
                    camYaw = 90f; camPitch = 0f
                }
                UvPaintChip("Topo", false, CyanAccent, panelColor, textColor) {
                    camYaw = 0f; camPitch = -78f
                }
                UvPaintChip("Baixo", false, CyanAccent, panelColor, textColor) {
                    camYaw = 0f; camPitch = 78f
                }
                UvPaintChip(
                    label = if (useRenderedLighting) "Luz: ON" else "Luz: OFF",
                    selected = useRenderedLighting,
                    accent = OrangeAccent,
                    panelColor = panelColor,
                    textColor = textColor
                ) { useRenderedLighting = !useRenderedLighting }
                UvPaintChip("Centralizar", false, NeonGreen, panelColor, textColor, Icons.Default.RestartAlt) {
                    camYaw = 24f; camPitch = -14f; viewZoom = 1.15f; panX = 0f; panY = 0f
                }
            }
        }

        // Optional Unfolded UV Window
        if (showUvUnfold && selectedObj != null) {
            val activeParamsJson = remember(activeBrushPreset) { activeBrushPreset.dynamicParams.toCompactJson() }
            UvUnfoldWindowOverlay(
                selectedObject = selectedObj,
                paintTool = paintTool,
                paintSize = paintSize,
                paintOpacity = paintOpacity,
                activeBrushId = activeBrushPreset.id,
                activeBrushParamsJson = activeParamsJson,
                currentColorArgb = currentColorArgb,
                onPaintUVSegment = onPaintUVSegment,
                onOpenUvVertexEditor = onOpenUvVertexEditor,
                onClose = { showUvUnfold = false },
                bgColor = panelColor,
                textColor = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 96.dp, end = 10.dp)
            )
        }

        // Paint Layers Drawer (camadas de pintura + Mesclar em 1 UV só)
        AnimatedVisibility(
            visible = showLayersDrawer && selectedObj != null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 78.dp)
        ) {
            if (selectedObj != null) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 250.dp, max = 330.dp)
                        .testTag("uv_paint_layers_drawer"),
                    color = panelColor.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.6f)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 270.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Camadas UV (${selectedObj.paintLayers.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = OrangeAccent)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                UvPaintChip("+ Camada", false, NeonGreen, panelColor, textColor, Icons.Default.Add) {
                                    onUpdateObject(selectedObj.id, true) { cur ->
                                        val nextLayer = ObjectTexturePaintLayer(name = "Camada ${cur.paintLayers.size + 1}")
                                        val nextList = cur.paintLayers + nextLayer
                                        cur.copy(paintLayers = nextList, activePaintLayerIndex = nextList.lastIndex)
                                    }
                                }
                                IconButton(onClick = { showLayersDrawer = false }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar camadas", tint = textColor, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        selectedObj.paintLayers.forEachIndexed { idx, layer ->
                            val isActive = idx == selectedObj.activePaintLayerIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) NeonPink.copy(alpha = 0.16f) else textColor.copy(alpha = 0.06f))
                                    .border(1.dp, if (isActive) NeonPink else textColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onUpdateObject(selectedObj.id, true) { it.copy(activePaintLayerIndex = idx) }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        onUpdateObject(selectedObj.id, true) { cur ->
                                            val list = cur.paintLayers.toMutableList()
                                            list[idx] = list[idx].copy(isVisible = !list[idx].isVisible)
                                            cur.copy(paintLayers = list)
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Visibilidade",
                                        tint = if (layer.isVisible) CyanAccent else textColor.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(layer.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f), maxLines = 1)
                                Surface(
                                    color = CyanAccent.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        val all = LayerBlendEffect.entries
                                        val nextB = all[(layer.blendEffect.ordinal + 1) % all.size]
                                        onUpdateObject(selectedObj.id, true) { cur ->
                                            val list = cur.paintLayers.toMutableList()
                                            list[idx] = list[idx].copy(blendEffect = nextB)
                                            cur.copy(paintLayers = list)
                                        }
                                    }
                                ) {
                                    Text(layer.blendEffect.labelPt, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                                IconButton(
                                    onClick = {
                                        onUpdateObject(selectedObj.id, true) { cur ->
                                            val list = cur.paintLayers.filterIndexed { i, _ -> i != idx }
                                            cur.copy(paintLayers = list, activePaintLayerIndex = cur.activePaintLayerIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir camada", tint = NeonPink, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Button(
                            onClick = { onMergePaintLayers(selectedObj.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("uv_paint_merge_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.MergeType, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Mesclar em 1 UV só", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // Reused Mobile 2D Brush Picker Sheet (Krita 12 + Imported + Sliders)
        ProcreateBrushPickerSheet(
            visible = showBrushPicker,
            activePreset = activeBrushPreset,
            brushSize = paintSize,
            brushOpacity = paintOpacity,
            importedBrushes = importedBrushes,
            theme = theme,
            onSelectPreset = onSelectBrushPreset,
            onChangeSize = onChangePaintSize,
            onChangeOpacity = onChangePaintOpacity,
            onRequestImportKrita = onRequestImportKrita,
            onDeleteImportedBrush = onDeleteImportedBrush,
            onDismiss = { showBrushPicker = false },
            onOpenBrushStudio = {
                showBrushPicker = false
                showBrushStudioDrawer = true
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 78.dp)
        )

        BrushStudioDrawer(
            visible = showBrushStudioDrawer,
            activePreset = activeBrushPreset,
            nativePresets = nativeBrushPresets,
            importedPresets = importedBrushes,
            theme = theme,
            onSelectPreset = onSelectBrushPreset,
            onSaveBrushCustomization = onSaveBrushCustomization,
            onResetBrushCustomization = onResetBrushCustomization,
            onRequestImportKrita = onRequestImportKrita,
            onClose = { showBrushStudioDrawer = false },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        )

        // BOTTOM PINTURA UV TOOLBAR
        Surface(
            color = panelColor.copy(alpha = 0.96f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("uv_paint_bottom_toolbar")
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Navigation/Paint mode switch
                UvPaintChip(
                    label = if (isOrbitMode) "Orbitar" else "Pintar",
                    selected = true,
                    accent = if (isOrbitMode) OrangeAccent else NeonGreen,
                    panelColor = bgColor,
                    textColor = textColor,
                    icon = if (isOrbitMode) Icons.Default.Explore else Icons.Default.Brush,
                    testTag = "uv_paint_nav_toggle"
                ) { isOrbitMode = !isOrbitMode }

                // Color swatch button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(currentColorArgb))
                        .border(2.dp, accentColor, CircleShape)
                        .clickable { onOpenColorPicker() }
                        .testTag("uv_paint_color_btn")
                )

                // Paint tools (Pincel, Borracha, Molhador)
                Estudio3DPaintTool.entries.forEach { t ->
                    val ic = when (t) {
                        Estudio3DPaintTool.PINCEL -> Icons.Default.Brush
                        Estudio3DPaintTool.BORRACHA -> Icons.Default.DeleteOutline
                        Estudio3DPaintTool.MOLHADOR -> Icons.Default.WaterDrop
                    }
                    UvPaintChip(
                        label = t.labelPt,
                        selected = !isOrbitMode && paintTool == t,
                        accent = NeonPink,
                        panelColor = bgColor,
                        textColor = textColor,
                        icon = ic,
                        testTag = "uv_paint_tool_${t.name}"
                    ) {
                        isOrbitMode = false
                        onSelectPaintTool(t)
                    }
                }

                // Reused Mobile 2D Brush Selector Pill
                Surface(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (showBrushPicker) accentColor else accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { showBrushPicker = !showBrushPicker }
                        .testTag("uv_paint_brush_picker_trigger"),
                    color = bgColor.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KritaBrushStrokeWaveformPreview(
                            preset = activeBrushPreset,
                            strokeColor = accentColor,
                            modifier = Modifier.width(30.dp).height(14.dp)
                        )
                        Text(
                            text = "${activeBrushPreset.shortLabel} • ${paintSize.toInt()}px",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                        ThreeWheelsIcon(tint = textColor, accentColor = accentColor, size = 14.dp)
                    }
                }

                UvPaintChip(
                    label = "Camadas (${selectedObj?.paintLayers?.size ?: 0})",
                    selected = showLayersDrawer,
                    accent = OrangeAccent,
                    panelColor = bgColor,
                    textColor = textColor,
                    icon = Icons.Default.Layers,
                    testTag = "uv_paint_layers_btn"
                ) { showLayersDrawer = !showLayersDrawer }

                UvPaintChip(
                    label = "Janela UV",
                    selected = showUvUnfold,
                    accent = CyanAccent,
                    panelColor = bgColor,
                    textColor = textColor,
                    icon = Icons.Default.GridOn,
                    testTag = "uv_paint_unfold_btn"
                ) { showUvUnfold = !showUvUnfold }

                UvPaintChip(
                    label = "Pontos UV",
                    selected = false,
                    accent = OrangeAccent,
                    panelColor = bgColor,
                    textColor = textColor,
                    icon = Icons.Default.Grain,
                    testTag = "uv_paint_open_vertex_editor_btn"
                ) { onOpenUvVertexEditor() }
            }
        }
    }
}

@Composable
private fun UvPaintChip(
    label: String,
    selected: Boolean,
    accent: Color,
    panelColor: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.25f) else panelColor.copy(alpha = 0.85f))
            .border(1.dp, if (selected) accent else textColor.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = if (selected) accent else textColor, modifier = Modifier.size(14.dp))
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
