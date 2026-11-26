package com.easytier.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import java.io.File

private enum class Tab { STATUS, CONFIG, LOGS, MORE }

// ═══════════════════════════════════════════════════
//  MainScreen — 3-tab layout with bottom navigation
// ═══════════════════════════════════════════════════

@Composable
fun MainScreen(
    connectionState: ConnectionButtonState,
    onToggleConnection: () -> Unit,
    config: ConfigState,
    onConfigChange: (ConfigState) -> Unit,
    currentConfigName: String,
    langZh: Boolean,
    onConfigNameClick: () -> Unit,
    onSaveConfig: () -> Unit,
    onAutoSaveConfig: () -> Unit = {},
    onManageConfigs: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onDeleteConfig: () -> Unit,
    myNodeItem: PeerInfoItem?,
    peerItems: List<PeerInfoItem>,
    onConfigServer: () -> Unit,
    onToggleLanguage: () -> Unit,
    onLaunchConfigEditor: () -> Unit,
    // Kept for caller compatibility: the log entry now lives in Settings → View Logs,
    // which switches to the Logs tab instead of opening a dialog.
    @Suppress("UNUSED_PARAMETER") onViewLogs: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    dynamicColor: Boolean,
    onToggleDynamic: () -> Unit,
    connectionStartTime: Long = 0L,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    var currentTab by remember { mutableStateOf(Tab.STATUS) }
    var showAboutPage by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(colors.bgStart, colors.bgMid, colors.bgEnd))
                )
        )

        // Decorative glows
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(280.dp)
                .offset(x = 60.dp, y = (-80).dp)
                .clip(RoundedCornerShape(140.dp))
                .background(colors.glowColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(220.dp)
                .offset(x = (-80).dp, y = 160.dp)
                .clip(RoundedCornerShape(110.dp))
                .background(colors.glowColorDark)
        )

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (showAboutPage) 0.dp else 72.dp)
        ) {
            if (showAboutPage) {
                // System back → leave the About page (back to "More"), not exit the app.
                BackHandler { showAboutPage = false }
                AboutPage(
                    langZh = langZh,
                    isConnected = connectionState.status == ConnectionStatus.RUNNING
                            || connectionState.status == ConnectionStatus.NO_TUN,
                    connectionStartTime = connectionStartTime,
                    onBack = { showAboutPage = false }
                )
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(tween(300, easing = HarmonyEasing)) +
                            slideInHorizontally(initialOffsetX = { it / 6 }))
                            .togetherWith(
                                fadeOut(tween(200, easing = HarmonyEasing)) +
                                    slideOutHorizontally(targetOffsetX = { -it / 6 })
                            )
                    },
                    label = "tabTransition"
                ) { tab ->
                when (tab) {
                    Tab.STATUS -> StatusTab(
                    connectionState = connectionState,
                    onToggleConnection = onToggleConnection,
                    myNodeItem = myNodeItem,
                    peerItems = peerItems,
                    langZh = langZh,
                    connectionStartTime = connectionStartTime
                )
                Tab.CONFIG -> ConfigTab(
                    config = config,
                    onConfigChange = onConfigChange,
                    currentConfigName = currentConfigName,
                    langZh = langZh,
                    onConfigNameClick = onConfigNameClick,
                    onSaveConfig = onSaveConfig,
                    onAutoSaveConfig = onAutoSaveConfig,
                    onManageConfigs = onManageConfigs,
                    onImportToml = onImportToml,
                    onExportToml = onExportToml,
                    onDeleteConfig = onDeleteConfig,
                    onLaunchConfigEditor = onLaunchConfigEditor,
                    onApplyConfig = {
                        onToggleConnection()
                        currentTab = Tab.STATUS
                    }
                )
                Tab.MORE -> MoreTab(
                    langZh = langZh,
                    onToggleLanguage = onToggleLanguage,
                    onConfigServer = onConfigServer,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    dynamicColor = dynamicColor,
                    onToggleDynamic = onToggleDynamic,
                    onNavigateToAbout = { showAboutPage = true },
                    onToggleConnection = onToggleConnection,
                    onOpenLogs = { currentTab = Tab.LOGS },
                    isConnected = connectionState.status == ConnectionStatus.RUNNING
                            || connectionState.status == ConnectionStatus.NO_TUN,
                    connectionStartTime = connectionStartTime
                )
                Tab.LOGS -> LogsTab(langZh = langZh)
                }
                }
            }
        }

        // Bottom Navigation
        if (!showAboutPage) {
            BottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                langZh = langZh,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  Logs Tab — live scrolling log view
// ═══════════════════════════════════════════════════

@Composable
private fun LogsTab(langZh: Boolean) {
    val colors = currentColors()
    var collectionOn by remember { mutableStateOf(com.easytier.AppLogger.enabled) }
    var logs by remember { mutableStateOf(if (collectionOn) com.easytier.AppLogger.getLogs() else "") }
    LaunchedEffect(Unit) {
        while (true) {
            collectionOn = com.easytier.AppLogger.enabled
            logs = if (collectionOn) com.easytier.AppLogger.getLogs() else ""
            delay(2000L)
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(logs) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                T("运行日志", "Runtime Logs", langZh),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (collectionOn) colors.statusGood else colors.textTertiary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (collectionOn) T("自动刷新", "Auto-refresh", langZh)
                    else T("已关闭", "Off", langZh),
                    color = colors.textTertiary, fontSize = 11.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(12.dp)
        ) {
            when {
                !collectionOn -> Text(
                    T(
                        "日志收集已关闭，请到设置开启",
                        "Log collection is off — enable it in Settings",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 13.sp
                )
                logs.isEmpty() -> Text(
                    T("暂无日志", "No logs yet", langZh),
                    color = colors.textTertiary,
                    fontSize = 13.sp
                )
                else -> Text(
                    text = logs,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

// ═══════════════════════════════════════════════════
//  Bottom Navigation
// ═══════════════════════════════════════════════════

@Composable
private fun BottomNavigationBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    langZh: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(24.dp)
    val pillShape = RoundedCornerShape(16.dp)
    // Glass base: keep it translucent so the list underneath bleeds through
    val glassBase = colors.navGlass.copy(alpha = if (isDark) 0.82f else 0.74f)
    val edgeTop = Color.White.copy(alpha = if (isDark) 0.22f else 0.70f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        // ── Liquid glass panel ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // soft outer shadow → the bar floats above the content
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = 0.26f)
                )
                .clip(shape)
                // layer 1: translucent glass tint base
                .background(glassBase)
                // layer 2: bright top → deeper bottom (light passing through glass)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (isDark) 0f else 0.05f)
                        )
                    )
                )
                // layer 3: edge stroke — bright hairline up top, faint around
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            edgeTop,
                            colors.navGlassBorder
                        )
                    ),
                    shape = shape
                )
        ) {
            // Specular highlight: 1dp white line along the top edge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(Icons.Rounded.Dashboard, T("仪表盘", "Dashboard", langZh), Tab.STATUS),
                    Triple(Icons.Rounded.Description, T("配置", "Config", langZh), Tab.CONFIG),
                    Triple(Icons.Rounded.Subject, T("日志", "Logs", langZh), Tab.LOGS),
                    Triple(Icons.Rounded.Build, T("设置", "Settings", langZh), Tab.MORE)
                ).forEach { (icon, label, tab) ->
                    val isActive = currentTab == tab
                    // Liquid pill: fades/grows in with the HarmonyOS easing
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0f,
                        animationSpec = tween(300, easing = HarmonyEasing),
                        label = "navPill"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else colors.textTertiary,
                        animationSpec = tween(300, easing = HarmonyEasing),
                        label = "navTint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(pillShape)
                            // active pill = blue gradient, alpha animated so it melts in
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.accentStart.copy(alpha = pillAlpha),
                                        colors.accentEnd.copy(alpha = pillAlpha)
                                    )
                                )
                            )
                            .elasticPress { onTabSelected(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // glossy sheen on the upper half of the pill
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.White.copy(alpha = 0.18f * pillAlpha),
                                            0.6f to Color.White.copy(alpha = 0.04f * pillAlpha),
                                            1f to Color.Transparent
                                        )
                                    )
                                )
                        )
                        // hairline rim so the pill reads as a liquid blob
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(
                                    0.5.dp,
                                    Color.White.copy(alpha = 0.25f * pillAlpha),
                                    pillShape
                                )
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = label,
                                color = contentColor,
                                fontSize = 10.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Status Tab
// ═══════════════════════════════════════════════════

@Composable
private fun StatusTab(
    connectionState: ConnectionButtonState,
    onToggleConnection: () -> Unit,
    myNodeItem: PeerInfoItem?,
    peerItems: List<PeerInfoItem>,
    langZh: Boolean,
    connectionStartTime: Long = 0L
) {
    val scrollState = rememberScrollState()
    val isConnected = connectionState.status == ConnectionStatus.RUNNING ||
                      connectionState.status == ConnectionStatus.NO_TUN

    // ── Uptime ticker ──
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectionStartTime, isConnected) {
        while (isConnected && connectionStartTime > 0L) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val uptime = if (isConnected && connectionStartTime > 0L) {
        val sec = (now - connectionStartTime) / 1000
        "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    } else ""

    // ── Speed history for sparkline (sample on change, keep 40 pts) ──
    val speedHistory = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val rxSpeed = myNodeItem?.rxSpeed ?: ""
    LaunchedEffect(rxSpeed) {
        val v = parseSpeedValue(rxSpeed)
        if (v > 0f) {
            speedHistory.add(v)
            if (speedHistory.size > 40) speedHistory.removeAt(0)
        }
    }

    fun fmtBytes(b: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, units[i])
    }

    val peerCount = (if (myNodeItem != null) 1 else 0) + peerItems.size
    val latency = myNodeItem?.let {
        if (it.latencyUs > 0) "${it.latencyUs / 1000} ms" else ""
    } ?: ""

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── FlClash-style status card with blob button ──
            FlClashStatusCard(
                state = connectionState,
                onClick = onToggleConnection,
                nodeLabel = myNodeItem?.ip ?: connectionState.subLabel,
                nodeSub = myNodeItem?.let { "${it.hostname}  ·  ${it.protocol}" } ?: "",
                uptime = uptime,
                peerCount = peerCount,
                latency = latency,
                langZh = langZh
            )

            // ── Speed cards (only when running) ──
            if (isConnected && myNodeItem != null) {
                SpeedStatRow(
                    rxSpeed = rxSpeed,
                    txSpeed = myNodeItem.txSpeed,
                    rxTotal = fmtBytes(myNodeItem.rxBytes),
                    txTotal = fmtBytes(myNodeItem.txBytes),
                    history = speedHistory,
                    langZh = langZh
                )
            }

            PeerInfoSection(
                myNode = myNodeItem,
                peers = peerItems,
                expanded = true,
                onToggle = {},
                langZh = langZh
            )

            Spacer(Modifier.height(80.dp))
        }
        // ControlFAB removed — connect button now lives inside the status card
    }
}

// ═══════════════════════════════════════════════════
//  Config Tab
// ═══════════════════════════════════════════════════

@Composable
private fun ConfigTab(
    config: ConfigState,
    onConfigChange: (ConfigState) -> Unit,
    currentConfigName: String,
    langZh: Boolean,
    onConfigNameClick: () -> Unit,
    onSaveConfig: () -> Unit,
    onAutoSaveConfig: () -> Unit,
    onManageConfigs: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onDeleteConfig: () -> Unit,
    onLaunchConfigEditor: () -> Unit,
    onApplyConfig: () -> Unit
) {
    val colors = currentColors()
    val scrollState = rememberScrollState()
    var hasUnsaved by remember { mutableStateOf(false) }
    var lastConfig by remember { mutableStateOf(config) }

    // ⏱ Debounced auto-save: when config text fields change, wait 3s idle then auto-save
    LaunchedEffect(config) {
        if (config != lastConfig) {
            hasUnsaved = true
            lastConfig = config
            delay(3000L)
            onAutoSaveConfig()
            hasUnsaved = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // ── Config Management Bar ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1: Config name selector + unsaved indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.inputBg)
                            .clickable { onConfigNameClick() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("▪", fontSize = 14.sp, color = colors.textTertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                currentConfigName,
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            if (hasUnsaved) {
                                Text(
                                    "● ${T("未保存", "Unsaved", langZh)}",
                                    color = colors.accentLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("▼", color = colors.textTertiary, fontSize = 10.sp)
                        }
                    }
                }

                // Row 2: Action buttons (reflow on small screens)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionBtn(
                        text = T("💾 保存", "💾 Save", langZh),
                        onClick = onSaveConfig,
                        colors = colors,
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("📥 导入", "📥 Import", langZh),
                        onClick = onImportToml,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("📤 导出", "📤 Export", langZh),
                        onClick = onExportToml,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Secondary actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionBtn(
                        text = T("📋 管理", "📋 Manage", langZh),
                        onClick = onManageConfigs,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("✏️ 编辑器", "✏️ Editor", langZh),
                        onClick = onLaunchConfigEditor,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("🗑 删除", "🗑 Delete", langZh),
                        onClick = onDeleteConfig,
                        colors = colors,
                        danger = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Config Form ──
        ConfigForm(
            config = config,
            langZh = langZh,
            onConfigChange = onConfigChange
        )

        // ── Apply Button ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accentGradient)
                .clickable {
                    // Save before applying
                    onAutoSaveConfig()
                    onApplyConfig()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▸ ${T("应用并启动", "Apply & Start", langZh)}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionBtn(
    text: String,
    onClick: () -> Unit,
    colors: AppColors,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false
) {
    val textColor = when {
        accent -> Color.White
        danger -> colors.dangerText
        else -> colors.textSecondary
    }
    val bgMod = when {
        accent -> Modifier.background(colors.accentGradient)
        danger -> Modifier.background(colors.dangerBg)
        else -> Modifier.background(colors.inputBg)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(bgMod)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════════
//  Settings Tab (previously "More") — features, prefs & about
// ═══════════════════════════════════════════════════

@Composable
private fun MoreTab(
    langZh: Boolean,
    onToggleLanguage: () -> Unit,
    onConfigServer: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    dynamicColor: Boolean,
    onToggleDynamic: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onToggleConnection: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    isConnected: Boolean = false,
    connectionStartTime: Long = 0L
) {
    val ctx = LocalContext.current
    val version: String = remember {
        try {
            val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pkg.versionName ?: "0.1.5"
        } catch (_: Exception) { "0.1.5" }
    }
    val colors = currentColors()
    val scrollState = rememberScrollState()

    // ── Update check state ──
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateDialogVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ── Language / Theme pickers + log collection ──
    var langDialogVisible by remember { mutableStateOf(false) }
    var themeDialogVisible by remember { mutableStateOf(false) }
    var logEnabled by remember { mutableStateOf(com.easytier.AppLogger.enabled) }

    // 当前主题模式：0 = 浅色, 1 = 深色, 2 = 跟随系统
    val themeIndex = when {
        dynamicColor -> 2
        isDarkTheme -> 1
        else -> 0
    }
    val themeValue = when (themeIndex) {
        2 -> T("跟随系统", "System", langZh)
        1 -> T("深色", "Dark", langZh)
        else -> T("浅色", "Light", langZh)
    }

    // Live uptime ticker
    var uptimeText by remember { mutableStateOf("") }
    LaunchedEffect(isConnected, connectionStartTime) {
        if (!isConnected || connectionStartTime <= 0L) {
            uptimeText = ""
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = System.currentTimeMillis() - connectionStartTime
            val secs = (elapsed / 1000) % 60
            val mins = (elapsed / 60000) % 60
            val hours = elapsed / 3600000
            uptimeText = if (hours > 0)
                String.format("%dh %dm %ds", hours, mins, secs)
            else
                String.format("%dm %ds", mins, secs)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Config Server Entry
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                .clickable { onConfigServer() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        T("配置服务器", "Config Server", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        T("从远端 URL 一键拉取 TOML 配置", "Fetch TOML config from URL", langZh),
                        color = colors.textTertiary, fontSize = 12.sp
                    )
                }
                Text("›", color = colors.textTertiary, fontSize = 18.sp)
            }
        }

        // Restart Core — same call chain as the old dashboard restart button
        SettingsNavRow(
            icon = "↻",
            title = T("重启核心", "Restart Core", langZh),
            subtitle = T("断开并重新拉起 EasyTier 实例", "Tear down and restart the EasyTier core", langZh),
            colors = colors,
            onClick = { onToggleConnection() }
        )

        // View Logs — jumps to the Logs tab
        SettingsNavRow(
            icon = "☰",
            title = T("查看日志", "View Logs", langZh),
            subtitle = T("打开日志页面查看运行记录", "Open the logs page", langZh),
            colors = colors,
            onClick = { onOpenLogs() }
        )

        // Log collection toggle (AppLogger.setEnabled)
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
                HmToggle(
                    label = if (logEnabled) T("已开启", "On", langZh) else T("已关闭", "Off", langZh),
                    checked = logEnabled,
                    onCheckedChange = { next ->
                        logEnabled = next
                        com.easytier.AppLogger.setEnabled(next)
                    },
                    colors = colors
                )
            }
        }

        // Language — current value + chevron, opens an Apple-style radio sheet
        SettingsNavRow(
            icon = "⋮",
            title = T("语言", "Language", langZh),
            value = if (langZh) "中文" else "English",
            colors = colors,
            onClick = { langDialogVisible = true }
        )

        // Theme — current value + chevron, opens a 3-way radio sheet
        SettingsNavRow(
            icon = if (isDarkTheme) "●" else "○",
            title = T("主题", "Theme", langZh),
            value = themeValue,
            colors = colors,
            onClick = { themeDialogVisible = true }
        )

        // Check Update card (clickable → dialog with download options)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !checking) {
                    checking = true
                    scope.launch {
                        try {
                            val info = withContext(Dispatchers.IO) { fetchLatestRelease() }
                            if (isNewerVersion(info.tag, version)) {
                                updateInfo = info
                                updateDialogVisible = true
                            } else {
                                Toast.makeText(
                                    ctx,
                                    T("已是最新版本 v$version", "Already up to date (v$version)", langZh),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                ctx,
                                T(
                                    "检查更新失败：${e.message ?: "unknown"}",
                                    "Update check failed: ${e.message ?: "unknown"}",
                                    langZh
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            checking = false
                        }
                    }
                }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (checking) "⋯" else "⟳", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        T("检查更新", "Check Update", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (checking) T("正在检查…", "Checking…", langZh)
                        else T("从 GitHub 获取最新版本", "Fetch the latest release from GitHub", langZh),
                        color = colors.textTertiary, fontSize = 12.sp
                    )
                }
                Text("›", color = colors.textTertiary, fontSize = 18.sp)
            }
        }

        // About Card (clickable → navigates to AboutPage)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                .clickable { onNavigateToAbout() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◇", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        T("关于 Orbit", "About Orbit", langZh),
                        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        T("查看版本、描述和运行信息", "Version, description & runtime info", langZh),
                        color = colors.textTertiary, fontSize = 12.sp
                    )
                }
                Text("›", color = colors.textTertiary, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // Update dialog (outside the scroll column so it overlays the whole tab)
    UpdateDialog(
        visible = updateDialogVisible,
        info = updateInfo,
        currentVersion = version,
        langZh = langZh,
        onDownloadApk = {
            val info = updateInfo
            val apkUrl = info?.apkUrl
            if (info == null || apkUrl.isNullOrBlank()) {
                Toast.makeText(
                    ctx,
                    T("该版本未提供 APK 资源", "No APK asset in this release", langZh),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startApkDownload(ctx, apkUrl, info.tag, scope, langZh)
            }
        },
        onOpenBrowser = {
            val url = updateInfo?.htmlUrl
            if (!url.isNullOrBlank()) {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        T("无法打开浏览器", "Cannot open browser", langZh),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onDismiss = { updateDialogVisible = false }
    )

    // Language picker — Apple-style radio sheet
    SettingsChoiceDialog(
        visible = langDialogVisible,
        title = T("语言", "Language", langZh),
        options = listOf("简体中文", "English"),
        selectedIndex = if (langZh) 0 else 1,
        langZh = langZh,
        onSelect = { idx ->
            if (idx == 0 && !langZh) onToggleLanguage()
            if (idx == 1 && langZh) onToggleLanguage()
            langDialogVisible = false
        },
        onDismiss = { langDialogVisible = false }
    )

    // Theme picker — light / dark / follow-system, same state machine as the old pills
    SettingsChoiceDialog(
        visible = themeDialogVisible,
        title = T("主题", "Theme", langZh),
        options = listOf(
            T("浅色", "Light", langZh),
            T("深色", "Dark", langZh),
            T("跟随系统", "System", langZh)
        ),
        selectedIndex = themeIndex,
        langZh = langZh,
        onSelect = { idx ->
            when (idx) {
                0 -> { if (dynamicColor) onToggleDynamic(); if (isDarkTheme) onToggleTheme() }
                1 -> { if (dynamicColor) onToggleDynamic(); if (!isDarkTheme) onToggleTheme() }
                2 -> { if (!dynamicColor) onToggleDynamic() }
            }
            themeDialogVisible = false
        },
        onDismiss = { themeDialogVisible = false }
    )
}

/**
 * Settings list row: icon + title (+ optional subtitle) on the left,
 * current value and a chevron on the right.
 */
@Composable
private fun SettingsNavRow(
    icon: String,
    title: String,
    colors: AppColors,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(subtitle, color = colors.textTertiary, fontSize = 12.sp)
                }
            }
            if (value != null) {
                Text(value, color = colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text("›", color = colors.textTertiary, fontSize = 18.sp)
        }
    }
}

/**
 * Apple-style single-choice sheet: one option per row, the selected row shows a
 * check mark. Enter/exit mirrors the other dialogs (scale + fade with HarmonyEasing).
 */
@Composable
private fun SettingsChoiceDialog(
    visible: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    langZh: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = currentColors()

    // Keep the Dialog mounted until the exit animation finishes.
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300, easing = HarmonyEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = HarmonyEasing)),
            exit = fadeOut(tween(300, easing = HarmonyEasing)) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(300, easing = HarmonyEasing))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .flGlassCard(colors, 20.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)
                )
                options.forEachIndexed { idx, label ->
                    val selected = idx == selectedIndex
                    val rowBg by animateColorAsState(
                        targetValue = if (selected) colors.accentContainer else Color.Transparent,
                        animationSpec = tween(220, easing = HarmonyEasing),
                        label = "choiceRowBg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg)
                            .clickable { onSelect(idx) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.accentLight else colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.weight(1f))
                        if (selected) {
                            Text("✓", color = colors.accentLight, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = T("取消", "Cancel", langZh),
                        color = colors.textTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  AboutPage — full-screen about screen
// ═══════════════════════════════════════════════════

@Composable
fun AboutPage(
    langZh: Boolean,
    isConnected: Boolean = false,
    connectionStartTime: Long = 0L,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val version: String = remember {
        try {
            val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pkg.versionName ?: "0.1.5"
        } catch (_: Exception) { "0.1.5" }
    }
    val colors = currentColors()
    val scrollState = rememberScrollState()

    // Live uptime ticker
    var uptimeText by remember { mutableStateOf("") }
    LaunchedEffect(isConnected, connectionStartTime) {
        if (!isConnected || connectionStartTime <= 0L) {
            uptimeText = ""
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = System.currentTimeMillis() - connectionStartTime
            val secs = (elapsed / 1000) % 60
            val mins = (elapsed / 60000) % 60
            val hours = elapsed / 3600000
            uptimeText = if (hours > 0)
                String.format("%dh %dm %ds", hours, mins, secs)
            else
                String.format("%dm %ds", mins, secs)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.bgStart, colors.bgMid, colors.bgEnd))
            )
    ) {
        // Back button + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("← " + T("返回", "Back", langZh), color = colors.accentLight, fontSize = 13.sp)
            }
            Text(
                T("关于 Orbit", "About Orbit", langZh),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // About card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EasyTier",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = T("版本：2.6.4-8428a89d", "Version: 2.6.4-8428a89d", langZh),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = T(
                            "一个简单、安全、去中心化的内网穿透 VPN 组网方案，使用 Rust 语言和 Tokio 框架实现。",
                            "A simple, secure, decentralized intranet penetration VPN mesh solution, built with Rust and Tokio.",
                            langZh
                        ),
                        color = colors.textTertiary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = T("此版本由涵编译。", "This build by Han.", langZh),
                        color = colors.textTertiary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Official EasyTier link
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accentContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("◎ ", color = colors.accentLight, fontSize = 12.sp)
                            Text(
                                "github.com/EasyTier/EasyTier",
                                color = colors.accentLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // User's fork link
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceDim)
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/amelia4angela/orbit-easytier-android")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("◇ ", color = colors.accentLight, fontSize = 12.sp)
                            Text(
                                "amelia4angela · 编译版",
                                color = colors.accentLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }

                    // Version info
                    Spacer(Modifier.height(10.dp))

                    var updateStatus by remember { mutableStateOf<String?>(null) }
                    var checking by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceDim)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = T("应用版本", "App Version", langZh),
                                    color = colors.textTertiary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "v$version",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Check update button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (checking) colors.textTertiary.copy(alpha = 0.15f)
                                        else colors.accentContainer.copy(alpha = 0.5f)
                                    )
                                    .clickable(enabled = !checking) {
                                        checking = true
                                        updateStatus = null
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    val conn = URL(
                                                        "https://api.github.com/repos/amelia4angela/orbit-easytier-android/releases/latest"
                                                    ).openConnection() as HttpURLConnection
                                                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                                                    conn.setRequestProperty("User-Agent", "EasyTier-Android")
                                                    conn.connectTimeout = 10000
                                                    conn.readTimeout = 10000
                                                    val code = conn.responseCode
                                                    if (code == 200) {
                                                        val body = conn.inputStream.bufferedReader().readText()
                                                        JSONObject(body).getString("tag_name")
                                                    } else {
                                                        null
                                                    }
                                                }
                                                updateStatus = if (result != null) {
                                                    val latest = result.removePrefix("v")
                                                    val current = version
                                                    if (latest > current) {
                                                        "new:$result"
                                                    } else {
                                                        "latest"
                                                    }
                                                } else {
                                                    "error"
                                                }
                                            } catch (_: Exception) {
                                                updateStatus = "error"
                                            }
                                            checking = false
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (checking) {
                                        Text("⋯", color = colors.textTertiary, fontSize = 12.sp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = T("检查更新", "Check Update", langZh),
                                        color = colors.accentLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Update status
                    if (updateStatus != null) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        updateStatus == "latest" -> colors.statusGood.copy(alpha = 0.1f)
                                        updateStatus == "error" -> colors.statusBad.copy(alpha = 0.1f)
                                        updateStatus!!.startsWith("new:") -> colors.statusWarn.copy(alpha = 0.1f)
                                        else -> colors.surfaceDim
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    updateStatus == "latest" ->
                                        T("✔ 已是最新版本 v$version", "✔ Latest version v$version", langZh)
                                    updateStatus!!.startsWith("new:") -> {
                                        val ver = updateStatus!!.removePrefix("new:")
                                        T("发现新版本 $ver，点击上方链接下载", "New version $ver, tap link above to download", langZh)
                                    }
                                    else -> T("检查失败，请检查网络连接", "Check failed, check network", langZh)
                                },
                                color = when {
                                    updateStatus == "latest" -> colors.statusGood
                                    updateStatus!!.startsWith("new:") -> colors.statusWarn
                                    else -> colors.statusBad
                                },
                                fontSize = 12.sp
                            )
                        }
                        // Click on "new:" to open release page
                        if (updateStatus != null && updateStatus!!.startsWith("new:")) {
                            val ver = updateStatus!!.removePrefix("new:")
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.accentContainer.copy(alpha = 0.3f))
                                    .clickable {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                "https://github.com/amelia4angela/orbit-easytier-android/releases/tag/$ver"
                                            )
                                        )
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = T("前往下载 →", "Go to Download →", langZh),
                                    color = colors.accentLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    if (isConnected && uptimeText.isNotEmpty()) {
                        AboutRow(
                            T("运行时长", "Uptime", langZh),
                            uptimeText, colors
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.surfaceBorder.copy(alpha = 0.5f))
                        )

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Small helpers
// ═══════════════════════════════════════════════════

@Composable
private fun AboutRow(label: String, value: String?, colors: AppColors) {
    if (value != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.textSecondary, fontSize = 14.sp)
            Text(value, color = colors.textPrimary, fontSize = 14.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.surfaceBorder.copy(alpha = 0.5f))
        )
    }
}

// ═══════════════════════════════════════════════════
//  Update check — API, version compare, download
// ═══════════════════════════════════════════════════

private const val UPDATE_API =
    "https://api.github.com/repos/amelia4angela/orbit-easytier-android/releases/latest"

data class UpdateInfo(
    val tag: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?
)

/** Blocking GET of the latest release. Always call from Dispatchers.IO. */
private fun fetchLatestRelease(): UpdateInfo {
    val conn = URL(UPDATE_API).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "Orbit-Android")
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP $code")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        var apkUrl: String? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                    val url = asset.optString("browser_download_url", "")
                    if (url.isNotEmpty()) {
                        apkUrl = url
                        break
                    }
                }
            }
        }
        return UpdateInfo(
            tag = root.optString("tag_name", "").trim(),
            body = if (root.isNull("body")) "" else root.optString("body", ""),
            htmlUrl = root.optString("html_url", ""),
            apkUrl = apkUrl
        )
    } finally {
        conn.disconnect()
    }
}

/** "v0.1.8" / "0.1.8-beta" → [0, 1, 8]; non-numeric segments are skipped. */
private fun versionParts(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '+', '_', ' ')
        .mapNotNull { seg ->
            val digits = seg.takeWhile { it.isDigit() }
            if (digits.isEmpty()) null else digits.toIntOrNull()
        }

/** Numeric segment-wise comparison; true only when [latest] is strictly newer. */
private fun isNewerVersion(latest: String, current: String): Boolean {
    val a = versionParts(latest)
    val b = versionParts(current)
    if (a.isEmpty() || b.isEmpty()) return latest.trim() != current.trim()
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Download the APK via the system DownloadManager, then offer to install it. */
private fun startApkDownload(
    ctx: Context,
    apkUrl: String,
    tag: String,
    scope: kotlinx.coroutines.CoroutineScope,
    langZh: Boolean
) {
    val fileName = run {
        val fromUrl = apkUrl.substringAfterLast('/').substringBefore('?')
        val base = if (fromUrl.endsWith(".apk", ignoreCase = true)) fromUrl
        else "Orbit-${tag.trim().removePrefix("v")}.apk"
        base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
    val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    val file = File(dir, fileName)
    // A leftover file from a previous attempt makes DownloadManager fail.
    if (file.exists()) file.delete()

    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    if (dm == null) {
        Toast.makeText(
            ctx,
            T("系统下载服务不可用", "System download service unavailable", langZh),
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle("Orbit $tag")
        .setDescription(T("正在下载更新…", "Downloading update…", langZh))
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(ctx, null, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val id = try {
        dm.enqueue(request)
    } catch (e: Exception) {
        Toast.makeText(
            ctx,
            T("下载失败：${e.message}", "Download failed: ${e.message}", langZh),
            Toast.LENGTH_LONG
        ).show()
        return
    }
    Toast.makeText(ctx, T("开始下载…", "Downloading…", langZh), Toast.LENGTH_SHORT).show()

    // Poll DownloadManager instead of relying on a broadcast receiver: the
    // receiver would have to be manifest-registered and its delivery is not
    // guaranteed on API 26+ for implicit broadcasts.
    scope.launch {
        var done = false
        var failure: String? = null
        for (i in 0 until 1200) {                       // ≈10 min at 500 ms
            delay(500)
            val cursor = try {
                dm.query(DownloadManager.Query().setFilterById(id))
            } catch (e: Exception) {
                null
            }
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL -> done = true
                            DownloadManager.STATUS_FAILED -> failure =
                                "reason=${cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}"
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
            if (done || failure != null) break
        }
        if (done) {
            Toast.makeText(
                ctx,
                T("已下载：${file.absolutePath}", "Downloaded: ${file.absolutePath}", langZh),
                Toast.LENGTH_LONG
            ).show()
            installApk(ctx, file, langZh)
        } else if (failure != null) {
            Toast.makeText(
                ctx,
                T("下载失败（$failure）", "Download failed ($failure)", langZh),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/** Fire the system package installer for an already-downloaded APK. */
private fun installApk(ctx: Context, file: File, langZh: Boolean) {
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            ctx,
            T(
                "无法启动安装器，请手动安装：${file.absolutePath}",
                "Cannot launch installer, install manually: ${file.absolutePath}",
                langZh
            ),
            Toast.LENGTH_LONG
        ).show()
    }
}

/** Update dialog: release notes + "download APK" / "open in browser" / "close". */
@Composable
private fun UpdateDialog(
    visible: Boolean,
    info: UpdateInfo?,
    currentVersion: String,
    langZh: Boolean,
    onDownloadApk: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = currentColors()

    // Keep the Dialog mounted until the exit animation finishes.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            delay(320)
            mounted = false
        }
    }
    if (!mounted || info == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300, easing = HarmonyEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = HarmonyEasing)),
            exit = fadeOut(tween(300, easing = HarmonyEasing)) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(300, easing = HarmonyEasing))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .flGlassCard(colors, 20.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = T("发现新版本 ${info.tag}", "New version ${info.tag}", langZh),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = T("当前版本 v$currentVersion", "Current v$currentVersion", langZh),
                    color = colors.textTertiary,
                    fontSize = 11.sp
                )

                if (info.body.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.inputBg)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = info.body.trim(),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                } else {
                    Text(
                        text = T("（该版本没有更新说明）", "(No release notes for this version)", langZh),
                        color = colors.textTertiary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentGradient)
                            .clickable { onDownloadApk() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = T("直接下载", "Download APK", langZh),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.inputBg)
                            .border(0.5.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                            .clickable { onOpenBrowser() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = T("浏览器下载", "Open in Browser", langZh),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = T("关闭", "Close", langZh),
                        color = colors.textTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

