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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.roundToInt
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

/** Settings sub-pages, pushed onto a small back stack (never exits the app). */
private enum class SettingsPage { GENERAL, PERMISSIONS, THEME, ABOUT, PRIVACY }

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

    // ── Update check state（提升到 MainScreen，启动检查与关于页手动检查共用）──
    // 之前 updateInfo / updateDialogVisible 住在 AboutPage 内部：一进关于页就重置，
    // 启动时的静默检查也没地方放。现在两者都归这里，弹窗也只在 MainScreen 渲染一次。
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { appVersionName(ctx) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateDialogVisible by remember { mutableStateOf(false) }
    var autoCheckUpdate by remember { mutableStateOf(autoCheckUpdateEnabled(ctx)) }

    // ── 隐私政策在线版本：启动后比对内容 hash，变了才提示 ──
    var privacyUpdateVisible by remember { mutableStateOf(false) }
    /** true = 政策页是从「隐私政策已更新」弹窗进来的，底部多一个「已阅读并同意」 */
    var privacyAgreeMode by remember { mutableStateOf(false) }

    // 启动后 5s 静默检查一次：开关关闭就跳过；网络/解析失败一律静默忽略。
    LaunchedEffect(Unit) {
        delay(5_000)
        if (!autoCheckUpdateEnabled(ctx)) return@LaunchedEffect
        try {
            val info = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (isNewerVersion(info.tag, currentVersion)) {
                updateInfo = info
                updateDialogVisible = true
            }
        } catch (_: Exception) {
            // 静默：启动检查不因为网络问题打扰用户
        }
    }

    // 启动后 8s 拉一次 PRIVACY.md（与上面的 5s 更新检查错开），
    // hash 与「已同意版本」不同 → 弹「隐私政策已更新」；拉取失败静默。
    LaunchedEffect(Unit) {
        delay(8_000)
        if (!PrivacyMd.markCompared()) return@LaunchedEffect   // 本进程已比对过（Activity 重建）
        val fresh = withContext(Dispatchers.IO) { PrivacyMd.fetch() } ?: return@LaunchedEffect
        PrivacyMd.saveCache(ctx, fresh)
        if (PrivacyMd.acceptedHash(ctx) != PrivacyMd.hashOf(fresh)) {
            privacyUpdateVisible = true
        }
    }

    var currentTab by remember { mutableStateOf(Tab.STATUS) }
    // Sub-page stack: Settings → General → Permissions, About → Privacy Policy.
    val pageStack = remember { mutableStateListOf<SettingsPage>() }
    val subPage = pageStack.lastOrNull()
    // Direction of the last pageStack change: drives the enter/exit transition.
    var navForward by remember { mutableStateOf(true) }
    fun openPage(page: SettingsPage) {
        navForward = true
        pageStack.add(page)
    }
    fun closePage() {
        navForward = false
        if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex)
    }

    // System back pops the settings stack (back to Settings / General), never exits.
    // Kept outside the animated content so a mid-transition press can't double-pop.
    BackHandler(enabled = pageStack.isNotEmpty()) { closePage() }

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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (subPage != null) 0.dp else 72.dp)
        ) {
            // 二三级页进出转场：进入 = 新页从右滑入 + 淡入（it/4，260ms）；
            // 返回 = 当前页向右滑出 + 淡出。同一套 HarmonyEasing，pageStack 逻辑不变。
            AnimatedContent(
                targetState = subPage,
                transitionSpec = {
                    if (navForward) {
                        (slideInHorizontally(tween(260, easing = HarmonyEasing)) { it / 4 } +
                            fadeIn(tween(260, easing = HarmonyEasing)))
                            .togetherWith(fadeOut(tween(120, easing = HarmonyEasing)))
                    } else {
                        fadeIn(tween(200, easing = HarmonyEasing))
                            .togetherWith(
                                slideOutHorizontally(tween(260, easing = HarmonyEasing)) { it / 4 } +
                                    fadeOut(tween(200, easing = HarmonyEasing))
                            )
                    }
                },
                label = "settingsPageTransition"
            ) { page ->
                if (page != null) {
                    when (page) {
                        SettingsPage.GENERAL -> GeneralPage(
                            langZh = langZh,
                            config = config,
                            onConfigChange = onConfigChange,
                            onConfigServer = onConfigServer,
                            onRestartCore = onToggleConnection,
                            onOpenPermissions = { openPage(SettingsPage.PERMISSIONS) },
                            onBack = { closePage() }
                        )
                        SettingsPage.PERMISSIONS -> PermissionsPage(
                            langZh = langZh,
                            onBack = { closePage() }
                        )
                        SettingsPage.THEME -> ThemePage(
                            langZh = langZh,
                            isDarkTheme = isDarkTheme,
                            dynamicColor = dynamicColor,
                            onToggleTheme = onToggleTheme,
                            onToggleDynamic = onToggleDynamic,
                            onBack = { closePage() }
                        )
                        SettingsPage.ABOUT -> AboutPage(
                            langZh = langZh,
                            isConnected = connectionState.status == ConnectionStatus.RUNNING
                                    || connectionState.status == ConnectionStatus.NO_TUN,
                            connectionStartTime = connectionStartTime,
                            autoCheckUpdate = autoCheckUpdate,
                            onAutoCheckUpdateChange = { enabled ->
                                autoCheckUpdate = enabled
                                setAutoCheckUpdateEnabled(ctx, enabled)
                            },
                            // 手动检查到新版本 → 同一条状态 + 同一个弹窗
                            onNewVersionFound = { info ->
                                updateInfo = info
                                updateDialogVisible = true
                            },
                            onBack = { closePage() },
                            onOpenPrivacy = { openPage(SettingsPage.PRIVACY) }
                        )
                        SettingsPage.PRIVACY -> PrivacyDocPage(
                            langZh = langZh,
                            forced = false,
                            // 从「隐私政策已更新」弹窗进来时底部才给「已阅读并同意」
                            showAgree = privacyAgreeMode,
                            onAccept = {
                                privacyAgreeMode = false
                                closePage()
                            },
                            onDecline = {
                                privacyAgreeMode = false
                                closePage()
                            },
                            onBack = {
                                privacyAgreeMode = false
                                closePage()
                            }
                        )
                    }
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
                        langZh = langZh
                    )
                    Tab.CONFIG -> ConfigTab(
                        config = config,
                        onConfigChange = onConfigChange,
                        currentConfigName = currentConfigName,
                        langZh = langZh,
                        connectionStatus = connectionState.status,
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
                        },
                        onStopService = onToggleConnection
                    )
                    Tab.MORE -> MoreTab(
                        langZh = langZh,
                        onToggleLanguage = onToggleLanguage,
                        isDarkTheme = isDarkTheme,
                        dynamicColor = dynamicColor,
                        onNavigateToGeneral = { openPage(SettingsPage.GENERAL) },
                        onNavigateToTheme = { openPage(SettingsPage.THEME) },
                        onNavigateToAbout = { openPage(SettingsPage.ABOUT) },
                        onConfigServer = onConfigServer,
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
        }

        // Bottom Navigation
        if (subPage == null) {
            BottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                langZh = langZh,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ── 更新弹窗（唯一实例）──
        // 启动静默检查与关于页手动检查都写同一份 updateInfo/updateDialogVisible，
        // 所以弹窗放在最外层：不管用户当前在哪个 Tab / 二级页都能弹出来。
        UpdateDialog(
            visible = updateDialogVisible,
            info = updateInfo,
            currentVersion = currentVersion,
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
                    } catch (_: Exception) {
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

        // ── 隐私政策更新提示 ──
        // 启动 8s 拉取后 hash 与已同意版本不同才弹；「稍后」不写 hash，下次启动再提示。
        HmDialog(
            visible = privacyUpdateVisible,
            onDismiss = { privacyUpdateVisible = false },
            title = T("隐私政策已更新", "Privacy Policy Updated", langZh),
            maxWidth = 340.dp,
            horizontalPadding = 20.dp
        ) {
            Text(
                text = T(
                    "我们更新了隐私政策，请查看最新内容。",
                    "We updated our privacy policy. Please review the latest version.",
                    langZh
                ),
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            HmDialogActionRow {
                HmDialogPrimaryButton(
                    text = T("查看并同意", "Review & Agree", langZh),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        privacyUpdateVisible = false
                        privacyAgreeMode = true          // 政策页底部才会出现「已阅读并同意」
                        openPage(SettingsPage.PRIVACY)
                    }
                )
                HmDialogSecondaryButton(
                    text = T("稍后", "Later", langZh),
                    modifier = Modifier.weight(1f),
                    onClick = { privacyUpdateVisible = false }
                )
            }
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
            // 内存限制「严格」档把刷新从 2s 放慢到 5s
            delay(com.easytier.RuntimeTuning.logsRefreshMs)
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
                        "日志收集已关闭，请到「设置 → 常规」开启",
                        "Log collection is off — enable it in Settings → General",
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
    val shape = RoundedCornerShape(24.dp)
    val indicatorShape = RoundedCornerShape(16.dp)

    val items = listOf(
        Triple(Icons.Rounded.Dashboard, T("仪表盘", "Dashboard", langZh), Tab.STATUS),
        Triple(Icons.Rounded.Description, T("配置", "Config", langZh), Tab.CONFIG),
        Triple(Icons.Rounded.Subject, T("日志", "Logs", langZh), Tab.LOGS),
        Triple(Icons.Rounded.Build, T("设置", "Settings", langZh), Tab.MORE)
    )
    val selectedIndex = items.indexOfFirst { it.third == currentTab }.coerceAtLeast(0)

    // Floating indicator (AnimatedBottomBar style): animate the *slot* (0f..n-1)
    // instead of each pill's alpha, then derive the offset from the tab width.
    // The value is read inside the offset lambda, so sliding is layout-only.
    val slot by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 380, easing = HarmonyEasing),
        label = "navSlot"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        // ── Flat nav bar (no glass) ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.navGlass)
                .border(0.5.dp, colors.navGlassBorder, shape)
                .padding(6.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val itemWidth = maxWidth / items.size
                val itemWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemWidth.toPx() }

                // Sliding block sits behind the items and moves under the tap.
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((itemWidthPx * slot).roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .background(colors.accent)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    items.forEachIndexed { index, (icon, label, tab) ->
                        val isActive = index == selectedIndex
                        // Delay the white tint until the indicator has slid in,
                        // otherwise the label flashes white-on-white mid-travel.
                        var lit by remember { mutableStateOf(isActive) }
                        LaunchedEffect(isActive) {
                            if (isActive) {
                                delay(180L)
                                lit = true
                            } else {
                                lit = false
                            }
                        }
                        val tint by animateColorAsState(
                            targetValue = if (lit) Color.White else colors.textTertiary,
                            animationSpec = tween(180, easing = HarmonyEasing),
                            label = "navTint"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isActive) 1.08f else 1f,
                            animationSpec = tween(380, easing = HarmonyEasing),
                            label = "navIconScale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(indicatorShape)
                                .clickable { onTabSelected(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                                Text(
                                    text = label,
                                    color = tint,
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
    langZh: Boolean
) {
    val scrollState = rememberScrollState()
    val isConnected = connectionState.status == ConnectionStatus.RUNNING ||
                      connectionState.status == ConnectionStatus.NO_TUN

    // ── Speed history for the waveforms (one list per direction, 40 pts) ──
    val rxHistory = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val txHistory = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val rxSpeed = myNodeItem?.rxSpeed ?: ""
    val txSpeed = myNodeItem?.txSpeed ?: ""

    LaunchedEffect(rxSpeed) {
        val v = parseSpeedValue(rxSpeed)
        if (v > 0f) {
            rxHistory.add(v)
            if (rxHistory.size > 40) rxHistory.removeAt(0)
        }
    }
    LaunchedEffect(txSpeed) {
        val v = parseSpeedValue(txSpeed)
        if (v > 0f) {
            txHistory.add(v)
            if (txHistory.size > 40) txHistory.removeAt(0)
        }
    }

    fun fmtBytes(b: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, units[i])
    }

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
                rxSpeed = rxSpeed,
                txSpeed = txSpeed,
                latency = latency,
                langZh = langZh
            )

            // ── Speed cards (only when running) ──
            if (isConnected && myNodeItem != null) {
                SpeedStatRow(
                    rxSpeed = rxSpeed,
                    txSpeed = txSpeed,
                    rxTotal = fmtBytes(myNodeItem.rxBytes),
                    txTotal = fmtBytes(myNodeItem.txBytes),
                    rxHistory = rxHistory,
                    txHistory = txHistory,
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
    connectionStatus: ConnectionStatus,
    onConfigNameClick: () -> Unit,
    onSaveConfig: () -> Unit,
    onAutoSaveConfig: () -> Unit,
    onManageConfigs: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onDeleteConfig: () -> Unit,
    onLaunchConfigEditor: () -> Unit,
    onApplyConfig: () -> Unit,
    onStopService: () -> Unit
) {
    val colors = currentColors()
    val scrollState = rememberScrollState()
    var hasUnsaved by remember { mutableStateOf(false) }
    var lastConfig by remember { mutableStateOf(config) }

    // 运行中 / 启动中：配置字段全部锁定。NO_TUN 也是「内核在跑、只是没建 TUN」，
    // 同样属于运行态，一起锁。
    val running = connectionStatus == ConnectionStatus.RUNNING ||
            connectionStatus == ConnectionStatus.NO_TUN
    val starting = connectionStatus == ConnectionStatus.STARTING
    val locked = running || starting

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
        // ── 运行中提示（锁定时才出现）──
        if (locked) {
            RunningIndicator(running = running, langZh = langZh)
            Spacer(Modifier.height(2.dp))
        }

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
                            .clickable(enabled = !locked) { onConfigNameClick() }
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
                                color = if (locked) colors.textSecondary else colors.textPrimary,
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
                            if (!locked) {
                                Text("▼", color = colors.textTertiary, fontSize = 10.sp)
                            }
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
                        enabled = !locked,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("📥 导入", "📥 Import", langZh),
                        onClick = onImportToml,
                        colors = colors,
                        enabled = !locked,
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
                        enabled = !locked,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("✏️ 编辑器", "✏️ Editor", langZh),
                        onClick = onLaunchConfigEditor,
                        colors = colors,
                        enabled = !locked,
                        modifier = Modifier.weight(1f)
                    )
                    ActionBtn(
                        text = T("🗑 删除", "🗑 Delete", langZh),
                        onClick = onDeleteConfig,
                        colors = colors,
                        danger = true,
                        enabled = !locked,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Config Form（运行中整体只读：CompositionLocal 一处下发）──
        CompositionLocalProvider(LocalConfigEditable provides !locked) {
            ConfigForm(
                config = config,
                langZh = langZh,
                onConfigChange = onConfigChange
            )
        }

        // ── Apply / Stop Button ──
        // 运行中只剩「停止服务」可用（配置字段锁死，但必须能停）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (locked) Modifier.background(colors.dangerBg)
                    else Modifier.background(colors.accentGradient)
                )
                .clickable {
                    if (locked) {
                        onStopService()
                    } else {
                        // Save before applying
                        onAutoSaveConfig()
                        onApplyConfig()
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (locked) {
                    "■ ${T("停止服务", "Stop Service", langZh)}"
                } else {
                    "▸ ${T("应用并启动", "Apply & Start", langZh)}"
                },
                color = if (locked) colors.dangerText else Color.White,
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
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val textColor = when {
        !enabled -> (if (accent) Color.White else colors.textTertiary).copy(alpha = 0.6f)
        accent -> Color.White
        danger -> colors.dangerText
        else -> colors.textSecondary
    }
    val bgMod = when {
        !enabled && accent -> Modifier.background(colors.accent.copy(alpha = 0.35f))
        accent -> Modifier.background(colors.accentGradient)
        !enabled -> Modifier.background(colors.inputBg)
        danger -> Modifier.background(colors.dangerBg)
        else -> Modifier.background(colors.inputBg)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(bgMod)
            .clickable(enabled = enabled) { onClick() }
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
    isDarkTheme: Boolean,
    dynamicColor: Boolean,
    onNavigateToGeneral: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onConfigServer: () -> Unit,
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


    // ── Language / Theme entries ──
    // 语言：行下方下拉弹层（[HmDropdownField]）；主题：二级页（pageStack + BackHandler）

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
        // Config Server — lifted out of General, sits at the very top
        SettingsNavRow(
            icon = "☁",
            title = T("配置服务器", "Config Server", langZh),
            subtitle = T("从远端 URL 一键拉取 TOML 配置", "Fetch TOML config from URL", langZh),
            colors = colors,
            onClick = { onConfigServer() }
        )

        // General → core / logs / permissions (moved to its own sub-page)
        SettingsNavRow(
            icon = "⚙",
            title = T("常规", "General", langZh),
            colors = colors,
            onClick = { onNavigateToGeneral() }
        )

        // Language — 当前值 + 三角指示，点开行下方弹层（简体中文 / English）
        HmDropdownField(
            label = T("语言", "Language", langZh),
            icon = "A",
            options = listOf(
                HmDropdownOption(label = "简体中文", value = "zh"),
                HmDropdownOption(label = "English", value = "en")
            ),
            selectedIndex = if (langZh) 0 else 1,
            onSelect = { idx ->
                val wantZh = idx == 0
                if (wantZh != langZh) onToggleLanguage()
            },
            colors = colors,
            card = true
        )

        // Theme — current value + chevron, enters the theme sub-page
        SettingsNavRow(
            icon = if (isDarkTheme) "●" else "○",
            title = T("主题", "Theme", langZh),
            value = themeValue,
            colors = colors,
            onClick = { onNavigateToTheme() }
        )

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
                SettingsRowIcon("◇")
                Spacer(Modifier.width(SettingsIconGap))
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
}

/**
 * 设置列表统一的图标位：24dp 固定宽度的居中容器 + 12dp 间距。
 *
 * 为什么不用裸 Text(icon) 直接排：emoji / 符号的字形宽度各不相同（☁ 窄、🔋 宽、
 * 🛡 带变体选择符），裸 Text 会让每行图标宽度不一，右侧文字列随之错位。固定
 * 24dp 后所有行的 title / label 都从同一个 x 开始。
 */
val SettingsIconBoxSize = 24.dp
val SettingsIconGap = 12.dp

@Composable
fun SettingsRowIcon(
    icon: String?,
    color: Color = Color.Unspecified
) {
    Box(
        modifier = Modifier.size(SettingsIconBoxSize),
        contentAlignment = Alignment.Center
    ) {
        if (!icon.isNullOrEmpty()) {
            Text(
                text = icon,
                color = color,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Settings list row: icon + title (+ optional subtitle) on the left,
 * current value and a chevron on the right.
 */
@Composable
fun SettingsNavRow(
    icon: String,
    title: String,
    colors: AppColors,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = true,
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
            SettingsRowIcon(icon)
            Spacer(Modifier.width(SettingsIconGap))
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
            if (showChevron) {
                Text("›", color = colors.textTertiary, fontSize = 18.sp)
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
    autoCheckUpdate: Boolean = true,
    onAutoCheckUpdateChange: (Boolean) -> Unit = {},
    onNewVersionFound: (UpdateInfo) -> Unit = {},
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val version: String = remember { appVersionName(ctx) }
    val colors = currentColors()
    val scrollState = rememberScrollState()

    // 手动检查的按钮状态留在本页；updateInfo / 弹窗状态在 MainScreen（启动检查共用）。
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
        // Back control + title
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

                    // 检查更新 + 自动检查更新 —— 合并成一张卡片：
                    // 点卡片主体 = 立即检查（checking / latest / error 反馈都在副行里），
                    // 右侧 HmSwitch = 自动检查（状态在 MainScreen，落盘 auto_check_update）。
                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceDim)
                            .clickable(enabled = !checking) {
                                checking = true
                                updateStatus = null
                                scope.launch {
                                    try {
                                        val info = withContext(Dispatchers.IO) { fetchLatestRelease() }
                                        if (isNewerVersion(info.tag, version)) {
                                            // 新版本 → 交给 MainScreen 的统一弹窗（release notes + 下载）
                                            onNewVersionFound(info)
                                        } else {
                                            updateStatus = "latest"
                                        }
                                    } catch (_: Exception) {
                                        updateStatus = "error"
                                    }
                                    checking = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = T("检查更新", "Check Update", langZh),
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        checking -> T("正在检查更新…", "Checking…", langZh)
                                        updateStatus == "latest" ->
                                            T("✔ 已是最新版本 v$version", "✔ Latest version v$version", langZh)
                                        updateStatus == "error" ->
                                            T("检查失败，请检查网络连接", "Check failed, check your network", langZh)
                                        else -> T("当前版本 v$version · 点击检查", "Current v$version · tap to check", langZh)
                                    },
                                    color = when {
                                        updateStatus == "latest" && !checking -> colors.statusGood
                                        updateStatus == "error" && !checking -> colors.statusBad
                                        else -> colors.textTertiary
                                    },
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HmSwitch(
                                    checked = autoCheckUpdate,
                                    onCheckedChange = onAutoCheckUpdateChange,
                                    colors = colors
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = T("自动检查", "Auto", langZh),
                                    color = colors.textTertiary,
                                    fontSize = 10.sp
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

            // 隐私政策入口 —— 关于页最下面的一行蓝字（版本号等内容之后、页尾）
            Text(
                text = "《隐私政策》 / 《Privacy Policy》",
                color = colors.accentLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPrivacy() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
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

/** 「自动检查更新」偏好：与 MainActivity 的其他状态放同一个 SharedPreferences 文件。 */
private const val UPDATE_PREFS = "easytier_state"
private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"

/** 当前安装包的 versionName；读不到时退回 "0.1.5"（与关于页展示保持一致）。 */
private fun appVersionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1.5"
} catch (_: Exception) {
    "0.1.5"
}

/** 自动检查更新开关，默认开。 */
private fun autoCheckUpdateEnabled(ctx: Context): Boolean =
    ctx.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_CHECK_UPDATE, true)

private fun setAutoCheckUpdateEnabled(ctx: Context, enabled: Boolean) {
    ctx.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_AUTO_CHECK_UPDATE, enabled).apply()
}

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
    if (info == null) return

    HmDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = T("发现新版本 ${info.tag}", "New version ${info.tag}", langZh),
        maxWidth = 340.dp,
        horizontalPadding = 20.dp
    ) {
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

        HmDialogActionRow {
            HmDialogPrimaryButton(
                text = T("直接下载", "Download APK", langZh),
                modifier = Modifier.weight(1f),
                onClick = onDownloadApk
            )
            HmDialogSecondaryButton(
                text = T("浏览器下载", "Open in Browser", langZh),
                modifier = Modifier.weight(1f),
                onClick = onOpenBrowser
            )
        }

        HmDialogActionRow {
            HmDialogTextButton(
                text = T("关闭", "Close", langZh),
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
        }
    }
}

