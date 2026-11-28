package com.easytier.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassColors
import com.easytier.ui.glass.components.glassPress
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.LocalIsDarkTheme
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// ═══════════════════════════════════════════════════
//  Liquid-Glass proxy launch card
// ═══════════════════════════════════════════════════

/**
 * iOS-26-style "liquid glass" launch card for the dashboard.
 *
 * Presentational only: every value comes in as a parameter, every action goes out through
 * [onStart] / [onStop]. The one piece of local state is the *stop progress* flag, which is a UI
 * affordance (the business state still comes from [ConnectionButtonState.status]) — it turns the
 * button into a spinner the moment Stop is tapped and clears as soon as the tunnel leaves the
 * running state.
 *
 * @param state live connection state — single source of truth for the status row and the button
 * @param networkInfo exit IP / country code / transport, see [rememberProxyNetworkInfo]
 * @param uptimeText formatted uptime from the dashboard's existing 1 s ticker
 * @param orgName the EasyTier network name of the active config (`ConfigState.networkName`)
 * @param hideIp true = IP 脱敏显示（`113...60`）；只影响显示，不影响数据与代理
 * @param onIpClick 点 IP 区域打开「出站 IP」详情面板；null = 该区域不可点
 */
@Composable
fun ProxyLaunchCard(
    state: ConnectionButtonState,
    networkInfo: ProxyNetworkInfo,
    uptimeText: String,
    orgName: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    langZh: Boolean,
    hideIp: Boolean = false,
    onIpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    val glass = glassColors()
    val isDark = LocalIsDarkTheme.current
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL

    val running = state.status == ConnectionStatus.RUNNING ||
        state.status == ConnectionStatus.NO_TUN
    val starting = state.status == ConnectionStatus.STARTING
    val failed = state.status == ConnectionStatus.ERROR ||
        state.status == ConnectionStatus.REVOKED

    // ── Stop progress (UI only) ──────────────────────────────────────────────
    // Tapping Stop does not flip ConnectionButtonState synchronously — the manager callback owns
    // that. Keep a short-lived local flag so the button shows a spinner instead of a dead press.
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (!running) stopping = false }
    LaunchedEffect(stopping) {
        if (stopping) {
            delay(8_000)
            stopping = false
        }
    }
    val busy = starting || stopping

    // ── Liquid outline phase ─────────────────────────────────────────────────
    // Quantised to PHASE_STEPS and advanced once every PHASE_PERIOD_MS while running, so the
    // silhouette morphs at ~0.4 Hz: the Path is rebuilt a handful of times, never per frame.
    var phaseStep by remember { mutableStateOf(0) }
    LaunchedEffect(running) {
        if (!running) {
            phaseStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(LiquidLaunchShape.PHASE_PERIOD_MS)
            phaseStep = (phaseStep + 1) % LiquidLaunchShape.PHASE_STEPS
        }
    }

    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val shape: Shape = remember(cardSize.width, cardSize.height, phaseStep) {
        if (cardSize.width <= 0 || cardSize.height <= 0) {
            RectangleShape
        } else {
            LiquidLaunchShape(phaseStep.toFloat() / LiquidLaunchShape.PHASE_STEPS)
        }
    }

    val statusText = when {
        stopping -> T("正在停止", "Stopping", langZh)
        starting -> T("正在启动", "Starting", langZh)
        running -> T("运行中", "Running", langZh)
        // REVOKED = the tunnel was torn down by the system / another VPN, not a start failure.
        state.status == ConnectionStatus.REVOKED -> T("已中断", "Interrupted", langZh)
        failed -> T("启动失败", "Failed", langZh)
        state.status == ConnectionStatus.STOPPED -> T("已停止", "Stopped", langZh)
        else -> T("未运行", "Idle", langZh)
    }
    val statusColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            stopping || starting -> colors.statusWarn
            running -> colors.statusGood
            failed -> colors.statusBad
            else -> colors.textSecondary
        },
        animationSpec = tween(260),
        label = "launchStatusColor"
    )

    val milkyFill = when {
        // UNIVERSAL：异形轮廓保留，但填充换成不透明实色（深色 #1A1D23 系 / 浅色白），
        // 不再依赖「半透明白 + 背景模糊」把字压住。
        universal -> glass.universalBrush
        isDark -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.56f))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { if (it != cardSize) cardSize = it }
            .shadow(
                // UNIVERSAL：轻阴影 ≤6dp；玻璃模式保留原来的浮起感。
                elevation = when {
                    universal -> 6.dp
                    isDark -> 20.dp
                    else -> 14.dp
                },
                shape = shape,
                clip = false,
                ambientColor = if (universal) glass.universalShadow else glass.shadow,
                spotColor = if (universal) glass.universalShadow else glass.shadow
            )
            .clip(shape)
            .background(milkyFill, shape)
            // 玻璃统一走 glassSurface：档位由 LocalGlassLevel 决定，API 33 以下自动降级 LITE（不模糊）。
            .glassSurface(shape = shape, intensity = if (isDark) 0.45f else 0.85f)
            .padding(start = 28.dp, end = 26.dp, top = 24.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // ── 1. status ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusGear(
                        status = if (stopping) ConnectionStatus.STARTING else state.status,
                        size = 18.dp,
                        holeTint = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = T("状态", "Status", langZh),
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(10.dp))
                    Crossfade(
                        targetState = statusText,
                        animationSpec = tween(220),
                        label = "launchStatusText"
                    ) { text ->
                        Text(
                            text = text,
                            color = statusColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 2. network: country | exit IP + transport ──
                // 点这块（含 IP）打开出站 IP 详情面板。press = glassPress（缩放反馈 + 独立
                // interactionSource），与右侧启动按钮的 clickable 完全隔离，不会互相触发。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onIpClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .glassPress(onClick = onIpClick)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountryChip(code = networkInfo.countryCode, colors = colors, glass = glass, isDark = isDark, universal = universal)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            // 隐藏 IP 只改这一行显示：真实网络信息与代理链路完全不受影响。
                            text = (if (hideIp) IpMask.mask(networkInfo.ip) else networkInfo.ip) ?: "--",
                            color = colors.textPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (networkInfo.networkType == NetworkType.NONE) colors.textTertiary
                                        else colors.statusGood
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = T(
                                    networkInfo.networkType.labelZh,
                                    networkInfo.networkType.labelEn,
                                    langZh
                                ),
                                color = colors.textTertiary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 3. runtime: uptime | org (network name) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uptimeText.ifEmpty { "--" },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = orgName.ifBlank { "--" },
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            LaunchButton(
                running = running,
                busy = busy,
                enabled = !busy,
                langZh = langZh,
                onClick = {
                    if (!busy) {
                        if (running) {
                            stopping = true
                            onStop()
                        } else {
                            onStart()
                        }
                    }
                }
            )
        }
    }
}

/** Country / region chip. Falls back to `--` when the probe has no country code. */
@Composable
private fun CountryChip(
    code: String?,
    colors: AppColors,
    glass: GlassColors,
    isDark: Boolean,
    universal: Boolean
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    universal -> glass.universalFill
                    isDark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.66f)
                },
                shape
            )
            .border(
                width = if (universal) 0.8.dp else 0.5.dp,
                color = if (universal) glass.universalBorder else glass.border,
                shape = shape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = code?.uppercase() ?: "--",
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * The big blue circular action button.
 *
 * Shell = soft multi-layer radial glow (blue, no shadow API) + a blue gradient disc with a
 * top-lit rim. Content = play / stop icon crossfade, replaced by a hand-drawn spinning arc while
 * starting or stopping. Press feedback reuses [glassPress] (spring scale) and is disabled while
 * busy, so an in-flight start cannot be double-fired.
 */
@Composable
private fun LaunchButton(
    running: Boolean,
    busy: Boolean,
    enabled: Boolean,
    langZh: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        running -> T("停止", "Stop", langZh)
        else -> T("启动", "Start", langZh)
    }
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        // Ambient blue halo — two concentric radial gradients, drawn only inside this 96dp box
        // (never behind the whole card), so the outer glow costs nothing extra per frame.
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(GlassColors.AppleBlue.copy(alpha = 0.30f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(GlassColors.AppleBlue.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = radius * 0.66f
                ),
                radius = radius * 0.66f,
                center = center
            )
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .glassPress(enabled = enabled, onClick = onClick)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF4DA6FF),
                            GlassColors.AppleBlue,
                            GlassColors.AppleBlueDeep
                        )
                    )
                )
                // Inner sheen (top half) + top-lit hairline rim.
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.55f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 0 = start icon, 1 = stop icon, 2 = spinner — one Crossfade so every state change
            // (including icon ↔ spinner and stop ↔ play) fades instead of popping.
            val iconState = when {
                busy -> 2
                running -> 1
                else -> 0
            }
            Crossfade(
                targetState = iconState,
                animationSpec = tween(200),
                label = "launchIcon"
            ) { which ->
                when (which) {
                    2 -> LaunchSpinner()
                    1 -> Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/** Hand-drawn spinner (no third-party lib): faint full ring + bright 96° arc, 850 ms / turn. */
@Composable
private fun LaunchSpinner(boxSize: Dp = 26.dp) {
    val transition = rememberInfiniteTransition(label = "launchSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "launchSpinnerAngle"
    )
    Canvas(modifier = Modifier.size(boxSize)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        rotate(degrees = angle) {
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  Liquid silhouette
// ═══════════════════════════════════════════════════

/**
 * Organic card outline — **not** a rounded rectangle and not a pile of circles.
 *
 * The silhouette is a superellipse ("squircle" spine) whose radius is modulated by two travelling
 * sine lobes; the right-hand side gets a stronger, static outward lean plus extra lobes, which is
 * what produces the liquid, right-heavy bulge. Coordinates are derived from the measured card
 * size, so it adapts to any width / height / resolution.
 *
 * [phase] is quantised by the caller — this class allocates one Path per outline request, and
 * callers cache it with `remember(size, phase)`, so morphing costs a rebuild every few seconds,
 * not every frame.
 */
@Immutable
class LiquidLaunchShape(private val phase: Float = 0f) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(buildLiquidLaunchPath(size, phase))

    override fun equals(other: Any?): Boolean =
        other is LiquidLaunchShape && other.phase == phase

    override fun hashCode(): Int = phase.hashCode()

    companion object {
        /** Phase buckets in one full morph cycle (8 → 45° steps). */
        const val PHASE_STEPS = 8

        /** Time between two phase buckets: 2.6 s → ≤ 0.4 Hz silhouette change. */
        const val PHASE_PERIOD_MS = 2_600L
    }
}

/** Perimeter samples per outline. 48 cubics is smooth at any card size and costs ~1 KB. */
private const val SEGMENTS = 48

/** Superellipse exponent: 2 = ellipse, ∞ = rectangle. ~4.6 = soft-shouldered card. */
private const val SPINE = 4.6f

/**
 * Outline construction: a superellipse spine whose radius goes from 1.0 **down** to
 * `1 - base + dip`. Crests (sin = 1) sit exactly on the layout edge, so the card keeps full
 * width and the wavy edge can never be clipped; the valleys pull inward and produce the liquid
 * undulation. The right-hand side has both a shallower static inset and a deeper wave, which is
 * what makes the card read as "swelling out to the right".
 */
private fun buildLiquidLaunchPath(size: Size, phase: Float): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return path

    val halfW = w / 2f
    val halfH = h / 2f
    val theta = phase * 2f * PI.toFloat()

    val xs = FloatArray(SEGMENTS)
    val ys = FloatArray(SEGMENTS)
    for (i in 0 until SEGMENTS) {
        val t = i * (2f * PI.toFloat() / SEGMENTS)
        val ct = cos(t)
        val st = sin(t)
        // Superellipse point on the unit box.
        val sx = sign(ct) * abs(ct).pow(2f / SPINE)
        val sy = sign(st) * abs(st).pow(2f / SPINE)
        // right = 1 on the right edge, 0 on the left: the liquid bulge lives on the right.
        val right = ((ct + 1f) / 2f).pow(2f)
        val band = right * right * right
        // Static inset (the left sits further in) + a slow 2-lobe wave pinned to the right side.
        // base <= 1 always, so a crest can touch the layout edge but never cross it.
        val base = 0.960f + 0.040f * right
        val dip = (0.010f + 0.055f * band) * (1f - sin(2f * t + theta)) / 2f
        xs[i] = w / 2f + halfW * sx * (base - dip)
        ys[i] = h / 2f + halfH * sy * (base - dip * 0.6f)
    }

    // Catmull-Rom → cubic Bézier through every sample: one closed, C1-smooth outline.
    path.moveTo(xs[0], ys[0])
    for (i in 0 until SEGMENTS) {
        val p = (i + SEGMENTS - 1) % SEGMENTS
        val n = (i + 1) % SEGMENTS
        val nn = (i + 2) % SEGMENTS
        val x0 = xs[i]
        val y0 = ys[i]
        path.cubicTo(
            x0 + (xs[n] - xs[p]) / 6f,
            y0 + (ys[n] - ys[p]) / 6f,
            xs[n] - (xs[nn] - x0) / 6f,
            ys[n] - (ys[nn] - y0) / 6f,
            xs[n],
            ys[n]
        )
    }
    path.close()
    return path
}
