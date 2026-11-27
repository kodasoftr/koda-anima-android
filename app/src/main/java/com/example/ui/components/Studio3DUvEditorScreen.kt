package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Mesh3DImporter
import com.example.engine.Mesh3DSceneData
import com.example.engine.Studio3DPaintEngine
import com.example.model.KodanimaTheme
import com.example.model.ObjectUVMapSlot
import com.example.model.SceneObject3D
import com.example.model.UvUnwrapProjection
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class UvEditorToolMode(val labelPt: String, val shortHint: String) {
    SELECT_POINT("Ponto", "Toque nos vértices da malha UV para selecionar"),
    BOX_SELECT("Caixa", "Arraste uma caixa para selecionar vários pontos UV"),
    ISLAND_SELECT("Ilha", "Toque em um ponto para selecionar toda a ilha UV"),
    MOVE("Mover (G)", "Arraste para mover os pontos UV selecionados"),
    SCALE("Escala (S)", "Arraste para escalar os pontos a partir do centro"),
    ROTATE("Girar (R)", "Arraste para girar os pontos em torno do centro"),
    NAVIGATE("Navegar", "Arraste para mover a visualização • Pinça para zoom")
}

data class FlattenedUvMeshData(
    val totalVertices: Int,
    val baseUvs: FloatArray,
    val globalTriangleIndices: IntArray,
    val vertexPositions3D: FloatArray
)

/**
 * Pure geometric operations for the Blender-style Flat UV Mesh Vertex Editor.
 * Operates on flattened `[u0, v0, u1, v1, ...]` coordinates across all submeshes.
 */
object UvVertexEditorMath {

    fun buildFlattenedUvMesh(
        meshData: Mesh3DSceneData,
        slot: ObjectUVMapSlot
    ): FlattenedUvMeshData {
        var totalVerts = 0
        var totalIdx = 0
        for (sm in meshData.subMeshes) {
            totalVerts += sm.vertices.size / 3
            totalIdx += (sm.indices.size / 3) * 3
        }
        val uvs = FloatArray(totalVerts * 2)
        val pos3D = FloatArray(totalVerts * 3)
        val triIdx = IntArray(totalIdx)
        var vOff = 0
        var iOff = 0
        for (sm in meshData.subMeshes) {
            val vCount = sm.vertices.size / 3
            val eff = Studio3DPaintEngine.generateUVsForProjection(sm.vertices, sm.uvs, slot, vOff)
            for (k in 0 until min(eff.size, vCount * 2)) {
                uvs[vOff * 2 + k] = eff[k]
            }
            for (k in 0 until min(sm.vertices.size, vCount * 3)) {
                pos3D[vOff * 3 + k] = sm.vertices[k]
            }
            var t = 0
            while (t + 2 < sm.indices.size && iOff + 2 < triIdx.size) {
                val a = sm.indices[t]
                val b = sm.indices[t + 1]
                val c = sm.indices[t + 2]
                if (a in 0 until vCount && b in 0 until vCount && c in 0 until vCount) {
                    triIdx[iOff] = a + vOff
                    triIdx[iOff + 1] = b + vOff
                    triIdx[iOff + 2] = c + vOff
                } else {
                    triIdx[iOff] = vOff
                    triIdx[iOff + 1] = vOff
                    triIdx[iOff + 2] = vOff
                }
                iOff += 3
                t += 3
            }
            vOff += vCount
        }
        return FlattenedUvMeshData(
            totalVertices = totalVerts,
            baseUvs = uvs,
            globalTriangleIndices = if (iOff == triIdx.size) triIdx else triIdx.copyOf(iOff),
            vertexPositions3D = pos3D
        )
    }

    /**
     * Finds any vertices sharing the same coincident UV coordinate (and optionally same 3D seam vertex),
     * so split triangle corners move together cleanly when seam-welding is enabled.
     */
    fun expandWithWeldedCorners(
        seedIndices: Set<Int>,
        uvs: FloatArray,
        totalVertices: Int,
        includeCoincidentUv: Boolean = true
    ): Set<Int> {
        if (!includeCoincidentUv || seedIndices.isEmpty()) return seedIndices
        val result = LinkedHashSet<Int>(seedIndices.size * 2)
        result.addAll(seedIndices)
        val bucket = HashMap<Long, MutableList<Int>>(totalVertices)
        for (v in 0 until totalVertices) {
            val uq = (uvs[v * 2] * 2000f).roundToInt().toLong()
            val vq = (uvs[v * 2 + 1] * 2000f).roundToInt().toLong()
            val key = (uq shl 32) xor (vq and 0xFFFFFFFFL)
            bucket.getOrPut(key) { ArrayList(2) }.add(v)
        }
        for (s in seedIndices) {
            if (s !in 0 until totalVertices) continue
            val uq = (uvs[s * 2] * 2000f).roundToInt().toLong()
            val vq = (uvs[s * 2 + 1] * 2000f).roundToInt().toLong()
            val key = (uq shl 32) xor (vq and 0xFFFFFFFFL)
            bucket[key]?.let { result.addAll(it) }
        }
        return result
    }

    /**
     * Breadth-first traversal across UV triangle edges + coincident UV corners to select an entire UV island.
     */
    fun findConnectedUvIslandIndices(
        startVertexIdx: Int,
        uvs: FloatArray,
        triIndices: IntArray,
        totalVertices: Int
    ): Set<Int> {
        if (startVertexIdx !in 0 until totalVertices) return emptySet()
        val adj = Array<MutableList<Int>>(totalVertices) { ArrayList(4) }
        var t = 0
        while (t + 2 < triIndices.size) {
            val a = triIndices[t]
            val b = triIndices[t + 1]
            val c = triIndices[t + 2]
            if (a in 0 until totalVertices && b in 0 until totalVertices && c in 0 until totalVertices) {
                adj[a].add(b); adj[a].add(c)
                adj[b].add(a); adj[b].add(c)
                adj[c].add(a); adj[c].add(b)
            }
            t += 3
        }
        val bucket = HashMap<Long, MutableList<Int>>(totalVertices)
        for (v in 0 until totalVertices) {
            val uq = (uvs[v * 2] * 1000f).roundToInt().toLong()
            val vq = (uvs[v * 2 + 1] * 1000f).roundToInt().toLong()
            val key = (uq shl 32) xor (vq and 0xFFFFFFFFL)
            bucket.getOrPut(key) { ArrayList(2) }.add(v)
        }

        val visited = BooleanArray(totalVertices)
        val queue = ArrayDeque<Int>()
        val island = LinkedHashSet<Int>()
        visited[startVertexIdx] = true
        queue.add(startVertexIdx)

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            island.add(cur)
            for (nb in adj[cur]) {
                if (!visited[nb]) {
                    visited[nb] = true
                    queue.add(nb)
                }
            }
            val uq = (uvs[cur * 2] * 1000f).roundToInt().toLong()
            val vq = (uvs[cur * 2 + 1] * 1000f).roundToInt().toLong()
            val key = (uq shl 32) xor (vq and 0xFFFFFFFFL)
            bucket[key]?.forEach { partner ->
                if (!visited[partner]) {
                    visited[partner] = true
                    queue.add(partner)
                }
            }
        }
        return island
    }

    fun computeSelectionCentroid(
        selectedIndices: Set<Int>,
        uvs: FloatArray,
        totalVertices: Int
    ): Pair<Float, Float> {
        if (selectedIndices.isEmpty()) return 0.5f to 0.5f
        var sumU = 0f
        var sumV = 0f
        var count = 0
        for (idx in selectedIndices) {
            if (idx in 0 until totalVertices) {
                sumU += uvs[idx * 2]
                sumV += uvs[idx * 2 + 1]
                count++
            }
        }
        return if (count > 0) (sumU / count) to (sumV / count) else 0.5f to 0.5f
    }

    fun translateSelectedUvs(
        baseUvs: FloatArray,
        selectedIndices: Set<Int>,
        du: Float,
        dv: Float,
        snapStep: Float = 0f
    ): FloatArray {
        val out = baseUvs.copyOf()
        val vCount = out.size / 2
        for (idx in selectedIndices) {
            if (idx !in 0 until vCount) continue
            var nu = out[idx * 2] + du
            var nv = out[idx * 2 + 1] + dv
            if (snapStep > 1e-5f) {
                nu = (nu / snapStep).roundToInt() * snapStep
                nv = (nv / snapStep).roundToInt() * snapStep
            }
            out[idx * 2] = nu.coerceIn(-2f, 3f)
            out[idx * 2 + 1] = nv.coerceIn(-2f, 3f)
        }
        return out
    }

    fun scaleSelectedUvs(
        baseUvs: FloatArray,
        selectedIndices: Set<Int>,
        pivotU: Float,
        pivotV: Float,
        scaleFactor: Float
    ): FloatArray {
        val out = baseUvs.copyOf()
        val vCount = out.size / 2
        val clampedScale = scaleFactor.coerceIn(0.05f, 8f)
        for (idx in selectedIndices) {
            if (idx !in 0 until vCount) continue
            val u = pivotU + (out[idx * 2] - pivotU) * clampedScale
            val v = pivotV + (out[idx * 2 + 1] - pivotV) * clampedScale
            out[idx * 2] = u.coerceIn(-2f, 3f)
            out[idx * 2 + 1] = v.coerceIn(-2f, 3f)
        }
        return out
    }

    fun rotateSelectedUvs(
        baseUvs: FloatArray,
        selectedIndices: Set<Int>,
        pivotU: Float,
        pivotV: Float,
        angleRad: Float
    ): FloatArray {
        val out = baseUvs.copyOf()
        val vCount = out.size / 2
        val c = cos(angleRad.toDouble()).toFloat()
        val s = sin(angleRad.toDouble()).toFloat()
        for (idx in selectedIndices) {
            if (idx !in 0 until vCount) continue
            val du = out[idx * 2] - pivotU
            val dv = out[idx * 2 + 1] - pivotV
            out[idx * 2] = (pivotU + du * c - dv * s).coerceIn(-2f, 3f)
            out[idx * 2 + 1] = (pivotV + du * s + dv * c).coerceIn(-2f, 3f)
        }
        return out
    }
}

/**
 * Fullscreen Blender-style Flat UV Mesh Vertex & Island Editor ("Editor de Malha UV").
 * Allows selecting individual or multiple UV points, Box Select, Island Select,
 * Moving (G), Scaling (S), Rotating (R), Snap to Grid (1/16), and exporting the edited UV mesh.
 */
@Composable
fun Studio3DUvEditorScreen(
    sceneObjects: List<SceneObject3D>,
    selectedObjectId: String?,
    onSelectObject: (String) -> Unit,
    onUpdateObject: (String, Boolean, (SceneObject3D) -> SceneObject3D) -> Unit,
    onCommitCustomUvs: (String, List<Float>, Long) -> Unit,
    onResetCustomUvs: (String) -> Unit,
    onExportObjectZip: (SceneObject3D) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    val slots = selectedObj?.uvSlots?.ifEmpty { listOf(ObjectUVMapSlot()) } ?: listOf(ObjectUVMapSlot())
    val activeSlotIdx = (selectedObj?.activeUvSlotIndex ?: 0).coerceIn(0, slots.lastIndex)
    val activeSlot = slots[activeSlotIdx]

    var toolMode by remember { mutableStateOf(UvEditorToolMode.SELECT_POINT) }
    var multiSelectEnabled by remember { mutableStateOf(true) }
    var weldSeamsEnabled by remember { mutableStateOf(true) }
    var snapGridEnabled by remember { mutableStateOf(false) }

    var viewZoom by remember { mutableFloatStateOf(1.0f) }
    var viewPanX by remember { mutableFloatStateOf(0f) }
    var viewPanY by remember { mutableFloatStateOf(0f) }

    var flatMesh by remember { mutableStateOf<FlattenedUvMeshData?>(null) }
    var workingUvs by remember { mutableStateOf(FloatArray(0)) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var textureBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var boxDragStart by remember { mutableStateOf<Offset?>(null) }
    var boxDragEnd by remember { mutableStateOf<Offset?>(null) }
    var liveTransformBadge by remember { mutableStateOf<String?>(null) }

    // Load texture preview + flattened UV mesh whenever object / slot / customUvs change externally
    LaunchedEffect(
        selectedObj?.id,
        selectedObj?.meshAssetPath,
        selectedObj?.activeUvSlotIndex,
        activeSlot,
        selectedObj?.paintLayers,
        selectedObj?.bakedTextureBase64
    ) {
        val obj = selectedObj
        if (obj == null) {
            flatMesh = null
            workingUvs = FloatArray(0)
            textureBitmap = null
            selectedIndices = emptySet()
            return@LaunchedEffect
        }
        val (meshData, texImg) = withContext(Dispatchers.Default) {
            val mesh = Mesh3DImporter.getOrLoadMesh(obj.meshAssetPath)
            val flattened = if (mesh != null) UvVertexEditorMath.buildFlattenedUvMesh(mesh, activeSlot) else null
            val firstSm = mesh?.subMeshes?.firstOrNull()
            val comp = Studio3DPaintEngine.resolveCompositeObjectTexture(
                obj = obj,
                basePixels = firstSm?.texturePixels,
                baseW = firstSm?.textureWidth ?: 0,
                baseH = firstSm?.textureHeight ?: 0,
                baseColorArgb = firstSm?.baseColorArgb ?: obj.colorTint.toInt()
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
            flattened to bmp?.asImageBitmap()
        }
        flatMesh = meshData
        workingUvs = meshData?.baseUvs ?: FloatArray(0)
        textureBitmap = texImg
        selectedIndices = selectedIndices.filter { it in 0 until (meshData?.totalVertices ?: 0) }.toSet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141824), Color(0xFF0B0E16))
                )
            )
            .testTag("studio3d_uv_editor_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // TOP HUD
            Surface(
                color = panelColor.copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.55f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(38.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("uv_editor_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar ao Estúdio 3D", tint = textColor)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editor UV • ${selectedObj?.name ?: "Sem Objeto"}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val totalVerts = flatMesh?.totalVertices ?: 0
                            Text(
                                text = "${selectedIndices.size}/$totalVerts pontos selecionados • ${activeSlot.name}",
                                fontSize = 9.5.sp,
                                color = textColor.copy(alpha = 0.76f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { if (canUndo) onUndo() },
                            enabled = canUndo,
                            modifier = Modifier
                                .size(36.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("uv_editor_undo_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer UV",
                                tint = if (canUndo) textColor else textColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (canRedo) onRedo() },
                            enabled = canRedo,
                            modifier = Modifier
                                .size(36.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("uv_editor_redo_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Refazer UV",
                                tint = if (canRedo) textColor else textColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (selectedObj != null) {
                            UvEditorPill(
                                label = "Resetar",
                                selected = false,
                                accent = OrangeAccent,
                                bgColor = bgColor,
                                textColor = textColor,
                                icon = Icons.Default.RestartAlt,
                                testTag = "uv_editor_reset_btn"
                            ) {
                                selectedIndices = emptySet()
                                onResetCustomUvs(selectedObj.id)
                            }
                            UvEditorPill(
                                label = "Exportar",
                                selected = true,
                                accent = NeonGreen,
                                bgColor = bgColor,
                                textColor = textColor,
                                icon = Icons.Default.Download,
                                testTag = "uv_editor_export_btn"
                            ) { onExportObjectZip(selectedObj) }
                        }
                    }

                    // Object switcher + UVMap slots + Projection Unwrap Quick Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sceneObjects.size > 1) {
                            sceneObjects.forEach { obj ->
                                UvEditorPill(
                                    label = obj.name.take(14),
                                    selected = obj.id == selectedObj?.id,
                                    accent = OrangeAccent,
                                    bgColor = bgColor,
                                    textColor = textColor
                                ) { onSelectObject(obj.id) }
                            }
                        }
                        if (selectedObj != null) {
                            slots.forEachIndexed { idx, slot ->
                                UvEditorPill(
                                    label = slot.name,
                                    selected = idx == activeSlotIdx,
                                    accent = CyanAccent,
                                    bgColor = bgColor,
                                    textColor = textColor
                                ) {
                                    onUpdateObject(selectedObj.id, true) { it.copy(activeUvSlotIndex = idx) }
                                }
                            }
                            Text("Unwrap:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                            UvUnwrapProjection.entries.forEach { proj ->
                                UvEditorPill(
                                    label = proj.labelPt,
                                    selected = activeSlot.projection == proj && activeSlot.customUvs.isEmpty(),
                                    accent = NeonPink,
                                    bgColor = bgColor,
                                    textColor = textColor,
                                    testTag = "uv_editor_proj_${proj.name}"
                                ) {
                                    selectedIndices = emptySet()
                                    onUpdateObject(selectedObj.id, true) { cur ->
                                        val nextSlots = cur.uvSlots.ifEmpty { listOf(ObjectUVMapSlot()) }.toMutableList()
                                        val si = cur.activeUvSlotIndex.coerceIn(0, nextSlots.lastIndex)
                                        nextSlots[si] = nextSlots[si].copy(projection = proj, customUvs = emptyList())
                                        cur.copy(uvSlots = nextSlots, activeUvSlotIndex = si)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MAIN INTERACTIVE FLAT UV PLANE
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E121B))
                    .border(1.dp, textColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            ) {
                val density = LocalDensity.current
                val canvasW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val canvasH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                val baseTilePx = min(canvasW, canvasH) * 0.76f
                val hitRadiusPx = with(density) { 28.dp.toPx() }

                val tilePx = baseTilePx * viewZoom
                val originX = (canvasW - tilePx) * 0.5f + viewPanX
                val originY = (canvasH - tilePx) * 0.5f + viewPanY

                fun uvToScreen(u: Float, v: Float): Offset =
                    Offset(originX + u * tilePx, originY + v * tilePx)

                fun screenToUv(pos: Offset): Pair<Float, Float> =
                    ((pos.x - originX) / tilePx.coerceAtLeast(1f)) to ((pos.y - originY) / tilePx.coerceAtLeast(1f))

                val curTool by rememberUpdatedState(toolMode)
                val curObj by rememberUpdatedState(selectedObj)
                val curMesh by rememberUpdatedState(flatMesh)
                val curUvs by rememberUpdatedState(workingUvs)
                val curSelected by rememberUpdatedState(selectedIndices)
                val curMulti by rememberUpdatedState(multiSelectEnabled)
                val curWeld by rememberUpdatedState(weldSeamsEnabled)
                val curSnap by rememberUpdatedState(snapGridEnabled)
                val curOriginX by rememberUpdatedState(originX)
                val curOriginY by rememberUpdatedState(originY)
                val curTilePx by rememberUpdatedState(tilePx)

                fun findNearestVertex(tapPos: Offset, uvs: FloatArray, totalVerts: Int): Int? {
                    var bestIdx: Int? = null
                    var bestDist = hitRadiusPx
                    for (v in 0 until totalVerts) {
                        val sx = curOriginX + uvs[v * 2] * curTilePx
                        val sy = curOriginY + uvs[v * 2 + 1] * curTilePx
                        val d = hypot(sx - tapPos.x, sy - tapPos.y)
                        if (d <= bestDist) {
                            bestDist = d
                            bestIdx = v
                        }
                    }
                    return bestIdx
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("uv_editor_canvas")
                        .pointerInput(selectedObj?.id) {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                firstDown.consume()
                                val meshNow = curMesh
                                val objNow = curObj
                                val uvsAtStart = curUvs.copyOf()
                                val vCount = meshNow?.totalVertices ?: 0
                                val startPos = firstDown.position
                                val toolAtStart = curTool

                                var isMultiTouch = false
                                var didDrag = false
                                var activeTransformSelection = curSelected
                                var startCentroid = UvVertexEditorMath.computeSelectionCentroid(activeTransformSelection, uvsAtStart, vCount)
                                val baseSnapshotUvs = uvsAtStart.copyOf()

                                if (toolAtStart == UvEditorToolMode.MOVE || toolAtStart == UvEditorToolMode.SCALE || toolAtStart == UvEditorToolMode.ROTATE) {
                                    if (activeTransformSelection.isEmpty() && vCount > 0) {
                                        findNearestVertex(startPos, uvsAtStart, vCount)?.let { nearest ->
                                            activeTransformSelection = UvVertexEditorMath.expandWithWeldedCorners(setOf(nearest), uvsAtStart, vCount, curWeld)
                                            selectedIndices = activeTransformSelection
                                            startCentroid = UvVertexEditorMath.computeSelectionCentroid(activeTransformSelection, uvsAtStart, vCount)
                                        }
                                    }
                                } else if (toolAtStart == UvEditorToolMode.BOX_SELECT) {
                                    boxDragStart = startPos
                                    boxDragEnd = startPos
                                }

                                var prevSinglePos = startPos
                                var workingLiveUvs = uvsAtStart.copyOf()

                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break

                                    if (pressed.size >= 2) {
                                        isMultiTouch = true
                                        boxDragStart = null
                                        boxDragEnd = null
                                        val z = ev.calculateZoom()
                                        val pan = ev.calculatePan()
                                        if (abs(z - 1f) > 0.002f) {
                                            val centroid = ev.calculateCentroid(useCurrent = true)
                                            val oldZoom = viewZoom
                                            val newZoom = (oldZoom * z).coerceIn(0.35f, 8.0f)
                                            val scaleRatio = newZoom / oldZoom
                                            viewZoom = newZoom
                                            viewPanX = viewPanX * scaleRatio + (centroid.x - canvasW * 0.5f) * (1f - scaleRatio) + pan.x
                                            viewPanY = viewPanY * scaleRatio + (centroid.y - canvasH * 0.5f) * (1f - scaleRatio) + pan.y
                                        } else {
                                            viewPanX += pan.x
                                            viewPanY += pan.y
                                        }
                                        ev.changes.forEach { it.consume() }
                                    } else if (!isMultiTouch) {
                                        val ch = pressed.first()
                                        ch.consume()
                                        val moveDist = hypot(ch.position.x - startPos.x, ch.position.y - startPos.y)
                                        if (moveDist > 6f) didDrag = true

                                        when (toolAtStart) {
                                            UvEditorToolMode.NAVIGATE -> {
                                                viewPanX += ch.position.x - prevSinglePos.x
                                                viewPanY += ch.position.y - prevSinglePos.y
                                            }
                                            UvEditorToolMode.BOX_SELECT -> {
                                                boxDragEnd = ch.position
                                            }
                                            UvEditorToolMode.MOVE -> {
                                                if (activeTransformSelection.isNotEmpty() && didDrag) {
                                                    val du = (ch.position.x - startPos.x) / curTilePx.coerceAtLeast(1f)
                                                    val dv = (ch.position.y - startPos.y) / curTilePx.coerceAtLeast(1f)
                                                    val snapStep = if (curSnap) (1f / 16f) else 0f
                                                    workingLiveUvs = UvVertexEditorMath.translateSelectedUvs(
                                                        baseSnapshotUvs, activeTransformSelection, du, dv, snapStep
                                                    )
                                                    workingUvs = workingLiveUvs
                                                    liveTransformBadge = String.format("ΔU: %+.3f  ΔV: %+.3f", du, dv)
                                                }
                                            }
                                            UvEditorToolMode.SCALE -> {
                                                if (activeTransformSelection.isNotEmpty() && didDrag) {
                                                    val pivScreen = Offset(
                                                        curOriginX + startCentroid.first * curTilePx,
                                                        curOriginY + startCentroid.second * curTilePx
                                                    )
                                                    val dStart = hypot(startPos.x - pivScreen.x, startPos.y - pivScreen.y).coerceAtLeast(24f)
                                                    val dNow = hypot(ch.position.x - pivScreen.x, ch.position.y - pivScreen.y)
                                                    val scaleFactor = (dNow / dStart).coerceIn(0.1f, 5f)
                                                    workingLiveUvs = UvVertexEditorMath.scaleSelectedUvs(
                                                        baseSnapshotUvs,
                                                        activeTransformSelection,
                                                        startCentroid.first,
                                                        startCentroid.second,
                                                        scaleFactor
                                                    )
                                                    workingUvs = workingLiveUvs
                                                    liveTransformBadge = String.format("Escala: %.2fx", scaleFactor)
                                                }
                                            }
                                            UvEditorToolMode.ROTATE -> {
                                                if (activeTransformSelection.isNotEmpty() && didDrag) {
                                                    val pivScreen = Offset(
                                                        curOriginX + startCentroid.first * curTilePx,
                                                        curOriginY + startCentroid.second * curTilePx
                                                    )
                                                    val a0 = atan2((startPos.y - pivScreen.y).toDouble(), (startPos.x - pivScreen.x).toDouble())
                                                    val a1 = atan2((ch.position.y - pivScreen.y).toDouble(), (ch.position.x - pivScreen.x).toDouble())
                                                    val deltaAngle = (a1 - a0).toFloat()
                                                    workingLiveUvs = UvVertexEditorMath.rotateSelectedUvs(
                                                        baseSnapshotUvs,
                                                        activeTransformSelection,
                                                        startCentroid.first,
                                                        startCentroid.second,
                                                        deltaAngle
                                                    )
                                                    workingUvs = workingLiveUvs
                                                    liveTransformBadge = "Giro: ${Math.toDegrees(deltaAngle.toDouble()).roundToInt()}°"
                                                }
                                            }
                                            UvEditorToolMode.SELECT_POINT, UvEditorToolMode.ISLAND_SELECT -> Unit
                                        }
                                        prevSinglePos = ch.position
                                    }
                                }

                                // Gesture Released -> Finalize Selection or Commit UV Coordinates
                                liveTransformBadge = null
                                if (!isMultiTouch) {
                                    when (toolAtStart) {
                                        UvEditorToolMode.SELECT_POINT -> {
                                            if (!didDrag && vCount > 0) {
                                                val hit = findNearestVertex(startPos, curUvs, vCount)
                                                if (hit != null) {
                                                    val group = UvVertexEditorMath.expandWithWeldedCorners(setOf(hit), curUvs, vCount, curWeld)
                                                    selectedIndices = if (curMulti) {
                                                        if (hit in curSelected) curSelected - group else curSelected + group
                                                    } else {
                                                        group
                                                    }
                                                } else if (!curMulti) {
                                                    selectedIndices = emptySet()
                                                }
                                            }
                                        }
                                        UvEditorToolMode.ISLAND_SELECT -> {
                                            if (!didDrag && vCount > 0 && meshNow != null) {
                                                val hit = findNearestVertex(startPos, curUvs, vCount)
                                                if (hit != null) {
                                                    val island = UvVertexEditorMath.findConnectedUvIslandIndices(
                                                        hit, curUvs, meshNow.globalTriangleIndices, vCount
                                                    )
                                                    selectedIndices = if (curMulti) {
                                                        if (hit in curSelected) curSelected - island else curSelected + island
                                                    } else {
                                                        island
                                                    }
                                                } else if (!curMulti) {
                                                    selectedIndices = emptySet()
                                                }
                                            }
                                        }
                                        UvEditorToolMode.BOX_SELECT -> {
                                            val bStart = boxDragStart
                                            val bEnd = boxDragEnd
                                            if (bStart != null && bEnd != null && vCount > 0) {
                                                val minX = min(bStart.x, bEnd.x)
                                                val maxX = max(bStart.x, bEnd.x)
                                                val minY = min(bStart.y, bEnd.y)
                                                val maxY = max(bStart.y, bEnd.y)
                                                val inside = LinkedHashSet<Int>()
                                                for (v in 0 until vCount) {
                                                    val sx = curOriginX + curUvs[v * 2] * curTilePx
                                                    val sy = curOriginY + curUvs[v * 2 + 1] * curTilePx
                                                    if (sx in minX..maxX && sy in minY..maxY) {
                                                        inside.add(v)
                                                    }
                                                }
                                                val expanded = UvVertexEditorMath.expandWithWeldedCorners(inside, curUvs, vCount, curWeld)
                                                selectedIndices = if (curMulti) curSelected + expanded else expanded
                                            }
                                        }
                                        UvEditorToolMode.MOVE, UvEditorToolMode.SCALE, UvEditorToolMode.ROTATE -> {
                                            if (didDrag && objNow != null && workingLiveUvs.isNotEmpty() && activeTransformSelection.isNotEmpty()) {
                                                onCommitCustomUvs(objNow.id, workingLiveUvs.toList(), 350L)
                                            }
                                        }
                                        UvEditorToolMode.NAVIGATE -> Unit
                                    }
                                }
                                boxDragStart = null
                                boxDragEnd = null
                            }
                        }
                ) {
                    // 1. Background workspace grid
                    val stepBg = 40f
                    var gx = ((originX % stepBg) + stepBg) % stepBg
                    while (gx < size.width) {
                        drawLine(Color.White.copy(alpha = 0.035f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                        gx += stepBg
                    }
                    var gy = ((originY % stepBg) + stepBg) % stepBg
                    while (gy < size.height) {
                        drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, gy), Offset(size.width, gy), 1f)
                        gy += stepBg
                    }

                    // 2. [0..1] UDIM tile backdrop + Texture Image
                    val tileLeft = originX.roundToInt()
                    val tileTop = originY.roundToInt()
                    val tileSide = tilePx.roundToInt().coerceAtLeast(1)
                    drawRect(
                        color = Color(0xFF191F2E),
                        topLeft = Offset(originX, originY),
                        size = Size(tilePx, tilePx)
                    )
                    textureBitmap?.let { bmp ->
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(tileLeft, tileTop),
                            dstSize = IntSize(tileSide, tileSide),
                            alpha = 0.86f
                        )
                    }

                    // 16x16 Snap Sub-grid inside [0..1]
                    val gridDivs = if (snapGridEnabled) 16 else 8
                    for (i in 1 until gridDivs) {
                        val f = i.toFloat() / gridDivs
                        val alpha = if (snapGridEnabled && i % 4 != 0) 0.10f else 0.18f
                        drawLine(
                            Color.White.copy(alpha = alpha),
                            Offset(originX + tilePx * f, originY),
                            Offset(originX + tilePx * f, originY + tilePx),
                            1f
                        )
                        drawLine(
                            Color.White.copy(alpha = alpha),
                            Offset(originX, originY + tilePx * f),
                            Offset(originX + tilePx, originY + tilePx * f),
                            1f
                        )
                    }
                    // UDIM [0..1] highlight frame
                    drawRect(
                        color = CyanAccent.copy(alpha = 0.72f),
                        topLeft = Offset(originX, originY),
                        size = Size(tilePx, tilePx),
                        style = Stroke(width = 2.2f)
                    )

                    // 3. UV Triangle Edges
                    val meshNow = flatMesh
                    val uvs = workingUvs
                    val vCount = min(meshNow?.totalVertices ?: 0, uvs.size / 2)
                    if (meshNow != null && vCount > 0) {
                        val tri = meshNow.globalTriangleIndices
                        val maxEdgesDraw = min(tri.size, 15000)
                        var t = 0
                        while (t + 2 < maxEdgesDraw) {
                            val a = tri[t]
                            val b = tri[t + 1]
                            val c = tri[t + 2]
                            t += 3
                            if (a !in 0 until vCount || b !in 0 until vCount || c !in 0 until vCount) continue
                            val pA = uvToScreen(uvs[a * 2], uvs[a * 2 + 1])
                            val pB = uvToScreen(uvs[b * 2], uvs[b * 2 + 1])
                            val pC = uvToScreen(uvs[c * 2], uvs[c * 2 + 1])

                            fun edgeColor(uIdx: Int, vIdx: Int): Color {
                                val uSel = uIdx in selectedIndices
                                val vSel = vIdx in selectedIndices
                                return when {
                                    uSel && vSel -> NeonGreen.copy(alpha = 0.92f)
                                    uSel || vSel -> OrangeAccent.copy(alpha = 0.78f)
                                    else -> CyanAccent.copy(alpha = 0.48f)
                                }
                            }
                            drawLine(edgeColor(a, b), pA, pB, strokeWidth = 1.6f)
                            drawLine(edgeColor(b, c), pB, pC, strokeWidth = 1.6f)
                            drawLine(edgeColor(c, a), pC, pA, strokeWidth = 1.6f)
                        }

                        // 4. UV Vertex Points ("pontos por pontos")
                        val stepV = if (vCount > 2400) (vCount / 2400).coerceAtLeast(1) else 1
                        var v = 0
                        while (v < vCount) {
                            val isSel = v in selectedIndices
                            val pt = uvToScreen(uvs[v * 2], uvs[v * 2 + 1])
                            if (isSel) {
                                drawCircle(Color(0xFF0A0F14), radius = 7.5f, center = pt)
                                drawCircle(NeonGreen, radius = 5.8f, center = pt)
                                drawCircle(Color.White, radius = 2.2f, center = pt)
                            } else {
                                drawCircle(Color(0xFF0E121B), radius = 4.8f, center = pt)
                                drawCircle(OrangeAccent.copy(alpha = 0.9f), radius = 3.2f, center = pt)
                            }
                            v += stepV
                        }

                        // Ensure any selected vertex skipped by stepV still renders prominently
                        if (stepV > 1) {
                            for (sv in selectedIndices) {
                                if (sv in 0 until vCount) {
                                    val pt = uvToScreen(uvs[sv * 2], uvs[sv * 2 + 1])
                                    drawCircle(Color(0xFF0A0F14), radius = 7.5f, center = pt)
                                    drawCircle(NeonGreen, radius = 5.8f, center = pt)
                                    drawCircle(Color.White, radius = 2.2f, center = pt)
                                }
                            }
                        }

                        // Selection Pivot Centroid Crosshair
                        if (selectedIndices.isNotEmpty()) {
                            val (cu, cv) = UvVertexEditorMath.computeSelectionCentroid(selectedIndices, uvs, vCount)
                            val piv = uvToScreen(cu, cv)
                            drawCircle(NeonPink.copy(alpha = 0.25f), radius = 14f, center = piv)
                            drawCircle(NeonPink, radius = 9f, center = piv, style = Stroke(width = 2f))
                            drawLine(NeonPink, Offset(piv.x - 13f, piv.y), Offset(piv.x + 13f, piv.y), 1.8f)
                            drawLine(NeonPink, Offset(piv.x, piv.y - 13f), Offset(piv.x, piv.y + 13f), 1.8f)
                        }
                    }

                    // 5. Box Selection Overlay
                    val bStart = boxDragStart
                    val bEnd = boxDragEnd
                    if (bStart != null && bEnd != null) {
                        val rect = Rect(
                            left = min(bStart.x, bEnd.x),
                            top = min(bStart.y, bEnd.y),
                            right = max(bStart.x, bEnd.x),
                            bottom = max(bStart.y, bEnd.y)
                        )
                        drawRect(CyanAccent.copy(alpha = 0.16f), rect.topLeft, rect.size)
                        drawRect(CyanAccent, rect.topLeft, rect.size, style = Stroke(width = 2f))
                    }
                }

                // Helpful hint & zoom reset chip in top-right corner of UV Canvas
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    liveTransformBadge?.let { badge ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeonGreen)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        color = panelColor.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, textColor.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewZoom = 1.0f
                                viewPanX = 0f
                                viewPanY = 0f
                            }
                            .testTag("uv_editor_center_view_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CenterFocusWeak, contentDescription = "Centralizar UV", tint = CyanAccent, modifier = Modifier.size(14.dp))
                            Text("${(viewZoom * 100).roundToInt()}%", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }

                // Bottom-left active tool hint
                Surface(
                    color = panelColor.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = toolMode.shortHint,
                        fontSize = 9.5.sp,
                        color = textColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // BOTTOM CONTROLS: Selection helpers + Transform & Select Tools
            Surface(
                color = panelColor.copy(alpha = 0.96f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Selection Helpers & Grid Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UvEditorPill(
                            label = if (multiSelectEnabled) "Multi: ON" else "Multi: OFF",
                            selected = multiSelectEnabled,
                            accent = NeonGreen,
                            bgColor = bgColor,
                            textColor = textColor,
                            testTag = "uv_editor_multi_toggle"
                        ) { multiSelectEnabled = !multiSelectEnabled }

                        UvEditorPill(
                            label = if (weldSeamsEnabled) "Juntas: Unidas" else "Juntas: Soltas",
                            selected = weldSeamsEnabled,
                            accent = OrangeAccent,
                            bgColor = bgColor,
                            textColor = textColor,
                            icon = Icons.Default.Hub,
                            testTag = "uv_editor_weld_toggle"
                        ) { weldSeamsEnabled = !weldSeamsEnabled }

                        UvEditorPill(
                            label = if (snapGridEnabled) "Snap 1/16" else "Snap: OFF",
                            selected = snapGridEnabled,
                            accent = CyanAccent,
                            bgColor = bgColor,
                            textColor = textColor,
                            icon = Icons.Default.GridOn,
                            testTag = "uv_editor_snap_toggle"
                        ) { snapGridEnabled = !snapGridEnabled }

                        val totalV = flatMesh?.totalVertices ?: 0
                        UvEditorPill(
                            label = "Todos",
                            selected = totalV > 0 && selectedIndices.size == totalV,
                            accent = CyanAccent,
                            bgColor = bgColor,
                            textColor = textColor,
                            icon = Icons.Default.SelectAll,
                            testTag = "uv_editor_select_all_btn"
                        ) {
                            selectedIndices = if (selectedIndices.size == totalV) emptySet() else (0 until totalV).toSet()
                        }

                        UvEditorPill(
                            label = "Inverter",
                            selected = false,
                            accent = NeonPink,
                            bgColor = bgColor,
                            textColor = textColor,
                            testTag = "uv_editor_invert_sel_btn"
                        ) {
                            selectedIndices = (0 until totalV).filterTo(LinkedHashSet()) { it !in selectedIndices }
                        }

                        UvEditorPill(
                            label = "Limpar",
                            selected = false,
                            accent = textColor.copy(alpha = 0.6f),
                            bgColor = bgColor,
                            textColor = textColor,
                            testTag = "uv_editor_clear_sel_btn"
                        ) { selectedIndices = emptySet() }
                    }

                    // Blender UV Tools Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UvEditorToolMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                UvEditorToolMode.SELECT_POINT -> Icons.Default.TouchApp
                                UvEditorToolMode.BOX_SELECT -> Icons.Default.CropFree
                                UvEditorToolMode.ISLAND_SELECT -> Icons.Default.Grain
                                UvEditorToolMode.MOVE -> Icons.Default.OpenWith
                                UvEditorToolMode.SCALE -> Icons.Default.AspectRatio
                                UvEditorToolMode.ROTATE -> Icons.Default.RotateRight
                                UvEditorToolMode.NAVIGATE -> Icons.Default.PanTool
                            }
                            val toolAccent = when (mode) {
                                UvEditorToolMode.SELECT_POINT,
                                UvEditorToolMode.BOX_SELECT,
                                UvEditorToolMode.ISLAND_SELECT -> CyanAccent
                                UvEditorToolMode.MOVE,
                                UvEditorToolMode.SCALE,
                                UvEditorToolMode.ROTATE -> NeonGreen
                                UvEditorToolMode.NAVIGATE -> OrangeAccent
                            }
                            UvEditorPill(
                                label = mode.labelPt,
                                selected = toolMode == mode,
                                accent = toolAccent,
                                bgColor = bgColor,
                                textColor = textColor,
                                icon = icon,
                                testTag = "uv_editor_tool_${mode.name}"
                            ) { toolMode = mode }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UvEditorPill(
    label: String,
    selected: Boolean,
    accent: Color,
    bgColor: Color,
    textColor: Color,
    icon: ImageVector? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.24f) else bgColor.copy(alpha = 0.65f))
            .border(1.dp, if (selected) accent else textColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = if (selected) accent else textColor.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
        }
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
