package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.KodanimaTheme
import com.example.ui.theme.NeonPink
import kotlin.math.roundToInt

/**
 * Floating Brush Studio Configurator Drawer opened by clicking the Brush icon.
 * Allows switching between Native (12) and Imported (.kpp) brushes, searching by name
 * with the search loupe, tweaking full stroke/engine parameters, saving persistently,
 * or resetting any preset back to factory defaults.
 */
@Composable
fun BrushStudioDrawer(
    visible: Boolean,
    activePreset: BrushPreset,
    nativePresets: List<BrushPreset>,
    importedPresets: List<BrushPreset>,
    theme: KodanimaTheme,
    onSelectPreset: (BrushPreset) -> Unit,
    onSaveBrushCustomization: (BrushPreset, Float, Float, BrushDynamicParams) -> Unit,
    onResetBrushCustomization: (BrushPreset) -> BrushPreset,
    onRequestImportKrita: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it / 2 }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 2 }),
        modifier = modifier
    ) {
        var showImportedTab by remember { mutableStateOf(activePreset.isImportedKrita) }
        var isSearchActive by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        var editingPreset by remember(activePreset.id) { mutableStateOf(activePreset) }
        var editSize by remember(activePreset.id) { mutableFloatStateOf(activePreset.defaultSize) }
        var editOpacity by remember(activePreset.id) { mutableFloatStateOf(activePreset.defaultOpacity) }
        var editFlow by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.flow) }
        var editHardness by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.hardness) }
        var editSpacing by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.spacing) }
        var editGrain by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.grainAmount) }
        var editTaper by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.taperStrength) }
        var editBristles by remember(activePreset.id) { mutableIntStateOf(activePreset.dynamicParams.bristleCount) }
        var editSizeScale by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.sizeScale) }
        var editDualColorEnabled by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.dualColorEnabled) }
        var editSecondaryColor by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.secondaryColor) }
        var editSecondaryColorMix by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.secondaryColorMix) }
        var editDualColorMode by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.dualColorMode) }
        var editInnerDotsEnabled by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.innerDotsEnabled) }
        var editInnerDotsDensity by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.innerDotsDensity) }
        var editInnerDotsSize by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.innerDotsSize) }
        var editInnerDotsUseSecondary by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.innerDotsUseSecondaryColor) }
        var editDualShapeEnabled by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.dualShapeEnabled) }
        var editSecondaryShapeKind by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.secondaryShapeKind) }
        var editDualShapeScale by remember(activePreset.id) { mutableFloatStateOf(activePreset.dynamicParams.dualShapeScale) }
        var editDualShapeBlendMode by remember(activePreset.id) { mutableStateOf(activePreset.dynamicParams.dualShapeBlendMode) }

        LaunchedEffect(activePreset) {
            editingPreset = activePreset
            editSize = activePreset.defaultSize
            editOpacity = activePreset.defaultOpacity
            editFlow = activePreset.dynamicParams.flow
            editHardness = activePreset.dynamicParams.hardness
            editSpacing = activePreset.dynamicParams.spacing
            editGrain = activePreset.dynamicParams.grainAmount
            editTaper = activePreset.dynamicParams.taperStrength
            editBristles = activePreset.dynamicParams.bristleCount
            editSizeScale = activePreset.dynamicParams.sizeScale
            editDualColorEnabled = activePreset.dynamicParams.dualColorEnabled
            editSecondaryColor = activePreset.dynamicParams.secondaryColor
            editSecondaryColorMix = activePreset.dynamicParams.secondaryColorMix
            editDualColorMode = activePreset.dynamicParams.dualColorMode
            editInnerDotsEnabled = activePreset.dynamicParams.innerDotsEnabled
            editInnerDotsDensity = activePreset.dynamicParams.innerDotsDensity
            editInnerDotsSize = activePreset.dynamicParams.innerDotsSize
            editInnerDotsUseSecondary = activePreset.dynamicParams.innerDotsUseSecondaryColor
            editDualShapeEnabled = activePreset.dynamicParams.dualShapeEnabled
            editSecondaryShapeKind = activePreset.dynamicParams.secondaryShapeKind
            editDualShapeScale = activePreset.dynamicParams.dualShapeScale
            editDualShapeBlendMode = activePreset.dynamicParams.dualShapeBlendMode
        }

        val panelColor = Color(theme.panel)
        val bgColor = Color(theme.bg)
        val accentColor = Color(theme.accent)
        val secondaryAccent = Color(theme.secondaryAccent)
        val onAccentColor = if (theme.isLight) Color.White else bgColor
        val textColor = Color(theme.text)

        val baseList = if (showImportedTab) importedPresets else nativePresets
        val filteredList = remember(baseList, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) baseList else baseList.filter {
                it.namePt.contains(q, ignoreCase = true) ||
                    it.shortLabel.contains(q, ignoreCase = true) ||
                    it.textureNamePt.contains(q, ignoreCase = true)
            }
        }

        val previewPreset = remember(
            editingPreset, editSize, editOpacity, editFlow, editHardness,
            editSpacing, editGrain, editTaper, editBristles, editSizeScale,
            editDualColorEnabled, editSecondaryColor, editSecondaryColorMix, editDualColorMode,
            editInnerDotsEnabled, editInnerDotsDensity, editInnerDotsSize, editInnerDotsUseSecondary,
            editDualShapeEnabled, editSecondaryShapeKind, editDualShapeScale, editDualShapeBlendMode
        ) {
            editingPreset.copy(
                defaultSize = editSize,
                defaultOpacity = editOpacity,
                dynamicParams = editingPreset.dynamicParams.copy(
                    hardness = editHardness,
                    spacing = editSpacing,
                    flow = editFlow,
                    grainAmount = editGrain,
                    taperStrength = editTaper,
                    bristleCount = editBristles,
                    sizeScale = editSizeScale,
                    dualColorEnabled = editDualColorEnabled,
                    secondaryColor = editSecondaryColor,
                    secondaryColorMix = editSecondaryColorMix,
                    dualColorMode = editDualColorMode,
                    innerDotsEnabled = editInnerDotsEnabled,
                    innerDotsDensity = editInnerDotsDensity,
                    innerDotsSize = editInnerDotsSize,
                    innerDotsUseSecondaryColor = editInnerDotsUseSecondary,
                    dualShapeEnabled = editDualShapeEnabled,
                    secondaryShapeKind = editSecondaryShapeKind,
                    dualShapeScale = editDualShapeScale,
                    dualShapeBlendMode = editDualShapeBlendMode
                )
            )
        }

        Surface(
            modifier = Modifier
                .widthIn(min = 290.dp, max = 350.dp)
                .heightIn(max = 520.dp)
                .border(1.2.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .testTag("brush_studio_drawer"),
            color = panelColor.copy(alpha = 0.97f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            ) {
                // Header: Title + Close X button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Estúdio de Pincéis",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("brush_studio_close_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar Estúdio de Pincéis",
                            tint = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Switcher row: Nativos / Importados + Lupa Search button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StudioTabPill(
                        label = "Nativos (${nativePresets.size})",
                        selected = !showImportedTab,
                        accentColor = accentColor,
                        textColor = textColor,
                        onClick = { showImportedTab = false },
                        modifier = Modifier.weight(1f)
                    )
                    StudioTabPill(
                        label = "Importados (${importedPresets.size})",
                        selected = showImportedTab,
                        accentColor = NeonPink,
                        textColor = textColor,
                        onClick = { showImportedTab = true },
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSearchActive) accentColor.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.7f))
                            .border(
                                1.dp,
                                if (isSearchActive) accentColor else textColor.copy(alpha = 0.25f),
                                RoundedCornerShape(7.dp)
                            )
                            .clickable {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }
                            .testTag("brush_studio_search_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar Pincel",
                            tint = if (isSearchActive) accentColor else textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Inline Search Bar when Loupe is toggled
                AnimatedVisibility(visible = isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor.copy(alpha = 0.85f))
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Filtrar pincel por nome...",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.45f)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = textColor, fontSize = 11.sp),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("brush_studio_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpar busca",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                if (showImportedTab) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(secondaryAccent.copy(alpha = 0.14f))
                            .border(1.dp, secondaryAccent.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                            .clickable { onRequestImportKrita() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = secondaryAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Importar Brush Krita (.kpp / .bundle)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Brush list (vertical scroll)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor.copy(alpha = 0.55f))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (filteredList.isEmpty()) {
                        item {
                            Text(
                                text = if (showImportedTab) "Nenhum brush .kpp importado." else "Nenhum brush encontrado.",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.55f),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        items(filteredList, key = { it.id }) { preset ->
                            val isSel = preset.id == editingPreset.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) accentColor.copy(alpha = 0.24f) else Color.Transparent)
                                    .border(
                                        width = if (isSel) 1.2.dp else 0.5.dp,
                                        color = if (isSel) accentColor else textColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        onSelectPreset(preset)
                                        editingPreset = preset
                                    }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                    .testTag("brush_studio_item_${preset.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = preset.textureSymbol, fontSize = 12.sp, color = accentColor)
                                    Text(
                                        text = preset.namePt,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) accentColor else textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                KritaBrushStrokeWaveformPreview(
                                    preset = if (isSel) previewPreset else preset,
                                    strokeColor = if (isSel) accentColor else textColor.copy(alpha = 0.78f),
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Editable Parameters Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Parâmetros: ${editingPreset.namePt}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BrushParamSlider("Tamanho", "${editSize.roundToInt()} px", editSize, 1f..80f, accentColor, textColor) {
                        editSize = it
                    }
                    BrushParamSlider("Opacidade", "${(editOpacity * 100).roundToInt()}%", editOpacity, 0.05f..1.0f, accentColor, textColor) {
                        editOpacity = it
                    }
                    BrushParamSlider("Fluxo", "${(editFlow * 100).roundToInt()}%", editFlow, 0.05f..1.0f, accentColor, textColor) {
                        editFlow = it
                    }
                    BrushParamSlider("Dureza", "${(editHardness * 100).roundToInt()}%", editHardness, 0.05f..1.0f, accentColor, textColor) {
                        editHardness = it
                    }
                    BrushParamSlider("Espaçamento", "${(editSpacing * 100).roundToInt()}%", editSpacing, 0.02f..1.0f, accentColor, textColor) {
                        editSpacing = it
                    }
                    BrushParamSlider("Grão / Textura", "${(editGrain * 100).roundToInt()}%", editGrain, 0.0f..1.0f, accentColor, textColor) {
                        editGrain = it
                    }
                    BrushParamSlider("Afinamento (Taper)", "${(editTaper * 100).roundToInt()}%", editTaper, 0.0f..1.0f, accentColor, textColor) {
                        editTaper = it
                    }
                    BrushParamSlider("Cerdas", "$editBristles", editBristles.toFloat(), 3f..9f, accentColor, textColor) {
                        editBristles = it.roundToInt().coerceIn(3, 9)
                    }
                    BrushParamSlider("Escala de Traço", "${((editSizeScale * 10).roundToInt() / 10f)}x", editSizeScale, 0.4f..2.0f, accentColor, textColor) {
                        editSizeScale = it
                    }

                    // --- 2 Cores no Pincel ---
                    BrushFeatureToggleRow(
                        title = "2 Cores no Pincel",
                        enabled = editDualColorEnabled,
                        accentColor = accentColor,
                        textColor = textColor,
                        testTag = "brush_studio_dual_color_toggle"
                    ) { editDualColorEnabled = !editDualColorEnabled }
                    if (editDualColorEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("gradient" to "Gradiente", "alternating" to "Alternado", "edge" to "Borda").forEach { (modeKey, modeLabel) ->
                                val active = editDualColorMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) accentColor.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.7f))
                                        .border(0.8.dp, if (active) accentColor else textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { editDualColorMode = modeKey }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(modeLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) accentColor else textColor)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("2ª Cor:", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f))
                            listOf(
                                0xFFFF7A00L, 0xFFFF3366L, 0xFF00D2FFL, 0xFF00E676L,
                                0xFFFFD700L, 0xFF7C4DFFL, 0xFFFFFFFFL, 0xFF14161FL
                            ).forEach { colLong ->
                                val sel = editSecondaryColor == colLong
                                Box(
                                    modifier = Modifier
                                        .size(if (sel) 17.dp else 14.dp)
                                        .clip(CircleShape)
                                        .background(Color(colLong))
                                        .border(
                                            if (sel) 2.dp else 0.8.dp,
                                            if (sel) accentColor else textColor.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { editSecondaryColor = colLong }
                                )
                            }
                        }
                        BrushParamSlider(
                            "Mistura 2ª Cor",
                            "${(editSecondaryColorMix * 100).roundToInt()}%",
                            editSecondaryColorMix,
                            0.1f..1.0f,
                            accentColor,
                            textColor
                        ) { editSecondaryColorMix = it }
                    }

                    // --- Pontinhos Internos ---
                    BrushFeatureToggleRow(
                        title = "Pontinhos Internos",
                        enabled = editInnerDotsEnabled,
                        accentColor = accentColor,
                        textColor = textColor,
                        testTag = "brush_studio_inner_dots_toggle"
                    ) { editInnerDotsEnabled = !editInnerDotsEnabled }
                    if (editInnerDotsEnabled) {
                        BrushParamSlider(
                            "Densidade Pontinhos",
                            "${(editInnerDotsDensity * 100).roundToInt()}%",
                            editInnerDotsDensity,
                            0.1f..1.0f,
                            accentColor,
                            textColor
                        ) { editInnerDotsDensity = it }
                        BrushParamSlider(
                            "Tamanho Pontinhos",
                            "${(editInnerDotsSize * 100).roundToInt()}%",
                            editInnerDotsSize,
                            0.1f..1.0f,
                            accentColor,
                            textColor
                        ) { editInnerDotsSize = it }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editInnerDotsUseSecondary = !editInnerDotsUseSecondary }
                                .padding(vertical = 3.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Usar 2ª cor nos pontinhos", fontSize = 9.sp, color = textColor.copy(alpha = 0.85f))
                            Text(
                                if (editInnerDotsUseSecondary) "Sim" else "Contraste",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }

                    // --- Múltiplos Formatos ---
                    BrushFeatureToggleRow(
                        title = "Múltiplos Formatos (Ponta Dupla)",
                        enabled = editDualShapeEnabled,
                        accentColor = accentColor,
                        textColor = textColor,
                        testTag = "brush_studio_dual_shape_toggle"
                    ) { editDualShapeEnabled = !editDualShapeEnabled }
                    if (editDualShapeEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("combine" to "Combinar", "mask" to "Máscara").forEach { (bKey, bLabel) ->
                                val active = editDualShapeBlendMode == bKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) accentColor.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.7f))
                                        .border(0.8.dp, if (active) accentColor else textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { editDualShapeBlendMode = bKey }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(bLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) accentColor else textColor)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("circle" to "Círculo", "rect" to "Quadrado", "gauss" to "Suave").forEach { (sKey, sLabel) ->
                                val active = editSecondaryShapeKind == sKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) accentColor.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.7f))
                                        .border(0.8.dp, if (active) accentColor else textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { editSecondaryShapeKind = sKey }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) accentColor else textColor)
                                }
                            }
                        }
                        BrushParamSlider(
                            "Escala 2º Formato",
                            "${(editDualShapeScale * 100).roundToInt()}%",
                            editDualShapeScale,
                            0.25f..1.8f,
                            accentColor,
                            textColor
                        ) { editDualShapeScale = it }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer: Resetar & Salvar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val restored = onResetBrushCustomization(editingPreset)
                            editingPreset = restored
                            editSize = restored.defaultSize
                            editOpacity = restored.defaultOpacity
                            editFlow = restored.dynamicParams.flow
                            editHardness = restored.dynamicParams.hardness
                            editSpacing = restored.dynamicParams.spacing
                            editGrain = restored.dynamicParams.grainAmount
                            editTaper = restored.dynamicParams.taperStrength
                            editBristles = restored.dynamicParams.bristleCount
                            editSizeScale = restored.dynamicParams.sizeScale
                            editDualColorEnabled = restored.dynamicParams.dualColorEnabled
                            editSecondaryColor = restored.dynamicParams.secondaryColor
                            editSecondaryColorMix = restored.dynamicParams.secondaryColorMix
                            editDualColorMode = restored.dynamicParams.dualColorMode
                            editInnerDotsEnabled = restored.dynamicParams.innerDotsEnabled
                            editInnerDotsDensity = restored.dynamicParams.innerDotsDensity
                            editInnerDotsSize = restored.dynamicParams.innerDotsSize
                            editInnerDotsUseSecondary = restored.dynamicParams.innerDotsUseSecondaryColor
                            editDualShapeEnabled = restored.dynamicParams.dualShapeEnabled
                            editSecondaryShapeKind = restored.dynamicParams.secondaryShapeKind
                            editDualShapeScale = restored.dynamicParams.dualShapeScale
                            editDualShapeBlendMode = restored.dynamicParams.dualShapeBlendMode
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("brush_studio_reset_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.7f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resetar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val savedParams = editingPreset.dynamicParams.copy(
                                hardness = editHardness,
                                spacing = editSpacing,
                                flow = editFlow,
                                grainAmount = editGrain,
                                bristleCount = editBristles,
                                taperStrength = editTaper,
                                sizeScale = editSizeScale,
                                dualColorEnabled = editDualColorEnabled,
                                secondaryColor = editSecondaryColor,
                                secondaryColorMix = editSecondaryColorMix,
                                dualColorMode = editDualColorMode,
                                innerDotsEnabled = editInnerDotsEnabled,
                                innerDotsDensity = editInnerDotsDensity,
                                innerDotsSize = editInnerDotsSize,
                                innerDotsUseSecondaryColor = editInnerDotsUseSecondary,
                                dualShapeEnabled = editDualShapeEnabled,
                                secondaryShapeKind = editSecondaryShapeKind,
                                dualShapeScale = editDualShapeScale,
                                dualShapeBlendMode = editDualShapeBlendMode
                            )
                            onSaveBrushCustomization(editingPreset, editSize, editOpacity, savedParams)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("brush_studio_save_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = onAccentColor),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTabPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accentColor else textColor.copy(alpha = 0.22f),
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accentColor else textColor.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrushParamSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    textColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 10.sp, color = textColor.copy(alpha = 0.85f))
            Text(text = valueText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.height(26.dp)
        )
    }
}

@Composable
private fun BrushFeatureToggleRow(
    title: String,
    enabled: Boolean,
    accentColor: Color,
    textColor: Color,
    testTag: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) accentColor.copy(alpha = 0.16f) else textColor.copy(alpha = 0.06f))
            .border(0.8.dp, if (enabled) accentColor.copy(alpha = 0.65f) else textColor.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (enabled) accentColor else textColor)
        Text(
            if (enabled) "ON" else "OFF",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) accentColor else textColor.copy(alpha = 0.5f)
        )
    }
}
