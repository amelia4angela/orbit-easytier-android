package com.easytier.ui.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.ui.HarmonyEasing
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import kotlinx.coroutines.delay

/**
 * Glass version of the app's `HmDialog` — new component, `HmDialog` itself is untouched.
 *
 * Differences: the scrim is a translucent wash (a Dialog window cannot sample the activity
 * behind it, so blur would only blur the scrim itself), the panel is a real glass pane with
 * backdrop-style sheen, and the appearance animation is a spring overshoot instead of a linear
 * tween. The mounted-until-exit-animation-finishes pattern is kept from `HmDialog`.
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 330.dp,
    horizontalPadding: Dp = 22.dp,
    level: GlassLevel = LocalGlassLevel.current,
    dismissOnOutsideTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = glassColors()
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            delay(220)
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
                ) { if (dismissOnOutsideTap) onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(GlassMotion.quickEase()) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = GlassMotion.appear()
                ),
                exit = fadeOut(GlassMotion.quickEase()) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(160, easing = HarmonyEasing)
                )
            ) {
                GlassSurface(
                    modifier = modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        // Swallow taps so tapping the panel body doesn't hit the scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    level = level,
                    radius = GlassRadius.lg,
                    shadowElevation = 24.dp,
                    contentPadding = PaddingValues(GlassSpace.lg)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(GlassSpace.md)
                    ) {
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
