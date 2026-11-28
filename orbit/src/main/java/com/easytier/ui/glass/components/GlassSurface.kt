package com.easytier.ui.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.effects.GlassBackdropSlice
import com.easytier.ui.glass.effects.GlassShaderSpec
import com.easytier.ui.glass.effects.LocalGlassRefraction
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.effects.rememberGlassPerformance
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape

/**
 * Spring press feedback for glass controls.
 *
 * Same interaction-source pattern as the app's `elasticPress`, but the spring constants come
 * from [GlassMotion] (stiffer press, gentler release) and reduced-motion collapses the transform
 * to a no-op instead of shortening it — a scaling button is exactly the thing users disable.
 */
@Composable
fun Modifier.glassPress(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    if (!enabled) return this
    var pressed by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val perf = rememberGlassPerformance()

    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    val target = if (pressed && !perf.reducedMotion) 0.955f else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (pressed) GlassMotion.pressSpring else GlassMotion.releaseSpring,
        label = "glassPressScale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Core glass pane.
 *
 * Layers, bottom → top:
 *  1. blurred copy of the [com.easytier.ui.glass.effects.GlassSky] backdrop (HIGH/BALANCED only)
 *  2. translucent fill + sheen + rim, via [Modifier.glassSurface]
 *  3. caller content
 *
 * Without a [com.easytier.ui.glass.effects.GlassSky] ancestor (or on API < 31) it degrades to
 * the LITE look automatically — the level is mapped through [rememberGlassPerformance].
 *
 * @param blurRadius override for the backdrop blur; defaults to the level's value
 * @param intensity 0..1 dial for fill alpha / rim strength
 * @param refraction AGSL refraction tunables (HIGH + API 33+ only, see GlassRefraction.kt)
 * @param contentPadding padding applied between the rim and [content]
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = LocalGlassLevel.current,
    radius: Dp = GlassRadius.lg,
    shape: Shape = glassShape(radius),
    intensity: Float = 1f,
    blurRadius: Dp? = null,
    shadowElevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(GlassSpace.lg),
    refraction: GlassShaderSpec = LocalGlassRefraction.current,
    content: @Composable BoxScope.() -> Unit
) {
    val blurDp = blurRadius ?: level.backdropBlur.dp

    Box(modifier = modifier) {
        GlassBackdropSlice(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            blurRadius = blurDp,
            level = level,
            radius = radius,
            refraction = refraction
        )
        Box(
            Modifier
                .matchParentSize()
                .glassSurface(
                    level = level,
                    radius = radius,
                    intensity = intensity,
                    shape = shape,
                    shadowElevation = shadowElevation,
                    refraction = refraction
                )
        )
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Opaque-ish "stable" material for plain content cards.
 *
 * The design rule for this layer: only navigation, floating controls, buttons and dialogs get
 * real (blurred) glass; ordinary content stays legible, so it uses a higher-alpha fill and a
 * hairline rim with no blur at all.
 */
@Composable
fun Modifier.glassStable(
    radius: Dp = GlassRadius.lg,
    shape: Shape = glassShape(radius),
    shadowElevation: Dp = 0.dp
): Modifier {
    val colors = glassColors()
    var result = this
    if (shadowElevation > 0.dp) {
        result = result.shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow
        )
    }
    return result
        .clip(shape)
        .background(colors.stableFill, shape)
        .background(colors.sheenGradient, shape)
        .border(GlassSize.hairline, colors.border, shape)
}

/** Capsule variant — nav bars, chips, pills. */
fun capsuleShape(): RoundedCornerShape = RoundedCornerShape(percent = 50)

/**
 * Content-card material: the same high-alpha [GlassColors.stableFill] that [glassStable] uses,
 * topped by a low-intensity [glassSurface] sheen + rim.
 *
 * This is the one modifier for "glass on ordinary content" — settings rows, form group cards,
 * log panels, list cards, about/privacy blocks. Intensity stays low on purpose: full-strength
 * glass on every card of a scrolling screen is what drops frames, so navigation / dialogs /
 * floating controls keep intensity >= 0.9 while content cards stay in the 0.15–0.25 band.
 */
@Composable
fun Modifier.glassCard(
    radius: Dp = GlassRadius.lg,
    shape: Shape = glassShape(radius),
    intensity: Float = 0.55f,
    shadowElevation: Dp = 0.dp
): Modifier {
    val colors = glassColors()
    var result = this
    if (shadowElevation > 0.dp) {
        result = result.shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow
        )
    }
    // One fill + one rim: the stable opaque-ish fill keeps text readable, and
    // glassSurface paints the sheen/rim on top so a card never gets a double outline.
    return result
        .clip(shape)
        .background(colors.stableFill, shape)
        .glassSurface(radius = radius, shape = shape, intensity = intensity)
}
