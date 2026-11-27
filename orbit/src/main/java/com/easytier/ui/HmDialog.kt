package com.easytier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.delay

/**
 * Orbit 的统一弹窗外壳。
 *
 * 外观：20dp 圆角 + [flGlassCard] 玻璃底 + 16sp SemiBold 标题 + 内容区。
 * 动效：与 [NodeEditDialog] 一致 —— fadeIn + scaleIn(0.96) 240ms 线性进出，
 * 退出时 200ms；内部 `mounted` 标记让 Dialog 留到退出动画播完。
 *
 * 逻辑（谁该显示、按钮做什么、文案）仍由各调用方负责，这里只统一外观与动效。
 */
@Composable
fun HmDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    maxWidth: Dp = 320.dp,
    horizontalPadding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = currentColors()

    // Keep the Dialog mounted until the exit animation finishes.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            delay(240)
            mounted = false
        }
    }
    if (!mounted) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240, easing = LinearEasing)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(240, easing = LinearEasing)),
            exit = fadeOut(tween(200, easing = LinearEasing)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(200, easing = LinearEasing))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .flGlassCard(colors, 20.dp)
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
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

/** 弹窗底部按钮行：两个按钮各占一半，间距 8dp。 */
@Composable
fun HmDialogActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** 主按钮：accent 实心，圆角 12，白字 13sp SemiBold。 */
@Composable
fun HmDialogPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = currentColors()
    val container = if (enabled) colors.accentGradient
    else androidx.compose.ui.graphics.SolidColor(colors.accent.copy(alpha = 0.35f))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 次按钮：描边 + 透明底，圆角 12。 */
@Composable
fun HmDialogSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = currentColors()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.inputBg)
            .border(0.5.dp, colors.inputBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.textSecondary, fontSize = 13.sp)
    }
}

/** 第三类操作（取消 / 关闭 / 删除）：纯文字，不抢主次按钮的视觉重量。 */
@Composable
fun HmDialogTextButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: () -> Unit
) {
    val colors = currentColors()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color ?: colors.textTertiary,
            fontSize = 13.sp,
            fontWeight = if (color != null) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 单选弹窗的一项：标题 + 可选说明。 */
data class HmDialogChoice(val label: String, val hint: String? = null)

/**
 * 单选弹窗：每个选项一行（标题 + 说明 + 右侧单选圆点），选中行 accent 高亮。
 * 用于「内存限制」这类需要逐项解释的三选一。
 */
@Composable
fun HmChoiceDialog(
    visible: Boolean,
    title: String,
    options: List<HmDialogChoice>,
    selectedIndex: Int,
    cancelLabel: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = currentColors()
    HmDialog(visible = visible, onDismiss = onDismiss, title = title) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.accentContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        color = if (selected) colors.accentLight else colors.textSecondary,
                        fontSize = 14.sp,
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
                // 单选圆点：选中 = accent 描边 + 内圆点（半径随动画长出来）
                val dot by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(180, easing = HarmonyEasing),
                    label = "choiceDot"
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            if (selected) colors.accent else colors.inputBorder,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (dot > 0f) {
                        Box(
                            modifier = Modifier
                                .size((9 * dot).dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                }
            }
        }
        HmDialogActionRow {
            HmDialogSecondaryButton(
                text = cancelLabel,
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
        }
    }
}
