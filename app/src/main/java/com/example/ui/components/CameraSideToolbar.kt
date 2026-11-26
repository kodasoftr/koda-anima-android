package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CameraToolAction
import com.example.model.CameraViewportNavTarget
import com.example.model.KodanimaTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent

/**
 * Barra lateral dedicada ao Modo Câmera (2D Plano e Estúdio 3D Espacial).
 * Oferece manipulação direta por toque/mouse (Mover, Zoom, Girar, Lente FOV, Órbita 3D),
 * Desfazer/Refazer, acesso às 3 Rodas 3D (UV/Shader/Luz/Pintura 3D) e Editor de Nodes em tela dividida.
 */
@Composable
fun CameraSideToolbar(
    selectedAction: CameraToolAction,
    is3DMode: Boolean,
    isSideView3D: Boolean,
    showCaptureFrame: Boolean,
    theme: KodanimaTheme,
    onSelectAction: (CameraToolAction) -> Unit,
    onAddOrUpdateKeyframe: () -> Unit,
    onConnectTwoKeyframes: () -> Unit,
    onToggleCaptureFrame: () -> Unit,
    onToggleSideView3D: () -> Unit,
    onResetCurrentKeyframe: () -> Unit,
    onCloseCameraMode: () -> Unit,
    cameraViewportNavTarget: CameraViewportNavTarget = CameraViewportNavTarget.CAMERA,
    onToggleViewportNavTarget: () -> Unit = {},
    onFocusSelected: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    isThreeWheels3DOpen: Boolean = false,
    onToggleThreeWheels3D: () -> Unit = {},
    isSplitNodesOpen: Boolean = false,
    onToggleSplitNodes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)
    val borderColor = accentColor.copy(alpha = 0.45f)

    BoxWithConstraints(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isCompactHeight = maxHeight < 420.dp
        val btnSize = if (isCompactHeight) 32.dp else 36.dp
        val maxDockHeight = (maxHeight - 16.dp).coerceAtLeast(180.dp)

        Surface(
            color = panelColor.copy(alpha = 0.96f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.2.dp, borderColor),
            shadowElevation = 10.dp,
            modifier = Modifier
                .heightIn(max = maxDockHeight)
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .testTag("camera_side_toolbar")
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 3.dp else 4.dp)
            ) {
                Text(
                    text = if (isSideView3D) "EST. 3D" else if (is3DMode) "CÂM 3D" else "CÂM 2D",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (is3DMode) OrangeAccent else CyanAccent
                )

                CameraActionBtn(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Desfazer",
                    isSelected = false,
                    accent = accentColor,
                    textColor = if (canUndo) textColor else textColor.copy(alpha = 0.35f),
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_undo_btn",
                    onClick = { if (canUndo) onUndo() }
                )

                CameraActionBtn(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "Refazer",
                    isSelected = false,
                    accent = accentColor,
                    textColor = if (canRedo) textColor else textColor.copy(alpha = 0.35f),
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_redo_btn",
                    onClick = { if (canRedo) onRedo() }
                )

                if (!isSideView3D) {
                    val isCanvasNav = cameraViewportNavTarget == CameraViewportNavTarget.CANVAS
                    CameraActionBtn(
                        icon = if (isCanvasNav) Icons.Default.CropLandscape else Icons.Default.CenterFocusWeak,
                        label = if (isCanvasNav) "Canvas" else "Câmera",
                        isSelected = true,
                        accent = if (isCanvasNav) NeonGreen else OrangeAccent,
                        textColor = textColor,
                        bgColor = bgColor,
                        size = btnSize,
                        testTag = "cam_tool_nav_target",
                        onClick = onToggleViewportNavTarget
                    )
                } else {
                    CameraActionBtn(
                        icon = Icons.Default.GpsFixed,
                        label = "Focar",
                        isSelected = false,
                        accent = NeonGreen,
                        textColor = NeonGreen,
                        bgColor = bgColor,
                        size = btnSize,
                        testTag = "cam_tool_focus",
                        onClick = onFocusSelected
                    )
                }

                CameraActionBtn(
                    icon = Icons.Default.OpenWith,
                    label = "Mover",
                    isSelected = selectedAction == CameraToolAction.PAN_2D,
                    accent = CyanAccent,
                    textColor = textColor,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_pan",
                    onClick = { onSelectAction(CameraToolAction.PAN_2D) }
                )

                CameraActionBtn(
                    icon = Icons.Default.ZoomIn,
                    label = "Zoom",
                    isSelected = selectedAction == CameraToolAction.ZOOM,
                    accent = CyanAccent,
                    textColor = textColor,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_zoom",
                    onClick = { onSelectAction(CameraToolAction.ZOOM) }
                )

                CameraActionBtn(
                    icon = Icons.Default.RotateRight,
                    label = "Girar",
                    isSelected = selectedAction == CameraToolAction.ROTATE_Z,
                    accent = CyanAccent,
                    textColor = textColor,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_rotate",
                    onClick = { onSelectAction(CameraToolAction.ROTATE_Z) }
                )

                CameraActionBtn(
                    icon = Icons.Default.CenterFocusWeak,
                    label = "FOV",
                    isSelected = selectedAction == CameraToolAction.FOV_LENS,
                    accent = accentColor,
                    textColor = textColor,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_fov",
                    onClick = { onSelectAction(CameraToolAction.FOV_LENS) }
                )

                if (is3DMode) {
                    CameraActionBtn(
                        icon = Icons.Default.Explore,
                        label = "Órbita",
                        isSelected = selectedAction == CameraToolAction.ORBIT_3D,
                        accent = OrangeAccent,
                        textColor = textColor,
                        bgColor = bgColor,
                        size = btnSize,
                        testTag = "cam_tool_orbit",
                        onClick = { onSelectAction(CameraToolAction.ORBIT_3D) }
                    )
                }

                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(borderColor)
                )

                CameraActionBtn(
                    icon = Icons.Default.Add,
                    label = "+ ◆",
                    isSelected = false,
                    accent = OrangeAccent,
                    textColor = OrangeAccent,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_add_key",
                    onClick = onAddOrUpdateKeyframe
                )

                CameraActionBtn(
                    icon = Icons.Default.Link,
                    label = "Interp.",
                    isSelected = false,
                    accent = NeonGreen,
                    textColor = NeonGreen,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_connect_keys",
                    onClick = onConnectTwoKeyframes
                )

                CameraActionBtn(
                    icon = Icons.Default.CropFree,
                    label = "Mold.",
                    isSelected = showCaptureFrame,
                    accent = accentColor,
                    textColor = textColor,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_frame_overlay",
                    onClick = onToggleCaptureFrame
                )

                if (is3DMode) {
                    CameraActionBtn(
                        icon = Icons.Default.Layers,
                        label = "Palco 3D",
                        isSelected = isSideView3D,
                        accent = OrangeAccent,
                        textColor = textColor,
                        bgColor = bgColor,
                        size = btnSize,
                        testTag = "cam_tool_stage_3d",
                        onClick = onToggleSideView3D
                    )

                    // 3 Rodas do Estúdio 3D (UV Desdobrado, Shader/Contorno, Luz, Pintura 3D)
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isThreeWheels3DOpen) accentColor.copy(alpha = 0.28f) else bgColor.copy(alpha = 0.35f))
                            .border(
                                width = if (isThreeWheels3DOpen) 1.3.dp else 0.7.dp,
                                color = if (isThreeWheels3DOpen) accentColor else OrangeAccent.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { onToggleThreeWheels3D() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("cam_three_wheels_btn"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ThreeWheelsIcon(
                            tint = textColor,
                            accentColor = if (isThreeWheels3DOpen) accentColor else OrangeAccent,
                            size = 18.dp
                        )
                        Text(
                            text = "3 Rodas",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isThreeWheels3DOpen) accentColor else textColor.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }

                    CameraActionBtn(
                        icon = Icons.Default.AccountTree,
                        label = "Nodes",
                        isSelected = isSplitNodesOpen,
                        accent = CyanAccent,
                        textColor = textColor,
                        bgColor = bgColor,
                        size = btnSize,
                        testTag = "studio3d_split_nodes_btn",
                        onClick = onToggleSplitNodes
                    )
                }

                CameraActionBtn(
                    icon = Icons.Default.RestartAlt,
                    label = "Zerar",
                    isSelected = false,
                    accent = NeonPink,
                    textColor = NeonPink,
                    bgColor = bgColor,
                    size = btnSize,
                    testTag = "cam_tool_reset",
                    onClick = onResetCurrentKeyframe
                )

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    modifier = Modifier
                        .width(48.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onCloseCameraMode() }
                        .testTag("cam_tool_exit_draw"),
                    color = NeonGreen.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Voltar a Desenhar",
                            tint = NeonGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Desenhar",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraActionBtn(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accent: Color,
    textColor: Color,
    bgColor: Color,
    size: Dp,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) accent.copy(alpha = 0.24f) else bgColor.copy(alpha = 0.35f))
            .border(
                width = if (isSelected) 1.2.dp else 0.6.dp,
                color = if (isSelected) accent else textColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) accent else textColor.copy(alpha = 0.9f),
            modifier = Modifier.size((size * 0.48f).coerceIn(15.dp, 20.dp))
        )
        Text(
            text = label,
            fontSize = 7.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accent else textColor.copy(alpha = 0.78f),
            maxLines = 1
        )
    }
}
