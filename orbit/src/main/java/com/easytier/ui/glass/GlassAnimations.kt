package com.easytier.ui.glass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.easytier.ui.HarmonyEasing

/**
 * One motion vocabulary for the glass layer.
 *
 * [HarmonyEasing] (fast start, long gentle settle) is the app's existing curve, so glass motion
 * matches the page transitions already in use. Springs are tuned per role rather than reusing
 * `elasticPress` verbatim: press feedback is snappier (stiffness 780) and the nav indicator is
 * closer to critically damped so the pill does not wobble.
 */
object GlassMotion {
    /** Press-down: quick, slightly under-damped. */
    val pressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.42f,
        stiffness = 780f,
        visibilityThreshold = 0.001f
    )

    /** Release: gentler so the button settles without a second bounce. */
    val releaseSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 460f,
        visibilityThreshold = 0.001f
    )

    /** Floating elements (dialogs, sheets): visible overshoot, then settle. */
    val appearSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 0.001f
    )

    /** Nav indicator / segmented thumb: no wobble. */
    val indicatorSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.88f,
        stiffness = 340f,
        visibilityThreshold = 0.001f
    )

    /** Standard spatial move. */
    val spatial: TweenSpec<Float> = tween(280, easing = HarmonyEasing)

    /** Small state colour / alpha change. */
    val quick: TweenSpec<Float> = tween(160, easing = HarmonyEasing)

    /** Reduced-motion substitute: short, linear, no overshoot. */
    val reduced: TweenSpec<Float> = tween(80, easing = LinearEasing)

    /** Swap [spec] for the reduced-motion one when motion is disabled. */
    @Suppress("UNCHECKED_CAST")
    fun <T> adapt(spec: AnimationSpec<T>, reducedMotion: Boolean): AnimationSpec<T> =
        if (reducedMotion) reduced as AnimationSpec<T> else spec

    // ── Generic variants (colour / Dp / offset animations need AnimationSpec<T>, not <Float>) ──

    /** 160ms eased change for any animatable type. */
    fun <T> quickEase(): TweenSpec<T> = tween(160, easing = HarmonyEasing)

    /** Spring settle for any animatable type (indicator travel, thumb offsets). */
    fun <T> settleSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = 340f
    )

    /** Overshoot spring for any animatable type (entrances). */
    fun <T> appear(): SpringSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )
}
