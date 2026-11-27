package com.easytier.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.LocalIsDarkTheme
import com.easytier.ui.theme.currentColors

/**
 * Glass-only palette.
 *
 * Everything here is *derived* from the app's existing [AppColors] so the glass layer never
 * invents a second theme: switching light/dark in Theme → 主题 keeps working unchanged, and
 * `LocalIsDarkTheme` stays the single source of truth.
 *
 * Dark default (深空灰): deep space-grey backdrop + low-alpha white glass + white hairline
 * highlight + Apple-blue accent. Light mode mirrors it with a lifted grey backdrop and
 * white glass at a higher alpha.
 */
@Immutable
data class GlassColors(
    val isDark: Boolean,

    // ── Surface fills (top → bottom of the vertical gradient) ──
    val fillTop: Color,
    val fillBottom: Color,
    /** More opaque fill used by STABLE content cards (no blur, must stay readable). */
    val stableFill: Color,
    /** Inner top highlight — the "lit edge" of a glass pane. */
    val innerHighlight: Color,
    /** Outer hairline / rim. */
    val border: Color,
    /** Rim highlight that catches the light on the top-left edge. */
    val borderHighlight: Color,
    /** Soft drop shadow under floating glass. */
    val shadow: Color,
    /** Dialog / sheet scrim. */
    val scrim: Color,

    // ── Accent & semantics ──
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val onAccent: Color,

    // ── Backdrop pattern (what the glass refracts/blurs) ──
    val backdropTop: Color,
    val backdropBottom: Color,
    val backdropBlobA: Color,
    val backdropBlobB: Color,
    val backdropBlobC: Color,
    val backdropStripe: Color,
) {
    /** Base pane gradient. */
    val fillGradient: Brush get() = Brush.verticalGradient(listOf(fillTop, fillBottom))

    /** Top-lit sheen painted above the base fill (below content). */
    val sheenGradient: Brush
        get() = Brush.verticalGradient(
            0f to innerHighlight,
            0.45f to Color.Transparent,
            1f to Color.Transparent
        )

    /** Rim gradient: bright at top-left, fading to the plain hairline. */
    val rimGradient: Brush
        get() = Brush.linearGradient(listOf(borderHighlight, border, border.copy(alpha = border.alpha * 0.4f)))

    /** Accent fill for the primary button. */
    val accentGradient: Brush get() = Brush.verticalGradient(listOf(accent, accentDeep))

    companion object {
        /** Apple system blue. */
        val AppleBlue = Color(0xFF0A84FF)
        val AppleBlueDeep = Color(0xFF0060DF)
        val AppleBlueSoft = Color(0x330A84FF)
        val AppleRed = Color(0xFFFF453A)

        fun of(app: AppColors, dark: Boolean): GlassColors = if (dark) {
            GlassColors(
                isDark = true,
                fillTop = Color(0x1FFFFFFF),
                fillBottom = Color(0x0DFFFFFF),
                stableFill = Color(0xE61A1D23),
                innerHighlight = Color(0x2EFFFFFF),
                border = Color(0x33FFFFFF),
                borderHighlight = Color(0x66FFFFFF),
                shadow = Color(0x8C000000),
                scrim = Color(0xB3000000),
                accent = AppleBlue,
                accentDeep = AppleBlueDeep,
                accentSoft = AppleBlueSoft,
                danger = AppleRed,
                textPrimary = app.textPrimary,
                textSecondary = app.textSecondary,
                textTertiary = app.textTertiary,
                onAccent = Color.White,
                backdropTop = Color(0xFF0B0D11),
                backdropBottom = Color(0xFF1B2028),
                backdropBlobA = Color(0xCC1E5BFF),
                backdropBlobB = Color(0xCC8B5CF6),
                backdropBlobC = Color(0xCC00C2A8),
                backdropStripe = Color(0x14FFFFFF),
            )
        } else {
            GlassColors(
                isDark = false,
                fillTop = Color(0xCCFFFFFF),
                fillBottom = Color(0x99FFFFFF),
                stableFill = Color(0xF2FFFFFF),
                innerHighlight = Color(0xCCFFFFFF),
                border = Color(0x33FFFFFF),
                borderHighlight = Color(0xE6FFFFFF),
                shadow = Color(0x26000000),
                scrim = Color(0x66000000),
                accent = AppleBlue,
                accentDeep = AppleBlueDeep,
                accentSoft = Color(0x1F0A84FF),
                danger = AppleRed,
                textPrimary = app.textPrimary,
                textSecondary = app.textSecondary,
                textTertiary = app.textTertiary,
                onAccent = Color.White,
                backdropTop = Color(0xFFEDF1F7),
                backdropBottom = Color(0xFFDDE4EE),
                backdropBlobA = Color(0x994285F4),
                backdropBlobB = Color(0x998B5CF6),
                backdropBlobC = Color(0x9900C2A8),
                backdropStripe = Color(0x14000000),
            )
        }
    }
}

/**
 * Nullable on purpose: components stay usable outside [GlassTheme] by lazily deriving the
 * palette from the current app theme instead of crashing.
 */
val LocalGlassColors = staticCompositionLocalOf<GlassColors?> { null }

/** Current glass palette — theme-provided, or derived from [currentColors] as a fallback. */
@Composable
fun glassColors(): GlassColors {
    LocalGlassColors.current?.let { return it }
    val app = currentColors()
    val dark = LocalIsDarkTheme.current
    return remember(app, dark) { GlassColors.of(app, dark) }
}
