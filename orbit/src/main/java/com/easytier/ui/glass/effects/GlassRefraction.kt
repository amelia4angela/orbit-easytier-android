package com.easytier.ui.glass.effects

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassRadius

/*
 * Liquid-glass optics: rim refraction + chromatic dispersion, done with an AGSL RuntimeShader.
 *
 * ── Why AGSL and not a library ────────────────────────────────────────────────────────────────
 * Every published liquid-glass library (cloudy / haze / liquid-glass) needs AGP 9 +
 * compileSdk 36/37 + Kotlin 2.4, which would force minSdk up. `android.graphics.RuntimeShader`
 * is a platform class available since API 33, so the same optics cost zero dependencies and stay
 * behind a runtime gate — Android 12 and below never load this file's API-33 classes at all.
 *
 * ── How the backdrop is sampled ───────────────────────────────────────────────────────────────
 * A `Modifier` has no children, so the shader cannot be attached to `glassSurface()` itself: the
 * only node that has glass content underneath is the blurred backdrop slice
 * (`GlassBackdropSlice`), whose layer already holds a blurred copy of the [GlassSky] backdrop.
 * We give *that* layer a `RenderEffect.createRuntimeShaderEffect(shader, "content")`, so the
 * shader's `uniform shader content` is bound to the layer's own pixels — a real refraction of the
 * blurred backdrop, not a painted-on highlight. Compose exposes the bridge as
 * `android.graphics.RenderEffect.asComposeRenderEffect()` (androidx.compose.ui.graphics), which
 * is what makes `Modifier.graphicsLayer { renderEffect = … }` usable from 1.6.x up.
 *
 * ── What the shader does ─────────────────────────────────────────────────────────────────────
 *   rim band   signed distance to the rounded-rect edge; refraction only inside [rimWidth]
 *   refraction samples the backdrop slightly *outward* near the rim (foreshortening), clamped so
 *              the sample never leaves the pane → no transparent fringe, no black border
 *   dispersion R sampled further out than B by [dispersion] px → 1–1.5px warm/cool split
 *   specular   edge-weighted white lift, biased toward the light (top-left) corner
 *   lift       +3% brightness inside the rim so the pane reads as glass, not as a cut-out
 *
 * ── Cost ────────────────────────────────────────────────────────────────────────────────────
 * One extra GPU pass over the pane's own pixels, HIGH level only, and only where a backdrop
 * slice exists (nav bar, floating cards, dialog/sheet panels). The RuntimeShader is compiled
 * once per pane (remembered) and its uniforms are updated in place; nothing is allocated per
 * frame. If the AGSL fails to compile on some OEM driver the cache returns null and the pane
 * renders exactly as before — the feature can never crash the app.
 */

/**
 * Tunables for the refraction pass. Everything the shader needs lives here so the whole look can
 * be dialled from one place (Showcase → 「折射 Refraction」).
 *
 * @property refractionStrength 0..1 rim lens strength (0 = pass-through, the layer is skipped)
 * @property dispersion R/B separation in px at the rim; iOS-like look is 0.8–1.5
 * @property rimWidth width of the refracting band, measured inward from the pane edge
 * @property highlight specular rim + interior lift, 0..1
 * @property enabled master switch; false = the layer is not built at all
 */
@Immutable
data class GlassShaderSpec(
    val refractionStrength: Float = 0.55f,
    val dispersion: Float = 1.1f,
    val rimWidth: Dp = 12.dp,
    val highlight: Float = 0.45f,
    val enabled: Boolean = true
)

/**
 * Refraction spec for the current composition. `staticCompositionLocalOf` because a whole-screen
 * change is rare (Showcase dial only) and static reads are cheaper than dynamic ones.
 */
val LocalGlassRefraction = staticCompositionLocalOf { GlassShaderSpec() }

/** Runtime capability probe. Reads an int only — safe to call on every API level. */
object GlassRefractionSupport {
    /** `RuntimeShader` + runtime-shader `RenderEffect` are both API 33+. */
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= 33
}

/**
 * Attaches the AGSL refraction pass to the layer that holds the blurred backdrop.
 *
 * No-op (identical modifier, zero allocation, no shader constructed) when:
 *  - the device is below API 33,
 *  - the spec is disabled / strength is 0,
 *  - or [GlassRefractionSupport] says the platform can't render it.
 *
 * The caller is responsible for the HIGH-level gate: this modifier only knows about the platform
 * and the spec, not about the quality tier.
 *
 * @param spec refraction tunables
 * @param radius corner radius of the pane, so the rim band follows the actual shape
 */
@Composable
fun Modifier.glassRefractionLens(
    spec: GlassShaderSpec = LocalGlassRefraction.current,
    radius: Dp = GlassRadius.lg
): Modifier {
    if (!GlassRefractionSupport.isAvailable) return this
    if (!spec.enabled || spec.refractionStrength <= 0.001f) return this

    // Compiled once per pane; uniforms are re-bound (in place) whenever size/spec changes.
    val cache = remember { GlassLensCache() }

    return this.graphicsLayer {
        // `size` is the layer size in px; the Density receiver gives us Dp → px.
        cache.bind(
            width = size.width,
            height = size.height,
            radiusPx = radius.toPx(),
            rimPx = spec.rimWidth.toPx(),
            spec = spec
        )
        val effect = cache.effect()
        if (effect != null) renderEffect = effect
    }
}

/**
 * AGSL source of the rim lens.
 *
 * Every uniform is a scalar/vec2 — the child shader is bound by name from
 * `RenderEffect.createRuntimeShaderEffect(shader, "content")`.
 *
 * Written for the strict AGSL dialect: explicit `float(...)` casts, no mixed half/float
 * arithmetic, no implicit int literals, `half4 main(float2)` signature only.
 */
private const val GLASS_LENS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uRim;
uniform float uRefract;
uniform float uDisperse;
uniform float uHighlight;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r, r);
    float2 outside = max(q, float2(0.0, 0.0));
    return length(outside) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 size = max(uSize, float2(1.0, 1.0));
    float2 halfSize = size * 0.5;
    float r = min(max(uRadius, 0.0), min(halfSize.x, halfSize.y));
    float2 p = fragCoord - halfSize;

    // Positive inside the rounded rect; 0 on the edge.
    float inside = -sdRoundRect(p, halfSize, r);
    float rim = max(uRim, 1.0);
    float t = 1.0 - clamp(inside / rim, 0.0, 1.0);
    float bend = t * t;

    float len = length(p);
    float2 dir = len > 0.0001 ? p / len : float2(0.0, -1.0);

    // Pull the sample outward near the rim, but never past the edge (inside * 0.85) so we never
    // sample the transparent padding outside the pane.
    float amp = min(uRefract * rim * 0.35 * bend, inside * 0.85);
    float2 base = fragCoord + dir * amp;
    float2 lim = size - float2(1.0, 1.0);
    float2 midCoord = clamp(base, float2(0.0, 0.0), lim);
    float2 redCoord = clamp(base + dir * uDisperse, float2(0.0, 0.0), lim);
    float2 blueCoord = clamp(base - dir * uDisperse, float2(0.0, 0.0), lim);

    half4 mid = content.eval(midCoord);
    half4 red = content.eval(redCoord);
    half4 blue = content.eval(blueCoord);

    float alpha = float(mid.a);
    float3 rgb = float3(float(red.r), float(mid.g), float(blue.b));

    // Specular rim, biased toward the top-left light source of the glass system.
    float facing = clamp(dot(dir, float2(-0.38, -0.92)), 0.0, 1.0);
    float spec = pow(t, 4.0) * uHighlight * (0.45 + 0.75 * facing);
    rgb += float3(spec * alpha);
    // Interior lift, strongest just inside the rim.
    rgb *= float3(1.0 + 0.03 * uHighlight * alpha * (1.0 - t));

    return half4(half3(rgb), half(alpha));
}
"""

/**
 * Per-pane shader + RenderEffect cache.
 *
 * Isolated in an API-33-only class so a pre-33 device never loads (or even verifies)
 * `RuntimeShader`. Instances are held by `remember`, so a pane recompiles only when it is
 * disposed and re-created — never per frame.
 */
@RequiresApi(33)
private class GlassLensCache {

    private val shader: RuntimeShader? = try {
        RuntimeShader(GLASS_LENS_AGSL)
    } catch (t: Throwable) {
        // OEM driver rejected the program: degrade to "no refraction", never crash.
        Log.w(TAG, "AGSL compile failed, refraction disabled for this pane", t)
        null
    }

    /** Wrapped once; the wrapper is stable so `graphicsLayer` sees no change. */
    private val composeEffect: androidx.compose.ui.graphics.RenderEffect? =
        shader?.let { s ->
            try {
                RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
            } catch (t: Throwable) {
                Log.w(TAG, "runtime shader effect unavailable", t)
                null
            }
        }

    private var lastWidth = Float.NaN
    private var lastHeight = Float.NaN
    private var lastRadius = Float.NaN
    private var lastRim = Float.NaN
    private var lastSpec: GlassShaderSpec? = null

    fun effect(): androidx.compose.ui.graphics.RenderEffect? = composeEffect

    /** Re-binds uniforms only when something actually changed (no per-frame JNI traffic). */
    fun bind(width: Float, height: Float, radiusPx: Float, rimPx: Float, spec: GlassShaderSpec) {
        val s = shader ?: return
        if (width <= 0f || height <= 0f) return
        if (width == lastWidth && height == lastHeight && radiusPx == lastRadius &&
            rimPx == lastRim && spec == lastSpec
        ) {
            return
        }
        lastWidth = width
        lastHeight = height
        lastRadius = radiusPx
        lastRim = rimPx
        lastSpec = spec

        s.setFloatUniform("uSize", width, height)
        s.setFloatUniform("uRadius", radiusPx)
        s.setFloatUniform("uRim", rimPx)
        s.setFloatUniform("uRefract", spec.refractionStrength.coerceIn(0f, 1f))
        s.setFloatUniform("uDisperse", spec.dispersion.coerceIn(0f, 1.5f))
        s.setFloatUniform("uHighlight", spec.highlight.coerceIn(0f, 1f))
    }

    private companion object {
        const val TAG = "GlassRefraction"
    }
}
