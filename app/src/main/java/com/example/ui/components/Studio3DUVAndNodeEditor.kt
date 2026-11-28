package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Camera3DEngine
import com.example.engine.Mesh3DImporter
import com.example.engine.NativeGL3DEngine
import com.example.engine.Studio3DPaintEngine
import com.example.model.BrushPreset
import com.example.model.Estudio3DPaintTool
import com.example.model.LayerBlendEffect
import com.example.model.ObjectLightSettings
import com.example.model.ObjectOutlineSettings
import com.example.model.ObjectShaderMode
import com.example.model.ObjectTexturePaintLayer
import com.example.model.ObjectUVMapSlot
import com.example.model.SceneObject3D
import com.example.model.ShaderNode
import com.example.model.ShaderNodeGraph
import com.example.model.ShaderNodeKind
import com.example.model.ShaderNodeLink
import com.example.model.UvUnwrapProjection
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.example.model.CustomShaderNodePreset
import com.example.model.StageShadingMode

private val STUDIO_3D_SWATCHES = listOf(
    0xFF10131CL, 0xFFFFFFFFL, 0xFF00E5FFL, 0xFFFF3366L,
    0xFFFF9100L, 0xFF39FF14L, 0xFFFFD54FL, 0xFF8E44ADL
)

/**
 * Estúdio 3D "3 Rodas" Sheet:
 * - Controles globais do Palco 3D: Importar 3D (.glb/.gltf/.obj/.fbx/.max/.3ds/.zip/.rar), 4 Modos de Visualização, Ocultar/Mostrar Grade do Chão
 * - Tab 0: Textura & UV (UVMaps, projeções Planar/Box/Esférico/Cilíndrico, escala/offset, importar textura, Janela UV Desdobrado)
 * - Tab 1: Shader & Luz (PBR / Cel Anime / Cartoon Brush / Sem Luz / Aramado, Contorno, Iluminação própria)
 * - Tab 2: Pintura UV (abre tela cheia dedicada "Pintura UV" + Exportar Objeto 3D + Mesclar Camadas)
 */
@Composable
fun Studio3DThreeWheelsSheet(
    sceneObjects: List<SceneObject3D>,
    selectedObjectId: String?,
    onSelectObject: (String) -> Unit,
    onUpdateObject: (String, Boolean, (SceneObject3D) -> SceneObject3D) -> Unit,
    onPickTextureImage: (String, Int) -> Unit,
    isUvUnfoldOpen: Boolean,
    onToggleUvUnfold: () -> Unit,
    onOpenUvVertexEditor: () -> Unit = {},
    onOpenSplitNodes: () -> Unit,
    onOpenFullscreenUvPaint: () -> Unit = {},
    onRequestImport3D: () -> Unit = {},
    onExportObjectZip: (SceneObject3D) -> Unit = {},
    shadingMode: StageShadingMode = StageShadingMode.RENDERED,
    onSelectShadingMode: (StageShadingMode) -> Unit = {},
    showFloorGrid: Boolean = true,
    onToggleFloorGrid: () -> Unit = {},
    studio3DPaintEnabled: Boolean,
    onToggleStudio3DPaint: (Boolean) -> Unit,
    paintTool: Estudio3DPaintTool,
    onSelectPaintTool: (Estudio3DPaintTool) -> Unit,
    paintSize: Float,
    onChangePaintSize: (Float) -> Unit,
    paintOpacity: Float,
    onChangePaintOpacity: (Float) -> Unit,
    activeBrushId: Int,
    importedBrushes: List<BrushPreset>,
    onSelectBrushPreset: (BrushPreset) -> Unit,
    currentColorArgb: Long,
    onMergePaintLayers: (String) -> Unit,
    onClose: () -> Unit,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val selectedObj = remember(sceneObjects, selectedObjectId) {
        sceneObjects.firstOrNull { it.id == selectedObjectId } ?: sceneObjects.firstOrNull()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .navigationBarsPadding()
            .testTag("studio3d_three_wheels_sheet"),
        color = bgColor.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.6f)),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Estúdio 3D • 3 Rodas",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StudioPill(
                        label = "Importar 3D",
                        selected = false,
                        accent = CyanAccent,
                        textColor = textColor,
                        icon = Icons.Default.FolderOpen,
                        testTag = "wheels_import_3d_btn"
                    ) { onRequestImport3D() }

                    StudioPill(
                        label = "Nodes",
                        selected = false,
                        accent = NeonGreen,
                        textColor = textColor,
                        icon = Icons.Default.Hub,
                        testTag = "wheels_open_nodes_btn"
                    ) { onOpenSplitNodes() }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("wheels_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar 3 Rodas", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 4 Modos de Visualização do Blender + Ocultar Grade do Chão
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Visão:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                StageShadingMode.entries.forEach { mode ->
                    StudioPill(
                        label = mode.labelPt,
                        selected = shadingMode == mode,
                        accent = OrangeAccent,
                        textColor = textColor,
                        testTag = "wheels_shading_${mode.name}"
                    ) { onSelectShadingMode(mode) }
                }
                StudioPill(
                    label = if (showFloorGrid) "Grade: ON" else "Grade: OFF",
                    selected = showFloorGrid,
                    accent = if (showFloorGrid) NeonGreen else NeonPink,
                    textColor = textColor,
                    icon = if (showFloorGrid) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    testTag = "wheels_floor_grid_toggle"
                ) { onToggleFloorGrid() }
            }

            if (sceneObjects.isEmpty() || selectedObj == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Importe modelos 3D (OBJ, GLB, GLTF, FBX, MAX, 3DS, ZIP ou RAR — com UVs, texturas e ossos automáticos) ou adicione uma primitiva em Cenas 3D.",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = onRequestImport3D,
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("wheels_empty_import_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar Arquivo 3D / ZIP / RAR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }
                }
                return@Column
            }

            // Object Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Objeto:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.75f))
                sceneObjects.forEach { obj ->
                    StudioPill(
                        label = obj.name.take(18),
                        selected = obj.id == selectedObj.id,
                        accent = CyanAccent,
                        textColor = textColor,
                        testTag = "wheels_obj_${obj.id}"
                    ) { onSelectObject(obj.id) }
                }
            }

            // 3 Wheel Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioPill(
                    label = "Textura & UV",
                    selected = activeTab == 0,
                    accent = OrangeAccent,
                    textColor = textColor,
                    icon = Icons.Default.GridOn,
                    testTag = "wheels_tab_uv",
                    modifier = Modifier.weight(1f)
                ) { activeTab = 0 }

                StudioPill(
                    label = "Shader & Luz",
                    selected = activeTab == 1,
                    accent = CyanAccent,
                    textColor = textColor,
                    icon = Icons.Default.LightMode,
                    testTag = "wheels_tab_shader",
                    modifier = Modifier.weight(1f)
                ) { activeTab = 1 }

                StudioPill(
                    label = "Pintura UV",
                    selected = activeTab == 2,
                    accent = NeonPink,
                    textColor = textColor,
                    icon = Icons.Default.Brush,
                    testTag = "wheels_tab_paint",
                    modifier = Modifier.weight(1f)
                ) { activeTab = 2 }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 285.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeTab) {
                    // =====================================================
                    // TAB 0: TEXTURA & UV
                    // =====================================================
                    0 -> {
                        val slots = selectedObj.uvSlots.ifEmpty { listOf(ObjectUVMapSlot()) }
                        val activeIdx = selectedObj.activeUvSlotIndex.coerceIn(0, slots.lastIndex)
                        val activeSlot = slots[activeIdx]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UVs:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.8f))
                            slots.forEachIndexed { idx, slot ->
                                StudioPill(
                                    label = slot.name,
                                    selected = idx == activeIdx,
                                    accent = OrangeAccent,
                                    textColor = textColor,
                                    testTag = "uv_slot_$idx"
                                ) {
                                    onUpdateObject(selectedObj.id, true) {
                                        it.copy(activeUvSlotIndex = idx)
                                    }
                                }
                            }
                            StudioPill(
                                label = "+ Novo UV",
                                selected = false,
                                accent = NeonGreen,
                                textColor = textColor,
                                icon = Icons.Default.Add,
                                testTag = "uv_add_slot_btn"
                            ) {
                                onUpdateObject(selectedObj.id, true) { cur ->
                                    val newSlot = ObjectUVMapSlot(
                                        name = "UVMap ${cur.uvSlots.size + 1}",
                                        projection = UvUnwrapProjection.BOX
                                    )
                                    val nextList = cur.uvSlots + newSlot
                                    cur.copy(uvSlots = nextList, activeUvSlotIndex = nextList.lastIndex)
                                }
                            }
                        }

                        // Projection Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Projeção:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.8f))
                            UvUnwrapProjection.entries.forEach { proj ->
                                StudioPill(
                                    label = proj.labelPt,
                                    selected = activeSlot.projection == proj,
                                    accent = CyanAccent,
                                    textColor = textColor,
                                    testTag = "uv_proj_${proj.name}"
                                ) {
                                    onUpdateObject(selectedObj.id, true) { cur ->
                                        val updated = cur.uvSlots.toMutableList()
                                        if (updated.isEmpty()) updated.add(ObjectUVMapSlot())
                                        val i = cur.activeUvSlotIndex.coerceIn(0, updated.lastIndex)
                                        updated[i] = updated[i].copy(projection = proj, customUvs = emptyList())
                                        cur.copy(uvSlots = updated)
                                    }
                                }
                            }
                        }

                        // Tile Scale & Offset Sliders
                        StudioSlider(
                            label = "Escala U",
                            valueText = String.format("%.2fx", activeSlot.scaleU),
                            value = activeSlot.scaleU,
                            range = 0.25f..4.0f,
                            accent = OrangeAccent,
                            textColor = textColor
                        ) { v ->
                            onUpdateObject(selectedObj.id, true) { cur ->
                                val updated = cur.uvSlots.toMutableList().ifEmpty { mutableListOf(ObjectUVMapSlot()) }
                                val i = cur.activeUvSlotIndex.coerceIn(0, updated.lastIndex)
                                updated[i] = updated[i].copy(scaleU = v)
                                cur.copy(uvSlots = updated)
                            }
                        }
                        StudioSlider(
                            label = "Escala V",
                            valueText = String.format("%.2fx", activeSlot.scaleV),
                            value = activeSlot.scaleV,
                            range = 0.25f..4.0f,
                            accent = OrangeAccent,
                            textColor = textColor
                        ) { v ->
                            onUpdateObject(selectedObj.id, true) { cur ->
                                val updated = cur.uvSlots.toMutableList().ifEmpty { mutableListOf(ObjectUVMapSlot()) }
                                val i = cur.activeUvSlotIndex.coerceIn(0, updated.lastIndex)
                                updated[i] = updated[i].copy(scaleV = v)
                                cur.copy(uvSlots = updated)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioSlider(
                                label = "Desloc U",
                                valueText = String.format("%.2f", activeSlot.offsetU),
                                value = activeSlot.offsetU,
                                range = -1f..1f,
                                accent = CyanAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { cur ->
                                    val updated = cur.uvSlots.toMutableList().ifEmpty { mutableListOf(ObjectUVMapSlot()) }
                                    val i = cur.activeUvSlotIndex.coerceIn(0, updated.lastIndex)
                                    updated[i] = updated[i].copy(offsetU = v)
                                    cur.copy(uvSlots = updated)
                                }
                            }
                            StudioSlider(
                                label = "Desloc V",
                                valueText = String.format("%.2f", activeSlot.offsetV),
                                value = activeSlot.offsetV,
                                range = -1f..1f,
                                accent = CyanAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { cur ->
                                    val updated = cur.uvSlots.toMutableList().ifEmpty { mutableListOf(ObjectUVMapSlot()) }
                                    val i = cur.activeUvSlotIndex.coerceIn(0, updated.lastIndex)
                                    updated[i] = updated[i].copy(offsetV = v)
                                    cur.copy(uvSlots = updated)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPickTextureImage(selectedObj.id, activeIdx) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .testTag("uv_pick_texture_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(5.dp))
                                Text("Carregar Textura", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                            }
                            Button(
                                onClick = onToggleUvUnfold,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .testTag("uv_unfold_window_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isUvUnfoldOpen) CyanAccent else Color(0xFF283044)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.GridOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isUvUnfoldOpen) Color.Black else textColor
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Janela UV",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUvUnfoldOpen) Color.Black else textColor,
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = onOpenUvVertexEditor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("uv_open_vertex_editor_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.22f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Grain, contentDescription = null, modifier = Modifier.size(17.dp), tint = CyanAccent)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Editar Pontos UV (Malha Plana)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanAccent,
                                maxLines = 1
                            )
                        }
                    }

                    // =====================================================
                    // TAB 1: SHADER, CONTORNO & ILUMINAÇÃO PRÓPRIA
                    // =====================================================
                    1 -> {
                        Text("Modo de Shader:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ObjectShaderMode.entries.forEach { sm ->
                                StudioPill(
                                    label = sm.labelPt,
                                    selected = selectedObj.shaderMode == sm,
                                    accent = CyanAccent,
                                    textColor = textColor,
                                    testTag = "shader_mode_${sm.name}"
                                ) {
                                    onUpdateObject(selectedObj.id, true) { it.copy(shaderMode = sm) }
                                }
                            }
                        }

                        // Contorno / Outline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Contorno no Objeto", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Switch(
                                checked = selectedObj.outline.enabled,
                                onCheckedChange = { on ->
                                    onUpdateObject(selectedObj.id, true) {
                                        it.copy(outline = it.outline.copy(enabled = on))
                                    }
                                },
                                modifier = Modifier.testTag("outline_toggle_switch"),
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                            )
                        }
                        if (selectedObj.outline.enabled) {
                            StudioSlider(
                                label = "Espessura",
                                valueText = "${selectedObj.outline.width.roundToInt()}px",
                                value = selectedObj.outline.width,
                                range = 1f..5f,
                                accent = NeonGreen,
                                textColor = textColor
                            ) { w ->
                                onUpdateObject(selectedObj.id, true) {
                                    it.copy(outline = it.outline.copy(width = w))
                                }
                            }
                            SwatchStrip(
                                label = "Cor Contorno:",
                                swatches = STUDIO_3D_SWATCHES,
                                selected = selectedObj.outline.colorArgb,
                                textColor = textColor
                            ) { c ->
                                onUpdateObject(selectedObj.id, true) {
                                    it.copy(outline = it.outline.copy(colorArgb = c))
                                }
                            }
                        }

                        // Iluminação Própria do Objeto
                        Text("Iluminação Própria do Objeto", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioSlider(
                                label = "Azimute",
                                valueText = "${selectedObj.light.azimuthDeg.roundToInt()}°",
                                value = selectedObj.light.azimuthDeg,
                                range = -180f..180f,
                                accent = OrangeAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(azimuthDeg = v)) }
                            }
                            StudioSlider(
                                label = "Elevação",
                                valueText = "${selectedObj.light.elevationDeg.roundToInt()}°",
                                value = selectedObj.light.elevationDeg,
                                range = -85f..85f,
                                accent = OrangeAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(elevationDeg = v)) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioSlider(
                                label = "Intensid.",
                                valueText = "${(selectedObj.light.intensity * 100).roundToInt()}%",
                                value = selectedObj.light.intensity,
                                range = 0.2f..2.2f,
                                accent = CyanAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(intensity = v)) }
                            }
                            StudioSlider(
                                label = "Ambiente",
                                valueText = "${(selectedObj.light.ambient * 100).roundToInt()}%",
                                value = selectedObj.light.ambient,
                                range = 0.05f..0.85f,
                                accent = CyanAccent,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(ambient = v)) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioSlider(
                                label = "Luz Borda",
                                valueText = "${(selectedObj.light.rimStrength * 100).roundToInt()}%",
                                value = selectedObj.light.rimStrength,
                                range = 0f..1f,
                                accent = NeonPink,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(rimStrength = v)) }
                            }
                            StudioSlider(
                                label = "Emissivo",
                                valueText = "${(selectedObj.light.emissiveGlow * 100).roundToInt()}%",
                                value = selectedObj.light.emissiveGlow,
                                range = 0f..1f,
                                accent = NeonGreen,
                                textColor = textColor,
                                modifier = Modifier.weight(1f)
                            ) { v ->
                                onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(emissiveGlow = v)) }
                            }
                        }
                        SwatchStrip(
                            label = "Cor da Luz:",
                            swatches = STUDIO_3D_SWATCHES,
                            selected = selectedObj.light.lightColorArgb,
                            textColor = textColor
                        ) { c ->
                            onUpdateObject(selectedObj.id, true) { it.copy(light = it.light.copy(lightColorArgb = c)) }
                        }
                    }

                    // =====================================================
                    // TAB 2: PINTURA UV (TELA CHEIA DEDICADA + EXPORTAR OBJETO)
                    // =====================================================
                    else -> {
                        Text(
                            text = "Pintura UV dedicada em tela cheia para '${selectedObj.name}', com barra de ferramentas, pincéis 2D, camadas, gestos de órbita/zoom e exportação.",
                            fontSize = 10.5.sp,
                            color = textColor.copy(alpha = 0.82f)
                        )

                        Button(
                            onClick = onOpenFullscreenUvPaint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("wheels_open_uv_paint_screen_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Abrir Pintura UV (Tela Cheia)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onMergePaintLayers(selectedObj.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("paint3d_merge_uv_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.MergeType, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Mesclar 1 UV (${selectedObj.paintLayers.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { onExportObjectZip(selectedObj) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("wheels_export_obj_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Exportar Objeto",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pintura rápida no Palco 3D",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.8f)
                            )
                            Switch(
                                checked = studio3DPaintEnabled,
                                onCheckedChange = onToggleStudio3DPaint,
                                modifier = Modifier.testTag("studio3d_paint_toggle"),
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonPink)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating "Janela UV Desdobrado" showing unwrapped UV wireframe triangles over composited texture,
 * supporting direct 2D UV texture painting!
 */
@Composable
fun UvUnfoldWindowOverlay(
    selectedObject: SceneObject3D,
    paintTool: Estudio3DPaintTool,
    paintSize: Float,
    paintOpacity: Float,
    activeBrushId: Int,
    activeBrushParamsJson: String?,
    currentColorArgb: Long,
    onPaintUVSegment: (String, Float, Float, Float, Float) -> Unit,
    onOpenUvVertexEditor: () -> Unit = {},
    onClose: () -> Unit,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val activeSlot = selectedObject.uvSlots.getOrNull(selectedObject.activeUvSlotIndex)
        ?: selectedObject.uvSlots.firstOrNull() ?: ObjectUVMapSlot()

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var wireframeLines by remember { mutableStateOf<FloatArray>(FloatArray(0)) }

    LaunchedEffect(
        selectedObject.id,
        selectedObject.meshAssetPath,
        selectedObject.bakedTextureBase64,
        selectedObject.activeUvSlotIndex,
        activeSlot,
        selectedObject.paintLayers
    ) {
        withContext(Dispatchers.Default) {
            val mesh = Mesh3DImporter.getOrLoadMesh(selectedObject.meshAssetPath)
            val firstSm = mesh?.subMeshes?.firstOrNull()
            val comp = Studio3DPaintEngine.resolveCompositeObjectTexture(
                obj = selectedObject,
                basePixels = firstSm?.texturePixels,
                baseW = firstSm?.textureWidth ?: 0,
                baseH = firstSm?.textureHeight ?: 0,
                baseColorArgb = firstSm?.baseColorArgb ?: selectedObject.colorTint.toInt()
            )
            val bmp = when {
                comp != null -> Bitmap.createBitmap(comp.width, comp.height, Bitmap.Config.ARGB_8888).apply {
                    setPixels(comp.pixels, 0, comp.width, 0, 0, comp.width, comp.height)
                }
                firstSm?.texturePixels != null && firstSm.textureWidth > 0 && firstSm.textureHeight > 0 -> {
                    Bitmap.createBitmap(firstSm.textureWidth, firstSm.textureHeight, Bitmap.Config.ARGB_8888).apply {
                        setPixels(firstSm.texturePixels, 0, firstSm.textureWidth, 0, 0, firstSm.textureWidth, firstSm.textureHeight)
                    }
                }
                else -> null
            }

            // Build normalized [0..1] wireframe segments from submeshes (cap at 1800 tris for snappy UI)
            val linesList = ArrayList<Float>()
            var subMeshVertOffset = 0
            mesh?.subMeshes?.forEach { sm ->
                val effUvs = Studio3DPaintEngine.generateUVsForProjection(sm.vertices, sm.uvs, activeSlot, subMeshVertOffset)
                val vCount = sm.vertices.size / 3
                subMeshVertOffset += vCount
                var t = 0
                val maxIdx = minOf(sm.indices.size, 5400)
                while (t + 2 < maxIdx) {
                    val i0 = sm.indices[t]; val i1 = sm.indices[t + 1]; val i2 = sm.indices[t + 2]
                    t += 3
                    if (i0 !in 0 until vCount || i1 !in 0 until vCount || i2 !in 0 until vCount) continue
                    val u0 = (effUvs[i0 * 2] % 1f + 1f) % 1f
                    val v0 = (effUvs[i0 * 2 + 1] % 1f + 1f) % 1f
                    val u1 = (effUvs[i1 * 2] % 1f + 1f) % 1f
                    val v1 = (effUvs[i1 * 2 + 1] % 1f + 1f) % 1f
                    val u2 = (effUvs[i2 * 2] % 1f + 1f) % 1f
                    val v2 = (effUvs[i2 * 2 + 1] % 1f + 1f) % 1f
                    linesList.add(u0); linesList.add(v0); linesList.add(u1); linesList.add(v1)
                    linesList.add(u1); linesList.add(v1); linesList.add(u2); linesList.add(v2)
                    linesList.add(u2); linesList.add(v2); linesList.add(u0); linesList.add(v0)
                }
            }
            withContext(Dispatchers.Main) {
                previewBitmap = bmp
                wireframeLines = linesList.toFloatArray()
            }
        }
    }

    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(14.dp))
            .testTag("uv_unfold_window_overlay"),
        color = bgColor.copy(alpha = 0.96f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, CyanAccent),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Janela UV • ${activeSlot.name}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyanAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StudioPill(
                        label = "Pontos UV",
                        selected = false,
                        accent = OrangeAccent,
                        textColor = textColor,
                        icon = Icons.Default.Grain,
                        testTag = "uv_unfold_open_vertex_editor_btn"
                    ) { onOpenUvVertexEditor() }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("uv_unfold_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar Janela UV", tint = textColor, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1E2B))
                    .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Mapa UV Desdobrado",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val curObjId by rememberUpdatedState(selectedObject.id)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedObject.id, paintTool, paintSize, paintOpacity, activeBrushId, currentColorArgb) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                var prevU = (down.position.x / w).coerceIn(0f, 1f)
                                var prevV = (down.position.y / h).coerceIn(0f, 1f)
                                down.consume()
                                onPaintUVSegment(curObjId, prevU, prevV, prevU, prevV)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    ch.consume()
                                    val u = (ch.position.x / w).coerceIn(0f, 1f)
                                    val v = (ch.position.y / h).coerceIn(0f, 1f)
                                    onPaintUVSegment(curObjId, prevU, prevV, u, v)
                                    prevU = u
                                    prevV = v
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    // UV checkerboard faint grid
                    for (i in 1..7) {
                        val f = i / 8f
                        drawLine(Color.White.copy(alpha = 0.08f), Offset(w * f, 0f), Offset(w * f, h), 1f)
                        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, h * f), Offset(w, h * f), 1f)
                    }
                    // Unfolded UV wireframe triangles
                    val segs = wireframeLines
                    var idx = 0
                    while (idx + 3 < segs.size) {
                        drawLine(
                            color = CyanAccent.copy(alpha = 0.62f),
                            start = Offset(segs[idx] * w, segs[idx + 1] * h),
                            end = Offset(segs[idx + 2] * w, segs[idx + 3] * h),
                            strokeWidth = 1.1f
                        )
                        idx += 4
                    }
                }
            }
            Text(
                text = "Arraste dentro da grade para pintar diretamente no UV (${paintTool.labelPt})",
                fontSize = 9.sp,
                color = textColor.copy(alpha = 0.72f)
            )
        }
    }
}

/**
 * Split-Screen Blender-Style Shader Node Editor Overlay:
 * - Top 50%: Live Interactive 3D Object Preview (1-finger orbits around the object — zero paint clutter)
 * - Bottom 50%: Node Graph Editor with + Adicionar Node (Principled BSDF, Fresnel, Mix RGB, Ruído, Emissão, etc.),
 *               Link/Unlink nodes, Delete node, Blender Parameter sliders, and "Nodes Próprios" custom presets!
 */
@Composable
fun Studio3DSplitNodesOverlay(
    selectedObject: SceneObject3D?,
    studio3DPaintEnabled: Boolean = false,
    onToggleStudio3DPaint: (Boolean) -> Unit = {},
    onPaintObjectUV: (String, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onUpdateObject: (String, Boolean, (SceneObject3D) -> SceneObject3D) -> Unit,
    customPresets: List<CustomShaderNodePreset> = emptyList(),
    onSaveCustomPreset: (String, ShaderNodeGraph) -> Unit = { _, _ -> },
    onApplyCustomPreset: (String, CustomShaderNodePreset) -> Unit = { _, _ -> },
    onDeleteCustomPreset: (String) -> Unit = {},
    onClose: () -> Unit,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var previewYaw by remember { mutableFloatStateOf(28f) }
    var previewPitch by remember { mutableFloatStateOf(-18f) }
    var selectedNodeId by remember { mutableStateOf<String?>("ramp") }
    var showAddNodeStrip by remember { mutableStateOf(false) }
    var linkingFromNodeId by remember { mutableStateOf<String?>(null) }
    var showCustomSaveRow by remember { mutableStateOf(false) }
    var customPresetName by remember { mutableStateOf("") }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }

    val curObj = selectedObject
    LaunchedEffect(
        curObj,
        previewYaw,
        previewPitch
    ) {
        if (curObj == null) {
            previewBmp = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val glScale = if (NativeGL3DEngine.isHardwareAvailable()) 1.5f else 1f
            val res = Camera3DEngine.renderSceneObjectsSoftware3D(
                outW = (480 * glScale).toInt(),
                outH = (320 * glScale).toInt(),
                sceneObjects = listOf(curObj),
                frameIndex = 0,
                autoInterpolate = true,
                camYawDeg = previewYaw,
                camPitchDeg = previewPitch,
                camRollDeg = 0f,
                camPanX = 0f,
                camPanY = 0f,
                focalPx = 360f * glScale,
                vpCx = 240f * glScale,
                vpCy = 160f * glScale,
                worldUnitScale = 0.72f * glScale,
                selectedObjectId = null
            )
            withContext(Dispatchers.Main) {
                previewBmp = res?.first
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1018))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("studio3d_split_nodes_overlay")
    ) {
        // TOP 50%: Live 3D Shader Preview (Orbit only — Paint lives on Pintura UV screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.92f)
                .background(Color(0xFF151926))
        ) {
            previewBmp?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Prévia Shader 3D",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(curObj?.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var prevPos = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                val dx = ch.position.x - prevPos.x
                                val dy = ch.position.y - prevPos.y
                                previewYaw += dx * 0.45f
                                previewPitch = (previewPitch + dy * 0.45f).coerceIn(-80f, 80f)
                                prevPos = ch.position
                            }
                        }
                    }
            )

            // Top Preview HUD Bar (minimal — only Node header & close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = bgColor.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (curObj != null) "Nodes • ${curObj.name}" else "Nenhum Objeto 3D",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bgColor.copy(alpha = 0.85f))
                        .testTag("nodes_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar Nodes", tint = Color.White)
                }
            }
        }

        // DIVIDER BAR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(CyanAccent.copy(alpha = 0.6f))
        )

        // BOTTOM 50%: Blender Shader Node Graph Editor
        if (curObj == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.08f),
                contentAlignment = Alignment.Center
            ) {
                Text("Selecione ou adicione um objeto 3D para editar os Nodes.", color = textColor)
            }
        } else {
            val graph = curObj.nodeGraph
            val activeNode = graph.nodes.firstOrNull { it.id == selectedNodeId } ?: graph.nodes.firstOrNull()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.08f)
                    .background(bgColor.copy(alpha = 0.98f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Toolbar: Active Switch + Add Node + Custom Nodes + Built-in Presets + Saved Custom Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ativo:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Switch(
                            checked = graph.enabled,
                            onCheckedChange = { on ->
                                onUpdateObject(curObj.id, true) {
                                    it.copy(nodeGraph = it.nodeGraph.copy(enabled = on))
                                }
                            },
                            modifier = Modifier.testTag("node_graph_enabled_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                        )
                    }

                    StudioPill(
                        label = "+ Node",
                        selected = showAddNodeStrip,
                        accent = NeonGreen,
                        textColor = textColor,
                        icon = Icons.Default.Add,
                        testTag = "nodes_add_node_toggle"
                    ) { showAddNodeStrip = !showAddNodeStrip }

                    StudioPill(
                        label = "Salvar Próprio",
                        selected = showCustomSaveRow,
                        accent = OrangeAccent,
                        textColor = textColor,
                        icon = Icons.Default.Save,
                        testTag = "nodes_save_custom_toggle"
                    ) { showCustomSaveRow = !showCustomSaveRow }

                    StudioPill("Cartoon Studio", graph.presetName == "Cartoon Studio" && graph.enabled, OrangeAccent, textColor) {
                        onUpdateObject(curObj.id, true) {
                            it.copy(
                                shaderMode = ObjectShaderMode.CARTOON_BRUSH,
                                outline = it.outline.copy(enabled = true, width = 2f),
                                nodeGraph = ShaderNodeGraph(
                                    enabled = true,
                                    presetName = "Cartoon Studio",
                                    nodes = ShaderNodeGraph.defaultNodes(),
                                    links = ShaderNodeGraph.defaultLinks()
                                )
                            )
                        }
                    }
                    StudioPill("Anime Cel", graph.presetName == "Anime Cel" && graph.enabled, CyanAccent, textColor) {
                        val animeNodes = ShaderNodeGraph.defaultNodes().map { n ->
                            when (n.kind) {
                                ShaderNodeKind.CEL_RAMP -> n.copy(paramA = 3f, paramB = 0.7f)
                                ShaderNodeKind.BRUSH_GRAIN -> n.copy(paramA = 0f)
                                ShaderNodeKind.CONTORNO -> n.copy(paramA = 2.5f, paramB = 1f)
                                else -> n
                            }
                        }
                        onUpdateObject(curObj.id, true) {
                            it.copy(
                                shaderMode = ObjectShaderMode.ANIME_CEL,
                                outline = it.outline.copy(enabled = true, width = 2.5f),
                                nodeGraph = ShaderNodeGraph(enabled = true, presetName = "Anime Cel", nodes = animeNodes, links = ShaderNodeGraph.defaultLinks())
                            )
                        }
                    }
                    StudioPill("Principled PBR", graph.presetName == "Principled PBR" && graph.enabled, Color(0xFF3498DBL), textColor) {
                        val pbrNodes = listOf(
                            ShaderNode("uv", ShaderNodeKind.UV_COORD, 40f, 110f, paramA = 1f, paramB = 1f),
                            ShaderNode("tex", ShaderNodeKind.TEXTURA_UV, 190f, 60f, paramA = 1f, paramB = 0f),
                            ShaderNode("pbr", ShaderNodeKind.PRINCIPLED_BSDF, 360f, 110f, paramA = 0.25f, paramB = 0.35f),
                            ShaderNode("fresnel", ShaderNodeKind.FRESNEL, 360f, 230f, paramA = 0.45f, paramB = 0.6f),
                            ShaderNode("out", ShaderNodeKind.SAIDA_MATERIAL, 560f, 120f, paramA = 1f, paramB = 0f)
                        )
                        val pbrLinks = listOf(
                            ShaderNodeLink("p1", "uv", "tex"),
                            ShaderNodeLink("p2", "tex", "pbr"),
                            ShaderNodeLink("p3", "pbr", "out"),
                            ShaderNodeLink("p4", "fresnel", "out")
                        )
                        onUpdateObject(curObj.id, true) {
                            it.copy(
                                shaderMode = ObjectShaderMode.PADRAO,
                                nodeGraph = ShaderNodeGraph(enabled = true, presetName = "Principled PBR", nodes = pbrNodes, links = pbrLinks)
                            )
                        }
                    }

                    // User's Custom Saved Presets ("Nodes Próprios")
                    customPresets.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonPink.copy(alpha = 0.16f))
                                .border(1.dp, NeonPink.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .clickable { onApplyCustomPreset(curObj.id, preset) }
                                .padding(start = 7.dp, end = 2.dp, top = 3.dp, bottom = 3.dp)
                                .testTag("custom_node_preset_${preset.id}")
                        ) {
                            Text(preset.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
                            IconButton(
                                onClick = { onDeleteCustomPreset(preset.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remover preset", tint = NeonPink, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }

                // Expandable Add Blender Node Strip
                if (showAddNodeStrip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Adicionar:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                        ShaderNodeKind.entries.forEach { kind ->
                            StudioPill(
                                label = "+ ${kind.labelPt}",
                                selected = false,
                                accent = Color(kind.accentArgb),
                                textColor = textColor,
                                testTag = "add_node_kind_${kind.name}"
                            ) {
                                val newId = "n_${UUID.randomUUID().toString().take(6)}"
                                val offsetIdx = (graph.nodes.size % 5)
                                val newNode = ShaderNode(
                                    id = newId,
                                    kind = kind,
                                    x = 120f + offsetIdx * 95f,
                                    y = 65f + ((graph.nodes.size / 5) % 3) * 75f,
                                    paramA = if (kind == ShaderNodeKind.CEL_RAMP) 3f else 0.6f,
                                    paramB = 0.5f
                                )
                                val outNode = graph.nodes.firstOrNull { it.kind == ShaderNodeKind.SAIDA_MATERIAL }
                                val autoLink = if (outNode != null && kind != ShaderNodeKind.SAIDA_MATERIAL) {
                                    listOf(ShaderNodeLink(fromNodeId = newId, toNodeId = outNode.id))
                                } else emptyList()
                                onUpdateObject(curObj.id, true) { cur ->
                                    cur.copy(
                                        nodeGraph = cur.nodeGraph.copy(
                                            enabled = true,
                                            nodes = cur.nodeGraph.nodes + newNode,
                                            links = cur.nodeGraph.links + autoLink
                                        )
                                    )
                                }
                                selectedNodeId = newId
                                showAddNodeStrip = false
                            }
                        }
                    }
                }

                // Expandable Save Custom Node Preset Row ("Nodes Próprios")
                if (showCustomSaveRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF1A2030),
                            shape = RoundedCornerShape(9.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (customPresetName.isEmpty()) {
                                    Text(
                                        text = "Nome do Node Próprio...",
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.48f)
                                    )
                                }
                                BasicTextField(
                                    value = customPresetName,
                                    onValueChange = { customPresetName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = textColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(CyanAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("nodes_custom_name_input")
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onSaveCustomPreset(customPresetName, graph)
                                customPresetName = ""
                                showCustomSaveRow = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("nodes_confirm_save_custom_btn")
                        ) {
                            Text("Salvar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                // Interactive Node Graph Canvas + Nodes
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121622))
                        .border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    val density = LocalDensity.current
                    val nodeW = 94.dp
                    val nodeH = 44.dp
                    val nodeWPx = with(density) { nodeW.toPx() }
                    val nodeHPx = with(density) { nodeH.toPx() }
                    val boxWPx = with(density) { maxWidth.toPx() }
                    val boxHPx = with(density) { maxHeight.toPx() }

                    // Faint node grid + Bezier Node Links
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var gx = 0f
                        while (gx < size.width) {
                            drawLine(Color.White.copy(alpha = 0.05f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                            gx += 32f
                        }
                        var gy = 0f
                        while (gy < size.height) {
                            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, gy), Offset(size.width, gy), 1f)
                            gy += 32f
                        }

                        val nodeMap = graph.nodes.associateBy { it.id }
                        for (link in graph.links) {
                            val a = nodeMap[link.fromNodeId] ?: continue
                            val b = nodeMap[link.toNodeId] ?: continue
                            val x1 = (a.x / 700f) * boxWPx + nodeWPx
                            val y1 = (a.y / 320f) * boxHPx + nodeHPx * 0.5f
                            val x2 = (b.x / 700f) * boxWPx
                            val y2 = (b.y / 320f) * boxHPx + nodeHPx * 0.5f
                            val ctrl = max(24f, kotlin.math.abs(x2 - x1) * 0.45f)
                            val p = Path().apply {
                                moveTo(x1, y1)
                                cubicTo(x1 + ctrl, y1, x2 - ctrl, y2, x2, y2)
                            }
                            drawPath(
                                path = p,
                                color = Color(a.kind.accentArgb).copy(alpha = 0.85f),
                                style = Stroke(width = 3.2f)
                            )
                        }
                    }

                    // Draggable Node Cards (Tap to select or connect when linking mode active)
                    graph.nodes.forEach { node ->
                        val px = ((node.x / 700f) * boxWPx).roundToInt().coerceIn(0, max(0, (boxWPx - nodeWPx).roundToInt()))
                        val py = ((node.y / 320f) * boxHPx).roundToInt().coerceIn(0, max(0, (boxHPx - nodeHPx).roundToInt()))
                        val accent = Color(node.kind.accentArgb)
                        val isSel = activeNode?.id == node.id
                        val isLinkSource = linkingFromNodeId == node.id

                        Surface(
                            modifier = Modifier
                                .offset { IntOffset(px, py) }
                                .size(nodeW, nodeH)
                                .pointerInput(node.id, curObj.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dxNorm = (dragAmount.x / boxWPx.coerceAtLeast(1f)) * 700f
                                        val dyNorm = (dragAmount.y / boxHPx.coerceAtLeast(1f)) * 320f
                                        onUpdateObject(curObj.id, true) { cur ->
                                            val updated = cur.nodeGraph.nodes.map { n ->
                                                if (n.id == node.id) {
                                                    n.copy(
                                                        x = (n.x + dxNorm).coerceIn(0f, 610f),
                                                        y = (n.y + dyNorm).coerceIn(0f, 270f)
                                                    )
                                                } else n
                                            }
                                            cur.copy(nodeGraph = cur.nodeGraph.copy(nodes = updated))
                                        }
                                    }
                                }
                                .clickable {
                                    val src = linkingFromNodeId
                                    if (src != null && src != node.id) {
                                        onUpdateObject(curObj.id, true) { cur ->
                                            val exists = cur.nodeGraph.links.any { it.fromNodeId == src && it.toNodeId == node.id }
                                            val nextLinks = if (exists) {
                                                cur.nodeGraph.links.filterNot { it.fromNodeId == src && it.toNodeId == node.id }
                                            } else {
                                                cur.nodeGraph.links + ShaderNodeLink(fromNodeId = src, toNodeId = node.id)
                                            }
                                            cur.copy(nodeGraph = cur.nodeGraph.copy(enabled = true, links = nextLinks))
                                        }
                                        linkingFromNodeId = null
                                        selectedNodeId = node.id
                                    } else {
                                        selectedNodeId = node.id
                                    }
                                }
                                .testTag("shader_node_${node.id}"),
                            color = Color(0xFF1F2536),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isLinkSource || isSel) 2.dp else 1.dp,
                                color = if (isLinkSource) NeonGreen else if (isSel) Color.White else accent
                            ),
                            shadowElevation = 6.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accent.copy(alpha = 0.85f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = node.kind.labelPt,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "A:${String.format("%.1f", node.paramA)}",
                                        fontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "B:${String.format("%.1f", node.paramB)}",
                                        fontSize = 8.sp,
                                        color = CyanAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Node Blender Parameter Inspector (with Link / Delete / Semantic Labels / Color)
                if (activeNode != null) {
                    val accent = Color(activeNode.kind.accentArgb)
                    val (labelA, labelB, rangeA) = when (activeNode.kind) {
                        ShaderNodeKind.PRINCIPLED_BSDF -> Triple("Metálico", "Rugos.", 0f..1f)
                        ShaderNodeKind.FRESNEL -> Triple("Borda", "IOR", 0f..1.5f)
                        ShaderNodeKind.MIX_RGB -> Triple("Fator", "Intens.", 0f..1f)
                        ShaderNodeKind.NOISE_PROC -> Triple("Escala", "Força", 0.1f..3f)
                        ShaderNodeKind.EMISSAO -> Triple("Brilho", "Suave", 0f..2f)
                        ShaderNodeKind.CEL_RAMP -> Triple("Bandas", "Suav.", 2f..6f)
                        ShaderNodeKind.CONTORNO -> Triple("Espess.", "Força", 0.5f..5f)
                        ShaderNodeKind.UV_COORD -> Triple("Escala", "Desloc.", 0.5f..5f)
                        else -> Triple("Valor A", "Valor B", 0f..1.5f)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeNode.kind.labelPt,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        StudioPill(
                            label = if (linkingFromNodeId == activeNode.id) "Toque Destino..." else "Conectar",
                            selected = linkingFromNodeId == activeNode.id,
                            accent = NeonGreen,
                            textColor = textColor,
                            icon = Icons.Default.Link,
                            testTag = "nodes_connect_btn"
                        ) {
                            linkingFromNodeId = if (linkingFromNodeId == activeNode.id) null else activeNode.id
                        }
                        if (activeNode.kind != ShaderNodeKind.SAIDA_MATERIAL && graph.nodes.size > 1) {
                            StudioPill(
                                label = "Excluir",
                                selected = false,
                                accent = NeonPink,
                                textColor = NeonPink,
                                icon = Icons.Default.DeleteOutline,
                                testTag = "nodes_delete_node_btn"
                            ) {
                                val delId = activeNode.id
                                onUpdateObject(curObj.id, true) { cur ->
                                    cur.copy(
                                        nodeGraph = cur.nodeGraph.copy(
                                            nodes = cur.nodeGraph.nodes.filterNot { it.id == delId },
                                            links = cur.nodeGraph.links.filterNot { it.fromNodeId == delId || it.toNodeId == delId }
                                        )
                                    )
                                }
                                selectedNodeId = graph.nodes.firstOrNull { it.id != delId }?.id
                                if (linkingFromNodeId == delId) linkingFromNodeId = null
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioSlider(
                            label = labelA,
                            valueText = String.format("%.2f", activeNode.paramA),
                            value = activeNode.paramA,
                            range = rangeA,
                            accent = accent,
                            textColor = textColor,
                            modifier = Modifier.weight(1f)
                        ) { na ->
                            onUpdateObject(curObj.id, true) { cur ->
                                val nextNodes = cur.nodeGraph.nodes.map {
                                    if (it.id == activeNode.id) it.copy(paramA = na) else it
                                }
                                cur.copy(nodeGraph = cur.nodeGraph.copy(enabled = true, nodes = nextNodes))
                            }
                        }
                        StudioSlider(
                            label = labelB,
                            valueText = String.format("%.2f", activeNode.paramB),
                            value = activeNode.paramB,
                            range = 0f..1f,
                            accent = CyanAccent,
                            textColor = textColor,
                            modifier = Modifier.weight(1f)
                        ) { nb ->
                            onUpdateObject(curObj.id, true) { cur ->
                                val nextNodes = cur.nodeGraph.nodes.map {
                                    if (it.id == activeNode.id) it.copy(paramB = nb) else it
                                }
                                cur.copy(nodeGraph = cur.nodeGraph.copy(enabled = true, nodes = nextNodes))
                            }
                        }
                    }

                    if (activeNode.kind in listOf(
                            ShaderNodeKind.MIX_RGB,
                            ShaderNodeKind.EMISSAO,
                            ShaderNodeKind.CONTORNO,
                            ShaderNodeKind.PRINCIPLED_BSDF
                        )
                    ) {
                        SwatchStrip(
                            label = "Cor Node:",
                            swatches = STUDIO_3D_SWATCHES,
                            selected = activeNode.colorArgb,
                            textColor = textColor
                        ) { c ->
                            onUpdateObject(curObj.id, true) { cur ->
                                val nextNodes = cur.nodeGraph.nodes.map {
                                    if (it.id == activeNode.id) it.copy(colorArgb = c) else it
                                }
                                cur.copy(nodeGraph = cur.nodeGraph.copy(enabled = true, nodes = nextNodes))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPill(
    label: String,
    selected: Boolean,
    accent: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.24f) else textColor.copy(alpha = 0.08f))
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

@Composable
private fun StudioSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 9.5.sp, color = textColor.copy(alpha = 0.85f), modifier = Modifier.width(54.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(22.dp),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
        Text(valueText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun SwatchStrip(
    label: String,
    swatches: List<Long>,
    selected: Long,
    textColor: Color,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.82f))
        swatches.forEach { sw ->
            val isSel = selected == sw
            Box(
                modifier = Modifier
                    .size(if (isSel) 22.dp else 18.dp)
                    .clip(CircleShape)
                    .background(Color(sw))
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) CyanAccent else Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable { onSelect(sw) }
            )
        }
    }
}
