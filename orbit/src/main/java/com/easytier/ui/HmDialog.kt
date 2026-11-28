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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSize
import com.easytier.ui.glass.components.milkyGlass
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.delay

/**
 * Orbit 的统一弹窗外壳。
 *
 * 外观：乳白玻璃面板 —— [milkyGlass]（乳白填充深 10→5% / 浅 78→56%，与 ProxyLaunchCard
 * 同一个值）+ glassSurface 的顶部 specular + 0.5dp 描边 + 弹窗档 24dp 柔和阴影，
 * 20dp 圆角（GlassRadius.lg）+ 16sp SemiBold 标题 + 内容区。
 * 背景：Dialog 是独立窗口，既采样不到 GlassSky（没法做真实背景模糊），也自带一层平台
 * 暗化（窗口 dim），所以这里不再叠第二层 scrim，避免重复压暗。
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
    val glass = glassColors()
    // Dialog 是独立窗口，无法采样/模糊活动背景 → 只上材质不模糊（milkyGlass）。
    val panelShape = remember { glassShape(GlassRadius.lg) }
    val glassTint = if (glass.isDark) 1.15f else 1.0f

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
                    // 乳白玻璃面板：乳白填充 + 顶部 specular + 0.5dp 描边 + 24dp 阴影。
                    .milkyGlass(
                        radius = GlassRadius.lg,
                        shape = panelShape,
                        intensity = glassTint,
                        shadowElevation = 24.dp
                    )
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

/** 主按钮：accent 实心（叠玻璃高光/描边），圆角 12，白字 13sp SemiBold；[danger] = 危险操作（红底）。 */
@Composable
fun HmDialogPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = currentColors()
    val shape = remember { glassShape(GlassRadius.sm) }
    val container: androidx.compose.ui.graphics.Brush = when {
        !enabled -> androidx.compose.ui.graphics.SolidColor(colors.accent.copy(alpha = 0.35f))
        danger -> androidx.compose.ui.graphics.SolidColor(colors.dangerText)
        else -> colors.accentGradient
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            // 品牌色/危险红底不动，只加一层玻璃高光 + 描边
            .glassSurface(radius = GlassRadius.sm, shape = shape, intensity = 0.45f)
            .clickable(enabled = enabled) { onClick() }
            // 无障碍最小触控高度（GlassSize.buttonHeight = 46dp ≥ 44dp）
            .heightIn(min = GlassSize.buttonHeight)
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

/** 次按钮：玻璃底 + 描边，圆角 12。 */
@Composable
fun HmDialogSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = currentColors()
    val shape = remember { glassShape(GlassRadius.sm) }
    Box(
        modifier = modifier
            // 浅色下 inputBg 是浅灰、深色下是 6% 白 —— 先铺它保证按钮与面板有对比，
            // 再叠玻璃薄雾/顶部高光/描边（不模糊，弹窗里没有可采样的背景）。
            .background(colors.inputBg, shape)
            .glassSurface(radius = GlassRadius.sm, shape = shape, intensity = 1.15f)
            .clickable { onClick() }
            .heightIn(min = GlassSize.buttonHeight)
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
    val shape = remember { glassShape(GlassRadius.sm) }
    Box(
        modifier = modifier
            .clip(shape)
            .clickable { onClick() }
            .heightIn(min = 44.dp)
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
