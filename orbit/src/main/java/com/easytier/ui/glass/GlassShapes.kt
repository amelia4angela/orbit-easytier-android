package com.easytier.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radius ladder (12 / 16 / 20 / 24 / 28).
 *
 * Compose 1.6 ships only circular [RoundedCornerShape]; iOS-style *continuous* corners
 * (squircles) are approximated by inflating the radius ~15% so the straight run of each edge
 * starts earlier and the corner reads softer. Same trick as `UISquircle` shims — not a real
 * superellipse, but visually close at these sizes and free of extra shaders.
 */
object GlassRadius {
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 28.dp

    /** Pills / capsules. */
    val full: Dp = 999.dp
}

object GlassShapes {
    /** Continuous-corner approximation of a rounded rect. */
    fun continuous(radius: Dp): RoundedCornerShape = RoundedCornerShape(radius * CONTINUOUS_BIAS)

    /** Plain circular corners — use for the 圆角对比 row where exact values are the point. */
    fun circular(radius: Dp): RoundedCornerShape = RoundedCornerShape(radius)

    val capsule: RoundedCornerShape = RoundedCornerShape(percent = 50)

    private const val CONTINUOUS_BIAS = 1.15f
}

/** Shorthand used by components: [radius] in, continuous-corner shape out. */
fun glassShape(radius: Dp): RoundedCornerShape = GlassShapes.continuous(radius)
