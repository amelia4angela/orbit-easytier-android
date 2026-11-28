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
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassShapes
import com.easytier.ui.glass.components.GlassSurface
import com.easytier.ui.glass.components.glassCard
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.AppThemeManager
import com.easytier.ui.theme.LocalSetThemeMode
import com.easytier.ui.theme.LocalThemeMode
import com.easytier.ui.theme.ThemeMode
import com.easytier.ui.theme.currentColors
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * 二级页（常规/主题/权限/关于）的底色，跟随 [LocalBgSurface]：
 *
 * - GRADIENT：主题渐变，**完全不透明** —— 进出转场两张页面同时在场，半透明会互相透出来。
 * - IMAGE：同一组渐变压到 72–78% 不透明度，让壁纸透出来（否则设了壁纸一进设置页就
 *   看不到，用户会以为背景没生效）；玻璃采样源仍是 GlassSky 那张图，顶栏模糊不受影响。
 * - FLAT：「关闭」模式，纯色，跟着主界面走。
 */
@Composable
fun settingsPageBackdrop(colors: AppColors): Brush = when (LocalBgSurface.current) {
    BgSurface.IMAGE -> {
        // 图片背景：留 1/4 左右的空隙让壁纸透出来（否则设了壁纸一进设置页就看不见了）
        val alpha = if (com.easytier.ui.theme.LocalIsDarkTheme.current) 0.78f else 0.72f
        Brush.verticalGradient(
            listOf(
                colors.bgStart.copy(alpha = alpha),
                colors.bgMid.copy(alpha = alpha),
                colors.bgEnd.copy(alpha = alpha)
            )
        )
    }
    BgSurface.FLAT -> androidx.compose.ui.graphics.SolidColor(colors.bgMid)
    BgSurface.GRADIENT -> Brush.verticalGradient(
        listOf(colors.bgStart, colors.bgMid, colors.bgEnd)
    )
}

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
            .clip(glassShape(GlassRadius.sm))
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Text("<", color = colors.accentLight, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Full-screen settings sub-page: floating glass top bar over a scrollable body.
 * Same visual language as [AboutPage].
 *
 * The top bar is an overlay: the body column fills the screen and reserves the bar's height
 * at the top, so content scrolls *under* the glass pane. The pane samples the screen backdrop
 * via [com.easytier.ui.glass.effects.GlassSky] (HIGH/BALANCED blur it, LITE stays translucent),
 * which is why the pane itself carries no hand-written blur — the material comes from
 * [com.easytier.ui.glass.components.GlassSurface] alone.
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
    val topBarHeight = 56.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 页面底色（与 MainScreen 的 GlassSky 同一组渐变；图片背景下半透明让壁纸透出）：
            // 二级页进出转场时两张页面同时在场，渐变模式下不透明才不会互相透出来。
            .background(settingsPageBackdrop(colors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = topBarHeight + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )

        // Floating glass top bar (overlay layer) — back glyph + centered title, unchanged.
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            radius = GlassRadius.md,
            shadowElevation = 8.dp,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight - 8.dp)
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
        }
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
    val restartShape = remember { glassShape(GlassRadius.sm) }
    var logEnabled by remember { mutableStateOf(com.easytier.AppLogger.enabled) }

    val memoryLevel = config.memoryLimit.ifEmpty { com.easytier.RuntimeTuning.MEMORY_STANDARD }

    SettingsSubPage(title = T("常规", "General", langZh), onBack = onBack) {
        // 1. 权限管理 — top of General
        SettingsNavRow(
            icon = "🛡",
            title = T("权限管理", "Permissions", langZh),
            subtitle = T("通知、VPN、电池优化与安装", "Notifications, VPN, battery & install", langZh),
            colors = colors,
            onClick = onOpenPermissions
        )

        // 顺序：常用开关在前 → 动作在后 → 相关聚合
        // 1. 省电模式 — slows the polling/notification cadence in the background
        //    and injects EasyTier power flags (disable_upnp) into the generated TOML.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 分组卡片：稳定玻璃材质（高不透明度填充 + 发丝描边，无背景模糊）
                .glassCard(radius = GlassRadius.sm)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsRowIcon("🔋")
                    Spacer(Modifier.width(SettingsIconGap))
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
            icon = "💾",
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
                .glassCard(radius = GlassRadius.sm)
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
                SettingsRowIcon("≡")
                Spacer(Modifier.width(SettingsIconGap))
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

        // 4. 重启核心 — eye-catching action row: red button instead of a chevron
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(radius = GlassRadius.sm)
                .clickable { onRestartCore() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsRowIcon("↻")
                Spacer(Modifier.width(SettingsIconGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        T("重启核心", "Restart Core", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        T("断开并重新拉起 EasyTier 实例", "Tear down and restart the EasyTier core", langZh),
                        color = colors.textTertiary, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        // 状态红底 + 玻璃薄雾/描边（语义色不动，只叠材质；与弹窗主按钮同一手法）
                        .clip(restartShape)
                        .background(colors.statusBad, restartShape)
                        .glassSurface(radius = GlassRadius.sm, shape = restartShape, intensity = 0.45f, paintFill = false)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        T("重启", "Restart", langZh),
                        color = ComposeColor.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
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
    bgMode: BgMode = BgMode.GRADIENT,
    onBgModeChange: (BgMode) -> Unit = {},
    bgBusy: Boolean = false,
    onRefreshBg: () -> Unit = {},
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
                .glassCard(radius = GlassRadius.md)
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

        // ── 外观：Liquid Glass 开关 ──
        // 位置就在「主题」分组下面（设置 → 主题 → 外观），是整棵树唯一的外观开关：
        //   · 33+：开关默认 ON（默认 LIQUID_GLASS），关掉立即全 App 切 Universal；
        //   · <33：不显示开关（强制 Universal），只留一句说明。
        // 开关只调用 LocalSetThemeMode → 改 CompositionLocal 状态 → 重组；不重建 Activity。
        Text(
            T("外观", "Appearance", langZh),
            color = colors.textTertiary,
            fontSize = 12.sp
        )

        val mode = LocalThemeMode.current
        val setMode = LocalSetThemeMode.current
        val glassCapable = AppThemeManager.isGlassCapable()
        val glassOn = mode == ThemeMode.LIQUID_GLASS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(radius = GlassRadius.md)
                .then(
                    if (glassCapable) {
                        Modifier.clickable {
                            setMode(
                                if (glassOn) ThemeMode.UNIVERSAL else ThemeMode.LIQUID_GLASS
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowIcon("✦")
            Spacer(Modifier.width(SettingsIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    T("Liquid Glass 效果", "Liquid Glass effects", langZh),
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (glassCapable) {
                        T(
                            "开启后使用 iOS 26 风格的液态玻璃视觉效果，可能增加图形渲染负载",
                            "Enables iOS 26-style liquid glass; may increase GPU load",
                            langZh
                        )
                    } else {
                        T(
                            "当前系统版本自动使用通用界面（Universal UI）",
                            "This Android version always uses Universal UI",
                            langZh
                        )
                    },
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (glassCapable) {
                Spacer(Modifier.width(10.dp))
                HmSwitch(
                    checked = glassOn,
                    onCheckedChange = { on ->
                        setMode(if (on) ThemeMode.LIQUID_GLASS else ThemeMode.UNIVERSAL)
                    },
                    colors = colors
                )
            }
        }

        // ── 背景壁纸 ──
        // 渐变 = 老的主题底图（离线可用）；在线二次元 = 随机图片 + 自动暗化蒙版；
        // 关闭 = 纯色。选择即写 SharedPreferences(easytier_theme/bg_mode)，MainScreen 直接生效。
        Text(
            T("背景壁纸", "Background wallpaper", langZh),
            color = colors.textTertiary,
            fontSize = 12.sp
        )

        HmDropdownField(
            label = T("背景模式", "Background", langZh),
            icon = "🖼",
            options = listOf(
                HmDropdownOption(
                    label = T("渐变（默认）", "Gradient (default)", langZh),
                    value = BgMode.GRADIENT.key,
                    hint = T("主题渐变 + 柔光，离线可用、最省电", "Theme gradient + glow, offline & cheapest", langZh)
                ),
                HmDropdownOption(
                    label = T("在线二次元（静态）", "Online anime (static)", langZh),
                    value = BgMode.ONLINE.key,
                    hint = T("随机二次元图片，自动暗化蒙版；下次启动先用缓存图", "Random anime artwork with a scrim; cached art shows first on next launch", langZh)
                ),
                HmDropdownOption(
                    label = T("关闭", "Off", langZh),
                    value = BgMode.OFF.key,
                    hint = T("纯色背景，不加载任何图片", "Flat theme colour, no images at all", langZh)
                )
            ),
            selectedIndex = when (bgMode) {
                BgMode.GRADIENT -> 0
                BgMode.ONLINE -> 1
                BgMode.OFF -> 2
            },
            onSelect = { index ->
                when (index) {
                    1 -> onBgModeChange(BgMode.ONLINE)
                    2 -> onBgModeChange(BgMode.OFF)
                    else -> onBgModeChange(BgMode.GRADIENT)
                }
            },
            colors = colors,
            hint = T(
                "图片背景会自动压一层蒙版（深色主题暗化 35%），保证卡片上的文字仍然清晰。",
                "Image backdrops get an automatic scrim (35% darkening in dark theme) so text stays legible.",
                langZh
            ),
            card = true
        )

        // 换一张：仅在线模式可见
        if (bgMode == BgMode.ONLINE) {
            BgRefreshRow(
                langZh = langZh,
                busy = bgBusy,
                colors = colors,
                onClick = onRefreshBg
            )
        }

        // 预留位：以后的主题卡片（配色包 / 图标风格）挂在这里
        // 占位观感只靠内容卡玻璃材质本身（稳定填充 + 发丝描边，低对比），不再叠第二层灰罩
        // —— 两层材质圆角/填充不一致会在边缘露白。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(radius = GlassRadius.md)
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

/**
 * 「换一张」：只在在线背景模式下出现。
 *
 * 与 GeneralPage 的「重启核心」行同一套结构（内容卡玻璃材质 + 24dp 图标位 + 右侧实色
 * 药丸按钮），只是把红色换成品牌色；下载中整行禁用并显示进度文案。
 */
@Composable
private fun BgRefreshRow(
    langZh: Boolean,
    busy: Boolean,
    colors: AppColors,
    onClick: () -> Unit
) {
    val glowShape = remember { glassShape(GlassRadius.sm) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(radius = GlassRadius.sm)
            .clickable(enabled = !busy) { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowIcon("↻")
            Spacer(Modifier.width(SettingsIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    T("换一张", "Shuffle wallpaper", langZh),
                    color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    if (busy) T("正在下载新壁纸…", "Downloading a new wallpaper…", langZh)
                    else T("重新拉取一张随机二次元图片", "Fetch another random anime artwork", langZh),
                    color = colors.textTertiary, fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    // 品牌色（或禁用态中性底）+ 玻璃薄雾/描边，与重启按钮同一手法
                    .clip(glowShape)
                    .background(if (busy) colors.inputBg else colors.accent, glowShape)
                    .glassSurface(radius = GlassRadius.sm, shape = glowShape, intensity = 0.45f, paintFill = false)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    T("换一张", "Shuffle", langZh),
                    color = if (busy) colors.textTertiary else ComposeColor.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
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
    val glass = glassColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(glassShape(GlassRadius.sm))
            .background(if (selected) glass.accentSoft else Color.Transparent)
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
                .clip(GlassShapes.capsule)
                .border(
                    1.5.dp,
                    if (selected) colors.accent else colors.inputBorder,
                    GlassShapes.capsule
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(GlassShapes.capsule)
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
    val glass = glassColors()
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
            .glassCard(radius = 12.dp)
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
                        .clip(GlassShapes.capsule)
                        .background(statusBg, GlassShapes.capsule)
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
                        // 行内动作芯片：胶囊 + 品牌柔色（accentSoft），点击区抬到 44dp
                        .clip(GlassShapes.capsule)
                        .background(glass.accentSoft, GlassShapes.capsule)
                        .clickable { onAction() }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
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
