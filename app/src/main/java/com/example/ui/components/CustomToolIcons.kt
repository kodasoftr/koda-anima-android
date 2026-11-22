package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Custom Canvas-drawn two-tone angled block eraser icon so the Eraser tool
 * is immediately recognizable (replacing the generic 'X' Clear icon).
 */
@Composable
fun EraserVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = (w * 0.085f).coerceAtLeast(1.4f)

        rotate(degrees = -34f, pivot = Offset(w * 0.48f, h * 0.48f)) {
            val left = w * 0.16f
            val top = h * 0.24f
            val rectW = w * 0.66f
            val rectH = h * 0.44f
            val divX = left + rectW * 0.45f

            // Rubber tip fill (tinted lightly)
            drawRoundRect(
                color = tint.copy(alpha = 0.28f),
                topLeft = Offset(left, top),
                size = Size(divX - left + 1f, rectH),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
            )
            // Outer eraser block outline
            drawRoundRect(
                color = tint,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                style = Stroke(width = strokeW, join = StrokeJoin.Round)
            )
            // Dividing sleeve line
            drawLine(
                color = tint,
                start = Offset(divX, top),
                end = Offset(divX, top + rectH),
                strokeWidth = strokeW
            )
        }
        // Baseline rub mark at bottom
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.84f),
            end = Offset(w * 0.84f, h * 0.84f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Custom Canvas-drawn dashed Lasso loop + rope tail icon
 * so the Lasso Selection tool is instantly recognizable across PC and Mobile toolbars.
 */
@Composable
fun LassoVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = (w * 0.09f).coerceAtLeast(1.5f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.10f), 0f)

        val loopPath = Path().apply {
            moveTo(w * 0.20f, h * 0.42f)
            cubicTo(w * 0.16f, h * 0.14f, w * 0.78f, h * 0.10f, w * 0.84f, h * 0.36f)
            cubicTo(w * 0.90f, h * 0.62f, w * 0.56f, h * 0.72f, w * 0.36f, h * 0.66f)
            cubicTo(w * 0.24f, h * 0.63f, w * 0.22f, h * 0.52f, w * 0.20f, h * 0.42f)
            close()
        }
        drawPath(
            path = loopPath,
            color = tint,
            style = Stroke(
                width = strokeW,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dash
            )
        )
        // Lasso knot dot + trailing rope handle
        val knot = Offset(w * 0.48f, h * 0.68f)
        drawCircle(color = tint, radius = strokeW * 1.15f, center = knot)
        val tailPath = Path().apply {
            moveTo(knot.x, knot.y)
            quadraticTo(w * 0.60f, h * 0.84f, w * 0.74f, h * 0.88f)
        }
        drawPath(
            path = tailPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

/**
 * Clickable numeric "px" badge that smoothly turns into an editable numeric field
 * so users can type exact brush/eraser thickness directly next to any slider.
 */
@Composable
fun InlineNumericPxInput(
    valuePx: Float,
    minPx: Float = 1f,
    maxPx: Float = 120f,
    accentColor: Color,
    textColor: Color,
    panelBgColor: Color,
    testTag: String = "inline_numeric_px_input",
    onCommitValue: (Float) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var textValue by remember(valuePx) { mutableStateOf(valuePx.roundToInt().toString()) }

    LaunchedEffect(valuePx) {
        if (!editing) {
            textValue = valuePx.roundToInt().toString()
        }
    }

    fun applyParsed() {
        val parsed = textValue.trim().replace(",", ".").toFloatOrNull()
        if (parsed != null) {
            onCommitValue(parsed.coerceIn(minPx, maxPx))
        }
        editing = false
    }

    if (editing) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(panelBgColor)
                .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("${testTag}_editing"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { raw ->
                    if (raw.length <= 4 && raw.all { it.isDigit() }) {
                        textValue = raw
                        raw.toFloatOrNull()?.let { n ->
                            onCommitValue(n.coerceIn(minPx, maxPx))
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { applyParsed() }),
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = "OK",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clickable { applyParsed() }
                    .padding(horizontal = 2.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor.copy(alpha = 0.14f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .clickable {
                    textValue = valuePx.roundToInt().toString()
                    editing = true
                }
                .padding(horizontal = 7.dp, vertical = 3.dp)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${valuePx.roundToInt()}px",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
