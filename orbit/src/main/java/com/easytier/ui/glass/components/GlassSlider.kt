package com.easytier.ui.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors

/**
 * Minimal glass slider — custom track so the fill/thumbs match the glass palette.
 *
 * Dragging and tapping both update the value; the thumb is a small glass puck with an accent
 * core. Width is captured from layout so the gesture math stays in pixels.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    level: GlassLevel = LocalGlassLevel.current,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val colors = glassColors()
    var widthPx by remember { mutableStateOf(0f) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        label = "glassSliderFraction",
        animationSpec = com.easytier.ui.glass.GlassMotion.quick
    )

    fun emit(x: Float) {
        if (!enabled || widthPx <= 0f) return
        val f = (x / widthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + f * span)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                // One gesture loop for both tap and drag: press picks a value immediately,
                // then every move keeps tracking until the finger lifts.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    emit(down.position.x)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        emit(change.position.x)
                        change.consume()
                    }
                    onValueChangeFinished?.invoke()
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val trackH = GlassSize.sliderTrack.toPx()
            val cy = size.height / 2f
            val r = CornerRadius(trackH / 2f, trackH / 2f)

            // Inactive track
            drawRoundRect(
                color = colors.fillTop,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = r
            )
            drawRoundRect(
                color = colors.border,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = r,
                style = Stroke(1f)
            )

            // Active fill
            val filled = size.width * animatedFraction
            if (filled > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(colors.accent, colors.accentDeep)),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(filled, trackH),
                    cornerRadius = r
                )
            }

            // Thumb: rim + white puck + accent core
            val thumbR = GlassSize.sliderThumb.toPx() / 2f
            val cx = (filled).coerceIn(thumbR, size.width - thumbR)
            drawCircle(color = colors.shadow, radius = thumbR + 3f, center = Offset(cx, cy + 1.5f))
            drawCircle(color = Color.White, radius = thumbR, center = Offset(cx, cy))
            drawCircle(color = colors.accent, radius = thumbR * 0.42f, center = Offset(cx, cy))
        }
    }
}
