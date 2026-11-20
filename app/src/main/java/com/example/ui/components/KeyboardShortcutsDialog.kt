package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.KeyboardShortcutBinding
import com.example.model.ShortcutAction
import com.example.model.ShortcutRegistry

@Composable
fun KeyboardShortcutsDialog(
    initialBindings: Map<ShortcutAction, KeyboardShortcutBinding>,
    onSave: (Map<ShortcutAction, KeyboardShortcutBinding>) -> Unit,
    onResetFactory: () -> Map<ShortcutAction, KeyboardShortcutBinding>,
    onDismiss: () -> Unit
) {
    var draftMap by remember(initialBindings) { mutableStateOf(initialBindings) }
    var editingAction by remember { mutableStateOf<ShortcutAction?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editingAction) {
        if (editingAction != null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val groupedActions = remember {
        ShortcutAction.entries.groupBy { it.categoryPt }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .heightIn(max = 620.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val target = editingAction ?: return@onPreviewKeyEvent false
                    val native = keyEvent.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val code = native.keyCode
                    if (code == KeyEvent.KEYCODE_CTRL_LEFT ||
                        code == KeyEvent.KEYCODE_CTRL_RIGHT ||
                        code == KeyEvent.KEYCODE_SHIFT_LEFT ||
                        code == KeyEvent.KEYCODE_SHIFT_RIGHT ||
                        code == KeyEvent.KEYCODE_ALT_LEFT ||
                        code == KeyEvent.KEYCODE_ALT_RIGHT
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    if (code == KeyEvent.KEYCODE_ESCAPE) {
                        editingAction = null
                        return@onPreviewKeyEvent true
                    }
                    val normalizedCode = if (code == KeyEvent.KEYCODE_NUMPAD_ENTER) KeyEvent.KEYCODE_ENTER else code
                    val updated = draftMap.toMutableMap()
                    updated[target] = KeyboardShortcutBinding(
                        action = target,
                        keyCode = normalizedCode,
                        ctrl = native.isCtrlPressed,
                        shift = native.isShiftPressed
                    )
                    draftMap = updated
                    editingAction = null
                    true
                }
                .testTag("keyboard_shortcuts_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.65f)),
            tonalElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Atalhos de Teclado",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Toque em uma tecla para alterar ou conecte um teclado",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive key remapper strip when an action is selected
                val activeEdit = editingAction
                if (activeEdit != null) {
                    val curBinding = draftMap[activeEdit] ?: KeyboardShortcutBinding(activeEdit)
                    Surface(
                        color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Editando: ${activeEdit.labelPt}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "Pressione uma tecla física ou toque abaixo:",
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChipPill(
                                    label = "Ctrl",
                                    selected = curBinding.ctrl,
                                    onClick = {
                                        val m = draftMap.toMutableMap()
                                        m[activeEdit] = curBinding.copy(ctrl = !curBinding.ctrl)
                                        draftMap = m
                                    }
                                )
                                FilterChipPill(
                                    label = "Shift",
                                    selected = curBinding.shift,
                                    onClick = {
                                        val m = draftMap.toMutableMap()
                                        m[activeEdit] = curBinding.copy(shift = !curBinding.shift)
                                        draftMap = m
                                    }
                                )
                                Text(
                                    text = "Atual: ${curBinding.formatDisplay()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ShortcutRegistry.selectableQuickKeys.forEach { (code, label) ->
                                    val isSel = curBinding.keyCode == code
                                    FilterChipPill(
                                        label = label,
                                        selected = isSel,
                                        onClick = {
                                            val m = draftMap.toMutableMap()
                                            m[activeEdit] = curBinding.copy(keyCode = code)
                                            draftMap = m
                                            editingAction = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Scrollable shortcuts list by category
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedActions.forEach { (category, actions) ->
                        item(key = "cat_$category") {
                            Text(
                                text = category.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                            )
                        }
                        items(actions, key = { it.name }) { action ->
                            val binding = draftMap[action] ?: KeyboardShortcutBinding(action)
                            val isEditing = editingAction == action
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        editingAction = if (isEditing) null else action
                                    }
                                    .border(
                                        1.dp,
                                        if (isEditing) colorScheme.primary else colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .testTag("shortcut_row_${action.name}"),
                                color = if (isEditing) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = action.labelPt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isEditing) colorScheme.primary else colorScheme.surface)
                                            .border(
                                                1.dp,
                                                if (isEditing) colorScheme.primary else colorScheme.outlineVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (isEditing) "Pressione..." else binding.formatDisplay(),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEditing) colorScheme.onPrimary else colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar: Redefinir para Padrão de Fábrica & Salvar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            editingAction = null
                            draftMap = onResetFactory()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("shortcuts_reset_factory_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Redefinir Padrão",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            onSave(draftMap)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("shortcuts_save_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Salvar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) colorScheme.primary else colorScheme.outlineVariant,
                RoundedCornerShape(8.dp)
            ),
        color = if (selected) colorScheme.primary else colorScheme.surface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
            )
        }
    }
}
