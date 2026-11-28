package com.easytier.ui.glass

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.easytier.ui.theme.LocalIsDarkTheme
import com.easytier.ui.theme.currentColors

/**
 * How much of the glass pipeline a device can afford.
 *
 * | Level | Min SDK | Rendering |
 * |-------|---------|-----------|
 * | HIGH | 33+ | backdrop blur (larger radius) + specular rim + accent sheen |
 * | BALANCED | 31-32 | backdrop blur (smaller radius) + gradient rim |
 * | LITE | < 31 | translucent gradient fill + rim + shadow, zero blur |
 *
 * The threshold follows the Compose/RenderEffect availability line: `Modifier.blur` is a no-op
 * below API 31, so a "blur" level below 31 would silently render as LITE anyway. HIGH starts at
 * 33 because that is where `BlurEffect` / RuntimeShader based effects became cheap enough for a
 * per-frame backdrop.
 */
enum class GlassLevel(val labelZh: String, val labelEn: String) {
    HIGH("高", "HIGH"),
    BALANCED("均衡", "BALANCED"),
    LITE("轻量", "LITE");

    /** Blur radius applied to the backdrop copy for floating glass. */
    val backdropBlur: Float
        get() = when (this) {
            HIGH -> 32f
            BALANCED -> 14f
            LITE -> 0f
        }

    /** Fill alpha multiplier — LITE needs a more opaque pane because nothing is blurred. */
    val fillAlpha: Float
        get() = when (this) {
            HIGH -> 1f
            BALANCED -> 1.15f
            LITE -> 1.9f
        }

    companion object {
        /** Device default: glass only on Android 13+ (33+ HIGH); everything below is LITE. */
        fun auto(sdk: Int = Build.VERSION.SDK_INT): GlassLevel = when {
            sdk >= 33 -> HIGH
            else -> LITE
        }
    }
}

val LocalGlassLevel = staticCompositionLocalOf { GlassLevel.auto() }

private const val PREFS = "glass_prefs"
private const val KEY_QUALITY = "glass_quality"

/**
 * Quality level for the current screen, remembered across launches.
 *
 * Resolution order: the stored override in SharedPreferences ("glass_quality") → [GlassLevel.auto].
 * Writing `.value` persists immediately, which is what the Showcase quality switch relies on.
 */
@Composable
fun rememberGlassLevel(persist: Boolean = false): MutableState<GlassLevel> {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val state = remember {
        // Default: always device-auto. A level picked in the Showcase is a *local preview*
        // and must not leak into the whole app (that bug made every screen render native
        // material after a single tap on "LITE"). Only an explicit persist=true reads/writes
        // SharedPreferences, and stale keys from older builds are ignored.
        val stored = if (persist) prefs.getString(KEY_QUALITY, null) else null
        mutableStateOf(
            stored?.let { name -> runCatching { GlassLevel.valueOf(name) }.getOrNull() }
                ?: GlassLevel.auto()
        )
    }
    if (persist) {
        LaunchedEffect(state.value) {
            prefs.edit().putString(KEY_QUALITY, state.value.name).apply()
        }
    }
    return state
}

/**
 * Wraps content with the glass palette + quality level.
 *
 * Must sit *inside* [com.easytier.ui.theme.EasyTierTheme] so light/dark stays driven by the
 * existing theme state rather than a second switch.
 */
@Composable
fun GlassTheme(
    level: GlassLevel = LocalGlassLevel.current,
    content: @Composable () -> Unit
) {
    val app = currentColors()
    val dark = LocalIsDarkTheme.current
    val colors = remember(app, dark) { GlassColors.of(app, dark) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassLevel provides level,
        content = content
    )
}
