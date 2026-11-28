package com.easytier.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * iOS 26 风格的悬浮胶囊底栏 —— 外层乳白玻璃 + **可拖拽的淡蓝玻璃选中胶囊**。
 *
 * 结构（自下而上）：
 *   外层  GlassSurface（唯一的一层背景模糊 / Universal 时自动换成实色描边材质）
 *   选中  SelectedLiquidPill（纯 Canvas 绘制：淡蓝底 → 白 sheen → 椭圆 specular → 蓝色光晕 → 上弧折射 → 1dp 白描边）
 *   内容  Row 里 N 个 tab（图标 24dp + 12sp 文字），点击区域整格 ≥44dp
 *
 * 交互（三条路径共用同一份胶囊位置状态 `pillLeft`）：
 *   点击 tab   → onSelect 立即回调，胶囊在 LaunchedEffect 里 spring 过去（动画与回调解耦）
 *   按住胶囊拖 → 胶囊跟手（直接写状态、不走动画），松手才提交导航
 *   拖动中     → 按水平速度做真实 scale 形变（Q 弹），松手 spring 回收
 *
 * 只有视觉与手势是新的：Tab 数量 / 名称 / 顺序 / 点击语义全部由调用方通过 [items] 传入，
 * 胶囊宽度永远 = 栏宽 / items.size，没有任何硬编码的 tab 数。
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

// ── 拖拽 / 形变参数（唯一出处，改手感只改这里）──────────────────────────────

/** 抓取容差：判定的位置≈按下点+已越过的 slop，所以再放宽 2dp 才能从胶囊边缘往外拖。 */
private val GRAB_TOLERANCE = 2.dp

/** tab 中心的轻微吸附半径：±9dp 内拖动量打折（阻尼，不是硬吸附）。 */
private val DETENT_RADIUS = 9.dp

/** 阻尼最深处：拖动量只剩 45%（手指要多走一点才能离开 tab 中心）。 */
private const val DETENT_MIN_SCALE = 0.45f

/** 满形变对应的水平速度（px/s）。约 900px/s ≈ 中速一划。 */
private const val JELLY_FULL_SPEED_PX_PER_SEC = 900f

/** 满速时 scaleX = 1.12（上限），scaleY = 1 - 0.06 = 0.94（下限）。 */
private const val JELLY_SCALE_X = 0.12f
private const val JELLY_SCALE_Y = 0.06f

/** 每帧朝目标形变追赶的比例（0.45 ≈ 2~3 帧跟上，手指停住后同样速度回收）。 */
private const val JELLY_CHASE = 0.45f

/** 位置吸附弹簧：轻微过冲但不抖。 */
private const val PILL_DAMPING = 0.68f
private const val PILL_STIFFNESS = 380f

/** 形状（果冻）回收弹簧：比位置更弹一点，过冲即「Q 弹」。 */
private const val JELLY_DAMPING = 0.62f
private const val JELLY_STIFFNESS = 360f

/** 帧间隔夹取范围，避免掉帧瞬间算出爆炸速度。 */
private const val MIN_FRAME_SEC = 0.008f
private const val MAX_FRAME_SEC = 0.05f

/** 选中胶囊的淡蓝玻璃主色（#DCEEFF）。 */
private val PillBlue = Color(0xFFDCEEFF)

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
 * @param items N 个 tab（顺序即显示顺序；胶囊按 N 等分，不硬编码数量）
 * @param selectedIndex 当前选中项下标
 * @param onSelect 选中回调（与动画解耦：点击立即回调，胶囊自己 spring 过去；
 *   拖动则松手后才回调，拖动过程中绝不重复触发）
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
    val density = LocalDensity.current
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)

    // 手势协程比重组活得久：回调用 rememberUpdatedState 取最新引用，pointerInput 的 key
    // 里就不需要放 lambda（否则每次重组都会重启手势，正在进行的拖动会被打断）。
    val onSelectState = rememberUpdatedState(onSelect)

    // 形状/常量缓存：底栏常驻，别每帧重新构造。
    val barShape: Shape = remember { GlassShapes.capsule }
    val pillShape: Shape = remember {
        RoundedCornerShape(LiquidGlassNavDimens.barHeight / 2 - LiquidGlassNavDimens.contentInset)
    }
    val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val grabTolerancePx = with(density) { GRAB_TOLERANCE.toPx() }
    val detentRadiusPx = with(density) { DETENT_RADIUS.toPx() }
    val pillSpring = remember {
        spring<Float>(dampingRatio = PILL_DAMPING, stiffness = PILL_STIFFNESS)
    }
    val jellySpring = remember {
        spring<Float>(dampingRatio = JELLY_DAMPING, stiffness = JELLY_STIFFNESS)
    }

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
                // N 等分：宽度只由栏宽和 items.size 决定。
                val itemWidthPx = constraints.maxWidth.toFloat() / items.size
                val maxOffsetPx = (constraints.maxWidth - itemWidthPx).coerceAtLeast(0f)
                val pillWidth = with(density) { itemWidthPx.toDp() }

                // ── 胶囊状态（本文件私有，外部不可见）────────────────────────
                // pillLeft: 胶囊左边缘（px）。拖动时直接写 → 只失效 layout，不重组页面。
                // jelly:     -1..1 的有符号归一化速度，只在 graphicsLayer 里读 → 只失效绘制层。
                var pillLeft by remember { mutableFloatStateOf(itemWidthPx * safeIndex) }
                var jelly by remember { mutableFloatStateOf(0f) }
                var dragging by remember { mutableStateOf(false) }
                // 手势协程写、帧循环消费：本帧内手指横向移动了多少 px（普通数组，不参与快照）。
                val frameDrag = remember { floatArrayOf(0f) }

                // 位置动画的唯一出口：点击（safeIndex 变）/ 松手（dragging 变）/ 换布局都会
                // 重启本 effect，restart 同时取消上一次动画 —— 天然保证同一时刻只有一个动画。
                // dragging=true 时直接返回：拖动跟手，不许动画抢位置。
                LaunchedEffect(safeIndex, itemWidthPx, dragging) {
                    if (dragging) return@LaunchedEffect
                    animate(
                        initialValue = pillLeft,
                        targetValue = itemWidthPx * safeIndex,
                        animationSpec = pillSpring
                    ) { value, _ -> pillLeft = value }
                }

                // 果冻形变：拖动中逐帧把「这一帧的位移」换算成速度 → 目标形变；手指停住时
                // frameDrag 归零，目标自动回 0。松手后用 spring 回收（轻微过冲＝Q 弹）。
                LaunchedEffect(dragging) {
                    if (!dragging) {
                        if (jelly != 0f) {
                            animate(jelly, 0f, animationSpec = jellySpring) { value, _ ->
                                jelly = value
                            }
                        }
                        return@LaunchedEffect
                    }
                    var lastFrameNanos = 0L
                    while (true) {
                        withFrameNanos { now ->
                            val dtSec = if (lastFrameNanos == 0L) {
                                1f / 60f
                            } else {
                                ((now - lastFrameNanos) / 1_000_000_000f)
                                    .coerceIn(MIN_FRAME_SEC, MAX_FRAME_SEC)
                            }
                            lastFrameNanos = now
                            val pxPerSec = frameDrag[0] / dtSec
                            frameDrag[0] = 0f
                            val target = (pxPerSec / JELLY_FULL_SPEED_PX_PER_SEC).coerceIn(-1f, 1f)
                            jelly += (target - jelly) * JELLY_CHASE
                        }
                    }
                }

                // ── 拖动：挂在栏级（pill 与 Row 的共同祖先）──────────────────
                // 为什么不挂在胶囊上：胶囊画在 tab 内容之下，事件先被上层 tab 的 clickable
                // 吃掉，胶囊自己的 pointerInput 根本收不到。挂在祖先节点上，Main pass 里
                // 子节点先拿到事件（因此短按=点击照旧），越过 slop 后我们 consume，
                // 子 clickable 看到「事件已被消费」自动取消这次点击 → 点击与拖拽互斥。
                val dragModifier = Modifier.pointerInput(itemWidthPx, maxOffsetPx, grabTolerancePx, detentRadiusPx) {
                    var grabbed = false
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            // 只接管「按住胶囊」的手势。onDragStart 的位置 ≈ 按下点 + 已越过的
                            // slop（8dp），所以容差取 slop + 2dp，边缘往外拖也能抓住。
                            val left = pillLeft
                            val tolerance = viewConfiguration.touchSlop + grabTolerancePx
                            grabbed = start.x >= left - tolerance &&
                                start.x <= left + itemWidthPx + tolerance
                            if (grabbed) {
                                dragging = true // 重启位置动画 effect → 取消进行中的 spring
                                frameDrag[0] = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            if (grabbed) {
                                // 松手才提交导航：最近 tab = 胶囊中心所在格（中心已夹在
                                // [0.5w, (n-0.5)w] 内，所以 floor 就是最近中心）。
                                val nearest = ((pillLeft + itemWidthPx / 2f) / itemWidthPx)
                                    .toInt()
                                    .coerceIn(0, items.lastIndex)
                                dragging = false // 交给上面的 effect 做 spring 吸附
                                frameDrag[0] = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectState.value(nearest)
                            }
                            grabbed = false
                        },
                        onDragCancel = {
                            // 被系统打断（多指、切后台）：回到当前选中项，不提交导航。
                            dragging = false
                            grabbed = false
                            frameDrag[0] = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!grabbed) return@detectHorizontalDragGestures
                            // consume = 声明这次拖动归胶囊；子 tab 的 clickable 会取消点击。
                            change.consume()
                            frameDrag[0] += dragAmount
                            // 经过 tab 中心时的轻微阻尼（不是硬吸附）：越靠中心拖动量打折越多。
                            val center = pillLeft + itemWidthPx / 2f
                            val slotCenter =
                                (center / itemWidthPx).toInt() * itemWidthPx + itemWidthPx / 2f
                            val distance = abs(center - slotCenter)
                            val factor = if (distance < detentRadiusPx && detentRadiusPx > 0f) {
                                val near = 1f - distance / detentRadiusPx
                                1f - (1f - DETENT_MIN_SCALE) * near
                            } else {
                                1f
                            }
                            // 跟手：直接写状态、夹在 0..maxOffset 内，绝不越界。
                            pillLeft = (pillLeft + dragAmount * factor).coerceIn(0f, maxOffsetPx)
                        }
                    )
                }

                Box(Modifier.fillMaxSize().then(dragModifier)) {
                    // 选中胶囊先声明 = 画在 tab 内容之下。位置走 offset{}（layout 阶段读 state），
                    // 形变走 graphicsLayer（draw 阶段读 state）：两者都不触发重组。
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(pillLeft.roundToInt(), 0) }
                            .width(pillWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // 真实 scale 形变：scaleX ∈ [0.88, 1.12]，scaleY ∈ [0.94, 1.0]。
                                // 形变不吃 Path / 不改胶囊宽度，绘制缓存（brush）完全不用重建。
                                scaleX = 1f + JELLY_SCALE_X * jelly
                                scaleY = 1f - JELLY_SCALE_Y * abs(jelly)
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                    ) {
                        SelectedLiquidPill(
                            shape = pillShape,
                            universal = universal,
                            accent = glass.accent
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
}

/**
 * 单个 tab：24dp 图标（选中填充 / 未选中线性，Crossfade 交接）+ 12sp 文字。
 * 整格都是点击热区（≈80dp 高 × 1/N 宽），满足 44dp 触控下限。
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
 * 独立的选中玻璃胶囊 —— **淡蓝玻璃**（不是深蓝、不是霓虹）。
 *
 * 玻璃模式自下而上 6 层，全部在一次 Canvas 里画完，无静态图片、无逐帧分配：
 *  1. 淡蓝底     —— #DCEEFF 竖向渐变（浅色 68%→54%，深色 30%→20%）
 *  2. 白 sheen   —— 白 55%→透明 竖向渐变，把上沿提亮，形成「白 → 淡蓝」的柔和过渡
 *  3. 椭圆高光   —— 顶部椭圆 specular（白 28%→透明径向），模拟光在曲面上的聚集
 *  4. 蓝色光晕   —— 中心透明、边缘 accent 9% 的径向渐变（外侧极轻蓝光，不外溢成霓虹）
 *  5. 折射弧     —— 上半圈横向渐变描边（中间亮、两端透），1.5dp
 *  6. 描边       —— 1dp 竖向渐变：上沿白 95% → 中部 18% → 下沿极淡黑（厚度感）
 * 外层再叠一层 5dp 柔和阴影（染色 accent，不是重黑），保证它浮在外层玻璃之上。
 *
 * Universal：淡蓝半透明实色 + 1dp 白描边 + 轻阴影，无高光、无折射、无模糊；
 * 手势与形变完全一致（形变在调用处的 graphicsLayer 上，与材质无关）。
 */
@Composable
private fun SelectedLiquidPill(
    shape: Shape,
    universal: Boolean,
    accent: Color
) {
    if (universal) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 3.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = accent.copy(alpha = 0.16f),
                    spotColor = accent.copy(alpha = 0.20f)
                )
                .clip(shape)
                .background(PillBlue.copy(alpha = 0.92f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.90f), shape)
        )
        return
    }

    val colors = glassColors()
    val paint = remember(colors.isDark, accent) { LiquidPillPaint(colors.isDark, accent) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                // 柔和阴影：只染一点 accent，避免底栏下面压出一块黑。
                elevation = 5.dp,
                shape = shape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.18f),
                spotColor = accent.copy(alpha = 0.24f)
            )
            .clip(shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            paint.ensure(w, h)

            val fullRadius = CornerRadius(h / 2f)

            // 1) 淡蓝底
            drawRoundRect(brush = paint.base, cornerRadius = fullRadius)
            // 2) 白 sheen（上亮下淡）
            drawRoundRect(brush = paint.sheen, cornerRadius = fullRadius)
            // 3) 顶部椭圆高光：椭圆形柔光，横向铺在胶囊上半部
            drawOval(
                brush = paint.specular,
                topLeft = Offset(w * 0.12f, h * 0.02f),
                size = Size(w * 0.76f, h * 0.52f)
            )
            // 4) 外侧极轻蓝色光晕（径向，只吃边缘）
            drawRoundRect(brush = paint.bloom, cornerRadius = fullRadius)

            // 5) 折射弧：仅上半圈，中间亮两端透
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

            // 6) 1dp 描边：上沿亮线、下沿极淡暗线
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
 * 与尺寸相关的径向画笔只在胶囊尺寸变化时重建（换 tab 尺寸不变 → 一次都不重建；
 * 拖动只改 offset / graphicsLayer scale，也不重建），尺寸无关的线性画笔直接常量持有
 * —— 拖动这条高频路径上绘制阶段零分配。
 */
private class LiquidPillPaint(
    private val dark: Boolean,
    private val accent: Color
) {
    private var lastW = Float.NaN
    private var lastH = Float.NaN

    /** 1) 淡蓝底：#DCEEFF 半透明（浅色 68%→54%，深色 30%→20%）。 */
    val base: Brush = Brush.verticalGradient(
        listOf(
            PillBlue.copy(alpha = if (dark) 0.30f else 0.68f),
            PillBlue.copy(alpha = if (dark) 0.20f else 0.54f)
        )
    )

    /** 2) 白 sheen：上沿提亮，55% 处收干，形成「白 → 淡蓝」的柔和竖向渐变。 */
    val sheen: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
        0.55f to Color.Transparent,
        1f to Color.Transparent
    )

    /** 6) 1dp 描边：上沿白高光 → 中部极淡 → 下沿极淡暗线（厚度感）。 */
    val rim: Brush = if (dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.60f),
            0.38f to Color.White.copy(alpha = 0.12f),
            0.74f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f)
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.38f to Color.White.copy(alpha = 0.18f),
            0.74f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.07f)
        )
    }

    /** 5) 折射弧：横向渐变，中间亮、两端透。 */
    val specularArc: Brush = Brush.horizontalGradient(
        listOf(
            Color.Transparent,
            Color.White.copy(alpha = if (dark) 0.30f else 0.80f),
            Color.White.copy(alpha = if (dark) 0.30f else 0.80f),
            Color.Transparent
        )
    )

    private var specularBrush: Brush =
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent))

    private var bloomBrush: Brush =
        Brush.radialGradient(listOf(Color.Transparent, accent.copy(alpha = 0.09f)))

    /** 3) 椭圆 specular 高光（画笔按椭圆自身尺寸创建）。 */
    val specular: Brush get() = specularBrush

    /** 4) 边缘蓝光晕。 */
    val bloom: Brush get() = bloomBrush

    /** 尺寸变化时才重建径向画笔。 */
    fun ensure(w: Float, h: Float) {
        if (w == lastW && h == lastH) return
        lastW = w
        lastH = h
        // 椭圆高光：垂直方向的柔和径向（中心偏上，半径 = 半宽 → 横向铺开、纵向收敛）。
        val ovalW = w * 0.76f
        val ovalH = h * 0.52f
        specularBrush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = if (dark) 0.16f else 0.28f),
                Color.White.copy(alpha = if (dark) 0.04f else 0.08f),
                Color.Transparent
            ),
            center = Offset(ovalW / 2f, ovalH * 0.30f),
            radius = ovalW * 0.52f
        )
        // 蓝光晕：中心透明，越靠边缘越亮（0 → 9%），只染边缘一圈，不做霓虹。
        bloomBrush = Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to accent.copy(alpha = if (dark) 0.12f else 0.09f),
            center = Offset(w / 2f, h / 2f),
            radius = h * 0.95f
        )
    }
}
