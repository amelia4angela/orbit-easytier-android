package com.easytier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.delay

/** 下拉弹层的一项：[label] 给用户看，[value] 写回配置，[hint] 可选一行说明。 */
data class HmDropdownOption(
    val label: String,
    val value: String,
    val hint: String? = null
)

/**
 * 统一下拉选择字段（语言 / 内存限制 / 加密算法 / 压缩算法共用）。
 *
 * 外观与 [HmTextField] / [HmToggle] 同一套语汇：左侧标签（+ 当前值），右侧一个由
 * 本组件自绘的三角图标（[DropdownTriangle]，展开时旋转 90°）。
 *
 * 三角为什么不用 `Text("▶")`：U+25B6 属于「可 emoji 表现」的字符，Android 会用
 * 彩色 emoji 字体渲染它，`Text(color = …)` 会被忽略——深色主题下就成了一个深灰
 * 三角，看起来像图标丢了。改成 Canvas 画 Path 后颜色、大小、旋转都完全可控。
 *
 * 点击在**行右侧**弹出锚定的小弹层（[Popup]，不遮全屏）：右边缘与行右端对齐，
 * 宽度按内容自适应（160–220dp，不再与整行同宽）；16dp 圆角 + [flGlassCard] 玻璃底
 * + 细边框，单选高亮，点选即生效并收起，点外部关闭。入场 220ms：从上往下 8dp +
 * fade，全部走 [HarmonyEasing]；下方空间不够时向上翻，水平方向夹在窗口内。
 *
 * [card] = true 按设置列表的卡片样式渲染（surface 底 + 12dp 圆角 + 16dp 内边距），
 * 用于替代 [SettingsNavRow]；false 按输入框样式描边渲染，用于表单内部。
 */
@Composable
fun HmDropdownField(
    label: String,
    options: List<HmDropdownOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: AppColors = currentColors(),
    hint: String? = null,
    card: Boolean = false,
    fallbackValue: String = "",
    enabled: Boolean = LocalConfigEditable.current
) {
    var expanded by remember { mutableStateOf(false) }
    // Popup stays mounted until the exit animation finishes.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            mounted = true
        } else if (mounted) {
            delay(200)
            mounted = false
        }
    }

    val density = LocalDensity.current
    val gapPx = with(density) { 6.dp.roundToPx() }
    val slidePx = with(density) { 8.dp.roundToPx() }

    val currentLabel = options.getOrNull(selectedIndex)?.label
        ?: fallbackValue.ifEmpty { options.firstOrNull()?.label.orEmpty() }

    val triangleAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220, easing = HarmonyEasing),
        label = "dropdownTriangle"
    )

    val positionProvider = remember(gapPx) { HmDropdownPositionProvider(gapPx) }

    Column(modifier = modifier) {
        if (!card && label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (card) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(0.5.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(
                        horizontal = if (card) 16.dp else 12.dp,
                        vertical = if (card) 16.dp else 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (card) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (hint != null) {
                            Text(
                                text = hint,
                                color = colors.textTertiary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = currentLabel,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Text(
                        text = currentLabel.ifEmpty { "—" },
                        color = if (enabled) colors.textPrimary
                        else colors.textTertiary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                DropdownTriangle(
                    color = if (enabled) colors.textTertiary else colors.textTertiary.copy(alpha = 0.5f),
                    angle = triangleAngle
                )
            }

            if (mounted) {
                Popup(
                    popupPositionProvider = positionProvider,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(220, easing = HarmonyEasing)) +
                                slideInVertically(tween(220, easing = HarmonyEasing)) { -slidePx },
                        exit = fadeOut(tween(160, easing = HarmonyEasing)) +
                                slideOutVertically(tween(160, easing = HarmonyEasing)) { -slidePx }
                    ) {
                        Column(
                            modifier = Modifier
                                // 小弹层：按内容自适应，下限 160dp、上限 220dp，
                                // 不再跟着整行宽度走；位置由下方 PositionProvider 右对齐。
                                .widthIn(min = 160.dp, max = 220.dp)
                                .flGlassCard(colors, 16.dp)
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            options.forEachIndexed { index, option ->
                                val selected = index == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) colors.accentContainer else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .clickable {
                                            expanded = false
                                            onSelect(index)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.label,
                                            color = if (selected) colors.accentLight else colors.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (option.hint != null) {
                                            Text(
                                                text = option.hint,
                                                color = colors.textTertiary,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    // 单选圆点：选中 = accent 描边 + 实心内点
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                if (selected) colors.accent else colors.inputBorder,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!card && hint != null) {
            Text(
                text = hint,
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 2.dp, top = 3.dp)
            )
        }
    }
}

/**
 * 弹层锚定在行下方 6dp 处，**右边缘对齐行的右端**；底部空间不够（会跑出屏幕）
 * 时向上翻转，水平方向再夹一次窗口边界。
 *
 * 宽度不再等于行宽，所以 x 用 `anchorBounds.right - popupContentSize.width` 现算，
 * 这样「按内容自适应宽度」的弹层也能贴住行右侧。
 */
private class HmDropdownPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val below = anchorBounds.bottom + gapPx
        val fitsBelow = below + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val rightAligned = anchorBounds.right - popupContentSize.width
        val x = rightAligned.coerceIn(0, maxX)
        return IntOffset(x, y)
    }
}

/**
 * 下拉三角图标：Canvas + Path 自绘。
 *
 * 不用 `Text("▶")` 的原因：U+25B6 是「可 emoji 表现」字符，Android 会走彩色 emoji
 * 字体渲染，`Text(color = …)` 被忽略（深色主题下等于看不见）。自绘后颜色/尺寸/旋转
 * 完全由本组件控制，四个下拉（语言 / 内存 / 加密 / 压缩）外观统一。
 */
@Composable
private fun DropdownTriangle(
    color: Color,
    angle: Float,
    size: androidx.compose.ui.unit.Dp = 8.dp
) {
    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
    ) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, h / 2f)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
    }
}
