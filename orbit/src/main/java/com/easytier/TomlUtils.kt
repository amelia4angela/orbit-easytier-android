package com.easytier

import com.easytier.ui.ConfigState
import com.easytier.ui.PeerNode
import org.json.JSONObject

/**
 * Shared utility for ConfigState ↔ JSON ↔ TOML conversion.
 *
 * Single source of truth — both [MainActivity] and [ConfigEditActivity]
 * delegate to these functions so adding a field to [ConfigState] only
 * needs updates here (plus the Compose form itself).
 */
object TomlUtils {

    // ═══════════════════════════════════════════════
    //  ConfigState ↔ JSON
    // ═══════════════════════════════════════════════

    /** Serialize [ConfigState] to JSONObject (for file persistence). */
    fun configStateToJson(s: ConfigState): JSONObject = JSONObject().also { j ->
        j.put("networkName", s.networkName)
        j.put("secret", s.secret)
        j.put("server", s.server)
        j.put("port", s.port)
        j.put("ip", s.ipAddress)
        j.put("prefix", s.ipPrefix)
        j.put("instanceName", s.instanceName)
        j.put("hostname", s.hostname)
        j.put("dhcp", s.dhcp)
        j.put("disableIpv6", s.disableIpv6)
        j.put("noTun", s.noTun)
        j.put("bindDevice", s.bindDevice)
        j.put("disableP2p", s.disableP2p)
        j.put("p2pOnly", s.p2pOnly)
        j.put("lazyP2p", s.lazyP2p)
        j.put("aggressiveP2p", s.aggressiveP2p)
        j.put("disableTcpHp", s.disableTcpHp)
        j.put("disableUdpHp", s.disableUdpHp)
        j.put("disableSymHp", s.disableSymHp)
        j.put("enableKcpProxy", s.enableKcpProxy)
        j.put("disableKcpInput", s.disableKcpInput)
        j.put("enableQuicProxy", s.enableQuicProxy)
        j.put("disableQuicInput", s.disableQuicInput)
        j.put("proxyForwardBySystem", s.proxyForwardBySystem)
        j.put("relayAllRpc", s.relayAllRpc)
        j.put("exitNode", s.exitNode)
        j.put("latencyFirst", s.latencyFirst)
        j.put("multiThread", s.multiThread)
        j.put("useSmoltcp", s.useSmoltcp)
        j.put("mtu", s.mtu)
        j.put("disableEncrypt", s.disableEncrypt)
        j.put("privateMode", s.privateMode)
        j.put("acceptDns", s.acceptDns)
        j.put("socks5", s.socks5)
        j.put("portForward", s.portForward)
        j.put("encryptAlgo", s.encryptAlgo)
        j.put("compression", s.compression)
        j.put("proxyNetworks", s.proxyNetworks)
        j.put("exitNodes", s.exitNodes)
        j.put("dnsServer", s.dnsServer)
        j.put("dnsEnabled", s.dnsEnabled)
        j.put("extraServers", org.json.JSONArray(s.extraServers))
        val arr = org.json.JSONArray()
        for (n in s.nodes) {
            val o = org.json.JSONObject()
            o.put("protocol", n.protocol); o.put("address", n.address); o.put("port", n.port)
            arr.put(o)
        }
        j.put("nodes", arr)
        j.put("powerSaving", s.powerSaving)
        j.put("memoryLimit", s.memoryLimit)
    }

    /**
     * Parse a peer URI ("tcp://host:port", v6 host may be bracketed) into a [PeerNode].
     * Returns null when the URI is malformed (no protocol / no host / no port).
     */
    fun parsePeerUri(uri: String): PeerNode? {
        val s = uri.trim()
        val sep = s.indexOf("://")
        if (sep <= 0) return null
        val protocol = s.substring(0, sep).lowercase()
        val rest = s.substring(sep + 3)
        if (rest.isEmpty()) return null
        return if (rest.startsWith("[")) {
            val close = rest.indexOf(']')
            if (close <= 1) null
            else {
                val host = rest.substring(0, close + 1)      // keep brackets
                val after = rest.substring(close + 1)
                val port = if (after.startsWith(":")) after.substring(1) else ""
                if (port.isEmpty()) null else PeerNode(protocol, host, port)
            }
        } else {
            val colon = rest.lastIndexOf(':')
            if (colon <= 0 || colon == rest.length - 1) null
            else PeerNode(protocol, rest.substring(0, colon), rest.substring(colon + 1))
        }
    }

    /** Deserialize JSONObject → [ConfigState] (from file load). */
    fun jsonToConfigState(j: JSONObject): ConfigState {
        val legacyExtra = j.optJSONArray("extraServers")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        // Structured nodes (new format)
        val parsed = mutableListOf<PeerNode>()
        j.optJSONArray("nodes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val address = o.optString("address").trim()
                if (address.isEmpty()) continue
                parsed.add(
                    PeerNode(
                        protocol = o.optString("protocol").ifEmpty { "tcp" },
                        address = address,
                        port = o.optString("port").trim()
                    )
                )
            }
        }

        // Migration: no nodes yet → derive from legacy server/port + extraServers.
        // Also re-derive when the legacy fields were edited elsewhere (the XML
        // config editor writes server/port but knows nothing about `nodes`),
        // otherwise stale nodes would silently win over the user's edit.
        val legacyServer = j.optString("server").trim()
        val legacyPort = j.optString("port").trim()
        val nodesStale = parsed.isNotEmpty() && legacyServer.isNotEmpty() &&
                (parsed.first().address != legacyServer || parsed.first().port != legacyPort)

        val nodes: List<PeerNode> = if (parsed.isNotEmpty() && !nodesStale) {
            parsed
        } else {
            val migrated = mutableListOf<PeerNode>()
            if (legacyServer.isNotEmpty()) {
                migrated.add(PeerNode("tcp", legacyServer, legacyPort))
            }
            for (extra in legacyExtra) {
                parsePeerUri(extra)?.let { migrated.add(it) }
            }
            migrated
        }

        return ConfigState(
            networkName = j.optString("networkName"),
            secret = j.optString("secret"),
            server = j.optString("server"),
            port = j.optString("port"),
            ipAddress = j.optString("ip"),
            ipPrefix = j.optString("prefix").ifEmpty { "24" },
            instanceName = j.optString("instanceName"),
            hostname = j.optString("hostname"),
            dhcp = j.optBoolean("dhcp"),
            disableIpv6 = j.optBoolean("disableIpv6"),
            noTun = j.optBoolean("noTun"),
            bindDevice = j.optString("bindDevice"),
            disableP2p = j.optBoolean("disableP2p"),
            p2pOnly = j.optBoolean("p2pOnly"),
            lazyP2p = j.optBoolean("lazyP2p"),
            // 旧配置：只有 needP2p（激进开关上线前导出的）时沿用它；两者都没有则默认激进。
            // 字段已合并，needP2p 只在读旧 JSON 时兼容，不再写出。
            aggressiveP2p = if (j.has("aggressiveP2p")) j.optBoolean("aggressiveP2p", true)
                            else j.optBoolean("needP2p", true),
            disableTcpHp = j.optBoolean("disableTcpHp"),
            disableUdpHp = j.optBoolean("disableUdpHp"),
            disableSymHp = j.optBoolean("disableSymHp"),
            enableKcpProxy = j.optBoolean("enableKcpProxy"),
            disableKcpInput = j.optBoolean("disableKcpInput"),
            enableQuicProxy = j.optBoolean("enableQuicProxy"),
            disableQuicInput = j.optBoolean("disableQuicInput"),
            proxyForwardBySystem = j.optBoolean("proxyForwardBySystem"),
            relayAllRpc = j.optBoolean("relayAllRpc"),
            exitNode = j.optBoolean("exitNode"),
            latencyFirst = j.optBoolean("latencyFirst"),
            multiThread = j.optBoolean("multiThread"),
            useSmoltcp = j.optBoolean("useSmoltcp"),
            mtu = j.optString("mtu"),
            disableEncrypt = j.optBoolean("disableEncrypt"),
            privateMode = j.optBoolean("privateMode"),
            acceptDns = j.optBoolean("acceptDns"),
            socks5 = j.optString("socks5"),
            portForward = j.optString("portForward"),
            encryptAlgo = j.optString("encryptAlgo"),
            compression = j.optString("compression"),
            proxyNetworks = j.optString("proxyNetworks"),
            exitNodes = j.optString("exitNodes"),
            dnsServer = j.optString("dnsServer"),
            dnsEnabled = j.optBoolean("dnsEnabled", false),
            extraServers = legacyExtra,
            nodes = nodes,
            powerSaving = j.optBoolean("powerSaving", false),
            memoryLimit = j.optString("memoryLimit").ifEmpty { "standard" }
        )
    }

    // ═══════════════════════════════════════════════
    //  ConfigState → TOML (export + daemon start)
    // ═══════════════════════════════════════════════

    /** Escape a string value for TOML double-quoted string. */
    private fun escapeToml(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Bracket a bare IPv6 host; already-bracketed (or IPv4/hostname) values pass through. */
    private fun formatHost(addr: String): String =
        if (addr.contains(':') && !addr.startsWith("[")) "[$addr]" else addr

    /**
     * 1:1 布尔开关：ConfigState 字段 ↔ EasyTier 2.6.4 `[flags]` 键。
     *
     * 键名取自 easytier 2.6.4 的 `easytier/src/proto/common.proto`
     * (message FlagsInConfig)，序列化形态见同版本 `easytier/src/common/config.rs`
     * 里的 `Config { flags: Option<serde_json::Map<..>> }` + `gen_flags()`：
     * 开关只能写在 `[flags]` 段，写在顶层会被 serde 静默忽略。
     *
     * 只输出开启的项（与官方 `dump()` 一致：等于默认值的项不写），
     * 除了 multi_thread —— 它的核心默认是 true，写 true 只是显式化。
     */
    private class BoolFlag(
        val key: String,
        val name: String,
        val read: (ConfigState) -> Boolean,
        val write: (ConfigState, Boolean) -> ConfigState
    )

    private val BOOL_FLAGS: List<BoolFlag> = listOf(
        BoolFlag("no_tun", "noTun", read = { it.noTun }, write = { c, b -> c.copy(noTun = b) }),
        BoolFlag("disable_p2p", "disableP2p", read = { it.disableP2p }, write = { c, b -> c.copy(disableP2p = b) }),
        BoolFlag("p2p_only", "p2pOnly", read = { it.p2pOnly }, write = { c, b -> c.copy(p2pOnly = b) }),
        BoolFlag("lazy_p2p", "lazyP2p", read = { it.lazyP2p }, write = { c, b -> c.copy(lazyP2p = b) }),
        BoolFlag("disable_tcp_hole_punching", "disableTcpHp", read = { it.disableTcpHp }, write = { c, b -> c.copy(disableTcpHp = b) }),
        BoolFlag("disable_udp_hole_punching", "disableUdpHp", read = { it.disableUdpHp }, write = { c, b -> c.copy(disableUdpHp = b) }),
        BoolFlag("disable_sym_hole_punching", "disableSymHp", read = { it.disableSymHp }, write = { c, b -> c.copy(disableSymHp = b) }),
        BoolFlag("enable_kcp_proxy", "enableKcpProxy", read = { it.enableKcpProxy }, write = { c, b -> c.copy(enableKcpProxy = b) }),
        BoolFlag("disable_kcp_input", "disableKcpInput", read = { it.disableKcpInput }, write = { c, b -> c.copy(disableKcpInput = b) }),
        BoolFlag("enable_quic_proxy", "enableQuicProxy", read = { it.enableQuicProxy }, write = { c, b -> c.copy(enableQuicProxy = b) }),
        BoolFlag("disable_quic_input", "disableQuicInput", read = { it.disableQuicInput }, write = { c, b -> c.copy(disableQuicInput = b) }),
        BoolFlag("proxy_forward_by_system", "proxyForwardBySystem", read = { it.proxyForwardBySystem }, write = { c, b -> c.copy(proxyForwardBySystem = b) }),
        BoolFlag("relay_all_peer_rpc", "relayAllRpc", read = { it.relayAllRpc }, write = { c, b -> c.copy(relayAllRpc = b) }),
        BoolFlag("enable_exit_node", "exitNode", read = { it.exitNode }, write = { c, b -> c.copy(exitNode = b) }),
        BoolFlag("latency_first", "latencyFirst", read = { it.latencyFirst }, write = { c, b -> c.copy(latencyFirst = b) }),
        // 核心默认 multi_thread = true，打开时显式写一次（关闭则保持核心默认）
        BoolFlag("multi_thread", "multiThread", read = { it.multiThread }, write = { c, b -> c.copy(multiThread = b) }),
        BoolFlag("use_smoltcp", "useSmoltcp", read = { it.useSmoltcp }, write = { c, b -> c.copy(useSmoltcp = b) }),
        BoolFlag("private_mode", "privateMode", read = { it.privateMode }, write = { c, b -> c.copy(privateMode = b) }),
        BoolFlag("accept_dns", "acceptDns", read = { it.acceptDns }, write = { c, b -> c.copy(acceptDns = b) })
    )

    /** ConfigState / JSON 字段名 → 开关定义（导入时用）。 */
    private val BOOL_FLAG_BY_NAME: Map<String, BoolFlag> = BOOL_FLAGS.associateBy { it.name }

    /** 激进 P2P 打开时要压掉的「软」P2P 开关（不输出，交给核心默认）。 */
    private val P2P_SOFT_KEYS = setOf(
        "lazy_p2p", "disable_tcp_hole_punching", "disable_udp_hole_punching", "disable_sym_hole_punching"
    )

    /** `a, b ,c` → `[a, b, c]`（丢掉空项）。 */
    private fun splitList(v: String): List<String> =
        v.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** 官方顶层 `socks5_proxy` 是 URL；UI 里填的是端口（如 1080）。 */
    private fun socks5ProxyUrl(v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        if (t.contains("://")) return t
        val port = t.filter { it.isDigit() }
        return if (port.isEmpty()) null else "socks5://0.0.0.0:$port"
    }

    /** 反向：`socks5://0.0.0.0:1080` → `1080`（UI 只编辑端口）。 */
    private fun socks5PortValue(v: String): String {
        val t = v.trim()
        if (!t.contains("://")) return t
        return t.substringAfterLast(':').filter { it.isDigit() }
    }

    /**
     * UI 里是 CLI 写法 `proto://bind/dst`（例：`udp://0.0.0.0:12345/10.126.126.1:23456`），
     * 官方 toml 是 `[[port_forward]] bind_addr / dst_addr / proto` 三个字段。
     */
    private fun parsePortForward(v: String): Triple<String, String, String>? {
        val t = v.trim()
        val sep = t.indexOf("://")
        if (sep <= 0) return null
        val proto = t.substring(0, sep).lowercase()
        if (proto != "tcp" && proto != "udp") return null
        val rest = t.substring(sep + 3)
        val slash = rest.lastIndexOf('/')
        if (slash <= 0 || slash == rest.length - 1) return null
        val bind = rest.substring(0, slash).trim()
        val dst = rest.substring(slash + 1).trim()
        if (bind.isEmpty() || dst.isEmpty()) return null
        return Triple(proto, bind, dst)
    }

    /**
     * 官方 `[flags] data_compress_algo` 是 CompressionAlgoPb 枚举的整数值
     * （proto: Invalid=0, None=1, Zstd=2）。写成 `"zstd"` 字符串会让核心
     * `serde_json::from_value(..).unwrap()` panic，所以这里必须给整数。
     */
    private fun compressionFlag(v: String): Int? = when (v.trim().lowercase()) {
        "zstd" -> 2
        else -> null            // ""/none/未识别 → 不写，用核心默认（None）
    }

    /** 有效 peer 列表：结构化节点优先，否则回退 server/port + extraServers。空 uri 不输出。 */
    private fun peerUris(s: ConfigState): List<String> {
        val out = mutableListOf<String>()
        if (s.nodes.isNotEmpty()) {
            for (n in s.nodes) {
                val addr = n.address.trim()
                val p = n.port.trim()
                if (addr.isEmpty() || p.isEmpty()) continue
                out.add("${n.protocol.ifEmpty { "tcp" }}://${formatHost(addr)}:$p")
            }
        } else {
            val server = s.server.trim()
            val port = s.port.trim()
            if (server.isNotEmpty() && port.isNotEmpty())
                out.add("tcp://${formatHost(server)}:$port")
            for (extra in s.extraServers) {
                val uri = extra.trim()
                if (uri.isNotEmpty()) out.add(uri)
            }
        }
        return out
    }

    /**
     * `[flags]` 段内容（官方 2.6.4 FlagsInConfig）。返回 键 → 已格式化的 TOML 右值。
     *
     * 顺序敏感的三个特殊项：
     *  - `enable_ipv6` / `enable_encryption` 是正向开关，UI 存的是「禁用」，需要取反；
     *  - `bind_device` 在 2.6.4 是布尔（把 socket 绑到物理网卡），没有网卡名参数；
     *  - `need_p2p` 只由 aggressiveP2p 生成（原「需要 P2P」开关已合并，见 P2P_SOFT_KEYS）。
     */
    private fun buildFlags(s: ConfigState): Map<String, String> {
        val f = linkedMapOf<String, String>()

        if (s.disableIpv6) f["enable_ipv6"] = "false"
        if (s.disableEncrypt) f["enable_encryption"] = "false"
        if (s.bindDevice.isNotBlank()) f["bind_device"] = "true"

        // 激进 P2P：唯一决定 need_p2p；开启时不输出 lazy_p2p / disable_*_hole_punching
        if (s.aggressiveP2p) f["need_p2p"] = "true"

        for (spec in BOOL_FLAGS) {
            if (s.aggressiveP2p && spec.key in P2P_SOFT_KEYS) continue
            if (!spec.read(s)) continue
            f[spec.key] = "true"
        }

        // mtu 必须是整数（写字符串会让核心 panic），0 = 自动，不输出
        s.mtu.trim().toIntOrNull()?.let { if (it > 0) f["mtu"] = it.toString() }
        if (s.encryptAlgo.isNotBlank())
            f["encryption_algorithm"] = "\"${escapeToml(s.encryptAlgo.trim())}\""
        compressionFlag(s.compression)?.let { f["data_compress_algo"] = it.toString() }

        // ── 省电模式（EasyTier 层）──
        // EasyTier 2.6.4 没有可配置的心跳 / peer 轮询间隔（`easytier-core --help` 与
        // 2.6.4 的 config.rs 里都没有 interval 类字段），能用且安全的是关掉 UPnP：
        // 否则核心会周期性做 SSDP 组播和 WANIPConnection 端口映射探测，每次都会
        // 唤醒无线电。stun_servers / lazy_p2p 会削弱 NAT 探测与打洞，这里刻意不动。
        if (s.powerSaving) f["disable_upnp"] = "true"

        return f
    }

    /**
     * Build TOML string from [ConfigState] for the EasyTier daemon.
     *
     * 结构对齐 EasyTier 2.6.4 `Config`（easytier/src/common/config.rs）：
     * 顶层标量必须在任何表头之前，否则 TOML 会把它们并入上一个表；
     * 所有 flag 类开关进 `[flags]`。
     */
    fun buildToml(s: ConfigState): String {
        val sb = StringBuilder()

        // ── 顶层标量 ──
        sb.append("instance_name = \"${escapeToml(s.instanceName.ifEmpty { "default" })}\"\n")
        if (s.hostname.isNotBlank()) sb.append("hostname = \"${escapeToml(s.hostname.trim())}\"\n")
        if (s.ipAddress.isNotBlank()) {
            val prefix = s.ipPrefix.ifBlank { "24" }
            sb.append("ipv4 = \"${escapeToml(s.ipAddress.trim())}/$prefix\"\n")
        }
        if (s.dhcp) sb.append("dhcp = true\n")
        socks5ProxyUrl(s.socks5)?.let { sb.append("socks5_proxy = \"${escapeToml(it)}\"\n") }

        // 官方顶层 exit_nodes 是「虚拟 IP 数组」，不是表
        val exitVips = splitList(s.exitNodes).filter { it.contains('.') || it.contains(':') }
        if (exitVips.isNotEmpty())
            sb.append("exit_nodes = [${exitVips.joinToString(", ") { "\"${escapeToml(it)}\"" }}]\n")

        sb.append("\n[network_identity]\n")
        sb.append("network_name = \"${escapeToml(s.networkName)}\"\n")
        sb.append("network_secret = \"${escapeToml(s.secret)}\"\n")

        // ── [[peer]] ── uri 必须是合法 URL，没有有效节点时整段省略
        for (uri in peerUris(s)) {
            sb.append("\n[[peer]]\n")
            sb.append("uri = \"${escapeToml(uri)}\"\n")
        }

        // ── [[proxy_network]] ── 官方字段是 cidr / mapped_cidr（旧代码的
        //    [[proxy_networks]] + proxy_network_cidr 是错的，核心不认识）
        for (entry in splitList(s.proxyNetworks)) {
            val parts = entry.split("->")
            val real = parts[0].trim()
            if (real.isEmpty()) continue
            sb.append("\n[[proxy_network]]\n")
            sb.append("cidr = \"${escapeToml(real)}\"\n")
            val mapped = parts.getOrNull(1)?.trim().orEmpty()
            if (mapped.isNotEmpty()) sb.append("mapped_cidr = \"${escapeToml(mapped)}\"\n")
        }

        // ── [[port_forward]] ── 官方字段 bind_addr / dst_addr / proto
        parsePortForward(s.portForward)?.let { (proto, bind, dst) ->
            sb.append("\n[[port_forward]]\n")
            sb.append("bind_addr = \"${escapeToml(bind)}\"\n")
            sb.append("dst_addr = \"${escapeToml(dst)}\"\n")
            sb.append("proto = \"${escapeToml(proto)}\"\n")
        }

        // ── [flags] ── 只能写在这里；顶层裸 flag 会被 serde 静默忽略
        val flags = buildFlags(s)
        if (flags.isNotEmpty()) {
            sb.append("\n[flags]\n")
            for ((k, v) in flags) sb.append("$k = $v\n")
        }

        return sb.toString()
    }

    // ═══════════════════════════════════════════════
    //  TOML → JSON (import)
    // ═══════════════════════════════════════════════

    /** `bind_device` 只有布尔；ConfigState 沿用旧的字符串字段，非空即视为开启。 */
    const val BIND_DEVICE_ON = "auto"

    /**
     * Parse raw TOML text into a JSONObject with the same keys as [configStateToJson].
     *
     * 同时吃两种格式：
     *  - EasyTier 2.6.4 官方：开关在 `[flags]`，网段是 `[[proxy_network]] cidr`，
     *    端口转发是 `[[port_forward]] bind_addr/dst_addr/proto`，`socks5_proxy` 是 URL；
     *  - 旧版 orbit 导出的「顶层裸 flag」格式 —— 迁移读取，不丢字段。
     */
    fun parseTomlToJson(text: String): JSONObject {
        val j = JSONObject()
        var section = ""

        // [[port_forward]] 的字段可能任意顺序出现，攒齐再落盘
        var pfProto = ""
        var pfBind = ""
        var pfDst = ""
        fun flushPortForward() {
            if (pfBind.isNotEmpty() && pfDst.isNotEmpty()) {
                val proto = pfProto.ifEmpty { "tcp" }
                j.put("portForward", "$proto://$pfBind/$pfDst")
            }
            pfProto = ""; pfBind = ""; pfDst = ""
        }

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("[")) {
                if (section == "port_forward") flushPortForward()
                // "[flags]" / "[[peer]]" → "flags" / "peer"
                section = trimmed.trimStart('[').trimEnd(']').trim()
                continue
            }

            val eqIdx = trimmed.indexOf('=')
            if (eqIdx < 0) continue
            val key = trimmed.substring(0, eqIdx).trim()
            val rawValue = trimmed.substring(eqIdx + 1).trim()
            val value = rawValue.removeSurrounding("\"").trim()

            when {
                section == "peer" && key == "uri" -> putPeerUri(j, value)
                section == "proxy_network" && key == "cidr" -> appendCsv(j, "proxyNetworks", value)
                // 映射网段（cidr->mapped）在 ConfigState 里是同一项的后半段
                section == "proxy_network" && key == "mapped_cidr" -> {
                    val existing = j.optString("proxyNetworks")
                    if (existing.isNotEmpty()) {
                        val parts = existing.split(",").map { it.trim() }.toMutableList()
                        parts[parts.lastIndex] = "${parts.last()}->$value"
                        j.put("proxyNetworks", parts.joinToString(","))
                    }
                }
                section == "port_forward" -> when (key) {
                    // 官方字段
                    "bind_addr", "port_forward_local" -> pfBind = value
                    "dst_addr", "port_forward_remote" -> pfDst = value
                    "proto", "port_forward_protocol" -> pfProto = value.lowercase()
                }
                // 顶层与 [flags]、[network_identity] 用同一套键表（宽容迁移）
                else -> applyTomlKey(j, key, rawValue, value)
            }
        }
        flushPortForward()
        return j
    }

    /** 单个 `key = value` → JSON 字段；官方键优先，旧版顶层裸 flag 也认。 */
    private fun applyTomlKey(j: JSONObject, key: String, rawValue: String, value: String) {
        // 反向开关 / 特殊类型：官方写正向 enable_*，ConfigState 存的是「禁用」
        when (key) {
            "enable_ipv6" -> { j.put("disableIpv6", !parseBool(rawValue)); return }
            "enable_encryption" -> { j.put("disableEncrypt", !parseBool(rawValue)); return }
            "need_p2p" -> { j.put("aggressiveP2p", parseBool(rawValue)); return }
            "bind_device" -> {
                j.put("bindDevice", if (parseBool(rawValue)) BIND_DEVICE_ON else ""); return
            }
            "data_compress_algo" -> {
                j.put("compression", if (value.filter { it.isDigit() } == "2") "zstd" else "none")
                return
            }
            "disable_upnp" -> { j.put("powerSaving", parseBool(rawValue)); return }
        }
        BOOL_KEYS[key]?.let { j.put(it, parseBool(rawValue)); return }

        when (key) {
            "instance_name" -> j.put("instanceName", value)
            "hostname" -> j.put("hostname", value)
            "ipv4" -> {
                val slashIdx = value.indexOf('/')
                if (slashIdx > 0) {
                    j.put("ip", value.substring(0, slashIdx))
                    j.put("prefix", value.substring(slashIdx + 1))
                } else { j.put("ip", value) }
            }
            "dhcp" -> j.put("dhcp", parseBool(rawValue))
            "network_name" -> j.put("networkName", value)
            "network_secret" -> j.put("secret", value)
            "uri" -> putPeerUri(j, value)
            // 旧版 orbit 顶层裸 flag
            "disable_ipv6" -> j.put("disableIpv6", parseBool(rawValue))
            "disable_encryption" -> j.put("disableEncrypt", parseBool(rawValue))
            "mtu" -> j.put("mtu", value)
            "socks5" -> j.put("socks5", value)
            "socks5_proxy" -> j.put("socks5", socks5PortValue(value))
            "encryption_algorithm" -> j.put("encryptAlgo", value)
            "compression" -> j.put("compression", value)
            "proxy_network_cidr" -> appendCsv(j, "proxyNetworks", value)
            "node" -> appendCsv(j, "exitNodes", value)
            "exit_nodes" -> for (ip in parseTomlArray(rawValue)) appendCsv(j, "exitNodes", ip)
        }
    }

    /** `[[peer]] uri = ...` → nodes 数组（保留 IPv6 方括号）+ 首个节点回填 server/port。 */
    private fun putPeerUri(j: JSONObject, uri: String) {
        val node = parsePeerUri(uri) ?: return
        val arr = j.optJSONArray("nodes") ?: org.json.JSONArray().also { j.put("nodes", it) }
        val o = JSONObject()
        o.put("protocol", node.protocol)
        o.put("address", node.address)
        o.put("port", node.port)
        arr.put(o)
        if (!j.has("server")) {
            j.put("server", node.address)
            j.put("port", node.port)
        }
    }

    /** 逗号串追加（去重）。 */
    private fun appendCsv(j: JSONObject, key: String, value: String) {
        val v = value.trim().removeSurrounding("[")
        if (v.isEmpty()) return
        val existing = j.optString(key)
        val parts = if (existing.isEmpty()) emptyList() else existing.split(",").map { it.trim() }
        if (v in parts) return
        j.put(key, if (existing.isEmpty()) v else "$existing,$v")
    }

    /** `["a", "b"]` → `[a, b]`；非数组返回空。 */
    private fun parseTomlArray(raw: String): List<String> {
        val t = raw.trim()
        if (!t.startsWith("[") || !t.endsWith("]")) return emptyList()
        return t.substring(1, t.length - 1)
            .split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
            .filter { it.isNotEmpty() }
    }

    /** Derive a human-friendly config name from parsed TOML content. */
    fun deriveConfigName(j: JSONObject): String {
        val fromNetwork = j.optString("networkName").ifEmpty { null }
        val fromInstance = j.optString("instanceName").ifEmpty { null }
        return fromNetwork ?: fromInstance ?: "imported"
    }

    /** Derive a config name from a parsed [ConfigState] (lenient import path). */
    fun deriveNameFromState(s: ConfigState): String =
        s.networkName.ifEmpty { s.instanceName }.ifEmpty { "imported" }

    /** Parse a TOML boolean value string. */
    fun parseBool(s: String): Boolean =
        s.trim().lowercase() in listOf("true", "yes", "1")

    // ═══════════════════════════════════════════════
    //  Lenient import — any text → ConfigState
    // ═══════════════════════════════════════════════

    const val DEFAULT_PEER_PORT = "11010"

    private val PEER_PROTOCOLS = setOf("tcp", "udp", "ws", "wss", "quic", "grpc")

    /** Any peer URI in free text, structure irrelevant. */
    private val URI_REGEX =
        Regex("(?i)\\b(?:tcp|udp|ws|wss|quic|grpc)://[^\\s\"'<>\\\\]+")

    /** `key = value`, `key: value`, `key="value"` — key may use -, ., or nothing. */
    private val KV_REGEX = Regex(
        "([A-Za-z_][A-Za-z0-9_.\\-]*)\\s*[:=]\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[^\\s,;]+)"
    )

    /** `--key value` CLI style (EasyTier command line pasted into a file). */
    private val CLI_REGEX = Regex(
        "--([A-Za-z_][A-Za-z0-9_.\\-]*)[ \\t]+(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[^\\s]+)"
    )

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6_LOOSE_REGEX = Regex("^[0-9a-fA-F:]+$")

    /**
     * Boolean-ish config keys (normalized) → ConfigState slot name.
     * 1:1 的官方 `[flags]` 键直接从 [BOOL_FLAGS] 派生，别名（旧版 / CLI 写法）另外补。
     */
    private val BOOL_KEYS: Map<String, String> = buildMap {
        for (f in BOOL_FLAGS) put(f.key, f.name)
        put("dhcp", "dhcp")
        put("notun", "noTun")                        // 旧版 --notun 写法
        put("disable_ipv6", "disableIpv6")           // 旧版 orbit 顶层裸 flag
        put("disable_encryption", "disableEncrypt")
        put("need_p2p", "aggressiveP2p")             // 已与激进 P2P 合并
        put("aggressive_p2p", "aggressiveP2p")
    }

    /**
     * Import arbitrary text (TOML / JSON / mixed / commented / unordered).
     *
     * Never throws and never fails because a field is missing — only a document
     * with zero recognizable content yields [ImportResult.recognized] == false.
     */
    fun parseLenient(text: String): ImportResult {
        val warnings = mutableListOf<String>()
        val raw = text.removePrefix("\uFEFF").trim()
        if (raw.isEmpty()) {
            return ImportResult(ConfigState(), 0, emptyList(), listOf("文件内容为空"))
        }

        // 1) Whole document parses as JSON → reuse the structured reader.
        var base: ConfigState? = null
        if (raw.startsWith("{") || raw.startsWith("[")) {
            val obj = runCatching { JSONObject(raw) }.getOrNull()
            if (obj != null) {
                val st = jsonToConfigState(obj)
                if (hasAnyValue(st)) base = st
                else warnings.add("JSON 里没有已知字段，已改用宽松文本扫描")
            } else {
                warnings.add("JSON 解析失败，已改用宽松文本扫描")
            }
        }

        // 2) Lenient scan always runs — it also fills gaps the JSON path left empty.
        val scan = scanFreeText(raw, warnings)
        val cfg = if (base != null) mergeStates(base, scan.state) else scan.state

        // "unrecognized lines" only matters when we had to read the text as text.
        if (base == null && scan.unknownLines > 0)
            warnings.add("有 ${scan.unknownLines} 行内容未识别，已忽略")
        if (cfg.nodes.isEmpty()) warnings.add("没有找到服务器节点")

        return ImportResult(cfg, cfg.nodes.size, describeFields(cfg), warnings)
    }

    private class ScanOut(val state: ConfigState, val unknownLines: Int)

    private fun scanFreeText(text: String, warnings: MutableList<String>): ScanOut {
        var cfg = ConfigState()
        val nodes = mutableListOf<PeerNode>()
        val peerValues = mutableListOf<String>()
        var lastPort = ""
        var unknownLines = 0
        var section = ""
        var pfProto = ""
        var pfBind = ""
        var pfDst = ""

        // Peers first: every URI in the whole text counts, structure ignored.
        for (m in URI_REGEX.findAll(text)) {
            val node = nodeFromUri(m.value, warnings) ?: continue
            addNode(nodes, node)
        }

        for (rawLine in text.lines()) {
            val line = stripInlineComment(rawLine).trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("[") && line.endsWith("]")) {          // [section] / [[peer]]
                section = line.trimStart('[').trimEnd(']').trim()
                continue
            }
            if (line == "{" || line == "}" || line == "[" || line == "]") continue

            val pairs = keyValues(line)
            if (pairs.isEmpty()) {
                if (!URI_REGEX.containsMatchIn(line)) unknownLines++
                continue
            }

            var consumed = false
            for ((rawKey, rawValue) in pairs) {
                val key = normalizeKey(rawKey)
                val value = cleanValue(rawValue)
                if (value.isEmpty()) continue
                when (key) {
                    "network_name", "networkname", "net_name" -> {
                        cfg = cfg.copy(networkName = value); consumed = true
                    }
                    "network_secret", "networksecret", "secret", "password", "passwd", "network_key" -> {
                        cfg = cfg.copy(secret = value); consumed = true
                    }
                    "ipv4", "ip", "ip_address", "ip_addr", "ipv4_addr", "tun_ip" -> {
                        cfg = applyIp(cfg, value, warnings); consumed = true
                    }
                    "ip_prefix", "ipv4_prefix", "prefix", "netmask", "subnet" -> {
                        cfg = cfg.copy(ipPrefix = value.trimStart('/')); consumed = true
                    }
                    // 官方 [[proxy_network]] cidr 是网段；其它上下文里的 cidr 是 TUN 前缀
                    "cidr" -> {
                        cfg = if (section == "proxy_network")
                            cfg.copy(proxyNetworks = appendCsv(cfg.proxyNetworks, value))
                        else
                            cfg.copy(ipPrefix = value.trimStart('/'))
                        consumed = true
                    }
                    "instance_name", "instancename", "instance" -> {
                        cfg = cfg.copy(instanceName = value); consumed = true
                    }
                    "hostname", "host_name" -> {
                        cfg = cfg.copy(hostname = value); consumed = true
                    }
                    "port", "peer_port", "server_port" -> {
                        val digits = value.filter { it.isDigit() }
                        if (digits.isNotEmpty()) lastPort = digits
                        else warnings.add("端口无法解析: $value")
                        consumed = true
                    }
                    "server", "host", "peer", "peers", "peer_uri", "uri", "uris",
                    "addr", "address", "server_addr", "server_address", "remote" -> {
                        peerValues.add(value); consumed = true
                    }
                    "mtu" -> {
                        cfg = cfg.copy(mtu = value.filter { it.isDigit() }); consumed = true
                    }
                    // 官方是布尔开关；旧版是网卡名字符串
                    "bind_device" -> {
                        val b = lenientBool(value)
                        cfg = cfg.copy(
                            bindDevice = if (b == null) value else if (b) BIND_DEVICE_ON else ""
                        )
                        consumed = true
                    }
                    // 官方 socks5_proxy 是 URL，UI 里只存端口
                    "socks5", "socks5_proxy" -> {
                        cfg = cfg.copy(socks5 = socks5PortValue(value)); consumed = true
                    }
                    "encryption_algorithm", "encrypt_algo", "encrypt", "cipher" -> {
                        cfg = cfg.copy(encryptAlgo = value); consumed = true
                    }
                    // 官方是正向开关，ConfigState 存「禁用」
                    "enable_ipv6" -> {
                        lenientBool(value)?.let { cfg = cfg.copy(disableIpv6 = !it) }
                        consumed = true
                    }
                    "enable_encryption" -> {
                        lenientBool(value)?.let { cfg = cfg.copy(disableEncrypt = !it) }
                        consumed = true
                    }
                    "compression", "compress" -> {
                        cfg = cfg.copy(compression = value); consumed = true
                    }
                    // 官方是 CompressionAlgoPb 整数（None=1, Zstd=2）
                    "data_compress_algo" -> {
                        cfg = cfg.copy(compression = if (value.filter { it.isDigit() } == "2") "zstd" else "none")
                        consumed = true
                    }
                    // 省电模式注入的 disable_upnp
                    "disable_upnp" -> {
                        lenientBool(value)?.let { cfg = cfg.copy(powerSaving = it) }
                        consumed = true
                    }
                    "dns", "dns_server", "dns_servers", "dns_server_list" -> {
                        cfg = cfg.copy(dnsServer = value); consumed = true
                    }
                    "proxy_network", "proxy_networks", "proxy_network_cidr" -> {
                        cfg = cfg.copy(proxyNetworks = appendCsv(cfg.proxyNetworks, value)); consumed = true
                    }
                    // [[proxy_network]] mapped_cidr 属于上一项的映射（cidr->mapped）
                    "mapped_cidr" -> {
                        if (cfg.proxyNetworks.isNotEmpty()) {
                            val parts = cfg.proxyNetworks.split(",").map { it.trim() }.toMutableList()
                            parts[parts.lastIndex] = "${parts.last()}->$value"
                            cfg = cfg.copy(proxyNetworks = parts.joinToString(","))
                        }
                        consumed = true
                    }
                    "exit_nodes", "exit_node", "exit_node_vip" -> {
                        for (ip in csvValues(value))
                            cfg = cfg.copy(exitNodes = appendCsv(cfg.exitNodes, ip))
                        consumed = true
                    }
                    // 官方 [[port_forward]] 的三个字段（顺序任意，循环结束后拼装）
                    "bind_addr", "port_forward_local" -> { pfBind = value; consumed = true }
                    "dst_addr", "port_forward_remote" -> { pfDst = value; consumed = true }
                    "proto", "port_forward_protocol" -> { pfProto = value.lowercase(); consumed = true }
                    else -> {
                        val slot = BOOL_KEYS[key]
                        if (slot != null) {
                            consumed = true
                            val b = lenientBool(value)
                            if (b == null) warnings.add("「$rawKey」的布尔值无法解析: $value")
                            else cfg = setBool(cfg, slot, b)
                        }
                    }
                }
            }
            if (!consumed && !URI_REGEX.containsMatchIn(line)) unknownLines++
        }

        if (pfBind.isNotEmpty() && pfDst.isNotEmpty() && cfg.portForward.isEmpty())
            cfg = cfg.copy(portForward = "${pfProto.ifEmpty { "tcp" }}://$pfBind/$pfDst")

        // Fallback: no URI anywhere → build nodes from server/host (+port) values.
        if (nodes.isEmpty()) {
            for (v in peerValues) {
                for (n in nodesFromValue(v, lastPort, warnings)) addNode(nodes, n)
            }
        }

        if (nodes.isNotEmpty()) {
            cfg = cfg.copy(
                server = cfg.server.ifEmpty { nodes.first().address },
                port = cfg.port.ifEmpty { nodes.first().port },
                nodes = nodes,
                extraServers = nodes.drop(1).map {
                    "${it.protocol}://${formatHost(it.address)}:${it.port}"
                }
            )
        }
        return ScanOut(cfg, unknownLines)
    }

    private fun addNode(list: MutableList<PeerNode>, n: PeerNode) {
        if (list.none { it.protocol == n.protocol && it.address == n.address && it.port == n.port })
            list.add(n)
    }

    /** Lenient URI → PeerNode: tolerates missing port (defaults), keeps v6 brackets. */
    private fun nodeFromUri(token: String, warnings: MutableList<String>): PeerNode? {
        val clean = token.trim()
            .substringBefore('#')
            .trim()
            .trimEnd('/', ',', ';', ')', '，', '；', '。')
        val sep = clean.indexOf("://")
        if (sep <= 0) return null
        val protocol = clean.substring(0, sep).lowercase()
        if (protocol !in PEER_PROTOCOLS) return null
        var rest = clean.substring(sep + 3)
        rest = rest.substringBefore('/').substringBefore('?')
        if (rest.isEmpty()) return null

        var host: String
        var port: String
        if (rest.startsWith("[")) {
            val close = rest.indexOf(']')
            if (close <= 1) {
                warnings.add("IPv6 节点地址不完整: $clean")
                return null
            }
            host = rest.substring(0, close + 1)
            val tail = rest.substring(close + 1)
            port = if (tail.startsWith(":")) tail.substring(1) else ""
        } else {
            when (rest.count { it == ':' }) {
                0 -> { host = rest; port = "" }
                1 -> { host = rest.substringBefore(':'); port = rest.substringAfter(':') }
                else -> {   // bare IPv6, no brackets
                    host = rest; port = ""
                    warnings.add("IPv6 地址未加方括号: $clean")
                }
            }
        }
        if (host.isEmpty()) return null
        if (port.isEmpty()) {
            port = DEFAULT_PEER_PORT
            warnings.add("节点 $host 缺少端口，已按 $DEFAULT_PEER_PORT 处理")
        } else if (port.any { !it.isDigit() }) {
            warnings.add("节点端口可疑: $clean")
        }
        return PeerNode(protocol, host, port)
    }

    /** Build nodes from a server/host value that may itself be a URI, host:port or bare host. */
    private fun nodesFromValue(
        value: String,
        fallbackPort: String,
        warnings: MutableList<String>
    ): List<PeerNode> {
        val out = mutableListOf<PeerNode>()
        for (rawPart in value.split(Regex("[\\s,]+"))) {
            val part = rawPart.trim().trim(',', ';')
            if (part.isEmpty()) continue
            if (part.contains("://")) {
                nodeFromUri(part, warnings)?.let { out.add(it) }
                continue
            }
            var host = part
            var port = fallbackPort
            if (part.count { it == ':' } == 1) {
                val tail = part.substringAfter(':')
                if (tail.isNotEmpty() && tail.all { it.isDigit() }) {
                    host = part.substringBefore(':')
                    port = tail
                }
            }
            if (host.isEmpty()) continue
            if (port.isEmpty()) {
                port = DEFAULT_PEER_PORT
                warnings.add("服务器 $host 缺少端口，已按 $DEFAULT_PEER_PORT 处理")
            }
            out.add(PeerNode("tcp", host, port))
        }
        return out
    }

    /** Cut an inline `# comment` while respecting quoted strings. */
    private fun stripInlineComment(line: String): String {
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '#' -> return line.substring(0, i)
            }
            i++
        }
        return line
    }

    /** All key/value pairs on one line (TOML-ish `k = v`, YAML-ish `k: v`, `--k v`). */
    private fun keyValues(line: String): List<Pair<String, String>> {
        val hits = mutableListOf<Pair<Int, Pair<String, String>>>()
        for (m in KV_REGEX.findAll(line)) {
            hits.add(m.range.first to (m.groupValues[1] to m.groupValues[2]))
        }
        if (line.contains("--")) {
            for (m in CLI_REGEX.findAll(line)) {
                hits.add(m.range.first to (m.groupValues[1] to m.groupValues[2]))
            }
        }
        return hits.sortedBy { it.first }.distinctBy { it.first }.map { it.second }
    }

    /** Lowercase, unify separators: `Network-Name` → `network_name`. */
    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase().replace('-', '_').replace('.', '_').trim('_')

    /** Strip quotes / inline comment / trailing punctuation from a raw value. */
    private fun cleanValue(raw: String): String {
        var v = raw.trim()
        if (v.isEmpty()) return v
        val q = v.first()
        if (q == '"' || q == '\'') {
            val end = v.indexOf(q, 1)
            if (end > 1) v = v.substring(1, end)
            return v.trim()
        }
        val hash = v.indexOf('#')
        if (hash >= 0) v = v.substring(0, hash)
        return v.trim().trimEnd(',', ';')
    }

    private fun applyIp(cfg: ConfigState, rawValue: String, warnings: MutableList<String>): ConfigState {
        val v = rawValue.trim()
        val ip = v.substringBefore('/').trim()
        val prefix = if (v.contains('/')) v.substringAfter('/').trim().filter { it.isDigit() } else ""
        if (!looksLikeIp(ip)) warnings.add("IP 字段格式可疑: $v")
        return cfg.copy(ipAddress = ip, ipPrefix = prefix.ifEmpty { cfg.ipPrefix })
    }

    private fun looksLikeIp(s: String): Boolean =
        IPV4_REGEX.matches(s) || (s.contains(':') && IPV6_LOOSE_REGEX.matches(s))

    private fun appendCsv(existing: String, v: String): String =
        if (existing.isBlank()) v else "$existing,$v"

    /** `["a","b"]` / `a, b` → [a, b]（宽松导入用）。 */
    private fun csvValues(v: String): List<String> =
        v.trim().removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
            .filter { it.isNotEmpty() }

    /** true/false/1/0/yes/no/on/off/是/否 … null when unparseable. */
    private fun lenientBool(v: String): Boolean? =
        when (v.trim().lowercase()) {
            "true", "1", "yes", "on", "enable", "enabled", "是", "开" -> true
            "false", "0", "no", "off", "disable", "disabled", "否", "关" -> false
            else -> null
        }

    private fun setBool(c: ConfigState, slot: String, b: Boolean): ConfigState {
        val spec = BOOL_FLAG_BY_NAME[slot]
        if (spec != null) return spec.write(c, b)
        return when (slot) {
            "dhcp" -> c.copy(dhcp = b)
            "disableIpv6" -> c.copy(disableIpv6 = b)
            "disableEncrypt" -> c.copy(disableEncrypt = b)
            "aggressiveP2p" -> c.copy(aggressiveP2p = b)
            else -> c
        }
    }

    /** Did the structured JSON path actually read anything? */
    private fun hasAnyValue(s: ConfigState): Boolean =
        s.copy(ipPrefix = "24") != ConfigState(ipPrefix = "24")

    /** JSON state wins; the scan only fills what JSON left empty. */
    private fun mergeStates(json: ConfigState, scan: ConfigState): ConfigState = json.copy(
        networkName = json.networkName.ifEmpty { scan.networkName },
        secret = json.secret.ifEmpty { scan.secret },
        server = json.server.ifEmpty { scan.server },
        port = json.port.ifEmpty { scan.port },
        ipAddress = json.ipAddress.ifEmpty { scan.ipAddress },
        ipPrefix = if (json.ipPrefix.isEmpty() || json.ipPrefix == "24") scan.ipPrefix.ifEmpty { json.ipPrefix } else json.ipPrefix,
        instanceName = json.instanceName.ifEmpty { scan.instanceName },
        hostname = json.hostname.ifEmpty { scan.hostname },
        bindDevice = json.bindDevice.ifEmpty { scan.bindDevice },
        mtu = json.mtu.ifEmpty { scan.mtu },
        socks5 = json.socks5.ifEmpty { scan.socks5 },
        portForward = json.portForward.ifEmpty { scan.portForward },
        encryptAlgo = json.encryptAlgo.ifEmpty { scan.encryptAlgo },
        compression = json.compression.ifEmpty { scan.compression },
        proxyNetworks = json.proxyNetworks.ifEmpty { scan.proxyNetworks },
        exitNodes = json.exitNodes.ifEmpty { scan.exitNodes },
        dnsServer = json.dnsServer.ifEmpty { scan.dnsServer },
        extraServers = if (json.extraServers.isNotEmpty()) json.extraServers else scan.extraServers,
        nodes = if (json.nodes.isNotEmpty()) {
            (json.nodes + scan.nodes).distinctBy { "${it.protocol}|${it.address}|${it.port}" }
        } else scan.nodes
    )

    /** Chinese labels for every field that ended up with a non-default value. */
    private fun describeFields(s: ConfigState): List<String> {
        val out = mutableListOf<String>()
        if (s.nodes.isNotEmpty()) out.add("服务器节点")
        if (s.networkName.isNotEmpty()) out.add("网络名称")
        if (s.secret.isNotEmpty()) out.add("密钥")
        if (s.ipAddress.isNotEmpty()) out.add("本机 IP")
        if (s.ipPrefix.isNotEmpty() && s.ipPrefix != "24") out.add("子网前缀")
        if (s.instanceName.isNotEmpty()) out.add("实例名")
        if (s.hostname.isNotEmpty()) out.add("主机名")
        if (s.dhcp) out.add("DHCP")
        if (s.disableIpv6) out.add("禁用 IPv6")
        if (s.noTun) out.add("无 TUN")
        if (s.bindDevice.isNotEmpty()) out.add("绑定网卡")
        if (s.mtu.isNotEmpty()) out.add("MTU")
        if (s.disableP2p) out.add("禁用 P2P")
        if (s.p2pOnly) out.add("仅 P2P")
        if (s.lazyP2p) out.add("延迟 P2P")
        if (s.aggressiveP2p) out.add("激进 P2P")
        if (s.disableTcpHp) out.add("禁用 TCP 打洞")
        if (s.disableUdpHp) out.add("禁用 UDP 打洞")
        if (s.disableSymHp) out.add("禁用 Sym 打洞")
        if (s.enableKcpProxy) out.add("KCP Proxy")
        if (s.disableKcpInput) out.add("禁用 KCP 入站")
        if (s.enableQuicProxy) out.add("QUIC Proxy")
        if (s.disableQuicInput) out.add("禁用 QUIC 入站")
        if (s.proxyForwardBySystem) out.add("系统代理转发")
        if (s.relayAllRpc) out.add("中继所有 RPC")
        if (s.exitNode) out.add("出口节点")
        if (s.latencyFirst) out.add("延迟优先")
        if (s.multiThread) out.add("多线程")
        if (s.useSmoltcp) out.add("smoltcp")
        if (s.disableEncrypt) out.add("禁用加密")
        if (s.privateMode) out.add("私有模式")
        if (s.acceptDns) out.add("接受 DNS")
        if (s.socks5.isNotEmpty()) out.add("SOCKS5")
        if (s.portForward.isNotEmpty()) out.add("端口转发")
        if (s.encryptAlgo.isNotEmpty()) out.add("加密算法")
        if (s.compression.isNotEmpty()) out.add("压缩")
        if (s.proxyNetworks.isNotEmpty()) out.add("代理网段")
        if (s.exitNodes.isNotEmpty()) out.add("出口节点 VIP")
        if (s.dnsEnabled && s.dnsServer.isNotEmpty()) out.add("DNS")
        return out
    }
}

/**
 * Report produced by [TomlUtils.parseLenient].
 *
 * [recognized] is false only when the text contained nothing usable at all —
 * missing optional fields never make an import fail.
 */
data class ImportResult(
    val state: ConfigState,
    val nodesFound: Int,
    val fieldsFound: List<String>,
    val warnings: List<String> = emptyList()
) {
    val recognized: Boolean get() = nodesFound > 0 || fieldsFound.isNotEmpty()
}
