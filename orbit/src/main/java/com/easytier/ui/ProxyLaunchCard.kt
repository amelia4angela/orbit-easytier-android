package com.easytier.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassColors
import com.easytier.ui.glass.components.glassPress
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.effects.rememberGlassPerformance
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

/** 淡蓝玻璃主色，与悬浮底栏选中胶囊同源（#DCEEFF）。 */
private val LaunchPillBlue = Color(0xFFDCEEFF)

/** 启动按钮圆盘直径（规格 76–92dp）。 */
private val LaunchDiscSize = 84.dp

/** 圆盘 + 外光晕容器：比圆盘大 12dp，光晕只画在这块盒子里，不铺满整卡。 */
private val LaunchHaloSize = 96.dp

/** 呼吸光周期 3.2 s（规格 ≥ 2 s）。 */
private const val BREATH_PERIOD_MS = 3_200

/** 呼吸光最大附加 alpha（规格 ≤ 0.12）。 */
private const val BREATH_GLOW_ALPHA = 0.10f

/** 启动成功一次性光效时长 + 峰值。 */
private const val FLASH_DURATION_MS = 1_100

/**
 * iOS-26-style "liquid glass" launch card for the dashboard.
 *
 * Presentational only: every value comes in as a parameter, every action goes out through
 * [onStart] / [onStop]. The one piece of local state is the *stop progress* flag, which is a UI
 * affordance (the business state still comes from [ConnectionButtonState.status]) — it turns the
 * button into a spinner the moment Stop is tapped and clears as soon as the tunnel leaves the
 * running state.
 *
 * Layout is three bands: status (top) / exit IP + control (middle, IP is the loudest element) /
 * runtime (bottom), separated by a 0.5dp hairline instead of a hard rule.
 *
 * @param state live connection state — single source of truth for the status band and the button
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
    // 只取 reducedMotion；玻璃档位由内层 glassSurface 自己决定，这里不干预。
    val reducedMotion = rememberGlassPerformance().reducedMotion

    val running = state.status == ConnectionStatus.RUNNING ||
        state.status == ConnectionStatus.NO_TUN
    val starting = state.status == ConnectionStatus.STARTING
    val failed = state.status == ConnectionStatus.ERROR ||
        state.status == ConnectionStatus.REVOKED
    val noTun = state.status == ConnectionStatus.NO_TUN

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

    // ── Running-only motion ──────────────────────────────────────────────────
    // 呼吸：仅在「运行中 且 未开启减少动效」时构建 InfiniteTransition；
    // 未运行时这条分支根本不进组合，所以不存在空转的无限动画。
    val breath = rememberLaunchBreath(active = running && !reducedMotion)

    // 启动成功的一次性柔光：只在 running 由 false → true 时播放，进页面不闪。
    val flash = remember { Animatable(0f) }
    var seenRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (running && !seenRunning) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_DURATION_MS, easing = LinearOutSlowInEasing))
        } else if (!running) {
            flash.snapTo(0f)
        }
        seenRunning = running
    }

    // 状态切换的轻微弹性（spring pop），减少动效时直接落在 1f。
    val pop = remember { Animatable(1f) }
    var seenPopRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (running != seenPopRunning && !reducedMotion) {
            pop.snapTo(0.90f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        } else if (running != seenPopRunning) {
            pop.snapTo(1f)
        }
        seenPopRunning = running
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
    val statusColor by animateColorAsState(
        targetValue = when {
            stopping || starting -> colors.statusWarn
            running -> colors.statusGood
            failed -> colors.statusBad
            else -> colors.textSecondary
        },
        animationSpec = tween(260),
        label = "launchStatusColor"
    )
    // 状态胶囊底：运行中柔和绿、异常柔和红、其它中性灰。透明底压在玻璃上仍然读得清，
    // Universal 模式换实色，保证关掉玻璃后不掉对比度。
    val pillColor by animateColorAsState(
        targetValue = when {
            stopping || starting -> colors.statusWarn.copy(alpha = 0.14f)
            running -> colors.statusGood.copy(alpha = 0.15f)
            failed -> colors.statusBad.copy(alpha = 0.14f)
            universal -> glass.universalFillLift
            isDark -> Color.White.copy(alpha = 0.10f)
            else -> Color.White.copy(alpha = 0.62f)
        },
        animationSpec = tween(260),
        label = "launchStatusPill"
    )
    val dotColor = when {
        stopping || starting -> colors.statusWarn
        running -> colors.statusGood
        failed -> colors.statusBad
        else -> colors.textTertiary
    }

    val milkyFill = when {
        // UNIVERSAL：异形轮廓保留，但填充换成不透明实色（深色 #1A1D23 系 / 浅色白），
        // 不再依赖「半透明白 + 背景模糊」把字压住。
        universal -> glass.universalBrush
        isDark -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f))
        )
        // 浅色：文字对比度优先，宁可少透一点（0.82 → 0.64）。
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.64f))
        )
    }

    val ipText = (if (hideIp) IpMask.mask(networkInfo.ip) else networkInfo.ip) ?: "--"
    val networkLabel = T(
        networkInfo.networkType.labelZh,
        networkInfo.networkType.labelEn,
        langZh
    )
    // 节点类型 / 质量：只有明细探测填过才有值；null = 不渲染，绝不留空标签占位。
    val nodeTypeLabel = ipTypeLabel(networkInfo.ipType, langZh)
    val levelLabel = networkInfo.level?.let { T("质量 $it", "Level $it", langZh) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { if (it != cardSize) cardSize = it }
            .shadow(
                // UNIVERSAL：轻阴影 ≤6dp；玻璃模式也收着力，不要重投影。
                elevation = when {
                    universal -> 4.dp
                    isDark -> 14.dp
                    else -> 10.dp
                },
                shape = shape,
                clip = false,
                ambientColor = if (universal) glass.universalShadow else glass.shadow,
                spotColor = if (universal) glass.universalShadow else glass.shadow
            )
            .clip(shape)
            .background(milkyFill, shape)
            // 玻璃统一走 glassSurface：档位由 LocalGlassLevel 决定，API 33 以下自动降级 LITE（不模糊）。
            // intensity 按「文字对比度优先」调高（浅色 0.94 / 深色 0.52）。
            // specular = false：HIGH 档的彩虹色散描边（1.5dp 暖/冷边）按规格禁用，
            // 顶部高光改由 CardOptics 自己画；0.5–1dp 白色 rim 仍由 rimGradient 提供（上沿更亮）。
            .glassSurface(shape = shape, intensity = if (isDark) 0.52f else 0.94f, specular = false)
    ) {
        // 玻璃光学层（右侧淡蓝光 + 顶部 specular）：静态 brush 一次 remember，
        // 画在填充之上、内容之下，不参与任何逐帧计算。
        CardOptics(
            isDark = isDark,
            universal = universal,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
        ) {
            // ── 1. status band: icon + status text pill (+ optional TUN badge) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusGear(
                    status = if (stopping) ConnectionStatus.STARTING else state.status,
                    size = 18.dp,
                    holeTint = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.width(9.dp))
                StatusPill(
                    text = statusText,
                    textColor = statusColor,
                    background = pillColor,
                    dotColor = dotColor,
                    // 运行中：极轻脉冲（0.55 → 0.95），复用同一份呼吸值，不再多开一条动画。
                    dotAlpha = if (running && !reducedMotion) {
                        { 0.55f + breath.value * 0.40f }
                    } else {
                        { 1f }
                    },
                    // 胶囊描边：中性态在乳白玻璃/白卡上也需要一点轮廓，靠极淡 hairline 撑住。
                    borderColor = when {
                        universal -> glass.universalBorder
                        isDark -> Color.White.copy(alpha = 0.10f)
                        else -> Color.Black.copy(alpha = 0.06f)
                    },
                    shape = RoundedCornerShape(50)
                )
                Spacer(Modifier.weight(1f))
                // 无 TUN = 内核在跑但没建虚拟网卡；给一个轻量标识，其余状态不占位。
                if (noTun) {
                    TunBadge(
                        text = T("无 TUN", "No TUN", langZh),
                        color = colors.statusWarn,
                        universal = universal,
                        isDark = isDark
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 2. network band: country → exit IP (loudest) → transport / node type ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // 固定 12dp 右侧留白 = IP 与按钮之间的保护间距，极窄屏也不贴边。
                        .padding(end = 12.dp)
                        // 点这块（含 IP）打开出站 IP 详情面板；press = glassPress（缩放反馈 + 独立
                        // interactionSource），与右侧启动按钮的 clickable 完全隔离。
                        .then(
                            if (onIpClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .glassPress(onClick = onIpClick)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountryChip(
                            code = networkInfo.countryCode,
                            colors = colors,
                            glass = glass,
                            isDark = isDark,
                            universal = universal
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // 隐藏 IP 只改这一行显示：真实网络信息与代理链路完全不受影响。
                        text = ipText,
                        color = colors.textPrimary,
                        fontSize = ipFontSize(ipText),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
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
                            text = networkLabel,
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        if (nodeTypeLabel != null) {
                            Spacer(Modifier.width(6.dp))
                            MetaChip(
                                text = nodeTypeLabel,
                                colors = colors,
                                glass = glass,
                                isDark = isDark,
                                universal = universal
                            )
                        }
                        if (levelLabel != null) {
                            Spacer(Modifier.width(6.dp))
                            MetaChip(
                                text = levelLabel,
                                colors = colors,
                                glass = glass,
                                isDark = isDark,
                                universal = universal
                            )
                        }
                    }
                }

                LaunchButton(
                    running = running,
                    busy = busy,
                    enabled = !busy,
                    langZh = langZh,
                    universal = universal,
                    breath = breath,
                    flash = flash,
                    scale = pop.value,
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

            Spacer(Modifier.height(14.dp))

            // ── hairline: 0.5dp + 两端渐隐，不是硬分割线 ──
            val hairline = remember(isDark, universal) {
                val edge = when {
                    universal -> Color.Black.copy(alpha = 0.10f)
                    isDark -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Black.copy(alpha = 0.09f)
                }
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.35f to edge,
                    0.75f to edge.copy(alpha = edge.alpha * 0.55f),
                    1f to Color.Transparent
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(hairline)
            )

            Spacer(Modifier.height(10.dp))

            // ── 3. runtime band: uptime (tabular) | org (ellipsised) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = T("时长", "Uptime", langZh),
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // 等宽字体：数字逐位等宽，秒跳变时不会左右抖动。
                    text = uptimeText.ifEmpty { "--" },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(Modifier.width(10.dp))
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
    }
}

// ═══════════════════════════════════════════════════
//  Bands
// ═══════════════════════════════════════════════════

/** 状态胶囊：柔和色底 + 状态点（运行中可轻脉冲）+ 文字 Crossfade。 */
@Composable
private fun StatusPill(
    text: String,
    textColor: Color,
    background: Color,
    dotColor: Color,
    dotAlpha: () -> Float,
    borderColor: Color,
    shape: Shape
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .border(width = 0.5.dp, color = borderColor, shape = shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    // 在 draw 阶段读动画值：只重绘这小圆点，不触发整卡重组。
                    .drawBehind {
                        drawCircle(color = dotColor, alpha = dotAlpha().coerceIn(0f, 1f))
                    }
            )
            Spacer(Modifier.width(6.dp))
            Crossfade(
                targetState = text,
                animationSpec = tween(220),
                label = "launchStatusText"
            ) { label ->
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** 「无 TUN」轻量标识：只有真的 NO_TUN 才渲染，其它状态不占位。 */
@Composable
private fun TunBadge(
    text: String,
    color: Color,
    universal: Boolean,
    isDark: Boolean
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                color.copy(
                    alpha = when {
                        universal -> 0.12f
                        isDark -> 0.16f
                        else -> 0.11f
                    }
                ),
                shape
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** 节点类型 / 质量的小标签（数据缺失时调用方根本不渲染）。 */
@Composable
private fun MetaChip(
    text: String,
    colors: AppColors,
    glass: GlassColors,
    isDark: Boolean,
    universal: Boolean
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    universal -> glass.universalFillLift
                    isDark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.60f)
                },
                shape
            )
            .border(
                width = 0.5.dp,
                color = if (universal) glass.universalBorder else glass.border,
                shape = shape
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = colors.textTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
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

// ═══════════════════════════════════════════════════
//  Glass optics
// ═══════════════════════════════════════════════════

/**
 * 玻璃的两处「光」：右侧淡蓝局部渐变 + 顶部 specular 高光。
 *
 * 两个 Brush 都按主题 remember，随内容尺寸自动铺满（linear/vertical gradient 在 draw 时
 * 按 DrawScope 尺寸解析），所以这里每帧没有任何对象分配。
 */
@Composable
private fun CardOptics(
    isDark: Boolean,
    universal: Boolean,
    modifier: Modifier = Modifier
) {
    // 右侧淡蓝光：横向渐变，只在右 45% 起亮，峰值 12% / 深色 10%，Universal 减半（无折射）。
    val blueGlow = remember(isDark, universal) {
        val strength = if (universal) 0.5f else 1f
        Brush.horizontalGradient(
            0.55f to Color.Transparent,
            0.84f to LaunchPillBlue.copy(alpha = (if (isDark) 0.07f else 0.09f) * strength),
            1f to LaunchPillBlue.copy(alpha = (if (isDark) 0.10f else 0.12f) * strength)
        )
    }
    // 顶部 specular：上沿更亮，45% 处完全消失。
    val specular = remember(isDark, universal) {
        val peak = when {
            universal -> 0.08f
            isDark -> 0.08f
            else -> 0.24f
        }
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = peak),
            0.18f to Color.White.copy(alpha = peak * 0.40f),
            0.45f to Color.Transparent
        )
    }
    Canvas(modifier) {
        drawRect(brush = blueGlow)
        drawRect(brush = specular)
    }
}

/** IPv4 / 脱敏串给足字号；完整 IPv6 降两档，宁可小一点也不换行。 */
private fun ipFontSize(ip: String): TextUnit = when {
    ip.length <= 15 -> 25.sp
    ip.length <= 21 -> 20.sp
    else -> 16.sp
}

// ═══════════════════════════════════════════════════
//  Launch button
// ═══════════════════════════════════════════════════

/**
 * 84dp 淡蓝玻璃圆盘 + 96dp 光晕容器。
 *
 * 外壳 = 静态 radial 光晕（alpha 由呼吸/一次性闪光在 draw 阶段调制，不重建 Brush）；
 * 盘面 = PillBlue → 浅蓝 → AppleBlue 的柔和竖向渐变 + 顶部 sheen + 上亮下暗 0.9dp 描边；
 * 内容 = 播放 / 停止图标 Crossfade，启动中/停止中换成手绘 spinner。
 * 按压仍走 [glassPress]（spring 缩放，busy 时禁用，避免重复触发）。
 */
@Composable
private fun LaunchButton(
    running: Boolean,
    busy: Boolean,
    enabled: Boolean,
    langZh: Boolean,
    universal: Boolean,
    breath: State<Float>,
    flash: Animatable<Float, AnimationVector1D>,
    scale: Float,
    onClick: () -> Unit
) {
    val label = when {
        running -> T("停止", "Stop", langZh)
        else -> T("启动", "Start", langZh)
    }

    // 光晕：一个静态 radial brush，靠 drawCircle(alpha=) 调制强度 → 零逐帧分配。
    val haloBrush = remember {
        Brush.radialGradient(
            listOf(
                GlassColors.AppleBlue.copy(alpha = 0.55f),
                GlassColors.AppleBlue.copy(alpha = 0.22f),
                Color.Transparent
            )
        )
    }
    // 盘面：PillBlue 只占顶部 16% 当高光，随后过渡到浅蓝/AppleBlue，全程不用深蓝。
    val discBrush = remember {
        Brush.verticalGradient(
            0f to LaunchPillBlue,
            0.16f to Color(0xFFCBE7FF),
            0.42f to Color(0xFF7FC0FF),
            0.70f to Color(0xFF4B9FFF),
            1f to GlassColors.AppleBlue
        )
    }
    val discSheen = remember {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.34f),
            0.42f to Color.White.copy(alpha = 0.06f),
            0.55f to Color.Transparent
        )
    }
    val discRim = remember {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.12f))
        )
    }

    Box(modifier = Modifier.size(LaunchHaloSize), contentAlignment = Alignment.Center) {
        // Ambient halo — 只画在这个 96dp 盒子里（不会溢到整卡），呼吸 + 一次性闪光都从这里出。
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val glow = (breath.value * BREATH_GLOW_ALPHA + flash.value * 0.42f).coerceIn(0f, 0.9f)
            drawCircle(
                brush = haloBrush,
                radius = radius * (1f + 0.04f * glow),
                center = center,
                alpha = (0.42f + glow * 0.9f).coerceIn(0f, 1f)
            )
            drawCircle(
                brush = haloBrush,
                radius = radius * 0.62f,
                center = center,
                alpha = (0.30f + glow * 0.6f).coerceIn(0f, 1f)
            )
        }

        Box(
            modifier = Modifier
                .size(LaunchDiscSize)
                .scale(scale)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = if (universal) Color(0x33000000)
                    else GlassColors.AppleBlue.copy(alpha = 0.45f),
                    spotColor = if (universal) Color(0x33000000)
                    else GlassColors.AppleBlue.copy(alpha = 0.55f)
                )
                .glassPress(enabled = enabled, onClick = onClick)
                .clip(CircleShape)
                .background(discBrush)
                .background(discSheen)
                .border(width = 0.9.dp, brush = discRim, shape = CircleShape),
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
                        modifier = Modifier.size(26.dp)
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

/** 呼吸值：未激活时不构建 InfiniteTransition（不空转），激活时 3.2 s 一个来回。 */
@Composable
private fun rememberLaunchBreath(active: Boolean): State<Float> {
    if (!active) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "launchBreath")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "launchBreathValue"
    )
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
 *
 * 幅度已调小（baseline 0.975 / 最大 dip 0.034，原来 0.96 / 0.065）：新布局信息密度更高，
 * 轮廓只在边缘露一点「液态」味道，不跟内容抢注意力。
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
        val base = 0.975f + 0.025f * right
        val dip = (0.004f + 0.030f * band) * (1f - sin(2f * t + theta)) / 2f
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
