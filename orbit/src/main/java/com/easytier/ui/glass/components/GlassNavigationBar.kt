package com.easytier.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.T
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassPaddings
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
import kotlin.math.roundToInt

/** One tab in a [GlassNavigationBar]. */
data class GlassNavItem(
    val icon: ImageVector,
    val labelZh: String,
    val labelEn: String
)

/**
 * Floating capsule nav bar with real glass.
 *
 * The interaction logic is the one the app's existing `BottomNavigationBar` already uses — a
 * single animated *slot* float drives the indicator offset, and the active label stays dim until
 * the pill has travelled 180ms so it never flashes white-on-white mid-slide. Only the visuals
 * changed: blurred capsule, glass rim, spring instead of tween.
 */
@Composable
fun GlassNavigationBar(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    langZh: Boolean,
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current
) {
    val colors = glassColors()
    val shape = RoundedCornerShape(24.dp)
    val indicatorShape = RoundedCornerShape(16.dp)
    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    // UNIVERSAL：悬浮胶囊 = 实色/极浅渐变 + 中层阴影；选中 = 淡蓝胶囊底 + 蓝色图标文字。
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL
    val indicatorColor = if (universal) colors.accentSoft else colors.accent
    val activeTint = if (universal) colors.accent else Color.White

    val slot by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = GlassMotion.settleSpring(),
        label = "glassNavSlot"
    )

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = level,
        radius = 24.dp,
        shape = shape,
        shadowElevation = 12.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(GlassSize.navIndicatorInset)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassSize.navBarHeight - GlassSize.navIndicatorInset * 2)
        ) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }

            Box(
                modifier = Modifier
                    .offset { IntOffset((itemWidthPx * slot).roundToInt(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(indicatorShape)
                    .background(indicatorColor)
            )

            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                items.forEachIndexed { index, item ->
                    val active = index == safeIndex
                    var lit by remember { mutableStateOf(active) }
                    LaunchedEffect(active) {
                        if (active) {
                            kotlinx.coroutines.delay(180L)
                            lit = true
                        } else {
                            lit = false
                        }
                    }
                    val tint by animateColorAsState(
                        targetValue = if (lit) activeTint else colors.textTertiary,
                        animationSpec = GlassMotion.quickEase(),
                        label = "glassNavTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        animationSpec = GlassMotion.spatial,
                        label = "glassNavIconScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(index) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = T(item.labelZh, item.labelEn, langZh),
                            tint = tint,
                            modifier = Modifier.size(GlassSize.iconMd + 2.dp)
                        )
                        Text(
                            text = T(item.labelZh, item.labelEn, langZh),
                            color = tint,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Convenience padding for the bar when it floats over content. */
val GlassNavBarPadding = GlassPaddings.surface
