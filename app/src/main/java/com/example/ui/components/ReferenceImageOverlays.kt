package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.KodanimaGraphicsEngine
import com.example.model.KodanimaTheme
import com.example.model.LayerImageAttachment
import com.example.model.ReferenceImageState
import com.example.ui.theme.NeonPink
import kotlin.math.roundToInt

@Composable
private fun rememberDecodedImageBitmap(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(uriString) {
        if (uriString.isBlank()) return@remember null
        val engineBmp = KodanimaGraphicsEngine.resolveLayerImageBitmap(uriString)
        if (engineBmp != null && !engineBmp.isRecycled) {
            return@remember engineBmp.asImageBitmap()
        }
        try {
            val raw: Bitmap? = when {
                uriString.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                uriString.startsWith("file://") -> {
                    BitmapFactory.decodeFile(uriString.removePrefix("file://"))
                }
                uriString.startsWith("/") -> BitmapFactory.decodeFile(uriString)
                else -> null
            }
            raw?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Bandeja/modal exibida logo ao escolher uma imagem da galeria (Mobile e PC),
 * permitindo decidir entre inserir como camada normal da Timeline ou janela flutuante.
 */
@Composable
fun ReferenceImageChoiceDialog(
    pendingUri: String?,
    theme: KodanimaTheme,
    onChooseTimelineLayer: (String) -> Unit,
    onChooseFloatingWindow: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (pendingUri.isNullOrBlank()) return

    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val secondaryAccent = Color(theme.secondaryAccent)
    val textColor = Color(theme.text)
    val previewBmp = rememberDecodedImageBitmap(pendingUri)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .border(1.3.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .testTag("reference_image_choice_dialog"),
            color = panelColor,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Como usar esta imagem?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar escolha de imagem",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (previewBmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor.copy(alpha = 0.7f))
                            .border(1.dp, textColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBmp,
                            contentDescription = "Prévia da imagem selecionada",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }

                // Option 1: Camada normal da Timeline
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChooseTimelineLayer(pendingUri) }
                        .testTag("choose_timeline_image_layer"),
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Camada normal da Timeline",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Text(
                                text = "Cria uma camada para toda a animação. Pode rotacionar/escalonar e sai no vídeo final.",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.78f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Option 2: Janela flutuante
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChooseFloatingWindow(pendingUri) }
                        .testTag("choose_floating_reference_window"),
                    color = secondaryAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, secondaryAccent.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(secondaryAccent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = null,
                                tint = secondaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Janela flutuante (Referência)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryAccent
                            )
                            Text(
                                text = "Fica em todos os quadros (acima ou atrás do canvas), com opacidade e trava. Nunca sai na exportação.",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.78f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_reference_image_choice")
                    ) {
                        Text("Cancelar", color = textColor.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

/**
 * Superfície visual da janela flutuante de referência.
 * Responde a gestos de arrastar/pinça/rotação apenas sobre si mesma quando destravada e acima do canvas.
 * Nunca entra nos bitmaps exportados pelo KodanimaGraphicsEngine.
 */
@Composable
fun FloatingReferenceImageSurface(
    refState: ReferenceImageState,
    theme: KodanimaTheme,
    onTransformDelta: (panX: Float, panY: Float, zoomFactor: Float, rotateDelta: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val refUri = refState.uriString
    if (!refState.isVisible || refUri.isNullOrBlank()) return
    val imageBitmap = rememberDecodedImageBitmap(refUri) ?: return
    val accentColor = Color(theme.accent)
    val canDirectGesture = !refState.isLocked && refState.renderAboveCanvas

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val interactiveModifier = if (canDirectGesture) {
            Modifier.pointerInput(refState.isLocked, refState.renderAboveCanvas) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onTransformDelta(pan.x, pan.y, zoom, rotation)
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .graphicsLayer(
                    translationX = refState.offsetX,
                    translationY = refState.offsetY,
                    scaleX = refState.scale.coerceIn(0.2f, 5.0f),
                    scaleY = refState.scale.coerceIn(0.2f, 5.0f),
                    rotationZ = refState.rotationDegrees,
                    alpha = refState.opacity.coerceIn(0.08f, 1.0f)
                )
                .sizeIn(minWidth = 80.dp, minHeight = 80.dp, maxWidth = 250.dp, maxHeight = 250.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .border(
                    width = if (refState.isLocked) 1.dp else 1.6.dp,
                    color = if (refState.isLocked) Color.Gray.copy(alpha = 0.7f) else accentColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .then(interactiveModifier)
                .testTag("floating_reference_window"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Janela flutuante de referência",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(3.dp)
            )
        }
    }
}

/**
 * Barra compacta de controle da Janela Flutuante de Referência.
 * Permanece acessível mesmo quando a janela está travada ou atrás do canvas.
 */
@Composable
fun FloatingReferenceControlsBar(
    refState: ReferenceImageState,
    theme: KodanimaTheme,
    onToggleLock: () -> Unit,
    onToggleZOrder: () -> Unit,
    onChangeOpacity: (Float) -> Unit,
    onZoomStep: (Float) -> Unit,
    onRotateStep: (Float) -> Unit,
    onResetTransform: () -> Unit,
    onDeleteWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!refState.isVisible || refState.uriString.isNullOrBlank()) return

    val panelColor = Color(theme.panel)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)
    var showOpacitySlider by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .testTag("floating_reference_controls_bar"),
            color = panelColor.copy(alpha = 0.94f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                Text(
                    text = "Ref",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                // Lock / Unlock
                RefPillButton(
                    icon = if (refState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    label = if (refState.isLocked) "Travada" else "Livre",
                    isActive = refState.isLocked,
                    accentColor = NeonPink,
                    textColor = textColor,
                    testTag = "floating_ref_lock_btn",
                    onClick = onToggleLock
                )

                // Above / Below canvas
                RefPillButton(
                    icon = if (refState.renderAboveCanvas) Icons.Default.FlipToFront else Icons.Default.FlipToBack,
                    label = if (refState.renderAboveCanvas) "Acima" else "Atrás",
                    isActive = !refState.renderAboveCanvas,
                    accentColor = accentColor,
                    textColor = textColor,
                    testTag = "floating_ref_zorder_btn",
                    onClick = onToggleZOrder
                )

                // Opacity toggle pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (showOpacitySlider) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.1f))
                        .clickable { showOpacitySlider = !showOpacitySlider }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("floating_ref_opacity_toggle")
                ) {
                    Text(
                        text = "Op ${(refState.opacity * 100).roundToInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showOpacitySlider) accentColor else textColor
                    )
                }

                if (!refState.isLocked) {
                    IconButton(onClick = { onZoomStep(-0.15f) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Diminuir referência", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onZoomStep(0.15f) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Ampliar referência", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onRotateStep(-15f) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Girar referência -15°", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onRotateStep(15f) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Girar referência +15°", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onResetTransform, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Resetar janela", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                }

                IconButton(
                    onClick = onDeleteWindow,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("floating_ref_delete_btn")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir janela flutuante", tint = NeonPink, modifier = Modifier.size(15.dp))
                }
            }
        }

        if (showOpacitySlider) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 210.dp, max = 270.dp)
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                color = panelColor.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Opacidade", fontSize = 10.sp, color = textColor)
                    Slider(
                        value = refState.opacity.coerceIn(0.08f, 1.0f),
                        onValueChange = onChangeOpacity,
                        valueRange = 0.08f..1.0f,
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .testTag("floating_ref_opacity_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor
                        )
                    )
                    Text(
                        "${(refState.opacity * 100).roundToInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun RefPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    textColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.1f))
            .border(
                0.8.dp,
                if (isActive) accentColor.copy(alpha = 0.7f) else Color.Transparent,
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (isActive) accentColor else textColor, modifier = Modifier.size(12.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) accentColor else textColor
        )
    }
}

/**
 * HUD compacto de transformação quando a camada ativa da Timeline é uma Camada de Imagem.
 */
@Composable
fun TimelineLayerImageTransformHud(
    layerName: String,
    attachment: LayerImageAttachment,
    theme: KodanimaTheme,
    onNudge: (dx: Float, dy: Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelColor = Color(theme.panel)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(min = 210.dp, max = 300.dp)
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("timeline_layer_image_hud"),
        color = panelColor.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Text(
                        text = layerName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (expanded) "Ocultar" else "Escalar/Girar",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    IconButton(onClick = onReset, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Resetar camada de imagem", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNudge(-0.04f, 0f) }, modifier = Modifier.size(36.dp)) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mover esquerda", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onNudge(0f, -0.04f) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mover cima", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onNudge(0f, 0.04f) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mover baixo", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onNudge(0.04f, 0f) }, modifier = Modifier.size(36.dp)) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mover direita", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }

                val safeScale = attachment.normalizedScaleFactor()
                LayerTransformSliderRow(
                    label = "Escala",
                    valueText = "${(safeScale * 100).roundToInt()}%",
                    value = safeScale,
                    range = 0.1f..3.0f,
                    accentColor = accentColor,
                    textColor = textColor,
                    onValueChange = onScaleChange
                )
                LayerTransformSliderRow(
                    label = "Rotação",
                    valueText = "${attachment.rotationDegrees.roundToInt()}°",
                    value = attachment.rotationDegrees,
                    range = -180f..180f,
                    accentColor = accentColor,
                    textColor = textColor,
                    onValueChange = onRotationChange
                )
                LayerTransformSliderRow(
                    label = "Opac.",
                    valueText = "${(attachment.opacity * 100).roundToInt()}%",
                    value = attachment.opacity,
                    range = 0.05f..1.0f,
                    accentColor = accentColor,
                    textColor = textColor,
                    onValueChange = onOpacityChange
                )
            }
        }
    }
}

@Composable
private fun LayerTransformSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    textColor: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 9.sp, color = textColor.copy(alpha = 0.8f), modifier = Modifier.width(42.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
        Text(
            valueText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(34.dp)
        )
    }
}
