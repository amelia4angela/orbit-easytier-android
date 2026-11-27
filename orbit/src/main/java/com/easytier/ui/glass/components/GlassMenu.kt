package com.easytier.ui.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.easytier.ui.HarmonyEasing
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors

/**
 * Anchored dropdown on a glass panel.
 *
 * Same behaviour as the app's `HmDropdownField` (tap the row, pick an item, outside-tap to
 * dismiss), with an anchor-relative [Popup] so the panel opens *under the field* instead of
 * inside the layout tree — the list can then overflow the card without clipping.
 */
@Composable
fun GlassMenu(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current,
    langZh: Boolean = true
) {
    val colors = glassColors()
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val selected = options.getOrNull(selectedIndex)
    val anchorWidth = with(density) { anchorWidthPx.toDp() }

    Column(modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { anchorWidthPx = it.width }
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = level,
                radius = GlassRadius.md,
                contentPadding = PaddingValues(horizontal = GlassSpace.md, vertical = GlassSpace.md)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPress { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = selected ?: "—",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (expanded) "▴" else "▾",
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = GlassSpace.xs)
                    )
                }
            }
        }

        if (expanded) {
            val positioner = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val gap = 6
                        val maxY = (windowSize.height - popupContentSize.height - gap)
                            .coerceAtLeast(gap)
                        return IntOffset(
                            x = anchorBounds.left,
                            y = (anchorBounds.bottom + gap).coerceAtMost(maxY)
                        )
                    }
                }
            }
            Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(160)) + scaleIn(
                        initialScale = 0.95f,
                        transformOrigin = TransformOrigin(0f, 0f),
                        animationSpec = tween(200, easing = HarmonyEasing)
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.97f)
                ) {
                    GlassSurface(
                        modifier = Modifier.width(anchorWidth),
                        level = level,
                        radius = GlassRadius.md,
                        shadowElevation = 18.dp,
                        contentPadding = PaddingValues(vertical = GlassSpace.xs)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            options.forEachIndexed { index, option ->
                                val active = index == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(index)
                                            expanded = false
                                        }
                                        .padding(horizontal = GlassSpace.md, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option,
                                        color = if (active) colors.accent else colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (active) {
                                        Text("✓", color = colors.accent, fontSize = 13.sp)
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
