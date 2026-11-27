package com.easytier.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.easytier.AppLogger
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.currentColors

// ═══════════════════════════════════════════════════
//  Shared shell for settings sub-pages
// ═══════════════════════════════════════════════════

/**
 * 二三级页统一的返回控件：一个 `<` 字符 + 44dp 点击区，不再是按钮外观
 * （无底色、无描边）。点击行为仍由调用方传入的 [onBack] 决定（pageStack pop）。
 */
@Composable
fun HmBackGlyph(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AppColors = currentColors()
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Text("<", color = colors.accentLight, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Full-screen settings sub-page: gradient background, back control + centered title,
 * scrollable body. Same visual language as [AboutPage].
 *
 * The caller owns the back stack (it registers the BackHandler), so [onBack] is
 * the only navigation hook here.
 */
@Composable
fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = currentColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.bgStart, colors.bgMid, colors.bgEnd))
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            HmBackGlyph(
                onBack = onBack,
                colors = colors,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════
//  General — server / core / logs / permissions
// ═══════════════════════════════════════════════════

@Composable
fun GeneralPage(
    langZh: Boolean,
    config: ConfigState,
    onConfigChange: (ConfigState) -> Unit,
    onConfigServer: () -> Unit,
    onRestartCore: () -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit
) {
    val colors = currentColors()
    var logEnabled by remember { mutableStateOf(com.easytier.AppLogger.enabled) }

    val memoryLevel = config.memoryLimit.ifEmpty { com.easytier.RuntimeTuning.MEMORY_STANDARD }

    SettingsSubPage(title = T("常规", "General", langZh), onBack = onBack) {
        // 顺序：常用开关在前 → 动作在后 → 相关聚合
        // 1. 省电模式 — slows the polling/notification cadence in the background
        //    and injects EasyTier power flags (disable_upnp) into the generated TOML.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔋", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        T("省电模式", "Power Saving", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                HmToggle(
                    label = T("后台降频", "Slow down in background", langZh),
                    checked = config.powerSaving,
                    onCheckedChange = { onConfigChange(config.copy(powerSaving = it)) },
                    hint = T(
                        "熄屏/后台时状态刷新从 1s 放宽到 60s、通知 60s 一次；同时关闭 EasyTier 的 UPnP 探测。回到前台立即恢复",
                        "When backgrounded: status every 60s instead of 1s, notification every 60s, UPnP probing off. Restored instantly in foreground",
                        langZh
                    ),
                    colors = colors
                )
            }
        }

        // 2. 内存限制 — log buffer / polling profile, picked from an anchored dropdown
        HmDropdownField(
            label = T("内存限制", "Memory Limit", langZh),
            options = listOf(
                HmDropdownOption(
                    label = T("标准", "Standard", langZh),
                    value = com.easytier.RuntimeTuning.MEMORY_STANDARD,
                    hint = memoryLevelHint(com.easytier.RuntimeTuning.MEMORY_STANDARD, langZh)
                ),
                HmDropdownOption(
                    label = T("严格", "Strict", langZh),
                    value = com.easytier.RuntimeTuning.MEMORY_STRICT,
                    hint = memoryLevelHint(com.easytier.RuntimeTuning.MEMORY_STRICT, langZh)
                ),
                HmDropdownOption(
                    label = T("关闭", "Off", langZh),
                    value = com.easytier.RuntimeTuning.MEMORY_OFF,
                    hint = memoryLevelHint(com.easytier.RuntimeTuning.MEMORY_OFF, langZh)
                )
            ),
            selectedIndex = when (memoryLevel) {
                com.easytier.RuntimeTuning.MEMORY_STRICT -> 1
                com.easytier.RuntimeTuning.MEMORY_OFF -> 2
                else -> 0
            },
            onSelect = { idx ->
                val level = when (idx) {
                    1 -> com.easytier.RuntimeTuning.MEMORY_STRICT
                    2 -> com.easytier.RuntimeTuning.MEMORY_OFF
                    else -> com.easytier.RuntimeTuning.MEMORY_STANDARD
                }
                onConfigChange(config.copy(memoryLimit = level))
            },
            colors = colors,
            hint = memoryLevelHint(memoryLevel, langZh),
            card = true
        )

        // 3. 日志收集 (AppLogger.setEnabled)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                .clickable {
                    val next = !logEnabled
                    logEnabled = next
                    com.easytier.AppLogger.setEnabled(next)
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("≡", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        T("日志收集", "Log Collection", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        T(
                            "默认关闭；开启后最多保留最近 3 天、100 条",
                            "Off by default; keeps the last 3 days / 100 lines",
                            langZh
                        ),
                        color = colors.textTertiary, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                HmSwitch(
                    checked = logEnabled,
                    onCheckedChange = { next ->
                        logEnabled = next
                        com.easytier.AppLogger.setEnabled(next)
                    },
                    colors = colors
                )
            }
        }

        // 4. 配置服务器
        SettingsNavRow(
            icon = "☁",
            title = T("配置服务器", "Config Server", langZh),
            subtitle = T("从远端 URL 一键拉取 TOML 配置", "Fetch TOML config from URL", langZh),
            colors = colors,
            onClick = onConfigServer
        )

        // 5. 重启核心 — same call chain as the old dashboard restart button
        SettingsNavRow(
            icon = "↻",
            title = T("重启核心", "Restart Core", langZh),
            subtitle = T("断开并重新拉起 EasyTier 实例", "Tear down and restart the EasyTier core", langZh),
            colors = colors,
            onClick = onRestartCore
        )

        // 6. 权限管理
        SettingsNavRow(
            icon = "🛡",
            title = T("权限管理", "Permissions", langZh),
            subtitle = T("通知、VPN、电池优化与安装", "Notifications, VPN, battery & install", langZh),
            colors = colors,
            onClick = onOpenPermissions
        )
    }
}

/** 档位说明（一行 hint，下拉项的说明也用它）。 */
private fun memoryLevelHint(level: String, langZh: Boolean): String = when (level) {
    com.easytier.RuntimeTuning.MEMORY_STRICT -> T(
        "日志 30 条 / 6 小时，轮询 2s、通知 10s、日志页 5s",
        "30 log lines / 6h; poll 2s, notify 10s, logs 5s",
        langZh
    )
    com.easytier.RuntimeTuning.MEMORY_OFF -> T(
        "不做任何限制",
        "No limits at all",
        langZh
    )
    else -> T(
        "日志 100 条 / 3 天，轮询 1s、通知 5s（现状）",
        "100 log lines / 3 days; poll 1s, notify 5s (current)",
        langZh
    )
}

// ═══════════════════════════════════════════════════
//  Theme — light / dark / follow system (second-level page)
// ═══════════════════════════════════════════════════

/**
 * 主题二级页：三个单选模式（浅色 / 深色 / 跟随系统）+ 每项一句说明，
 * 页面底部预留「更多主题」占位卡片，之后按卡片扩主题不用改导航结构。
 */
@Composable
fun ThemePage(
    langZh: Boolean,
    isDarkTheme: Boolean,
    dynamicColor: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDynamic: () -> Unit,
    onBack: () -> Unit
) {
    val colors = currentColors()
    val selectedIndex = when {
        dynamicColor -> 2
        isDarkTheme -> 1
        else -> 0
    }
    fun select(index: Int) {
        when (index) {
            0 -> { if (dynamicColor) onToggleDynamic(); if (isDarkTheme) onToggleTheme() }
            1 -> { if (dynamicColor) onToggleDynamic(); if (!isDarkTheme) onToggleTheme() }
            2 -> if (!dynamicColor) onToggleDynamic()
        }
    }

    SettingsSubPage(title = T("主题", "Theme", langZh), onBack = onBack) {
        Text(
            T(
                "选择外观模式；「跟随系统」会随系统的深色/浅色设置自动切换。",
                "Pick a look. \"System\" follows the device's dark/light setting.",
                langZh
            ),
            color = colors.textTertiary,
            fontSize = 12.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(14.dp))
                .padding(vertical = 6.dp, horizontal = 6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ThemeOptionRow(
                    title = T("浅色", "Light", langZh),
                    hint = T("始终使用浅色背景", "Always light", langZh),
                    selected = selectedIndex == 0,
                    colors = colors,
                    onClick = { select(0) }
                )
                ThemeOptionRow(
                    title = T("深色", "Dark", langZh),
                    hint = T("始终使用深色背景，夜间更护眼", "Always dark — easier at night", langZh),
                    selected = selectedIndex == 1,
                    colors = colors,
                    onClick = { select(1) }
                )
                ThemeOptionRow(
                    title = T("跟随系统", "System", langZh),
                    hint = T("跟随系统深浅色自动切换", "Follow the system appearance", langZh),
                    selected = selectedIndex == 2,
                    colors = colors,
                    onClick = { select(2) }
                )
            }
        }

        // 预留位：以后的主题卡片（配色包 / 图标风格）挂在这里
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.inputBg.copy(alpha = 0.5f))
                .border(0.5.dp, colors.inputBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "🎨 " + T("更多主题", "More themes", langZh),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    T(
                        "主题市场与自定义配色开发中，后续会在这里以卡片形式选择。",
                        "Theme gallery and custom palettes are in progress; they will show up here as cards.",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    hint: String,
    selected: Boolean,
    colors: AppColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) colors.accentLight else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                hint,
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .border(
                    1.5.dp,
                    if (selected) colors.accent else colors.inputBorder,
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Permissions
// ═══════════════════════════════════════════════════

private enum class PermStatus { GRANTED, DENIED, NOT_APPLICABLE }

@Composable
fun PermissionsPage(langZh: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val colors = currentColors()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permissions live in system state, not in Compose state: re-read them every time
    // the page returns to the foreground (system settings / dialogs return via ON_RESUME).
    var refreshKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    // 1. Notifications (API 33+, always granted below that)
    val notifSupported = Build.VERSION.SDK_INT >= 33
    val notifGranted = remember(refreshKey, notifSupported) {
        !notifSupported || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 2. VPN consent — prepare() returns null once the user has approved it
    val vpnGranted = remember(refreshKey) {
        try { VpnService.prepare(ctx) == null } catch (_: Exception) { false }
    }

    // 3. Battery optimization exemption
    val batteryGranted = remember(refreshKey) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
    }

    // 4. Install unknown apps (API 26+)
    val installSupported = Build.VERSION.SDK_INT >= 26
    val installGranted = remember(refreshKey, installSupported) {
        installSupported && try {
            ctx.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) { false }
    }

    fun openIntent(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (t: Throwable) {
            AppLogger.w("PermissionsPage", "cannot open settings: ${t.message}")
        }
    }

    SettingsSubPage(title = T("权限管理", "Permissions", langZh), onBack = onBack) {
        Text(
            T(
                "以下权限只在需要时使用，状态实时读取系统；授予后返回本页会自动刷新。",
                "Permissions are only used when needed. Status is read from the system and refreshes when you return to this page.",
                langZh
            ),
            color = colors.textTertiary,
            fontSize = 12.sp
        )

        // 1. Notification permission
        PermissionRow(
            colors = colors,
            langZh = langZh,
            title = T("通知权限", "Notifications", langZh),
            description = T(
                "显示连接状态与告警通知；拒绝后前台服务通知会被系统隐藏。",
                "Shows connection status and alerts; without it the foreground notification is hidden.",
                langZh
            ),
            status = when {
                !notifSupported -> PermStatus.NOT_APPLICABLE
                notifGranted -> PermStatus.GRANTED
                else -> PermStatus.DENIED
            },
            actionLabel = if (notifSupported && !notifGranted) T("请求权限", "Grant", langZh) else null,
            onAction = if (notifSupported && !notifGranted) {
                { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            } else null
        )

        // 2. VPN permission
        PermissionRow(
            colors = colors,
            langZh = langZh,
            title = T("VPN 权限", "VPN", langZh),
            description = T(
                "建立安全隧道所必需，连接时由系统弹窗授予。",
                "Required to build the tunnel; the system asks for it when connecting.",
                langZh
            ),
            status = if (vpnGranted) PermStatus.GRANTED else PermStatus.DENIED,
            actionLabel = if (!vpnGranted) T("去授权", "Authorize", langZh) else null,
            onAction = if (!vpnGranted) {
                {
                    val intent = try { VpnService.prepare(ctx) } catch (_: Exception) { null }
                    if (intent != null) {
                        try { settingsLauncher.launch(intent) } catch (_: Throwable) { }
                    }
                    refreshKey++
                }
            } else null
        )

        // 3. Battery optimization exemption
        PermissionRow(
            colors = colors,
            langZh = langZh,
            title = T("电池优化豁免", "Battery optimization", langZh),
            description = T(
                "允许后台保持连接不被系统杀掉。",
                "Keeps the tunnel alive in the background instead of being killed.",
                langZh
            ),
            status = if (batteryGranted) PermStatus.GRANTED else PermStatus.DENIED,
            actionLabel = if (!batteryGranted) T("去设置", "Settings", langZh) else null,
            onAction = if (!batteryGranted) {
                {
                    try {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    } catch (t: Throwable) {
                        // Some ROMs don't expose the direct-request dialog — fall back to the list
                        AppLogger.w("PermissionsPage", "direct battery request unavailable: ${t.message}")
                        openIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            } else null
        )

        // 4. Install unknown apps
        PermissionRow(
            colors = colors,
            langZh = langZh,
            title = T("安装未知应用", "Install unknown apps", langZh),
            description = T(
                "应用内更新时安装下载好的 APK。",
                "Needed to install the downloaded APK for in-app updates.",
                langZh
            ),
            status = when {
                !installSupported -> PermStatus.NOT_APPLICABLE
                installGranted -> PermStatus.GRANTED
                else -> PermStatus.DENIED
            },
            actionLabel = if (installSupported && !installGranted) T("去设置", "Settings", langZh) else null,
            onAction = if (installSupported && !installGranted) {
                {
                    openIntent(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }
            } else null
        )

        // 5. Background running — no reliable API to query autostart / OEM cleaners,
        //    so this row explains the situation and links to the battery settings list.
        PermissionRow(
            colors = colors,
            langZh = langZh,
            title = T("后台运行", "Background running", langZh),
            description = T(
                "被系统清理后自动恢复；是否允许自启动由系统或厂商设置决定，无法直接读取。",
                "Recovers automatically after the system kills it. Autostart control lives in system/OEM settings and cannot be queried.",
                langZh
            ),
            status = PermStatus.NOT_APPLICABLE,
            actionLabel = T("电池设置", "Battery settings", langZh),
            onAction = {
                openIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionRow(
    colors: AppColors,
    langZh: Boolean,
    title: String,
    description: String,
    status: PermStatus,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    val statusText = when (status) {
        PermStatus.GRANTED -> T("已授予", "Granted", langZh)
        PermStatus.DENIED -> T("未授予", "Not granted", langZh)
        PermStatus.NOT_APPLICABLE -> T("不适用", "N/A", langZh)
    }
    val statusColor = when (status) {
        PermStatus.GRANTED -> colors.statusGood
        PermStatus.DENIED -> colors.warning
        PermStatus.NOT_APPLICABLE -> colors.textTertiary
    }
    val statusBg = when (status) {
        PermStatus.GRANTED -> colors.statusGood.copy(alpha = 0.12f)
        PermStatus.DENIED -> colors.warningBg
        PermStatus.NOT_APPLICABLE -> colors.inputBg
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(description, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            if (actionLabel != null && onAction != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentContainer)
                        .clickable { onAction() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        actionLabel,
                        color = colors.accentLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Privacy policy — first-launch gate + About entry
// ═══════════════════════════════════════════════════

/**
 * Privacy policy, bilingual. Two modes:
 *  - [forced] = true  → first-launch gate: no back button, system back is consumed,
 *    the only exits are [onAccept] (persist + continue) and [onDecline] (finish()).
 *  - [forced] = false → read-only page opened from About, with a back button.
 */
@Composable
fun PrivacyPolicyPage(
    langZh: Boolean,
    forced: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit
) {
    val colors = currentColors()
    // The gate cannot be skipped with the system back gesture/button.
    if (forced) BackHandler { /* consume */ }

    val sections = listOf(
        T("1. 数据只保存在本机", "1. Everything stays on your device", langZh) to T(
            "所有网络配置、日志与节点信息只写入本机存储（应用私有目录以及 /EasyTier/configs）。Orbit 不设任何云端账号，也不会把你的配置上传到服务器。",
            "All network configs, logs and node info are written to local storage only (app-private dir and /EasyTier/configs). Orbit has no cloud account and never uploads your config.",
            langZh
        ),
        T("2. 不收集个人数据", "2. No personal data collected", langZh) to T(
            "应用内没有统计、广告或推送 SDK，不读取设备标识、位置、通讯录和通话记录。唯一的联网请求是「检查更新」，它只访问 GitHub Releases 接口，不附加任何个人标识。",
            "There is no analytics, ads or push SDK, and no access to device IDs, location, contacts or call logs. The only network request is the update check, which calls the public GitHub Releases API without any personal identifier.",
            langZh
        ),
        T("3. DNS 查询", "3. DNS queries", langZh) to T(
            "如果你填写了自定义 DNS，DNS 查询会直接发往该服务器（通常是你自建的），Orbit 不做中转、不做记录。留空则沿用系统 DNS。",
            "If you set a custom DNS, queries go straight to that server (usually your own). Orbit does not relay or log them. Left empty, the system DNS is used.",
            langZh
        ),
        T("4. 隧道流量", "4. Tunnel traffic", langZh) to T(
            "组网流量只在你加入的 EasyTier 网络内传输；未配置中继或出口节点时，流量不经过任何第三方服务器。",
            "Mesh traffic only travels inside the EasyTier network you joined. Without a relay or exit node configured, no third-party server sees it.",
            langZh
        ),
        T("5. 权限用途", "5. Why permissions are used", langZh) to T(
            "通知：显示连接状态与告警；VPN：创建虚拟网卡（TUN）；电池优化豁免：后台保持连接；安装未知应用：应用内更新安装 APK。以上均可在「设置 → 常规 → 权限管理」查看与调整。",
            "Notifications: show connection status and alerts. VPN: create the TUN interface. Battery exemption: keep the tunnel alive. Install unknown apps: in-app APK updates. All of them are listed under Settings → General → Permissions.",
            langZh
        ),
        T("6. 开源组件", "6. Open-source components", langZh) to T(
            "Orbit 本体（GPL-3.0）、EasyTier 内核（github.com/EasyTier/EasyTier）、AndroidX / Jetpack Compose（Apache-2.0）、Kotlin（Apache-2.0）。",
            "Orbit itself (GPL-3.0), the EasyTier core (github.com/EasyTier/EasyTier), AndroidX / Jetpack Compose (Apache-2.0) and Kotlin (Apache-2.0).",
            langZh
        ),
        T("7. 联系方式", "7. Contact", langZh) to T(
            "任何隐私相关问题，请在 GitHub 仓库提交 Issue：github.com/amelia4angela/orbit-easytier-android/issues",
            "For any privacy question, open an issue at github.com/amelia4angela/orbit-easytier-android/issues",
            langZh
        ),
        T("8. 政策更新", "8. Changes to this policy", langZh) to T(
            "本政策随版本更新，最新全文始终可在「设置 → 关于 → 隐私政策」中查看。",
            "This policy is updated with new releases; the current text is always available under Settings → About → Privacy Policy.",
            langZh
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.bgStart, colors.bgMid, colors.bgEnd))
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (!forced) {
                HmBackGlyph(
                    onBack = onBack,
                    colors = colors,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            Text(
                T("隐私政策", "Privacy Policy", langZh),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (forced) {
                Text(
                    T(
                        "使用 Orbit 前请阅读并同意以下说明。",
                        "Please read and accept the following before using Orbit.",
                        langZh
                    ),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            sections.forEach { (heading, body) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            heading,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            body,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        if (forced) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 按钮与统一弹窗组件对齐：主按钮 accent 实心、次按钮描边，圆角 12
                HmDialogPrimaryButton(
                    text = T("同意并继续", "Agree & Continue", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAccept
                )
                HmDialogSecondaryButton(
                    text = T("不同意", "Decline", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                )
                Text(
                    T(
                        "不同意将退出应用，Orbit 无法在未同意隐私政策的情况下使用。",
                        "Declining exits the app — Orbit cannot run without accepting this policy.",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
