package com.easytier.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassMotion
import com.easytier.ui.glass.GlassShapes
import com.easytier.ui.glass.components.GlassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode

/*
 * iOS 26 风格的悬浮胶囊底栏 —— 外层乳白玻璃 + **独立的动态玻璃选中胶囊**。
 *
 * 结构（自下而上）：
 *   外层  GlassSurface（唯一的一层背景模糊 / Universal 时自动换成实色描边材质）
 *   选中  SelectedLiquidPill（纯 Canvas 绘制：乳白底 → 透镜径向高光 → accent 光晕 → 渐变弧 → 1dp 厚度描边）
 *   内容  Row 里 4 个 tab（图标 24dp + 12sp 文字），点击区域整格 ≥44dp
 *
 * 只有视觉是新的：Tab 数量 / 名称 / 顺序 / 点击语义全部由调用方通过 [items] 传入。
 */

/** 底栏的一个 tab：未选中用线性图标，选中用同一 Material 家族的填充图标。 */
data class LiquidGlassNavItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val contentDescription: String = label
)

/**
 * 底栏尺寸的唯一来源 —— 组件内部和「内容区底部留白」共用同一组常量，
 * 不允许在别处再写 72dp / 56dp 之类的魔数。
 */
object LiquidGlassNavDimens {
    /** 悬浮胶囊高度（iOS 26 悬浮 bar 区间 76–88dp）。 */
    val barHeight: Dp = 80.dp

    /** 胶囊左右边距。 */
    val horizontalMargin: Dp = 16.dp

    /** 胶囊与系统手势区之间的距离。 */
    val bottomGap: Dp = 10.dp

    /** 胶囊 ↔ 内容区之间的呼吸间隔（只用于内容区 padding 推导，不参与绘制）。 */
    val contentGap: Dp = 12.dp

    /** 胶囊内边距：选中胶囊与外层玻璃之间的缝隙。 */
    val contentInset: Dp = 6.dp

    /** 图标统一 24dp。 */
    val iconSize: Dp = 24.dp

    /** 图标与文字间距。 */
    val iconLabelGap: Dp = 5.dp

    val labelSize = 12.sp
    val selectedIconScale = 1.08f
}

/**
 * 内容区底部需要预留的高度 = 胶囊高度 + 底距 + 系统手勢条 + 一点呼吸。
 *
 * 因为系统 inset 在不同 ROM / 手势条模式下不同，这个值只能在组合里算，
 * 不能在内容区里写死一个数字（原来写的是 72dp，底栏改高后必然遮挡内容）。
 */
@Composable
fun liquidGlassNavBarBottomPadding(): Dp {
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return LiquidGlassNavDimens.barHeight +
        LiquidGlassNavDimens.bottomGap +
        systemBar +
        LiquidGlassNavDimens.contentGap
}

/**
 * 悬浮胶囊底栏。
 *
 * @param items 4 个 tab（顺序即显示顺序）
 * @param selectedIndex 当前选中项下标
 * @param onSelect 点击回调（与动画解耦：点击立即回调，胶囊自己 spring 过去）
 */
@Composable
fun LiquidGlassNavigationBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val glass = glassColors()
    // 双主题只在这里读一次：材质分叉全部在下面，调用处不判断 ThemeMode。
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)

    // 形状/常量缓存：底栏常驻，别每帧重新构造。
    val barShape: Shape = remember { GlassShapes.capsule }
    val pillShape: Shape = remember {
        RoundedCornerShape(LiquidGlassNavDimens.barHeight / 2 - LiquidGlassNavDimens.contentInset)
    }
    val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = LiquidGlassNavDimens.horizontalMargin,
                end = LiquidGlassNavDimens.horizontalMargin,
                // 距屏幕底部 = 手势区 + 10dp；没有手势条（三键导航 / 非 edge-to-edge）时就是 10dp。
                bottom = gestureInset + LiquidGlassNavDimens.bottomGap
            )
    ) {
        // ── 外层玻璃容器 ──────────────────────────────────────────────
        // GlassSurface 自带全库唯一的一层背景模糊（HIGH/BALANCED），LITE / Universal 自动
        // 退化成「实色底 + 0.8dp 描边 + ≤6dp 轻阴影」，两条路径都不含第二处模糊调用。
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(LiquidGlassNavDimens.barHeight),
            radius = LiquidGlassNavDimens.barHeight / 2,
            shape = barShape,
            intensity = if (glass.isDark) 1.25f else 1.05f,
            shadowElevation = 18.dp,
            contentPadding = PaddingValues(LiquidGlassNavDimens.contentInset)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val itemWidth = maxWidth / items.size
                // 动画的是胶囊的 x / 宽度（Dp），spring 近临界阻尼，滑动只走 layout。
                val pillX by animateDpAsState(
                    targetValue = itemWidth * safeIndex,
                    animationSpec = GlassMotion.settleSpring(),
                    label = "liquidNavPillX"
                )
                val pillWidth by animateDpAsState(
                    targetValue = itemWidth,
                    animationSpec = GlassMotion.settleSpring(),
                    label = "liquidNavPillWidth"
                )

                // 选中胶囊先声明 = 画在 tab 内容之下；它没有任何 pointerInput，
                // 所以不会吃掉其它 tab 的点击。
                Box(
                    modifier = Modifier
                        // px 在 layout 阶段才换算：滑动只走 layout，不触发重组。
                        .offset { IntOffset(pillX.roundToPx(), 0) }
                        .width(pillWidth)
                        .fillMaxHeight()
                ) {
                    SelectedLiquidPill(
                        shape = pillShape,
                        universal = universal,
                        accent = glass.accent,
                        accentSoft = glass.accentSoft
                    )
                }

                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val active = index == safeIndex
                        NavTabItem(
                            item = item,
                            active = active,
                            accent = glass.accent,
                            inactive = glass.textTertiary,
                            haptics = haptics,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个 tab：24dp 图标（选中填充 / 未选中线性，Crossfade 交接）+ 12sp 文字。
 * 整格都是点击热区（≈80dp 高 × 1/4 宽），满足 44dp 触控下限。
 */
@Composable
private fun RowScope.NavTabItem(
    item: LiquidGlassNavItem,
    active: Boolean,
    accent: Color,
    inactive: Color,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit
) {
    // 颜色渐变 + 图标轻微放大 + 文字淡入淡出，三条动画互相独立。
    val tint by animateColorAsState(
        targetValue = if (active) accent else inactive,
        animationSpec = GlassMotion.quickEase(),
        label = "liquidNavTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) LiquidGlassNavDimens.selectedIconScale else 1f,
        animationSpec = GlassMotion.settleSpring(),
        label = "liquidNavIconScale"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.82f,
        animationSpec = GlassMotion.quickEase(),
        label = "liquidNavLabelAlpha"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // 设备不支持触觉反馈时系统静默忽略。
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(LiquidGlassNavDimens.iconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            ) {
                Crossfade(
                    targetState = active,
                    animationSpec = tween(160, easing = HarmonyEasing),
                    label = "liquidNavGlyph"
                ) { selected ->
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.contentDescription,
                        tint = tint,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(LiquidGlassNavDimens.iconLabelGap))
            Text(
                text = item.label,
                color = tint,
                fontSize = LiquidGlassNavDimens.labelSize,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha }
            )
        }
    }
}

/**
 * 独立的选中玻璃胶囊。
 *
 * 玻璃模式（自下而上，全部在一次 Canvas 里画完，无静态图片、无逐帧分配）：
 *  1. 乳白渐变底 —— 比外层玻璃亮一档，做出「玻璃叠玻璃」的层次
 *  2. 透镜高光 —— 顶部外侧射入的径向渐变，模拟光在曲面上的聚集（局部径向 = 折射感）
 *  3. accent 光晕 —— 中心到边缘的极轻径向渐变（8–12% 蓝），只落在边缘
 *  4. 边缘光弧 —— 上半圈横向渐变描边（中间亮、两端透），即「内亮外透」的折射弧
 *  5. 厚度线 —— 1dp 竖向渐变描边：上沿亮线（白），下沿暗线（黑 10–22%）
 * 外层再叠一层 6dp 柔影，保证它明显浮在外层玻璃之上。
 *
 * Universal：实色淡蓝 + 同圆角 + 0.5dp 描边，无高光、无折射、无模糊。
 */
@Composable
private fun SelectedLiquidPill(
    shape: Shape,
    universal: Boolean,
    accent: Color,
    accentSoft: Color
) {
    if (universal) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(accentSoft, shape)
                .border(0.5.dp, accent.copy(alpha = 0.28f), shape)
        )
        return
    }

    val colors = glassColors()
    val paint = remember(colors.isDark, accent) { LiquidPillPaint(colors.isDark, accent) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            )
            .clip(shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            paint.ensure(w, h)

            val fullRadius = CornerRadius(h / 2f)
            // 1) 乳白底
            drawRoundRect(brush = paint.fill, cornerRadius = fullRadius)
            // 2) 顶部透镜高光（径向）
            drawRoundRect(brush = paint.lens, cornerRadius = fullRadius)
            // 3) 边缘蓝光晕（径向，只吃边缘）
            drawRoundRect(brush = paint.bloom, cornerRadius = fullRadius)

            // 4) 折射线：仅上半圈，中间亮两端透
            val arcPx = 1.5.dp.toPx()
            clipRect(bottom = h * 0.55f) {
                drawRoundRect(
                    brush = paint.specularArc,
                    topLeft = Offset(arcPx / 2f, arcPx / 2f),
                    size = Size(w - arcPx, h - arcPx),
                    cornerRadius = CornerRadius(h / 2f - arcPx / 2f),
                    style = Stroke(arcPx)
                )
            }

            // 5) 厚度线：上亮下暗
            val rimPx = 1.dp.toPx()
            drawRoundRect(
                brush = paint.rim,
                topLeft = Offset(rimPx / 2f, rimPx / 2f),
                size = Size(w - rimPx, h - rimPx),
                cornerRadius = CornerRadius(h / 2f - rimPx / 2f),
                style = Stroke(rimPx)
            )
        }
    }
}

/**
 * 选中胶囊的画笔缓存。
 *
 * 与尺寸相关的径向画笔只在胶囊尺寸变化时重建（换 tab 尺寸不变 → 一次都不重建），
 * 尺寸无关的线性画笔直接常量持有 —— 绘制阶段零分配。
 */
private class LiquidPillPaint(
    private val dark: Boolean,
    private val accent: Color
) {
    private var lastW = Float.NaN
    private var lastH = Float.NaN

    /** 乳白底：浅色 90%→70%，深色 22%→12%（比外层玻璃各亮一档）。 */
    val fill: Brush = if (dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.12f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.70f)))
    }

    /** 厚度线：上沿亮线 → 中部透明 → 下沿暗线。 */
    val rim: Brush = if (dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.35f to Color.White.copy(alpha = 0.10f),
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f)
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White,
            0.35f to Color.White.copy(alpha = 0.18f),
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.10f)
        )
    }

    /** 折射弧：横向渐变，中间亮、两端透。 */
    val specularArc: Brush = Brush.horizontalGradient(
        listOf(
            Color.Transparent,
            Color.White.copy(alpha = if (dark) 0.28f else 0.85f),
            Color.White.copy(alpha = if (dark) 0.28f else 0.85f),
            Color.Transparent
        )
    )

    private var lensBrush: Brush = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
    )
    private var bloomBrush: Brush = Brush.radialGradient(
        listOf(Color.Transparent, accent.copy(alpha = 0.10f))
    )

    val lens: Brush get() = lensBrush
    val bloom: Brush get() = bloomBrush

    /** 尺寸变化时才重建径向画笔。 */
    fun ensure(w: Float, h: Float) {
        if (w == lastW && h == lastH) return
        lastW = w
        lastH = h
        // 光从上方来：径向中心在胶囊上方一点，半径覆盖整个高度 → 顶沿最亮，向下散开。
        lensBrush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = if (dark) 0.16f else 0.34f),
                Color.White.copy(alpha = if (dark) 0.04f else 0.10f),
                Color.Transparent
            ),
            center = Offset(w / 2f, -h * 0.2f),
            radius = h * 1.35f
        )
        // 蓝光晕：中心透明，越靠边缘越亮（0 → 10%），只染边缘一圈。
        bloomBrush = Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to accent.copy(alpha = 0.10f),
            center = Offset(w / 2f, h / 2f),
            radius = h * 0.95f
        )
    }
}
