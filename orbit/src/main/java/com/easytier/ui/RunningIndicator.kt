package com.easytier.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.theme.currentColors

/**
 * 配置页顶部的「运行中 / 启动中」条：三点脉冲动画 + 一行灰色提示。
 *
 * 动画是**自实现**的，没有引入任何第三方依赖（纯 androidx.compose.animation）：
 *  - 三点错峰 pulse（scale + alpha）的写法参考 razaghimahdi/Compose-Loading-Dots
 *    (Apache-2.0) https://github.com/razaghimahdi/Compose-Loading-Dots —— 每个点用
 *    `infiniteRepeatable(tween, RepeatMode.Reverse)` + `StartOffset(index * step)`
 *    错开相位，形成「一波一波」的效果；
 *  - 错峰公式与 Touchlab 的 BallPulseSyncIndicator 一致
 *    https://touchlab.co/jetpack-compose-animations-state-change
 *
 * 颜色：运行中走 accent，启动中走 warning，与状态卡的配色语义保持一致。
 */
@Composable
fun RunningIndicator(
    running: Boolean,
    langZh: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    val tint = if (running) colors.accent else colors.warning

    val transition = rememberInfiniteTransition(label = "runningPulse")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (running) colors.accentContainer else colors.warningBg)
            .border(0.5.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                // 每个点 0.45 → 1.0 反复呼吸，起始相位依次错开 150ms；
                // 周期 620ms ≈ 每秒 1.6 个脉冲，和「连接中」的心理节奏接近。
                val pulse by transition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 620, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 150)
                    ),
                    label = "pulseDot$index"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(pulse)
                        .alpha(pulse)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (running) {
                    T("正在运行，停止服务后可重新编辑", "Running — stop the service to edit", langZh)
                } else {
                    T("启动中，停止服务后可重新编辑", "Starting — stop the service to edit", langZh)
                },
                color = colors.textTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
