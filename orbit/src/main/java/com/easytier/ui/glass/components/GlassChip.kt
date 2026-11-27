package com.easytier.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassPaddings
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors

/**
 * Pill-shaped selectable tag. Selected = accent fill; unselected = glass pane.
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current,
    enabled: Boolean = true
) {
    val colors = glassColors()
    val shape = RoundedCornerShape(percent = 50)
    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.fillTop,
        animationSpec = GlassMotion.quickEase(),
        label = "glassChipFill"
    )
    val label by animateColorAsState(
        targetValue = if (selected) colors.onAccent else colors.textSecondary,
        animationSpec = GlassMotion.quickEase(),
        label = "glassChipLabel"
    )

    Box(
        modifier = modifier
            .height(34.dp)
            .glassPress(enabled = enabled, onClick = onClick)
            .clip(shape)
            .background(fill, shape)
            .border(GlassSize.hairline, colors.rimGradient, shape)
            .padding(GlassPaddings.chip),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * Two-to-four segment selector on a glass track.
 *
 * The active segment is a single background pill that slides between slots with the indicator
 * spring, so switching never flickers two fills at once.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current
) {
    val colors = glassColors()
    val trackShape = RoundedCornerShape(14.dp)
    val indicatorShape = RoundedCornerShape(11.dp)
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(trackShape)
            .background(colors.fillTop, trackShape)
            .border(GlassSize.hairline, colors.rimGradient, trackShape)
            .padding(3.dp)
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val segWidth = maxWidth / options.size
            val segWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { segWidth.toPx() }
            val slot by androidx.compose.animation.core.animateFloatAsState(
                targetValue = safeIndex.toFloat(),
                animationSpec = GlassMotion.settleSpring(),
                label = "glassSegmentSlot"
            )

            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((segWidthPx * slot).toInt(), 0) }
                    .width(segWidth)
                    .fillMaxHeight()
                    .clip(indicatorShape)
                    .background(colors.accent, indicatorShape)
            )

            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { index, option ->
                    val active = index == safeIndex
                    val label by animateColorAsState(
                        targetValue = if (active) colors.onAccent else colors.textSecondary,
                        animationSpec = GlassMotion.quickEase(),
                        label = "glassSegmentLabel$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .glassPress(onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            color = label,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
