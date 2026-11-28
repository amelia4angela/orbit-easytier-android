package com.easytier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassShapes
import com.easytier.ui.glass.components.glassPress
import com.easytier.ui.glass.components.milkyGlass
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.*

enum class ConnectionStatus {
    READY, RUNNING, NO_TUN, STOPPED, STARTING, ERROR, REVOKED
}

data class ConnectionButtonState(
    val status: ConnectionStatus = ConnectionStatus.READY,
    val label: String = "启动 VPN",
    val subLabel: String = "就绪",
    val protectionLevel: Int = 0
)

// ═══════════════════════════════════════════════════
//  ControlFAB — minimalist floating action button
// ═══════════════════════════════════════════════════

@Composable
fun ControlFAB(
    isConnected: Boolean,
    isStarting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    // 玻璃圆钮：先铺品牌 / 危险渐变打底，再叠 glassSurface 的高光 + 0.5dp 描边 ——
    // 与「应用并启动」CTA、GlassButton 同一手法。按压交回 glassPress（GlassMotion spring
    // 0.955），取代原来 150ms 的 0.85 scale tween。
    val fabShape = GlassShapes.capsule
    // 渐变 remembered：状态切换才重建，避免每帧重新分配 Brush
    val fabBrush = remember(isConnected, colors) {
        if (isConnected) {
            Brush.radialGradient(listOf(colors.statusBad, colors.statusBad.copy(alpha = 0.8f)))
        } else {
            Brush.radialGradient(listOf(colors.accentLight, colors.accent.copy(alpha = 0.9f)))
        }
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .glassPress(onClick = onClick)
            .clip(fabShape)
            .background(fabBrush, fabShape)
            .glassSurface(radius = 28.dp, shape = fabShape, intensity = 0.5f, paintFill = false),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isStarting) "⋯" else if (isConnected) "■" else "▶",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════
//  ConnectionBanner — minimalist status banner
// ═══════════════════════════════════════════════════

@Composable
fun ConnectionBanner(
    state: ConnectionButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    peerCount: Int = 0,
    uptime: String = "",
    rxSpeed: String = "",
    txSpeed: String = "",
    langZh: Boolean = true
) {
    val colors = currentColors()
    val glass = glassColors()
    val isConnected = state.status == ConnectionStatus.RUNNING || state.status == ConnectionStatus.NO_TUN
    val isStarting = state.status == ConnectionStatus.STARTING
    val isRevoked = state.status == ConnectionStatus.REVOKED

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 乳白玻璃横幅（卡片档 18dp 柔和阴影）。状态语义不再靠整块底色渐变，
            // 而是由左侧状态圆点 + 标题字色承载（与原实现同一套状态色）。
            .milkyGlass(
                radius = GlassRadius.lg,
                intensity = if (glass.isDark) 1.15f else 1.0f,
                shadowElevation = 18.dp
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status indicator — minimalist filled/empty circle
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected -> colors.statusGood
                            isStarting -> colors.statusWarn
                            isRevoked || state.status == ConnectionStatus.ERROR -> colors.statusBad
                            else -> colors.textTertiary.copy(alpha = 0.4f)
                        }
                    ),
            )

            Spacer(Modifier.width(14.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                if (isConnected) {
                    Text(
                        text = T("运行中", "Running", langZh),
                        color = colors.statusGood,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uptime.isNotEmpty())
                            Text(uptime, color = colors.textTertiary, fontSize = 12.sp)
                        if (rxSpeed.isNotEmpty())
                            Text("↓ $rxSpeed", color = colors.textTertiary, fontSize = 11.sp)
                        if (txSpeed.isNotEmpty())
                            Text("↑ $txSpeed", color = colors.textTertiary, fontSize = 11.sp)
                        if (peerCount > 0)
                            Text("◉ $peerCount", color = colors.textTertiary, fontSize = 11.sp)
                    }
                } else if (isStarting) {
                    Text(
                        text = T("正在启动…", "Starting…", langZh),
                        color = colors.statusWarn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(T("请稍候", "Please wait", langZh), color = colors.textTertiary, fontSize = 12.sp)
                } else if (state.status == ConnectionStatus.ERROR) {
                    Text(
                        text = T("连接错误", "Connection Error", langZh),
                        color = colors.statusBad,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(state.subLabel, color = colors.textTertiary, fontSize = 12.sp)
                } else if (isRevoked) {
                    Text(
                        text = T("VPN 被中断", "VPN Interrupted", langZh),
                        color = colors.statusBad,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = T("点击重新连接", "Tap to reconnect", langZh),
                        color = colors.textTertiary,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = T("已停止", "Stopped", langZh),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = T("点击启动", "Tap to start", langZh),
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

