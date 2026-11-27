package com.easytier.ui.glass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Spacing ladder — one scale for the whole glass layer. */
object GlassSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** Fixed sizes shared by the components. */
object GlassSize {
    /** Hairline width for rims and dividers. */
    val hairline = 0.5.dp
    val rim = 1.dp
    val buttonHeight = 46.dp
    val navBarHeight = 56.dp
    val navIndicatorInset = 6.dp
    val switchWidth = 46.dp
    val switchHeight = 28.dp
    val switchThumb = 22.dp
    val sliderTrack = 6.dp
    val sliderThumb = 22.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
}

object GlassPaddings {
    val surface = PaddingValues(horizontal = GlassSpace.lg, vertical = GlassSpace.md)
    val card = PaddingValues(GlassSpace.lg)
    val button = PaddingValues(horizontal = GlassSpace.xl, vertical = GlassSpace.sm)
    val chip = PaddingValues(horizontal = GlassSpace.md, vertical = GlassSpace.xs + GlassSpace.xxs)
}
