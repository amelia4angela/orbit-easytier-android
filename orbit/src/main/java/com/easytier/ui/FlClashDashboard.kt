package com.easytier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.easytier.ui.theme.LocalIsDarkTheme
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
 * Gear silhouette: toothed crown (outer radius) with root valleys, drawn as one
 * closed polygon. Sampled from polar coordinates so it can be split in halves
 * for the "cracked" failure state.
 */
fun gearPath(size: Float, teeth: Int = 8): Path {
    val path = Path()
    val c = size / 2f
    val outer = c * 0.98f
    val root = c * 0.72f
    val step = (2.0 * Math.PI / teeth).toFloat()
    var first = true
    for (i in 0 until teeth) {
        val a = i * step - (Math.PI / 2.0).toFloat()
        // valley → tooth rise → crown → tooth fall → valley
        val points = listOf(
            (a + step * 0.03f) to root,
            (a + step * 0.16f) to outer,
            (a + step * 0.50f) to outer,
            (a + step * 0.63f) to root,
            (a + step * 0.97f) to root
        )
        for ((ang, r) in points) {
            val x = c + r * cos(ang)
            val y = c + r * sin(ang)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
    }
    path.close()
    return path
}

/**
 * Status gear (22dp by default), hand-drawn so it can crack apart:
 *  - STARTING          → green, spins forever (~2s / turn)
 *  - ERROR / REVOKED   → red, split in two halves pushed apart + tilted
 *  - RUNNING / NO_TUN  → green, static
 *  - otherwise         → grey, static
 */
@Composable
fun StatusGear(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    holeTint: Color? = null
) {
    val colors = currentColors()
    val isStarting = status == ConnectionStatus.STARTING
    val isBroken = status == ConnectionStatus.ERROR || status == ConnectionStatus.REVOKED
    val isRunning = status == ConnectionStatus.RUNNING || status == ConnectionStatus.NO_TUN

    val gearColor = when {
        isBroken -> colors.statusBad
        isStarting || isRunning -> colors.statusGood
        else -> colors.textTertiary
    }

    // Spin only while STARTING — a static gear runs no animation at all
    // (the infinite transition is created inside the isStarting branch).
    val angle = if (isStarting) {
        val spin = rememberInfiniteTransition(label = "gearSpin")
        spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gearAngle"
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val gear = gearPath(s)
        val holeR = s * 0.15f
        val center = Offset(s / 2f, s / 2f)
        // Recessed centre hole. Defaults to a translucent dark disc so the gear
        // reads correctly on any card background (the status card is a gradient,
        // not a flat surface).
        val holeColor = holeTint ?: Color.Black.copy(alpha = 0.28f)

        // Full gear: toothed crown + punched center hole
        fun paintGear(rotation: Float) {
            if (rotation == 0f) {
                drawPath(gear, color = gearColor)
                drawCircle(color = holeColor, radius = holeR, center = center)
            } else {
                rotate(degrees = rotation, pivot = center) {
                    drawPath(gear, color = gearColor)
                    drawCircle(color = holeColor, radius = holeR, center = center)
                }
            }
        }

        when {
            isBroken -> {
                // Two halves, each nudged outward and tilted a few degrees.
                val dx = 1.5.dp.toPx()
                val dy = 0.5.dp.toPx()
                clipRect(left = 0f, top = 0f, right = s / 2f, bottom = s) {
                    rotate(degrees = -8f, pivot = center) {
                        translate(left = -dx, top = -dy) { paintGear(0f) }
                    }
                }
                clipRect(left = s / 2f, top = 0f, right = s, bottom = s) {
                    rotate(degrees = 8f, pivot = center) {
                        translate(left = dx, top = dy) { paintGear(0f) }
                    }
                }
            }
            isStarting -> paintGear(angle)
            else -> paintGear(0f)
        }
    }
}

/**
 * FlClash Status card: status gear + pill + big IP/hostname + sub info on the
 * left, organic blob connect button on the right, and a translucent stat strip
 * (down / up / peers / uptime) pinned to the bottom.
 *
 * The whole card is painted with a status-coloured top-left → bottom-right
 * gradient (green while starting, blue while running, red on error, neutral
 * grey when stopped) layered over the theme surface, so it keeps working in
 * both light and dark themes.
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
    langZh: Boolean = true,
    rxSpeed: String = "",
    txSpeed: String = ""
) {
    val colors = currentColors()
    val isDark = LocalIsDarkTheme.current
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

    // ── Status gradient: two palette colours, mixed into the surface so the
    // card stays readable in both themes (accent = where light comes from).
    val statusBase = when {
        isError -> colors.statusBad
        isStarting -> colors.statusGood
        isConnected -> colors.accent
        else -> colors.textTertiary
    }
    val statusAccent = when {
        isError -> lerp(colors.statusBad, Color.White, 0.30f)
        isStarting -> lerp(colors.statusGood, Color.White, 0.30f)
        isConnected -> colors.accentLight
        else -> colors.surfaceDim
    }
    val gradient = Brush.linearGradient(
        listOf(
            lerp(colors.surface, statusBase, if (isDark) 0.45f else 0.24f),
            lerp(colors.surface, statusAccent, if (isDark) 0.18f else 0.10f)
        )
    )
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = statusBase.copy(alpha = if (isDark) 0.35f else 0.18f),
                spotColor = statusBase.copy(alpha = if (isDark) 0.35f else 0.18f)
            )
            .clip(cardShape)
            .background(gradient)
            .border(0.5.dp, colors.glassHighlight, cardShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: status + node info ──
            Column(modifier = Modifier.weight(1f)) {
                // Status pill, preceded by the state gear
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusGear(status = state.status)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isDark) Color.Black.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.55f)
                            )
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
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Big node label (IP / hostname)
                Text(
                    text = nodeLabel.ifEmpty { "—" },
                    color = colors.textPrimary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (nodeSub.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = nodeSub,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                if (latency.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            T("延迟 ", "Ping ", langZh),
                            color = colors.textTertiary,
                            fontSize = 11.sp
                        )
                        Text(
                            latency,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
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

        Spacer(Modifier.height(14.dp))

        // ── Stat strip: down / up / peers / uptime in a translucent dark block ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isDark) Color.Black.copy(alpha = 0.22f)
                    else Color.Black.copy(alpha = 0.055f)
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardStatItem(
                icon = Icons.Rounded.ArrowDownward,
                label = T("下载", "Down", langZh),
                value = rxSpeed.ifEmpty { "—" },
                accent = statusBase,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
            CardStatItem(
                icon = Icons.Rounded.ArrowUpward,
                label = T("上传", "Up", langZh),
                value = txSpeed.ifEmpty { "—" },
                accent = statusBase,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
            CardStatItem(
                icon = Icons.Rounded.Lan,
                label = T("节点", "Peers", langZh),
                value = "$peerCount",
                accent = statusBase,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
            CardStatItem(
                icon = Icons.Rounded.Schedule,
                label = T("时长", "Uptime", langZh),
                value = uptime.ifEmpty { "—" },
                accent = statusBase,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One cell of the status card's stat strip: icon chip + tiny label + value. */
@Composable
private fun CardStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    colors: com.easytier.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(label, color = colors.textTertiary, fontSize = 9.sp, maxLines = 1)
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Two side-by-side speed cards (up / down) with blue sparkline,
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
    // 按下缩小，松开回弹一下
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Node editor dialog (add / edit a single peer node).
 *
 * 外观与动效走统一组件 [HmDialog]（20dp 圆角玻璃卡 + fadeIn/scaleIn 0.96 240ms）。
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

    var protocol by remember(initial) { mutableStateOf(initial?.protocol ?: "tcp") }
    var address by remember(initial) { mutableStateOf(initial?.address ?: "") }
    var port by remember(initial) { mutableStateOf(initial?.port ?: "") }
    val canSave = address.isNotBlank() && port.isNotBlank()

    HmDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = if (initial == null) T("添加节点", "Add Node", langZh)
        else T("编辑节点", "Edit Node", langZh),
        maxWidth = 300.dp,
        horizontalPadding = 30.dp
    ) {
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
                HmDialogTextButton(
                    text = T("删除", "Delete", langZh),
                    color = colors.dangerText,
                    onClick = delete
                )
            }
            Spacer(Modifier.weight(1f))
            HmDialogSecondaryButton(
                text = T("取消", "Cancel", langZh),
                modifier = Modifier.width(72.dp),
                onClick = onDismiss
            )
            HmDialogPrimaryButton(
                text = T("保存", "Save", langZh),
                modifier = Modifier.width(88.dp),
                enabled = canSave
            ) {
                onSave(PeerNode(protocol, address.trim(), port.trim()))
            }
        }
    }
}
