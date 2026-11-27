package com.easytier.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassPaddings
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassShape

/** Two materials — and only two — for content cards. */
enum class GlassCardElevation {
    /** Ordinary content: no blur, higher-alpha fill, hairline rim. Always readable. */
    STABLE,

    /** Floating / featured content: real glass with backdrop blur and a soft shadow. */
    FLOATING
}

/**
 * Content card.
 *
 * Design rule enforced here: `STABLE` (the default) is the right choice for anything that has to
 * be read — settings rows, log panels, forms. `FLOATING` is for the few cards that should feel
 * like they float over the backdrop (hero/status cards). Blur is expensive, so it is opt-in.
 *
 * @param elevation material tier, see [GlassCardElevation]
 * @param rounded radius from the [GlassRadius] ladder
 * @param onClick when non-null the card gets spring press feedback
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevation: GlassCardElevation = GlassCardElevation.STABLE,
    rounded: Dp = GlassRadius.lg,
    level: GlassLevel = LocalGlassLevel.current,
    shape: Shape = glassShape(rounded),
    contentPadding: PaddingValues = GlassPaddings.card,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(GlassSpace.sm),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) {
        Modifier.glassPress(onClick = onClick)
    } else {
        Modifier
    }

    when (elevation) {
        GlassCardElevation.STABLE -> Column(
            modifier = modifier
                .fillMaxWidth()
                .then(clickable)
                .glassStable(radius = rounded, shape = shape, shadowElevation = 2.dp)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )

        GlassCardElevation.FLOATING -> GlassSurface(
            modifier = modifier.fillMaxWidth().then(clickable),
            level = level,
            radius = rounded,
            shape = shape,
            shadowElevation = 14.dp,
            contentPadding = contentPadding
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    }
}
