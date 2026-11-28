package com.easytier.ui.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
import com.easytier.ui.theme.motionMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bottom sheet on a glass panel: scrim + spring slide-up from the bottom edge.
 *
 * Only the top corners are rounded (a sheet grows out of the screen edge), and a grab-handle
 * strip hints at the affordance. Dismiss by tapping the scrim, the back key, or (with
 * [dragToDismiss]) by pulling the handle down.
 *
 * @param cornerRadius 顶部圆角；默认 28dp（既有弹层不变），大盘面板传 32dp
 * @param fillHeightFraction 非 null 时面板固定占屏高比例（如 0.9f = 90%），此时内容区可用
 *   `Modifier.weight(1f)` 自行滚动；null = 包内容高度（原行为）
 * @param dragToDismiss 抓住拖动条下拉关闭（位移超过阈值或快速下甩）
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    level: GlassLevel = LocalGlassLevel.current,
    cornerRadius: Dp = 28.dp,
    fillHeightFraction: Float? = null,
    dragToDismiss: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = glassColors()
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL
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

    // Dialog 是独立窗口，采样不到 Activity 的背景，所以「玻璃模式的轻模糊」在这里只能降级成
    // 更柔和的渐变暗化 scrim（顶部更淡、底部压暗），Universal 则是原来的平铺暗化 —— 两者都是
    // 纯暗化，不会假装有一层模糊。
    val scrimBrush = if (universal) {
        Brush.verticalGradient(listOf(colors.scrim, colors.scrim))
    } else {
        Brush.verticalGradient(
            listOf(
                colors.scrim.copy(alpha = colors.scrim.alpha * 0.55f),
                colors.scrim
            )
        )
    }

    // ── 下拉关闭 ────────────────────────────────────────────────────────────
    // 位移只作用在面板本身（AnimatedVisibility 负责进场/退场），拖动区固定在顶部手柄条上，
    // 因此不会和内部列表的滚动抢手势。
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush)
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
                    animationSpec = tween(motionMs(220), easing = com.easytier.ui.HarmonyEasing)
                ) + fadeOut(tween(motionMs(160)))
            ) {
                GlassSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(
                            if (fillHeightFraction != null) {
                                Modifier.fillMaxHeight(fillHeightFraction.coerceIn(0.2f, 1f))
                            } else {
                                Modifier
                            }
                        )
                        .offset { IntOffset(0, dragY.value.roundToInt()) }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* swallow taps so the scrim doesn't dismiss */ },
                    level = level,
                    radius = cornerRadius,
                    shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                    shadowElevation = 24.dp,
                    contentPadding = PaddingValues(
                        start = GlassSpace.lg,
                        end = GlassSpace.lg,
                        top = GlassSpace.sm,
                        bottom = GlassSpace.xxl + navBottom
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (fillHeightFraction != null) Modifier.fillMaxHeight() else Modifier
                            ),
                        verticalArrangement = Arrangement.spacedBy(GlassSpace.md)
                    ) {
                        // Grab handle（拖动区比横线本身高得多，方便触达）
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .then(
                                    if (dragToDismiss) Modifier.height(26.dp)
                                    else Modifier.padding(vertical = GlassSpace.xs)
                                )
                                .fillMaxWidth()
                                .pointerInput(dragToDismiss, visible) {
                                    if (!dragToDismiss) return@pointerInput
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (dragY.value > dismissThresholdPx) {
                                                // 先归零再关，退场动画才会从原位滑走。
                                                scope.launch { dragY.snapTo(0f) }
                                                onDismiss()
                                            } else {
                                                scope.launch {
                                                    dragY.animateTo(
                                                        0f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch { dragY.animateTo(0f) }
                                        },
                                        onVerticalDrag = { _, delta ->
                                            scope.launch {
                                                dragY.snapTo((dragY.value + delta).coerceAtLeast(0f))
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .background(colors.border, RoundedCornerShape(percent = 50))
                            )
                        }
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
