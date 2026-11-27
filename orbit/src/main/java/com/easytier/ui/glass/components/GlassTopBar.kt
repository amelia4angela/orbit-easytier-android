package com.easytier.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors

/**
 * Floating glass top bar: back glyph on the left, centered title, optional trailing actions.
 *
 * Deliberately not a `TopAppBar` — it stays inside the content flow so it can float over the
 * Showcase backdrop and show the blur working.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    level: GlassLevel = LocalGlassLevel.current,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = glassColors()
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = level,
        radius = GlassRadius.md,
        shadowElevation = 8.dp,
        contentPadding = PaddingValues(horizontal = GlassSpace.sm, vertical = GlassSpace.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                GlassIconButton(icon = "‹", onClick = onBack, level = level)
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.width(38.dp))
            }

            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = GlassSpace.xs)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassSpace.xs),
                content = actions
            )
        }
    }
}
