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
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
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
    internal val sizeProvider: () -> Size,
    /** Paints the sky's own backdrop into a draw scope of the given size (in sky-local space). */
    internal val drawBackdrop: DrawScope.(Size) -> Unit
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
    // Keep the sky publishing the *current* backdrop lambda: a theme switch swaps the palette,
    // and a pane that still blurred the previous colours would show a visible seam.
    val backdropState = androidx.compose.runtime.rememberUpdatedState(backdrop)
    val sky = remember {
        GlassSky(
            originProvider = { origin },
            sizeProvider = { sizePx },
            drawBackdrop = { canvasSize -> backdropState.value.invoke(this, canvasSize) }
        )
    }

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

/** Universal UI 材质常量：0.8dp 清晰描边、2–6dp 轻阴影。 */
private val UniversalBorder = 0.8.dp
private val UniversalMinShadow = 2.dp
private val UniversalMaxShadow = 6.dp

/**
 * Universal UI（玻璃关闭）的**唯一**材质：极浅竖向渐变底 + 0.8dp 清晰描边 + ≤6dp 轻阴影。
 *
 * 整条链路只有 `background` / `border` / `shadow` 三样：
 * **不含 blur、不含 RenderEffect、不含 RuntimeShader** —— 这就是 UNIVERSAL 模式下
 * 「零高成本渲染」的落点。
 *
 * @param paintFill false = 调用方已经在下面铺了品牌色（CTA / 危险按钮 / 状态胶囊），
 *   这里只补描边和阴影，不能把语义色盖成白底。
 */
@Composable
fun Modifier.universalSurface(
    shape: Shape,
    shadowElevation: Dp = UniversalMinShadow,
    paintFill: Boolean = true
): Modifier {
    val colors = glassColors()
    val elevation = shadowElevation.coerceIn(0.dp, UniversalMaxShadow)
    var result = this
    if (elevation > 0.dp) {
        result = result.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.universalShadow,
            spotColor = colors.universalShadow
        )
    }
    var layers = result.clip(shape)
    if (paintFill) {
        layers = layers.background(colors.universalBrush, shape)
    }
    return layers.border(UniversalBorder, colors.universalBorder, shape)
}

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
 * @param refraction AGSL refraction spec; when the shader pass is active on this device the
 *   painted-on chromatic fringe is dropped, because the real dispersion comes from the shader.
 *   The shader itself is attached where the backdrop lives (GlassBackdropSlice) — a Modifier has
 *   no children to sample, so `glassSurface` alone can only carry the spec, not refract.
 * @param paintFill 仅对 UNIVERSAL 生效：false 表示调用方已经在下面铺了品牌色/状态色，
 *   只补描边 + 阴影（见 [universalSurface]）。玻璃模式下该参数无效果。
 */
@Composable
fun Modifier.glassSurface(
    level: GlassLevel = LocalGlassLevel.current,
    radius: Dp = 20.dp,
    intensity: Float = 1f,
    shape: Shape = com.easytier.ui.glass.glassShape(radius),
    shadowElevation: Dp = 0.dp,
    specular: Boolean = true,
    refraction: GlassShaderSpec = LocalGlassRefraction.current,
    paintFill: Boolean = true
): Modifier {
    // ── Universal UI 分叉（最先返回，保证后面一行 blur/渲染代码都不会被走到）──
    if (LocalThemeMode.current == ThemeMode.UNIVERSAL) {
        return universalSurface(
            shape = shape,
            shadowElevation = shadowElevation,
            paintFill = paintFill
        )
    }
    val colors = glassColors()
    val perf = rememberGlassPerformance(level)
    val effective = perf.effective
    // 图片背景（LocalGlassBoost > 1）时填充更实，否则浅色玻璃压在花色照片上会掉字。
    val boost = com.easytier.ui.glass.LocalGlassBoost.current
    val fillScale = (effective.fillAlpha * intensity.coerceIn(0.15f, 1.5f) * boost)
        .coerceIn(0.15f, 3f)
    // Real, shader-driven optics: only on HIGH, only where RuntimeShader exists. Otherwise the
    // static gradients below are the whole story (and nothing extra is allocated).
    val lensActive = effective == GlassLevel.HIGH &&
        refraction.enabled &&
        refraction.refractionStrength > 0.001f &&
        GlassRefractionSupport.isAvailable

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
    // Android 13 以下 / 手动 LITE / 省电模式：不开启玻璃质感，直接落到同一套 Universal 材质
    // （不透明底 + 0.8dp 描边 + 轻阴影）。留一条路径就不存在「两套无模糊外观」。
    if (effective == GlassLevel.LITE) {
        return universalSurface(
            shape = shape,
            shadowElevation = shadowElevation,
            paintFill = paintFill
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
                    .then(
                        // Chromatic fringe: warm on one diagonal, cool on the other. With the
                        // AGSL pass running, real per-channel dispersion exists, so the painted
                        // one would double up and read as a dirty outline.
                        if (lensActive) {
                            Modifier
                        } else {
                            Modifier.border(
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
                        }
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
 *
 * On HIGH + API 33+ the composed slice (scaled → blurred → clipped) gets the AGSL refraction
 * pass as its outer layer, so the shader bends the *blurred* backdrop instead of blurring the
 * bended one. Everything below HIGH, or below API 33, never constructs a RuntimeShader.
 *
 * @param radius corner radius of [shape], used by the shader to follow the rim
 * @param refraction refraction tunables; `enabled = false` or strength 0 removes the pass
 */
@Composable
internal fun GlassBackdropSlice(
    modifier: Modifier,
    shape: Shape,
    blurRadius: Dp,
    level: GlassLevel,
    radius: Dp = GlassRadius.lg,
    refraction: GlassShaderSpec = LocalGlassRefraction.current
) {
    val sky = LocalGlassSky.current ?: return
    val perf = rememberGlassPerformance(level)
    if (!perf.canBlur || blurRadius <= 0.dp) return

    // Lens only for the real HIGH tier on hardware that has RuntimeShader.
    val lensActive = perf.effective == GlassLevel.HIGH &&
        refraction.enabled &&
        refraction.refractionStrength > 0.001f &&
        GlassRefractionSupport.isAvailable

    var panePos by remember { mutableStateOf(Offset.Zero) }
    // Remember the sky origin/size so the pane redraws when they change.
    val skyOrigin = sky.originProvider()
    val skySize = sky.sizeProvider()

    Box(
        modifier = modifier
            .onGloballyPositioned { panePos = it.positionInRoot() }
            .then(
                if (lensActive) Modifier.glassRefractionLens(refraction, radius) else Modifier
            )
            // HIGH adds a mild lens magnification: the sampled backdrop is scaled about the pane
            // centre, which reads as refraction through a curved surface (no shader, no crash
            // risk on devices we cannot test). BALANCED/LITE sample 1:1. When the AGSL pass runs
            // the scaling is dropped — the shader does the optics, and doubling both would
            // magnify twice and push the samples outside the pane content.
            .graphicsLayer {
                val lens = if (level == GlassLevel.HIGH && !lensActive) 1.05f else 1f
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
                // Sample the sky's *own* backdrop (the screen's real background), not the demo
                // pattern — otherwise a pane over the app blurs stripes that are not there.
                sky.drawBackdrop.invoke(this, skySize)
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
