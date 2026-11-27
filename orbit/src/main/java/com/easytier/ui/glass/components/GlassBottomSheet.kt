package com.easytier.ui.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import kotlinx.coroutines.delay

/**
 * Bottom sheet on a glass panel: scrim + spring slide-up from the bottom edge.
 *
 * Only the top corners are rounded (a sheet grows out of the screen edge), and a grab-handle
 * strip hints at the affordance. Dismiss by tapping the scrim or the handler row.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    level: GlassLevel = LocalGlassLevel.current,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = glassColors()
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            delay(260)
            mounted = false
        }
    }
    if (!mounted) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = GlassMotion.appear()
                ) + fadeIn(tween(160)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220, easing = com.easytier.ui.HarmonyEasing)
                ) + fadeOut(tween(160))
            ) {
                GlassSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* swallow taps so the scrim doesn't dismiss */ },
                    level = level,
                    radius = 28.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    shadowElevation = 24.dp,
                    contentPadding = PaddingValues(
                        start = GlassSpace.lg,
                        end = GlassSpace.lg,
                        top = GlassSpace.sm,
                        bottom = GlassSpace.xxl
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(GlassSpace.md)
                    ) {
                        // Grab handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = GlassSpace.xs)
                                .size(width = 38.dp, height = 4.dp)
                                .background(colors.border, RoundedCornerShape(percent = 50))
                        ) { }
                        if (title != null) {
                            Text(
                                text = title,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}
