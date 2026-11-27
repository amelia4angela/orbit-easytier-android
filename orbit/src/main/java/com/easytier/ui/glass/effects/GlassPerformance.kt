package com.easytier.ui.glass.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.LocalGlassLevel

/**
 * Runtime facts the renderer needs, resolved once per composition.
 *
 * @property level requested quality level (from [LocalGlassLevel] unless overridden)
 * @property effective the level actually used — forced down to LITE when the device asks for
 *   reduced motion *and* the caller opts into downgrading ([downgradeOnReducedMotion])
 * @property reducedMotion system animation scale is 0 or the device is in power-save mode
 * @property blurSupported `Modifier.blur` renders for real (API 31+); false means LITE-quality
 *   output no matter what level was requested
 */
@Immutable
data class GlassPerformance(
    val level: GlassLevel,
    val effective: GlassLevel,
    val reducedMotion: Boolean,
    val blurSupported: Boolean
) {
    /** True when the pane can sample a blurred backdrop. */
    val canBlur: Boolean
        get() = blurSupported && effective != GlassLevel.LITE
}

/** `true` when animations should be shortened/disabled for this process. */
fun shouldReduceMotion(context: Context): Boolean =
    animatorDurationScale(context) == 0f || isPowerSaveMode(context)

/** System "Animator duration scale" (Settings → Developer options). 1f = normal. */
fun animatorDurationScale(context: Context): Float = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
} catch (_: Throwable) {
    1f
}

fun isPowerSaveMode(context: Context): Boolean = try {
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
} catch (_: Throwable) {
    false
}

/**
 * Observes reduced-motion (animator scale + power save) and maps the requested [level] onto the
 * level that will actually render.
 *
 * Power-save changes are received live; the animator scale is re-read on every recomposition
 * and on power-save broadcasts (there is no cheap observer for a developer setting).
 */
@Composable
fun rememberGlassPerformance(
    level: GlassLevel = LocalGlassLevel.current,
    downgradeOnReducedMotion: Boolean = true
): GlassPerformance {
    val ctx = LocalContext.current
    var reduced by remember { mutableStateOf(shouldReduceMotion(ctx)) }

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                reduced = shouldReduceMotion(ctx)
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction("android.settings.ANIMATOR_DURATION_SCALE_CHANGED")
        }
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }

    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effective = when {
        !blurSupported -> GlassLevel.LITE
        reduced && downgradeOnReducedMotion -> GlassLevel.LITE
        else -> level
    }
    return GlassPerformance(
        level = level,
        effective = effective,
        reducedMotion = reduced,
        blurSupported = blurSupported
    )
}
