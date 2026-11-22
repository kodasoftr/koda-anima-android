package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrushPreset
import com.example.model.KodanimaTheme
import com.example.model.KritaBrushEngineKind
import com.example.model.ToolType
import com.example.ui.theme.NeonPink
import kotlin.math.sin

private val brushIconBitmapCache = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64) {}

/**
 * Draws a clean Procreate / Krita Bezier sine-curve preview for any [BrushPreset] engine.
 */
@Composable
fun KritaBrushStrokeWaveformPreview(
    preset: BrushPreset,
    strokeColor: Color,
    modifier: Modifier = Modifier
) {
    val thumbBitmap = remember(preset.iconBase64) {
        val b64 = preset.iconBase64 ?: return@remember null
        brushIconBitmapCache.get(b64)?.let { return@remember it }
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also {
                brushIconBitmapCache.put(b64, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap,
                contentDescription = preset.namePt,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, strokeColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            )
        }

        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val padX = 6f
            val spanW = (w - padX * 2f).coerceAtLeast(8f)
            val midY = h * 0.52f
            val amp = h * 0.28f

            val engine = preset.engineKind
            val steps = 28

            when (engine) {
                KritaBrushEngineKind.AIRBRUSH_SOFT -> {
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = padX + spanW * t
                        val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp
                        drawCircle(strokeColor.copy(alpha = 0.18f), radius = h * 0.34f, center = Offset(x, y))
                        drawCircle(strokeColor.copy(alpha = 0.32f), radius = h * 0.19f, center = Offset(x, y))
                    }
                }
                KritaBrushEngineKind.BRISTLE_DRY, KritaBrushEngineKind.BRISTLE_GLAZE -> {
                    for (offsetIdx in -1..1) {
                        val path = Path()
                        for (i in 0..steps) {
                            val t = i.toFloat() / steps
                            val x = padX + spanW * t
                            val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp + offsetIdx * 3.5f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = if (offsetIdx == 0) 0.92f else 0.55f),
                            style = Stroke(width = if (engine == KritaBrushEngineKind.BRISTLE_GLAZE) 3.4f else 2.2f, cap = StrokeCap.Round)
                        )
                    }
                }
                KritaBrushEngineKind.PENCIL_GRAPHITE, KritaBrushEngineKind.CHARCOAL_SOFT, KritaBrushEngineKind.CHALK_GRAINY -> {
                    val path = Path()
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = padX + spanW * t
                        val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        val j = ((i * 37) % 7 - 3) * 1.4f
                        drawCircle(
                            color = strokeColor.copy(alpha = 0.65f),
                            radius = if (engine == KritaBrushEngineKind.PENCIL_GRAPHITE) 1.4f else 2.1f,
                            center = Offset(x, y + j)
                        )
                    }
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = 0.65f),
                        style = Stroke(width = if (engine == KritaBrushEngineKind.PENCIL_GRAPHITE) 2.2f else 4.2f, cap = StrokeCap.Round)
                    )
                }
                KritaBrushEngineKind.INK_GPEN -> {
                    var prev = Offset(padX, midY)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val x = padX + spanW * t
                        val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp
                        val taper = sin(t * Math.PI).toFloat().coerceIn(0.18f, 1.0f)
                        val cur = Offset(x, y)
                        drawLine(strokeColor, prev, cur, strokeWidth = 5.4f * taper, cap = StrokeCap.Round)
                        prev = cur
                    }
                }
                KritaBrushEngineKind.MARKER_CHISEL -> {
                    val path = Path()
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = padX + spanW * t
                        val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = 0.65f),
                        style = Stroke(width = 7.5f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                    )
                }
                else -> {
                    val path = Path()
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = padX + spanW * t
                        val y = midY - sin(t * Math.PI * 2.0).toFloat() * amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = if (engine == KritaBrushEngineKind.BLENDER_WET_SOFT) 0.55f else 0.95f),
                        style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

/**
 * Procreate-style floating brush library drawer for Mobile mode.
 * Features curved Bezier Krita stroke previews, 3-wheels library toggle (12 Native <-> Imported .kpp/.bundle),
 * + Import Krita trigger, and inline Size & Opacity sliders.
 */
@Composable
fun ProcreateBrushPickerSheet(
    visible: Boolean,
    activePreset: BrushPreset,
    brushSize: Float,
    brushOpacity: Float,
    importedBrushes: List<BrushPreset>,
    theme: KodanimaTheme,
    onSelectPreset: (BrushPreset) -> Unit,
    onChangeSize: (Float) -> Unit,
    onChangeOpacity: (Float) -> Unit,
    onRequestImportKrita: () -> Unit,
    onDeleteImportedBrush: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpenBrushStudio: (() -> Unit)? = null,
    selectedTool: ToolType = ToolType.BRUSH,
    onSelectTool: ((ToolType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showImportedLibrary by remember { mutableStateOf(false) }
    var showReadyShapesRow by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val panelColor = Color(theme.panel)
        val accentColor = Color(theme.accent)
        val secondaryAccent = Color(theme.secondaryAccent)
        val textColor = Color(theme.text)
        val bgColor = Color(theme.bg)

        val displayList = if (showImportedLibrary) importedBrushes else BrushPreset.ALL_12_PRESETS
        val readyShapeTools = listOf(
            Triple(ToolType.LINE, Icons.Default.HorizontalRule, "Linha"),
            Triple(ToolType.RECTANGLE, Icons.Default.CropSquare, "Retângulo"),
            Triple(ToolType.CIRCLE, Icons.Default.Circle, "Círculo")
        )
        val isShapeSelected = selectedTool in listOf(ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE)

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 460.dp)
                .border(1.5.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .testTag("procreate_brush_picker"),
            color = panelColor.copy(alpha = 0.97f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Title & Close Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showImportedLibrary) "Krita Importados (.kpp)" else "Biblioteca Krita (12)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = activePreset.namePt,
                            fontSize = 10.sp,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar Pincéis", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action Row: [+ Importar] [Config. Brush] [Ferr. Prontas] [3 Rodas]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, secondaryAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable { onRequestImportKrita() }
                            .testTag("mobile_import_krita_button"),
                        color = secondaryAccent.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Importar Krita", tint = secondaryAccent, modifier = Modifier.size(14.dp))
                            Text("Importar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = secondaryAccent)
                        }
                    }

                    if (onOpenBrushStudio != null) {
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenBrushStudio()
                                }
                                .testTag("mobile_brush_studio_btn"),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Configuração de Brush", tint = accentColor, modifier = Modifier.size(14.dp))
                                Text("Config. Brush", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                    }

                    if (onSelectTool != null) {
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (showReadyShapesRow || isShapeSelected) accentColor else textColor.copy(alpha = 0.25f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { showReadyShapesRow = !showReadyShapesRow }
                                .testTag("mobile_ready_shapes_btn"),
                            color = if (showReadyShapesRow || isShapeSelected) accentColor.copy(alpha = 0.22f) else bgColor.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Category, contentDescription = "Ferramentas Prontas", tint = if (isShapeSelected) accentColor else textColor, modifier = Modifier.size(14.dp))
                                Text("Ferr. Prontas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isShapeSelected) accentColor else textColor)
                            }
                        }
                    }

                    // 3-Wheels Toggle between Native 12 and Imported Krita
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.2.dp,
                                if (showImportedLibrary) NeonPink else accentColor,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { showImportedLibrary = !showImportedLibrary }
                            .testTag("mobile_three_wheels_brush_switch"),
                        color = if (showImportedLibrary) NeonPink.copy(alpha = 0.22f) else bgColor.copy(alpha = 0.7f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ThreeWheelsIcon(
                                tint = textColor,
                                accentColor = if (showImportedLibrary) NeonPink else accentColor,
                                size = 18.dp
                            )
                        }
                    }
                }

                if (showReadyShapesRow && onSelectTool != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        readyShapeTools.forEach { (shapeTool, shapeIcon, shapeLabel) ->
                            val isActiveShape = selectedTool == shapeTool
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isActiveShape) accentColor else textColor.copy(alpha = 0.18f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onSelectTool(shapeTool)
                                        onDismiss()
                                    }
                                    .testTag("tool_${shapeTool.name.lowercase()}"),
                                color = if (isActiveShape) accentColor.copy(alpha = 0.24f) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(shapeIcon, contentDescription = shapeLabel, tint = if (isActiveShape) accentColor else textColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(shapeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isActiveShape) accentColor else textColor)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Procreate-style Inline Quick Size & Opacity Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tam: ${brushSize.toInt()}px", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f))
                        Slider(
                            value = brushSize,
                            onValueChange = onChangeSize,
                            valueRange = 1f..80f,
                            modifier = Modifier.height(26.dp),
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Opac: ${(brushOpacity * 100).toInt()}%", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f))
                        Slider(
                            value = brushOpacity,
                            onValueChange = onChangeOpacity,
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier.height(26.dp),
                            colors = SliderDefaults.colors(thumbColor = secondaryAccent, activeTrackColor = secondaryAccent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (displayList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor.copy(alpha = 0.45f))
                            .clickable { onRequestImportKrita() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Nenhum brush Krita importado ainda",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "Toque em + Importar para carregar .kpp ou .bundle do Krita",
                                fontSize = 10.sp,
                                color = secondaryAccent
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayList, key = { it.id }) { preset ->
                            val isSelected = preset.id == activePreset.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.24f) else bgColor.copy(alpha = 0.65f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) accentColor else textColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onSelectPreset(preset)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.namePt,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) accentColor else textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = preset.textureNamePt,
                                        fontSize = 9.sp,
                                        color = textColor.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                KritaBrushStrokeWaveformPreview(
                                    preset = preset,
                                    strokeColor = if (isSelected) accentColor else textColor,
                                    modifier = Modifier
                                        .width(88.dp)
                                        .height(26.dp)
                                )

                                if (preset.isImportedKrita) {
                                    IconButton(
                                        onClick = { onDeleteImportedBrush(preset.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remover Brush",
                                            tint = NeonPink,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
