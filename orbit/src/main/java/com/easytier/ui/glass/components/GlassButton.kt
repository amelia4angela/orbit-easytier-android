package com.easytier.ui.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassPaddings
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.GlassTypography
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.effects.rememberGlassPerformance
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode

/** Visual/semantic role of a [GlassButton]. */
enum class GlassButtonTone {
    /** Filled Apple-blue — one per screen at most. */
    PRIMARY,

    /** Glass pane with accent text — secondary actions. */
    SECONDARY,

    /** Destructive: translucent red fill, red label. */
    DANGER
}

/**
 * Glass button with spring press feedback.
 *
 * Press/release use two different springs ([GlassMotion.pressSpring] / `releaseSpring`) so the
 * button snaps in and settles gently — the same feel as the app's `elasticPress`, retuned.
 * Reduced-motion users get no scale transform at all.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: GlassButtonTone = GlassButtonTone.PRIMARY,
    enabled: Boolean = true,
    level: GlassLevel = LocalGlassLevel.current,
    leading: String? = null,
    /** Optional vector icon shown before the label (toolbar/dialog actions). */
    leadingIcon: ImageVector? = null,
    rounded: Dp = GlassRadius.md,
    minHeight: Dp = GlassSize.buttonHeight,
    contentPadding: PaddingValues = GlassPaddings.button
) {
    val colors = glassColors()
    val perf = rememberGlassPerformance(level)
    val shape = RoundedCornerShape(rounded)

    var pressed by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !perf.reducedMotion) 0.96f else 1f,
        animationSpec = if (pressed) GlassMotion.pressSpring else GlassMotion.releaseSpring,
        label = "glassButtonScale"
    )

    val disabledAlpha = 0.45f
    val alpha = if (enabled) 1f else disabledAlpha
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL

    val background: Brush = when (tone) {
        GlassButtonTone.PRIMARY -> colors.accentGradient
        // UNIVERSAL：次按钮 = 不透明浅底（深色 #1A1D23 系 / 浅色白），不再是半透明玻璃
        GlassButtonTone.SECONDARY ->
            if (universal) colors.universalBrush
            else Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom))
        GlassButtonTone.DANGER -> Brush.verticalGradient(
            listOf(colors.danger.copy(alpha = 0.92f), colors.danger.copy(alpha = 0.74f))
        )
    }

    val labelColor: Color = when (tone) {
        GlassButtonTone.PRIMARY -> colors.onAccent
        GlassButtonTone.SECONDARY -> colors.accent
        GlassButtonTone.DANGER -> Color.White
    }

    // 阴影：玻璃模式保持原来的「主按钮抬高」，UNIVERSAL 收到 2–4dp 的轻阴影。
    val shadowDp = when {
        !enabled -> 0.dp
        universal -> if (tone == GlassButtonTone.SECONDARY) 2.dp else 4.dp
        tone == GlassButtonTone.PRIMARY -> 10.dp
        else -> 0.dp
    }

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .defaultMinSize(minHeight = minHeight)
            .shadow(
                elevation = shadowDp,
                shape = shape,
                clip = false,
                ambientColor = if (universal) colors.universalShadow else colors.shadow,
                spotColor = if (universal) colors.universalShadow else colors.shadow
            )
            .clip(shape)
            .background(background, shape)
            .then(
                when {
                    // 玻璃主按钮本来就是纯 brand 色，不加边
                    tone == GlassButtonTone.PRIMARY -> Modifier
                    // UNIVERSAL 的次按钮：清晰 0.8dp 描边（玻璃模式用发丝 rim 渐变）
                    universal -> Modifier.border(0.8.dp, colors.universalBorder, shape)
                    else -> Modifier.border(GlassSize.hairline, colors.rimGradient, shape)
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassSpace.xs)
        ) {
            if (leading != null) {
                Text(leading, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            if (leadingIcon != null) {
                androidx.compose.material3.Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(GlassSize.iconSm)
                )
            }
            Text(
                text = text,
                color = labelColor,
                fontSize = GlassTypography.button.fontSize,
                fontWeight = GlassTypography.button.fontWeight
            )
        }
    }
}

/**
 * Compact glass icon button (top bar actions, dialog close).
 */
@Composable
fun GlassIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current,
    enabled: Boolean = true
) {
    val colors = glassColors()
    Box(
        modifier = modifier
            .size(38.dp)
            .glassPress(enabled = enabled, onClick = onClick)
            .glassSurface(
                level = level,
                radius = 19.dp,
                intensity = 1.1f,
                shape = RoundedCornerShape(percent = 50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = colors.textPrimary, fontSize = 15.sp)
    }
}
