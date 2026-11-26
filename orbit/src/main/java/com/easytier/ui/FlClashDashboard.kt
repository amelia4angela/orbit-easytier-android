package com.easytier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** HarmonyOS-style emphasized easing: fast start, long gentle settle. */
val HarmonyEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// ═══════════════════════════════════════════════════
//  FlClash-style dashboard components
// ═══════════════════════════════════════════════════

/**
 * Organic wavy "blob" path (FlClash connection button shape).
 * Polar radius modulated by a cosine lobe function → flower/seal silhouette.
 */
fun blobPath(size: Float, petals: Int = 8): Path {
    val path = Path()
    val cx = size / 2f
    val cy = size / 2f
    val outer = size / 2f
    val inner = outer * 0.80f
    val n = 120
    for (i in 0..n) {
        val t = i.toFloat() / n
        val angle = t * 2.0 * Math.PI
        val lobe = (0.5 + 0.5 * cos(petals * angle)).pow(1.6)
        val r = inner + (outer - inner) * lobe
        val x = (cx + r * cos(angle)).toFloat()
        val y = (cy + r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * FlClash Status card: status pill + big IP/hostname + run time on the left,
 * organic blob connect button on the right.
 */
@Composable
fun FlClashStatusCard(
    state: ConnectionButtonState,
    onClick: () -> Unit,
    nodeLabel: String,
    nodeSub: String,
    uptime: String,
    peerCount: Int,
    latency: String,
    modifier: Modifier = Modifier,
    langZh: Boolean = true
) {
    val colors = currentColors()
    val isConnected = state.status == ConnectionStatus.RUNNING || state.status == ConnectionStatus.NO_TUN
    val isStarting = state.status == ConnectionStatus.STARTING
    val isError = state.status == ConnectionStatus.ERROR || state.status == ConnectionStatus.REVOKED

    var pressed by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(150), label = "blobScale"
    )

    // Pill text + color
    val pillText = when {
        isStarting -> T("启动中", "Starting", langZh)
        isConnected -> T("运行中", "Running", langZh)
        isError -> T("错误", "Error", langZh)
        else -> T("已停止", "Stopped", langZh)
    }
    val pillColor = when {
        isConnected -> colors.statusGood
        isStarting -> colors.statusWarn
        isError -> colors.statusBad
        else -> colors.textTertiary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .flGlassCard(colors, 20.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: status + node info ──
            Column(modifier = Modifier.weight(1f)) {
                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pillColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(pillColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pillText,
                            color = pillColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Big node label (IP / hostname)
                Text(
                    text = nodeLabel.ifEmpty { "—" },
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (nodeSub.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = nodeSub,
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Meta row: run time / peers / latency
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uptime.isNotEmpty()) {
                        MetaItem(label = T("运行", "Uptime", langZh), value = uptime, colors = colors)
                    }
                    MetaItem(label = T("节点", "Peers", langZh), value = "$peerCount", colors = colors)
                    if (latency.isNotEmpty()) {
                        MetaItem(label = T("延迟", "Ping", langZh), value = latency, colors = colors)
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── Right: organic blob connect button ──
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(btnScale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Transparent)
                    .clickable(
                        onClick = {
                            pressed = true
                            onClick()
                            pressed = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val s = size.minDimension
                    val path = blobPath(s)
                    val brush = if (isConnected) {
                        Brush.radialGradient(listOf(colors.statusBad, colors.statusBad.copy(alpha = 0.85f)))
                    } else {
                        Brush.radialGradient(listOf(colors.accentLight, colors.accent))
                    }
                    drawPath(path, brush)
                }
                Text(
                    text = if (isStarting) "⋯" else if (isConnected) "■" else "▶",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String, colors: com.easytier.ui.theme.AppColors) {
    Column {
        Text(label, color = colors.textTertiary, fontSize = 10.sp)
        Text(value, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Two side-by-side speed cards (up / down) with orange sparkline,
 * mirroring FlClash's "Network speed" section.
 */
@Composable
fun SpeedStatRow(
    rxSpeed: String,
    txSpeed: String,
    rxTotal: String,
    txTotal: String,
    history: List<Float>,
    modifier: Modifier = Modifier,
    langZh: Boolean = true
) {
    val colors = currentColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpeedStatCard(
            title = T("上传", "Upload", langZh),
            icon = "↑",
            speed = txSpeed,
            total = txTotal,
            history = history.map { -it },
            colors = colors,
            modifier = Modifier.weight(1f)
        )
        SpeedStatCard(
            title = T("下载", "Download", langZh),
            icon = "↓",
            speed = rxSpeed,
            total = rxTotal,
            history = history,
            colors = colors,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpeedStatCard(
    title: String,
    icon: String,
    speed: String,
    total: String,
    history: List<Float>,
    colors: com.easytier.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .flGlassCard(colors, 16.dp)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(title, color = colors.textTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                speed.ifEmpty { "—" },
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                total,
                color = colors.textTertiary,
                fontSize = 10.sp,
                maxLines = 1
            )
            // Sparkline
            if (history.size >= 2) {
                Spacer(Modifier.height(6.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    val maxV = history.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
                    val stepX = size.width / (history.size - 1)
                    val path = Path()
                    val fill = Path()
                    history.forEachIndexed { i, v ->
                        val x = i * stepX
                        val y = size.height * (1f - (kotlin.math.abs(v) / maxV) * 0.9f) - 1.dp.toPx()
                        if (i == 0) {
                            path.moveTo(x, y)
                            fill.moveTo(x, size.height)
                            fill.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(size.width, size.height)
                    fill.close()
                    drawPath(fill, colors.accent.copy(alpha = 0.12f))
                    drawPath(
                        path,
                        colors.accent,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/** Parse "1.2 MB/s" / "350 KB/s" style strings into a Float for sparklines. */
fun parseSpeedValue(s: String): Float {
    val num = s.split(" ").firstOrNull()?.toFloatOrNull() ?: return 0f
    val unit = s.split(" ").getOrElse(1) { "" }.uppercase()
    val mult = when {
        unit.startsWith("G") -> 1024f * 1024f
        unit.startsWith("M") -> 1024f
        unit.startsWith("K") -> 1f
        else -> 1f / 1024f
    }
    return num * mult
}


/** FlClash glass card: soft shadow + rounded surface + inner light hairline. */
fun Modifier.flGlassCard(
    colors: com.easytier.ui.theme.AppColors,
    radius: androidx.compose.ui.unit.Dp = 18.dp
): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    return this
        .then(Modifier.shadow(elevation = 6.dp, shape = shape, clip = false,
            ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f),
            spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f)))
        .clip(shape)
        .background(colors.surface)
        .border(0.5.dp, colors.glassHighlight, shape)
}


/**
 * FlClash-style elastic press: springy scale-down while pressed,
 * bouncy release, fires [onClick] on tap. Uses interactionSource
 * (no pointerInput needed).
 */
@Composable
fun Modifier.elasticPress(onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { int ->
            when (int) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release -> pressed = false
                is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    // 鸿蒙式按压：按下快速下陷，松开低阻尼 Q 弹回弹
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(0.90f, tween(70, easing = HarmonyEasing))
        } else {
            scale.animateTo(
                1f,
                spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    return this
        .scale(scale.value)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Node editor dialog (add / edit a single peer node).
 *
 * Always composed by the caller: [visible] drives an [AnimatedVisibility] panel
 * (Harmony-style scale+fade in/out) while an internal `mounted` flag keeps the
 * Dialog on screen until the exit animation finishes.
 */
@Composable
fun NodeEditDialog(
    visible: Boolean,
    initial: PeerNode? = null,
    langZh: Boolean = true,
    onSave: (PeerNode) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = currentColors()

    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            delay(320)
            mounted = false
        }
    }
    if (!mounted) return

    var protocol by remember(initial) { mutableStateOf(initial?.protocol ?: "tcp") }
    var address by remember(initial) { mutableStateOf(initial?.address ?: "") }
    var port by remember(initial) { mutableStateOf(initial?.port ?: "") }
    val canSave = address.isNotBlank() && port.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300, easing = HarmonyEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = HarmonyEasing)),
            exit = fadeOut(tween(300, easing = HarmonyEasing)) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(300, easing = HarmonyEasing))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .flGlassCard(colors, 20.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initial == null) T("添加节点", "Add Node", langZh)
                    else T("编辑节点", "Edit Node", langZh),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // ── Protocol: two segmented pills ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tcp", "udp").forEach { proto ->
                        val selected = protocol == proto
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) colors.accent else colors.inputBg)
                                .border(
                                    0.5.dp,
                                    if (selected) colors.accent else colors.inputBorder,
                                    RoundedCornerShape(50)
                                )
                                .clickable { protocol = proto }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = proto.uppercase(),
                                color = if (selected) Color.White else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                HmTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = T("地址", "Address", langZh),
                    placeholder = "1.2.3.4 或 [::1]",
                    colors = colors
                )

                HmTextField(
                    value = port,
                    onValueChange = { input -> if (input.all { it.isDigit() }) port = input },
                    label = T("端口", "Port", langZh),
                    placeholder = "11011",
                    keyboardType = KeyboardType.Number,
                    colors = colors
                )

                // ── Actions: delete (edit only) / cancel / save ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onDelete?.let { delete ->
                        Text(
                            text = T("删除", "Delete", langZh),
                            color = colors.dangerText,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { delete() }
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = T("取消", "Cancel", langZh),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (canSave) colors.accent else colors.accent.copy(alpha = 0.35f)
                            )
                            .clickable(enabled = canSave) {
                                onSave(PeerNode(protocol, address.trim(), port.trim()))
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = T("保存", "Save", langZh),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
