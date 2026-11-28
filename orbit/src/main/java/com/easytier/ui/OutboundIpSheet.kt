package com.easytier.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassColors
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.components.GlassBottomSheet
import com.easytier.ui.glass.components.glassPress
import com.easytier.ui.glass.components.milkyGlass
import com.easytier.ui.glass.glassColors
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.LocalIsDarkTheme
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
import com.easytier.ui.theme.currentColors

/**
 * 出站 IP 详情面板（90% 高的 Liquid Glass Bottom Sheet）。
 *
 * 三张卡片：基础信息（IP / 等级 / 类型 / 命中标记）、归属信息（组织 / ASN / 采用来源）、
 * 隐私设置（隐藏 IP）。数据全部来自 [fetchDetailedNetworkInfo]，**一条业务逻辑、两套样式**：
 * 玻璃模式用 `milkyGlass`（乳白玻璃），Universal 模式由同一条通道自动换成实色面板 + 细描边 + 轻阴影。
 *
 * 刷新失败不会清空：结果里没有 IP 就保留旧值，并给一行行内提示。
 *
 * @param seed 启动卡片已经拿到的轻量数据（先显示，避免打开瞬间一片空）
 * @param hideIp 隐藏 IP 开关的当前值（状态由调用方持有，写盘也由调用方负责）
 */
@Composable
fun OutboundIpSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    seed: ProxyNetworkInfo,
    langZh: Boolean,
    hideIp: Boolean,
    onHideIpChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val colors = currentColors()
    val glass = glassColors()
    val isDark = LocalIsDarkTheme.current
    val universal = LocalThemeMode.current == ThemeMode.UNIVERSAL

    var info by remember { mutableStateOf(seed) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    // 启动卡片在面板打开后拿到 IP 时补进来（只在面板自己还没有 IP 时，不覆盖明细）。
    LaunchedEffect(seed) {
        if (info.ip == null && seed.ip != null) {
            info = info.copy(
                ip = seed.ip,
                countryCode = info.countryCode ?: seed.countryCode,
                networkType = seed.networkType
            )
        }
    }

    // 打开面板即拉一次；点刷新（refreshTick++）再拉。协程被取消（关面板/连点）时旧请求直接作废，
    // 这是天然的「防重复」——按钮另外在 loading 期间禁用。
    LaunchedEffect(visible, refreshTick) {
        if (!visible) return@LaunchedEffect
        loading = true
        failed = false
        try {
            val fresh = fetchDetailedNetworkInfo(context, forceRefresh = true)
            info = mergeDetailed(info, fresh)
            failed = fresh.ip == null
        } finally {
            // 被取消（关面板 / 又点了一次刷新）也要把转圈关掉，避免下次打开停在 loading。
            loading = false
        }
    }

    val spin = if (loading) {
        val transition = rememberInfiniteTransition(label = "ipRefreshSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ipRefreshAngle"
        )
        angle
    } else {
        0f
    }

    val shownIp = if (hideIp) IpMask.mask(info.ip) else info.ip
    val unknown = T("未知", "Unknown", langZh)
    val noData = T("暂无数据", "No data", langZh)

    GlassBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        cornerRadius = 32.dp,
        fillHeightFraction = 0.9f,
        dragToDismiss = true
    ) {
        // ── 头部：关闭 / 标题 / 刷新 ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IpSheetRoundButton(
                icon = Icons.Rounded.Close,
                contentDescription = T("关闭", "Close", langZh),
                colors = colors,
                glass = glass,
                isDark = isDark,
                universal = universal,
                onClick = onDismiss
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = T("出站 IP", "Outbound IP", langZh),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = T("依赖第三方 API，仅供参考", "Third-party API · for reference only", langZh),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            IpSheetRoundButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = T("刷新", "Refresh", langZh),
                colors = colors,
                glass = glass,
                isDark = isDark,
                universal = universal,
                enabled = !loading,
                rotation = spin,
                onClick = { if (!loading) refreshTick++ }
            )
        }

        if (failed) {
            Text(
                text = T("刷新失败，显示上次数据", "Refresh failed · showing last data", langZh),
                color = colors.statusBad,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        // ── 三张卡片（内部独立滚动）────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IpBasicInfoCard(
                ip = shownIp,
                level = info.level,
                ipType = ipTypeLabel(info.ipType, langZh),
                riskTags = info.riskTags,
                noData = noData,
                unknown = unknown,
                langZh = langZh,
                colors = colors,
                glass = glass,
                isDark = isDark,
                universal = universal
            )

            IpNetworkInfoCard(
                organization = info.organization,
                asn = info.asn,
                apiSource = info.apiSource,
                unknown = unknown,
                langZh = langZh,
                colors = colors,
                glass = glass,
                isDark = isDark,
                universal = universal
            )

            IpPrivacySettingsCard(
                hideIp = hideIp,
                onHideIpChange = onHideIpChange,
                langZh = langZh,
                colors = colors,
                isDark = isDark
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
//  Cards
// ═══════════════════════════════════════════════════

/** 卡片一：IP / 等级 / 类型 / 命中标记。IP 与等级（有值时）用绿色强调。 */
@Composable
private fun IpBasicInfoCard(
    ip: String?,
    level: String?,
    ipType: String?,
    riskTags: List<String>,
    noData: String,
    unknown: String,
    langZh: Boolean,
    colors: AppColors,
    glass: GlassColors,
    isDark: Boolean,
    universal: Boolean
) {
    IpSheetCard(isDark = isDark) {
        IpSheetRow(
            label = T("IP 地址", "IP address", langZh),
            colors = colors,
            glass = glass,
            universal = universal
        ) {
            Text(
                text = ip ?: "--",
                color = if (ip != null) colors.statusGood else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IpSheetRow(
            label = T("等级", "Level", langZh),
            colors = colors,
            glass = glass,
            universal = universal
        ) {
            Text(
                // 免费源都不给等级 → 明确写「暂无数据」，不编一个 A/B/C。
                text = level ?: noData,
                color = if (level != null) colors.statusGood else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IpSheetRow(
            label = T("类型", "Type", langZh),
            colors = colors,
            glass = glass,
            universal = universal
        ) {
            Text(
                text = ipType ?: unknown,
                color = if (ipType != null) colors.textSecondary else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IpSheetRow(
            label = T("命中标记", "Flags", langZh),
            colors = colors,
            glass = glass,
            universal = universal,
            showDivider = false
        ) {
            if (riskTags.isEmpty()) {
                Text(
                    text = T("无标记", "No flags", langZh),
                    color = colors.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    riskTags.forEach { tag ->
                        RiskTagChip(tag = riskTagLabel(tag, langZh), colors = colors)
                    }
                }
            }
        }
    }
}

/** 卡片二：组织 / ASN / 采用来源（缺失一律「未知」）。 */
@Composable
private fun IpNetworkInfoCard(
    organization: String?,
    asn: String?,
    apiSource: String?,
    unknown: String,
    langZh: Boolean,
    colors: AppColors,
    glass: GlassColors,
    isDark: Boolean,
    universal: Boolean
) {
    IpSheetCard(isDark = isDark) {
        IpSheetRow(
            label = T("组织", "Organization", langZh),
            colors = colors,
            glass = glass,
            universal = universal
        ) {
            Text(
                text = organization ?: unknown,
                color = if (organization != null) colors.textSecondary else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
        IpSheetRow(
            label = "ASN",
            colors = colors,
            glass = glass,
            universal = universal
        ) {
            Text(
                text = asn ?: unknown,
                color = if (asn != null) colors.textSecondary else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IpSheetRow(
            label = T("采用来源", "Source", langZh),
            colors = colors,
            glass = glass,
            universal = universal,
            showDivider = false
        ) {
            Text(
                text = apiSource ?: unknown,
                color = if (apiSource != null) colors.textSecondary else colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 卡片三：隐藏 IP（只影响显示；开关走项目标准 [HmSwitch]）。 */
@Composable
private fun IpPrivacySettingsCard(
    hideIp: Boolean,
    onHideIpChange: (Boolean) -> Unit,
    langZh: Boolean,
    colors: AppColors,
    isDark: Boolean
) {
    IpSheetCard(isDark = isDark) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = T("隐藏 IP", "Hide IP", langZh),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = T(
                        "启动卡片与面板里的 IP 显示为 113...60。只影响显示，不影响代理与真实数据。",
                        "Show IPs as 113...60 on the card and this sheet. Display only — the tunnel is untouched.",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            HmSwitch(
                checked = hideIp,
                onCheckedChange = onHideIpChange,
                colors = colors
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  Building blocks
// ═══════════════════════════════════════════════════

/**
 * 卡片容器：玻璃模式 = 乳白玻璃（`milkyGlass`），Universal = 实色面板 + 0.8dp 描边 + 轻阴影。
 * 两套样式由同一条 `milkyGlass` 通道分叉，这里不做任何主题判断。
 */
@Composable
private fun IpSheetCard(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .milkyGlass(
                radius = GlassRadius.lg,
                intensity = if (isDark) 1.15f else 1.0f,
                shadowElevation = 18.dp
            )
            .padding(horizontal = 14.dp, vertical = 2.dp),
        content = content
    )
}

/** 左标签 / 右值的一行，行间 0.5dp 细分割线。 */
@Composable
private fun IpSheetRow(
    label: String,
    colors: AppColors,
    glass: GlassColors,
    universal: Boolean,
    showDivider: Boolean = true,
    value: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = colors.textTertiary,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                value()
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (universal) glass.universalBorder else glass.border)
            )
        }
    }
}

/** 命中标记小胶囊（代理 / 机房 / 移动 …）。 */
@Composable
private fun RiskTagChip(tag: String, colors: AppColors) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(colors.statusWarn.copy(alpha = 0.15f), shape)
            .border(0.5.dp, colors.statusWarn.copy(alpha = 0.35f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = tag,
            color = colors.statusWarn,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** 头部圆形按钮（关闭 / 刷新）。加载中禁用点击，图标随 [rotation] 旋转。 */
@Composable
private fun IpSheetRoundButton(
    icon: ImageVector,
    contentDescription: String,
    colors: AppColors,
    glass: GlassColors,
    isDark: Boolean,
    universal: Boolean,
    enabled: Boolean = true,
    rotation: Float = 0f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .glassPress(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(
                when {
                    universal -> glass.universalFill
                    isDark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.66f)
                }
            )
            .border(
                width = if (universal) 0.8.dp else 0.5.dp,
                color = if (universal) glass.universalBorder else glass.border,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.textSecondary else colors.textTertiary.copy(alpha = 0.5f),
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation)
        )
    }
}

/**
 * 合并刷新结果：失败（没有 IP）保留旧值；IP 变了就整份换成新的（旧的组织/ASN 属于旧 IP，
 * 不能贴到新 IP 上）；IP 相同则把这次没给的字段用旧值补上。
 */
private fun mergeDetailed(old: ProxyNetworkInfo, fresh: ProxyNetworkInfo?): ProxyNetworkInfo {
    if (fresh == null || fresh.ip == null) return old
    if (old.ip != null && old.ip != fresh.ip) return fresh
    return fresh.copy(
        countryCode = fresh.countryCode ?: old.countryCode,
        organization = fresh.organization ?: old.organization,
        asn = fresh.asn ?: old.asn,
        ipType = fresh.ipType ?: old.ipType,
        riskTags = fresh.riskTags.ifEmpty { old.riskTags },
        apiSource = fresh.apiSource ?: old.apiSource,
        level = fresh.level ?: old.level
    )
}
