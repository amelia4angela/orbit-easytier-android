package com.easytier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import com.easytier.TomlUtils
import com.easytier.ui.theme.*

@Composable
fun ConfigForm(
    config: ConfigState,
    langZh: Boolean,
    onConfigChange: (ConfigState) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = currentColors()
    var advancedExpanded by remember { mutableStateOf(false) }
    var secretVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Basic Config ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .flGlassCard(colors, 20.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionLabel(T("基本配置", "Basic Config", langZh), colors)

                HmTextField(
                    value = config.networkName,
                    onValueChange = { onConfigChange(config.copy(networkName = it)) },
                    label = T("网络名称", "Network Name", langZh),
                    placeholder = T("例如 my-vpn", "e.g. my-vpn", langZh),
                    colors = colors
                )

                // Secret with eye toggle
                Text(
                    text = T("密钥", "Secret", langZh),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HmTextField(
                        value = config.secret,
                        onValueChange = { onConfigChange(config.copy(secret = it)) },
                        label = "",
                        placeholder = T("输入网络密钥", "Enter network secret", langZh),
                        password = !secretVisible,
                        modifier = Modifier.weight(1f),
                        colors = colors
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.inputBg)
                            .border(0.5.dp, colors.inputBorder, RoundedCornerShape(12.dp))
                            .clickable { secretVisible = !secretVisible },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (secretVisible) "🙈" else "👁", fontSize = 16.sp)
                    }
                }

                // Server nodes (rounded sub-card: list + add)
                var nodeDialogVisible by remember { mutableStateOf(false) }
                var editingIndex by remember { mutableStateOf(-1) }

                // Keep legacy server/port/extraServers in sync so existing paths
                // (start validation, peer label, TOML export) keep working.
                fun applyNodes(list: List<PeerNode>) {
                    val first = list.firstOrNull()
                    onConfigChange(
                        config.copy(
                            nodes = list,
                            server = first?.address ?: "",
                            port = first?.port ?: "",
                            extraServers = list.drop(1).map { "${it.protocol}://${it.address}:${it.port}" }
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .flGlassCard(colors, 16.dp)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SectionLabel(T("服务器节点", "Server Nodes", langZh), colors)

                        if (config.nodes.isEmpty()) {
                            Text(
                                text = T("暂无节点，点击下方添加", "No nodes yet", langZh),
                                color = colors.textTertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            config.nodes.forEachIndexed { index, node ->
                                // Press feedback, linear on purpose (no scale, no bouncy spring).
                                //
                                // 圆角 bug 的根因：之前按压态用
                                // `Modifier.shadow(elevation = lift, shape, clip = false)`
                                // 抬升阴影，clip = false 的 shadow 层不会裁到圆角轮廓上，
                                // 阴影/内容在动画期间以矩形边界绘制，四个角就出现直角。
                                // 现在把「阴影 + 背景」放进同一个 graphicsLayer，
                                // clip = true + 同一个 10dp shape —— 常态与按压态共用
                                // 同一条圆角轮廓，四角全程保持圆润。
                                val interaction = remember(index) { MutableInteractionSource() }
                                val pressed by interaction.collectIsPressedAsState()
                                val lift by animateFloatAsState(
                                    targetValue = if (pressed) 1f else 0f,
                                    animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                    label = "nodeRowLift"
                                )
                                val rowBg by animateColorAsState(
                                    targetValue = if (pressed) colors.inputBg else colors.surface,
                                    animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                    label = "nodeRowBg"
                                )
                                val rowShape = RoundedCornerShape(10.dp)
                                val density = LocalDensity.current
                                val shadowTint = ComposeColor.Black.copy(alpha = 0.10f)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            shape = rowShape
                                            clip = true
                                            shadowElevation = with(density) { (lift * 2f).dp.toPx() }
                                            ambientShadowColor = shadowTint
                                            spotShadowColor = shadowTint
                                        }
                                        .background(rowBg)
                                        .clickable(
                                            interactionSource = interaction,
                                            indication = null
                                        ) {
                                            editingIndex = index
                                            nodeDialogVisible = true
                                        }
                                        .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${node.protocol}://${node.address}:${node.port}",
                                        color = colors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NodeActionButton("✎", colors.textTertiary) {
                                        editingIndex = index
                                        nodeDialogVisible = true
                                    }
                                    NodeActionButton("🗑", colors.dangerText) {
                                        applyNodes(config.nodes.filterIndexed { i, _ -> i != index })
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    editingIndex = -1
                                    nodeDialogVisible = true
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ " + T("添加初始节点", "Add Initial Node", langZh),
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Edit/create dialog (composed always so its exit animation can play)
                NodeEditDialog(
                    visible = nodeDialogVisible,
                    initial = config.nodes.getOrNull(editingIndex),
                    langZh = langZh,
                    onSave = { node ->
                        val list = config.nodes.toMutableList()
                        if (editingIndex in list.indices) list[editingIndex] = node else list.add(node)
                        applyNodes(list)
                        nodeDialogVisible = false
                    },
                    onDelete = if (editingIndex in config.nodes.indices) {
                        {
                            applyNodes(config.nodes.filterIndexed { i, _ -> i != editingIndex })
                            nodeDialogVisible = false
                        }
                    } else null,
                    onDismiss = { nodeDialogVisible = false }
                )

                // Local IP
                HmTextField(
                    value = config.ipAddress,
                    onValueChange = { onConfigChange(config.copy(ipAddress = it)) },
                    label = T("本地 IP", "Local IP", langZh),
                    placeholder = T("例如 10.0.0.2/24", "e.g. 10.0.0.2/24", langZh),
                    colors = colors
                )

                // Device identity
                HmTextField(
                    value = config.hostname,
                    onValueChange = { onConfigChange(config.copy(hostname = it)) },
                    label = T("设备名称", "Device Name", langZh),
                    placeholder = T("例如 设备名（其他节点看到的名字）", "e.g. my-device (shown to other nodes)", langZh),
                    colors = colors
                )
                HmTextField(
                    value = config.instanceName,
                    onValueChange = { onConfigChange(config.copy(instanceName = it)) },
                    label = T("实例标识", "Instance ID", langZh),
                    placeholder = T("例如 设备名（内部标识，留空自动）", "e.g. my-device (internal ID, auto if empty)", langZh),
                    colors = colors
                )

                // DHCP toggle (No TUN moved to Advanced Options)
                Spacer(Modifier.height(4.dp))
                HmToggle(
                    checked = config.dhcp,
                    onCheckedChange = { onConfigChange(config.copy(dhcp = it)) },
                    label = T("DHCP 自动 IP", "DHCP Auto IP", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors
                )
            }
        }

        // ── DNS Config (own card, right after Basic Config) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .flGlassCard(colors, 20.dp)
                .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        T("DNS 配置", "DNS Config", langZh),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    HmSwitch(
                        checked = config.dnsEnabled,
                        onCheckedChange = { onConfigChange(config.copy(dnsEnabled = it)) },
                        colors = colors
                    )
                }
                if (config.dnsEnabled) {
                    HmTextField(
                        value = config.dnsServer,
                        onValueChange = { onConfigChange(config.copy(dnsServer = it)) },
                        label = T("DNS 服务器", "DNS Server", langZh),
                        placeholder = "10.144.144.1",
                        colors = colors
                    )
                    Text(
                        T(
                            "请确认对应服务器已部署 DNS 服务",
                            "Make sure a DNS service is deployed on that server",
                            langZh
                        ),
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── Advanced Options ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .flGlassCard(colors, 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedExpanded = !advancedExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (advancedExpanded) "▲" else "▼",
                            color = colors.textTertiary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = T("高级选项", "Advanced Options", langZh),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AnimatedVisibility(
                    visible = advancedExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // No TUN mode — moved here from Basic Config (first item)
                        HmToggle(
                            checked = config.noTun,
                            onCheckedChange = { onConfigChange(config.copy(noTun = it)) },
                            label = T("无 TUN 模式", "No TUN Mode", langZh),
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            hint = T(
                                "不建虚拟网卡，仅应用内组网，更省电",
                                "In-app mesh only, no TUN, saves battery",
                                langZh
                            )
                        )

                        // Network
                        SectionLabel(T("网络", "Network", langZh), colors)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.disableIpv6,
                                onCheckedChange = { onConfigChange(config.copy(disableIpv6 = it)) },
                                label = T("禁用 IPv6", "Disable IPv6", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("隧道内不传 IPv6 流量", "No IPv6 traffic inside the tunnel", langZh)
                            )
                        }
                        // 官方 2.6.4 里 bind_device 是布尔（把 socket 绑到物理网卡），
                        // 没有「指定网卡名」的字段 → 这里做成开关。
                        HmToggle(
                            checked = config.bindDevice.isNotBlank(),
                            onCheckedChange = {
                                onConfigChange(
                                    config.copy(bindDevice = if (it) TomlUtils.BIND_DEVICE_ON else "")
                                )
                            },
                            label = T("绑定物理网卡", "Bind Physical NIC", langZh),
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            hint = T(
                                "把连接套接字绑到物理网卡，避免组网路由冲突",
                                "Bind sockets to physical NICs to avoid route conflicts",
                                langZh
                            )
                        )

                        // P2P
                        SectionLabel(T("点对点 (P2P)", "Peer-to-Peer (P2P)", langZh), colors)
                        HmToggle(
                            checked = config.aggressiveP2p,
                            onCheckedChange = { onConfigChange(config.copy(aggressiveP2p = it)) },
                            label = T("激进 P2P", "Aggressive P2P", langZh),
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            hint = T(
                                "立即并发打洞优先直连，连不上可关",
                                "Punch immediately for direct links",
                                langZh
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.disableP2p,
                                onCheckedChange = { onConfigChange(config.copy(disableP2p = it)) },
                                label = T("禁用 P2P", "Disable P2P", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("全部走中继，穿透差时保连通", "Relay everything when punching fails", langZh)
                            )
                            HmToggle(
                                checked = config.p2pOnly,
                                onCheckedChange = { onConfigChange(config.copy(p2pOnly = it)) },
                                label = T("仅 P2P", "P2P Only", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("只允许直连不中继", "Direct links only, no relay", langZh)
                            )
                        }
                        // 「需要 P2P」已合并进「激进 P2P」：两者都只是 need_p2p，留着会重复
                        HmToggle(
                            checked = config.lazyP2p,
                            onCheckedChange = { onConfigChange(config.copy(lazyP2p = it)) },
                            label = T("延迟 P2P", "Lazy P2P", langZh),
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            hint = T("先中继再尝试打洞，省电（激进 P2P 关闭时生效）", "Relay first, punch later; ignored while Aggressive P2P is on", langZh)
                        )

                        // NAT / Hole Punch
                        SectionLabel(T("NAT / 打洞", "NAT / Hole Punch", langZh), colors)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.disableTcpHp,
                                onCheckedChange = { onConfigChange(config.copy(disableTcpHp = it)) },
                                label = T("禁用 TCP 打洞", "Disable TCP HP", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("关闭 TCP NAT 穿透", "Disable TCP hole punching", langZh)
                            )
                            HmToggle(
                                checked = config.disableUdpHp,
                                onCheckedChange = { onConfigChange(config.copy(disableUdpHp = it)) },
                                label = T("禁用 UDP 打洞", "Disable UDP HP", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("关闭 UDP NAT 穿透", "Disable UDP hole punching", langZh)
                            )
                        }
                        HmToggle(
                            checked = config.disableSymHp,
                            onCheckedChange = { onConfigChange(config.copy(disableSymHp = it)) },
                            label = T("禁用 Sym 打洞", "Disable Sym HP", langZh),
                            colors = colors,
                            hint = T("关闭对称 NAT 穿透", "Disable symmetric NAT punching", langZh)
                        )

                        // Proxy / KCP / QUIC
                        SectionLabel(T("代理 / KCP / QUIC", "Proxy / KCP / QUIC", langZh), colors)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.enableKcpProxy,
                                onCheckedChange = { onConfigChange(config.copy(enableKcpProxy = it)) },
                                label = "KCP Proxy",
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("为组网提供 KCP 加速", "KCP acceleration for the mesh", langZh)
                            )
                            HmToggle(
                                checked = config.disableKcpInput,
                                onCheckedChange = { onConfigChange(config.copy(disableKcpInput = it)) },
                                label = T("禁用 KCP 入站", "Disable KCP In", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("不接受 KCP 加速连接", "Refuse KCP inbound", langZh)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.enableQuicProxy,
                                onCheckedChange = { onConfigChange(config.copy(enableQuicProxy = it)) },
                                label = "QUIC Proxy",
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("为组网提供 QUIC 加速", "QUIC acceleration for the mesh", langZh)
                            )
                            HmToggle(
                                checked = config.disableQuicInput,
                                onCheckedChange = { onConfigChange(config.copy(disableQuicInput = it)) },
                                label = T("禁用 QUIC 入站", "Disable QUIC In", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("不接受 QUIC 加速连接", "Refuse QUIC inbound", langZh)
                            )
                        }
                        HmToggle(
                            checked = config.proxyForwardBySystem,
                            onCheckedChange = { onConfigChange(config.copy(proxyForwardBySystem = it)) },
                            label = T("系统代理转发", "System Proxy", langZh),
                            colors = colors,
                            hint = T("把系统代理流量引入隧道", "Route system proxy into tunnel", langZh)
                        )
                        HmToggle(
                            checked = config.relayAllRpc,
                            onCheckedChange = { onConfigChange(config.copy(relayAllRpc = it)) },
                            label = T("中继所有节点 RPC", "Relay All RPC", langZh),
                            colors = colors,
                            hint = T("节点发现经中继，NAT 后发现失败时开", "Discover peers via relay", langZh)
                        )
                        HmToggle(
                            checked = config.exitNode,
                            onCheckedChange = { onConfigChange(config.copy(exitNode = it)) },
                            label = T("出口节点模式", "Exit Node", langZh),
                            colors = colors,
                            hint = T("本机作为流量出口", "Use this device as exit node", langZh)
                        )

                        HmTextField(
                            value = config.proxyNetworks,
                            onValueChange = { onConfigChange(config.copy(proxyNetworks = it)) },
                            label = T("代理网络 CIDR", "Proxy CIDR", langZh),
                            placeholder = "10.0.0.0/8, 192.168.1.0/24",
                            colors = colors,
                            hint = T("只代理这些网段，留空全部", "Proxy only these subnets; empty = all", langZh)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmTextField(
                                value = config.socks5,
                                onValueChange = { onConfigChange(config.copy(socks5 = it)) },
                                label = "SOCKS5",
                                placeholder = "1080",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Number,
                                colors = colors,
                                hint = T("为组网提供 SOCKS5 代理端口", "SOCKS5 proxy for the mesh", langZh)
                            )
                            HmTextField(
                                value = config.mtu,
                                onValueChange = { onConfigChange(config.copy(mtu = it)) },
                                label = "MTU",
                                placeholder = T("0=自动", "0=Auto", langZh),
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Number,
                                colors = colors,
                                hint = T("隧道最大包长，0=自动", "Max packet size; 0 = auto", langZh)
                            )
                        }

                        HmTextField(
                            value = config.portForward,
                            onValueChange = { onConfigChange(config.copy(portForward = it)) },
                            label = T("端口转发", "Port Forward", langZh),
                            placeholder = "udp://0.0.0.0:12345/10.126.126.1:23456",
                            colors = colors,
                            hint = T("把本机端口映射给组网设备", "Forward local ports to the mesh", langZh)
                        )

                        // Performance
                        SectionLabel(T("性能", "Performance", langZh), colors)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.latencyFirst,
                                onCheckedChange = { onConfigChange(config.copy(latencyFirst = it)) },
                                label = T("延迟优先", "Latency First", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("优先选低延迟链路", "Prefer low-latency paths", langZh)
                            )
                            HmToggle(
                                checked = config.multiThread,
                                onCheckedChange = { onConfigChange(config.copy(multiThread = it)) },
                                label = T("多线程", "Multi-Thread", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("多线程收发，提升吞吐", "Multi-threaded I/O, more throughput", langZh)
                            )
                        }
                        HmToggle(
                            checked = config.useSmoltcp,
                            onCheckedChange = { onConfigChange(config.copy(useSmoltcp = it)) },
                            label = "smoltcp",
                            colors = colors,
                            hint = T("用户态 TCP 栈，减少内核开销", "User-space TCP stack", langZh)
                        )

                        // Security
                        SectionLabel(T("安全", "Security", langZh), colors)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmToggle(
                                checked = config.disableEncrypt,
                                onCheckedChange = { onConfigChange(config.copy(disableEncrypt = it)) },
                                label = T("禁用加密", "Disable Encrypt", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("关闭隧道加密，更快但不安全", "No tunnel encryption (insecure)", langZh)
                            )
                            HmToggle(
                                checked = config.privateMode,
                                onCheckedChange = { onConfigChange(config.copy(privateMode = it)) },
                                label = T("私密模式", "Private Mode", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("不在公网暴露节点信息", "Hide node info from public", langZh)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HmTextField(
                                value = config.encryptAlgo,
                                onValueChange = { onConfigChange(config.copy(encryptAlgo = it)) },
                                label = T("加密算法", "Encrypt Algo", langZh),
                                placeholder = T("留空=自动", "empty=auto", langZh),
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("留空自动，如 aes-256-gcm", "Empty = auto, e.g. aes-256-gcm", langZh)
                            )
                            HmTextField(
                                value = config.compression,
                                onValueChange = { onConfigChange(config.copy(compression = it)) },
                                label = T("压缩", "Compression", langZh),
                                placeholder = "none / zstd",
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                hint = T("留空不压缩，可选 zstd", "Empty = none; zstd available", langZh)
                            )
                        }

                        // Extra
                        SectionLabel(T("附加", "Extra", langZh), colors)
                        HmTextField(
                            value = config.exitNodes,
                            onValueChange = { onConfigChange(config.copy(exitNodes = it)) },
                            label = T("出口节点 VIP", "Exit Node VIPs", langZh),
                            placeholder = "10.0.0.2, 10.0.0.3",
                            colors = colors,
                            hint = T("指定走哪些出口节点，留空不限制", "Exit nodes to use; empty = any", langZh)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section label with an accent left bar for visual grouping.
 */
@Composable
private fun SectionLabel(text: String, colors: AppColors) {
    Text(
        text = text,
        color = colors.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/**
 * Small square glyph button used for the per-node edit / delete actions.
 */
@Composable
private fun NodeActionButton(glyph: String, tint: ComposeColor, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = tint, fontSize = 14.sp)
    }
}

@Composable
internal fun HmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    colors: AppColors,
    hint: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    // Animate border color on focus
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.inputBorder,
        animationSpec = tween(200),
        label = "borderColor"
    )
    val bgColor = ComposeColor.Transparent

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(
                    width = if (isFocused) 1.dp else 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.textTertiary.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(colors.accentLight),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                modifier = Modifier.padding(start = 2.dp, top = 3.dp)
            )
        }
    }
}

/**
 * Pill-style toggle button: selected = accent filled, unselected = grey button.
 * Replaces the old checkbox so config options read as tappable chips.
 */
/** Android Settings style sliding switch: track + sliding thumb. */
@Composable
fun HmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: AppColors = currentColors()
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.inputBg,
        animationSpec = tween(220, easing = HarmonyEasing),
        label = "swTrack"
    )
    val trackBorder by animateColorAsState(
        targetValue = if (checked) colors.accent.copy(alpha = 0.6f) else colors.inputBorder,
        animationSpec = tween(220, easing = HarmonyEasing),
        label = "swBorder"
    )
    val thumbPos by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(240, easing = HarmonyEasing),
        label = "swThumb"
    )
    val thumbColor = if (checked) ComposeColor.White else colors.textTertiary
    Box(
        modifier = modifier
            .width(50.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(0.5.dp, trackBorder, RoundedCornerShape(15.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbPos)
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(thumbColor)
        )
    }
}

@Composable
fun HmToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: AppColors = currentColors(),
    hint: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        HmSwitch(checked = checked, onCheckedChange = onCheckedChange, colors = colors)
    }
}
