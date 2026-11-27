package com.easytier.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors

/**
 * Single-line glass text field.
 *
 * [BasicTextField] rather than Material's TextField so the decoration box is ours: a glass pane
 * whose rim animates from hairline to accent while focused.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    level: GlassLevel = LocalGlassLevel.current,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
) {
    val colors = glassColors()
    var focused by remember { mutableStateOf(false) }
    val rim by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.border,
        animationSpec = GlassMotion.quickEase(),
        label = "glassFieldRim"
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = GlassSpace.xs, bottom = GlassSpace.xs)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(level = level, radius = GlassRadius.md, intensity = 1.05f)
                .focusOutline(rim, GlassRadius.md)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .padding(PaddingValues(horizontal = GlassSpace.md, vertical = 12.dp)),
                enabled = enabled,
                singleLine = singleLine,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.textTertiary,
                                fontSize = 14.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/** 1dp rounded stroke drawn over the pane — the focus ring. */
private fun Modifier.focusOutline(color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        size = size,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
}
