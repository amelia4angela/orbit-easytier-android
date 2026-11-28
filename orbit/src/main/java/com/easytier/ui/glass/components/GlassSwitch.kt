package com.easytier.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode

/**
 * Glass toggle.
 *
 * 46×28 track, 22dp thumb, spring-slid. Track tint animates between the rim colour (off) and the
 * accent (on); reduced-motion users get an instant jump through the same spring-swap helper the
 * rest of the layer uses.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: GlassLevel = LocalGlassLevel.current
) {
    val colors = glassColors()
    val trackShape = RoundedCornerShape(percent = 50)
    // UNIVERSAL：关闭态轨道用不透明中性底 + 0.8dp 描边（玻璃模式下白系填充在白卡上看不见）。
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent
        else if (universal) colors.universalFill
        else colors.fillTop,
        animationSpec = GlassMotion.quickEase(),
        label = "glassSwitchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) GlassSize.switchWidth - GlassSize.switchThumb - 3.dp else 3.dp,
        animationSpec = GlassMotion.settleSpring(),
        label = "glassSwitchThumb"
    )

    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(width = GlassSize.switchWidth, height = GlassSize.switchHeight)
            .clip(trackShape)
            .background(trackColor, trackShape)
            .border(
                width = if (universal) 0.8.dp else GlassSize.hairline,
                brush = if (universal) SolidColor(colors.universalBorder) else colors.rimGradient,
                shape = trackShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(with(density) { thumbOffset.roundToPx() }, 0)
                }
                .size(GlassSize.switchThumb)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow
                )
                .background(if (checked) androidx.compose.ui.graphics.Color.White else colors.textTertiary, CircleShape)
        )
    }
}
