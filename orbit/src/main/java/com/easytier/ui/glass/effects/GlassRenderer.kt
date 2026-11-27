package com.easytier.ui.glass.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.easytier.ui.glass.GlassColors
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur

/*
 * Glass rendering.
 *
 * ── Why there is no skydoves/Cloudy here ──────────────────────────────────────────────
 * The project is pinned to Kotlin 1.9.22 / Compose BOM 2024.02.00 / AGP 8.2.2 / compileSdk 34.
 * Every published `cloudy` release (0.1.x … 1.0.0-alpha01) is built with a Kotlin 2.x toolchain;
 * 1.0.0-alpha01 additionally pulls Compose 1.11 (needs compileSdk 35 + AGP 8.6) and fails
 * `checkDebugAarMetadata`. See the note in orbit/build.gradle.kts.
 *
 * The same three-tier contract is implemented with Compose-native primitives instead:
 *   HIGH      backdrop blur (radius 26) + specular sheen + rim gradient
 *   BALANCED  backdrop blur (radius 14) + rim gradient
 *   LITE      no blur — translucent gradient fill + hairline rim + soft shadow
 *
 * Backdrop sampling works like Haze: a [GlassSky] container draws the pattern once and publishes
 * its root position/size; every glass pane re-draws the *same* pattern translated by its own
 * offset, blurs it (`Modifier.blur` → RenderEffect on API 31+), clips it to its shape and lays
 * the translucent pane + rim on top. When the toolchain is upgraded the blur call is the only
 * line that needs to become `Modifier.cloudy(sky = …, radius = …)`.
 */

/** Draws the demo/generated backdrop the glass pans sample. Coordinates are in *canvas* space. */
fun DrawScope.drawGlassBackdrop(colors: GlassColors, canvasSize: Size) {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
    val w = canvasSize.width
    val h = canvasSize.height

    // Base vertical wash.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(colors.backdropTop, colors.backdropBottom),
            startY = 0f,
            endY = h
        ),
        size = canvasSize
    )

    // Three off-centre colour blobs — cheap, and they make blur radius differences obvious.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.backdropBlobA, Color.Transparent),
            center = Offset(w * 0.18f, h * 0.14f),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.18f, h * 0.14f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.backdropBlobB, Color.Transparent),
            center = Offset(w * 0.86f, h * 0.30f),
            radius = w * 0.48f
        ),
        radius = w * 0.48f,
        center = Offset(w * 0.86f, h * 0.30f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.backdropBlobC, Color.Transparent),
            center = Offset(w * 0.35f, h * 0.86f),
            radius = w * 0.52f
        ),
        radius = w * 0.52f,
        center = Offset(w * 0.35f, h * 0.86f)
    )

    // Diagonal hairlines: fine detail is the clearest proof that a pane is really blurred.
    val step = 26f
    var x = -h
    while (x < w) {
        drawLine(
            color = colors.backdropStripe,
            start = Offset(x, h),
            end = Offset(x + h, 0f),
            strokeWidth = 1.2f
        )
        x += step
    }
}

/**
 * Backdrop source shared with every descendant glass pane.
 *
 * Positions are read lazily so the pane's `onGloballyPositioned` sees the latest values without
 * the sky itself recomposing.
 */
@Immutable
class GlassSky internal constructor(
    internal val originProvider: () -> Offset,
    internal val sizeProvider: () -> Size
)

val LocalGlassSky = staticCompositionLocalOf<GlassSky?> { null }

/**
 * Container that paints the backdrop and exposes it to descendants via [LocalGlassSky].
 *
 * Drop this around a screen (or a Showcase section) so floating glass has something to sample.
 * Without it, glass still renders — just without backdrop sampling (fill + rim only).
 */
@Composable
fun GlassSky(
    modifier: Modifier = Modifier,
    colors: GlassColors = glassColors(),
    backdrop: DrawScope.(Size) -> Unit = { size -> drawGlassBackdrop(colors, size) },
    content: @Composable BoxScope.() -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(Size.Zero) }
    val sky = remember { GlassSky(originProvider = { origin }, sizeProvider = { sizePx }) }

    androidx.compose.runtime.CompositionLocalProvider(LocalGlassSky provides sky) {
        Box(
            modifier = modifier
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    sizePx = it.size.toSize()
                }
        ) {
            Canvas(Modifier.matchParentSize()) { backdrop(size) }
            content()
        }
    }
}

/** Alpha-scaled copy used by the level-dependent fills. */
private fun Color.scaleAlphaBy(factor: Float): Color =
    copy(alpha = (alpha * factor).coerceIn(0f, 1f))

/**
 * The glass *material* — fills, rim, sheen and shadow for the requested [level].
 *
 * Backdrop blur needs a child to blur, so it lives in
 * [com.easytier.ui.glass.components.GlassSurface], which layers this modifier on top of a blurred
 * copy of the [GlassSky] backdrop. Use this modifier directly when the pane sits on an opaque
 * surface and only the material matters (buttons, chips, nav bar, sheet panels).
 *
 * @param level requested quality tier (mapped through [rememberGlassPerformance])
 * @param radius corner radius; builds a continuous-corner approximation unless [shape] is given
 * @param intensity 0..1 dial for fill alpha and rim strength
 * @param shape overrides the shape (e.g. capsule)
 * @param shadowElevation soft drop shadow; 0 for inline panes
 * @param specular paint the extra diagonal specular sheen (HIGH only, ignored otherwise)
 */
@Composable
fun Modifier.glassSurface(
    level: GlassLevel = LocalGlassLevel.current,
    radius: Dp = 20.dp,
    intensity: Float = 1f,
    shape: Shape = com.easytier.ui.glass.glassShape(radius),
    shadowElevation: Dp = 0.dp,
    specular: Boolean = true
): Modifier {
    val colors = glassColors()
    val perf = rememberGlassPerformance(level)
    val effective = perf.effective
    val fillScale = effective.fillAlpha * intensity.coerceIn(0.15f, 1.5f)

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
        .background(
            Brush.verticalGradient(
                listOf(
                    colors.fillTop.scaleAlphaBy(fillScale),
                    colors.fillBottom.scaleAlphaBy(fillScale)
                )
            ),
            shape
        )
        .background(colors.sheenGradient, shape)
        .then(
            if (effective == GlassLevel.HIGH && specular) {
                Modifier
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.16f * intensity),
                            0.42f to Color.Transparent,
                            1f to Color.Transparent
                        ),
                        shape
                    )
                    // Chromatic fringe: warm on one diagonal, cool on the other — the cheapest
                    // honest stand-in for dispersion without a RuntimeShader.
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0x2EFF6B6B),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0x2E5AC8FA)
                            )
                        ),
                        shape = shape
                    )
            } else {
                Modifier
            }
        )
        .border(
            width = if (effective == GlassLevel.LITE) GlassSize.hairline else GlassSize.rim,
            brush = colors.rimGradient,
            shape = shape
        )
}

/**
 * Blurred backdrop slice for one pane.
 *
 * Re-draws the sky pattern shifted so pane-local (0,0) maps back onto the sky origin, behind a
 * blur layer clipped to [shape]. No-op when the level cannot blur or there is no [GlassSky].
 */
@Composable
internal fun GlassBackdropSlice(
    modifier: Modifier,
    shape: Shape,
    blurRadius: Dp,
    level: GlassLevel
) {
    val sky = LocalGlassSky.current ?: return
    val perf = rememberGlassPerformance(level)
    if (!perf.canBlur || blurRadius <= 0.dp) return

    val colors = glassColors()
    var panePos by remember { mutableStateOf(Offset.Zero) }
    // Remember the sky origin/size so the pane redraws when they change.
    val skyOrigin = sky.originProvider()
    val skySize = sky.sizeProvider()

    Box(
        modifier = modifier
            .onGloballyPositioned { panePos = it.positionInRoot() }
            // HIGH adds a mild lens magnification: the sampled backdrop is scaled about the pane
            // centre, which reads as refraction through a curved surface (no shader, no crash
            // risk on devices we cannot test). BALANCED/LITE sample 1:1.
            .graphicsLayer {
                val lens = if (level == GlassLevel.HIGH) 1.05f else 1f
                scaleX = lens
                scaleY = lens
            }
            .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            .clip(shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            translate(
                left = skyOrigin.x - panePos.x,
                top = skyOrigin.y - panePos.y
            ) {
                drawGlassBackdrop(colors, skySize)
            }
        }
    }
}

/** Soft radial glow used behind floating elements (dialog/sheet scrims). */
internal fun Modifier.glassGlow(color: Color, alpha: Float = 0.25f): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = size.minDimension * 0.75f
        ),
        radius = size.minDimension * 0.75f,
        center = center
    )
}
