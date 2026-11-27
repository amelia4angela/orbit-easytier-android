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

    /** Sentinel value meaning "user hasn't filled this field yet" */
    const val TOML_FIELD_EMPTY = ""

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
        j.put("needP2p", s.needP2p)
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
            needP2p = j.optBoolean("needP2p"),
            // 旧配置没有这个字段 → optBoolean 默认 true（激进）
            aggressiveP2p = j.optBoolean("aggressiveP2p", true),
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

    /** Build TOML string from [ConfigState] for the EasyTier daemon. */
    fun buildToml(s: ConfigState): String {
        val sb = StringBuilder()

        val networkName = escapeToml(s.networkName.ifEmpty { TOML_FIELD_EMPTY })
        val networkSecret = escapeToml(s.secret.ifEmpty { TOML_FIELD_EMPTY })
        val server = s.server.ifEmpty { TOML_FIELD_EMPTY }
        val port = s.port.ifEmpty { TOML_FIELD_EMPTY }
        val ipAddr = s.ipAddress.ifEmpty { TOML_FIELD_EMPTY }
        val ipPrefix = s.ipPrefix.ifEmpty { "24" }
        val peerUri = if (server != TOML_FIELD_EMPTY && port != TOML_FIELD_EMPTY)
            "tcp://$server:$port"
        else
            "tcp://$TOML_FIELD_EMPTY"
        val instanceName = escapeToml(s.instanceName.ifEmpty { "default" })
        val hostname = escapeToml(s.hostname.ifEmpty { TOML_FIELD_EMPTY })

        sb.append("instance_name = \"$instanceName\"\n")
        if (hostname.isNotEmpty()) sb.append("hostname = \"$hostname\"\n")
        sb.append("ipv4 = \"$ipAddr/$ipPrefix\"\n")
        sb.append("dhcp = ${s.dhcp}\n\n")

        sb.append("[network_identity]\n")
        sb.append("network_name = \"$networkName\"\n")
        sb.append("network_secret = \"$networkSecret\"\n\n")

        if (s.nodes.isNotEmpty()) {
            // Structured node list (new UI)
            for (n in s.nodes) {
                val addr = n.address.trim()
                val p = n.port.trim()
                if (addr.isEmpty() || p.isEmpty()) continue
                val uri = "${n.protocol.ifEmpty { "tcp" }}://${formatHost(addr)}:$p"
                sb.append("[[peer]]\n")
                sb.append("uri = \"${escapeToml(uri)}\"\n\n")
            }
        } else {
            sb.append("[[peer]]\n")
            sb.append("uri = \"$peerUri\"\n\n")

            // Additional peers (multiple servers)
            for (extra in s.extraServers) {
                val uri = extra.trim()
                if (uri.isEmpty()) continue
                sb.append("[[peer]]\n")
                sb.append("uri = \"${escapeToml(uri)}\"\n\n")
            }
        }

        if (s.disableIpv6) sb.append("disable_ipv6 = true\n")
        if (s.noTun) sb.append("no_tun = true\n")
        if (s.bindDevice.isNotEmpty()) sb.append("bind_device = \"${escapeToml(s.bindDevice)}\"\n")

        if (s.disableP2p) sb.append("disable_p2p = true\n")
        if (s.p2pOnly) sb.append("p2p_only = true\n")

        // 激进 P2P：强制 need_p2p = true、无 lazy_p2p、且禁止任何 disable_*_hole_punching
        val aggressiveP2p = s.aggressiveP2p
        if (aggressiveP2p) {
            sb.append("need_p2p = true\n")
        } else {
            if (s.lazyP2p) sb.append("lazy_p2p = true\n")
            if (s.needP2p) sb.append("need_p2p = true\n")
        }

        if (s.disableTcpHp && !aggressiveP2p) sb.append("disable_tcp_hole_punching = true\n")
        if (s.disableUdpHp && !aggressiveP2p) sb.append("disable_udp_hole_punching = true\n")
        if (s.disableSymHp && !aggressiveP2p) sb.append("disable_sym_hole_punching = true\n")

        if (s.enableKcpProxy) sb.append("enable_kcp_proxy = true\n")
        if (s.disableKcpInput) sb.append("disable_kcp_input = true\n")
        if (s.enableQuicProxy) sb.append("enable_quic_proxy = true\n")
        if (s.disableQuicInput) sb.append("disable_quic_input = true\n")
        if (s.proxyForwardBySystem) sb.append("proxy_forward_by_system = true\n")
        if (s.relayAllRpc) sb.append("relay_all_peer_rpc = true\n")
        if (s.exitNode) sb.append("enable_exit_node = true\n")

        if (s.latencyFirst) sb.append("latency_first = true\n")
        if (s.multiThread) sb.append("multi_thread = true\n")
        if (s.useSmoltcp) sb.append("use_smoltcp = true\n")
        if (s.mtu.isNotEmpty()) sb.append("mtu = ${s.mtu}\n")

        if (s.disableEncrypt) sb.append("disable_encryption = true\n")
        if (s.privateMode) sb.append("private_mode = true\n")
        if (s.acceptDns) sb.append("accept_dns = true\n")

        if (s.socks5.isNotEmpty()) sb.append("socks5 = \"${escapeToml(s.socks5)}\"\n")

        if (s.portForward.isNotEmpty()) {
            val parts = s.portForward.split("://")
            if (parts.size == 2) {
                val slashIdx = parts[1].lastIndexOf('/')
                if (slashIdx > 0) {
                    val localPart = parts[1].substring(0, slashIdx)
                    val remotePart = parts[1].substring(slashIdx + 1)
                    sb.append("[[port_forward]]\n")
                    sb.append("port_forward_protocol = \"${escapeToml(parts[0])}\"\n")
                    sb.append("port_forward_local = \"${escapeToml(localPart)}\"\n")
                    sb.append("port_forward_remote = \"${escapeToml(remotePart)}\"\n")
                }
            }
        }

        if (s.encryptAlgo.isNotEmpty())
            sb.append("encryption_algorithm = \"${escapeToml(s.encryptAlgo)}\"\n")
        if (s.compression.isNotEmpty() && s.compression != "none")
            sb.append("compression = \"${escapeToml(s.compression)}\"\n")

        if (s.proxyNetworks.isNotEmpty()) {
            s.proxyNetworks.split(",").forEach { cidr ->
                val c = cidr.trim()
                if (c.isNotEmpty())
                    sb.append("[[proxy_networks]]\nproxy_network_cidr = \"${escapeToml(c)}\"\n")
            }
        }

        if (s.exitNodes.isNotEmpty()) {
            s.exitNodes.split(",").forEach { vip ->
                val v = vip.trim()
                if (v.isNotEmpty()) sb.append("[[exit_nodes]]\nnode = \"${escapeToml(v)}\"\n")
            }
        }

        // ── 省电模式（EasyTier 层）──
        // EasyTier 2.6.4 没有可配置的心跳 / peer 轮询间隔（`easytier-core --help` 与
        // 2.6.4 的 config.rs 里都没有 interval 类字段），能用且安全的是关掉 UPnP：
        // 否则核心会周期性做 SSDP 组播和 WANIPConnection 端口映射探测，每次都会
        // 唤醒无线电。stun_servers / lazy_p2p 会削弱 NAT 探测与打洞，这里刻意不动。
        // 注意 flags 是嵌套表：2.6.4 的 Config 用 `flags: Option<Map>` 接收，
        // 必须写在 [flags] 段里，顶层的裸 flag 会被 serde 静默忽略。
        if (s.powerSaving) {
            sb.append("\n[flags]\n")
            sb.append("disable_upnp = true\n")
        }

        return sb.toString()
    }

    // ═══════════════════════════════════════════════
    //  TOML → JSON (import)
    // ═══════════════════════════════════════════════

    /** Parse raw TOML text into a JSONObject with the same keys as [configStateToJson]. */
    fun parseTomlToJson(text: String): JSONObject {
        val j = JSONObject()
        var currentSection = ""

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("[")) {
                currentSection = trimmed.removeSurrounding("[", "]")
                continue
            }

            val eqIdx = trimmed.indexOf('=')
            if (eqIdx < 0) continue
            val key = trimmed.substring(0, eqIdx).trim()
            val rawValue = trimmed.substring(eqIdx + 1).trim()
            val value = rawValue.removeSurrounding("\"").trim()

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
                "uri" -> {
                    if (!j.has("server")) {
                        val parts = value.split("://")
                        if (parts.size == 2) {
                            val hostPort = parts[1].split(":")
                            if (hostPort.size == 2) {
                                j.put("server", hostPort[0])
                                j.put("port", hostPort[1])
                            } else { j.put("server", parts[1]) }
                        }
                    }
                }
                "disable_ipv6" -> j.put("disableIpv6", parseBool(rawValue))
                "no_tun" -> j.put("noTun", parseBool(rawValue))
                "bind_device" -> j.put("bindDevice", value)
                "disable_p2p" -> j.put("disableP2p", parseBool(rawValue))
                "p2p_only" -> j.put("p2pOnly", parseBool(rawValue))
                "lazy_p2p" -> j.put("lazyP2p", parseBool(rawValue))
                "need_p2p" -> j.put("needP2p", parseBool(rawValue))
                "disable_tcp_hole_punching" -> j.put("disableTcpHp", parseBool(rawValue))
                "disable_udp_hole_punching" -> j.put("disableUdpHp", parseBool(rawValue))
                "disable_sym_hole_punching" -> j.put("disableSymHp", parseBool(rawValue))
                "enable_kcp_proxy" -> j.put("enableKcpProxy", parseBool(rawValue))
                "disable_kcp_input" -> j.put("disableKcpInput", parseBool(rawValue))
                "enable_quic_proxy" -> j.put("enableQuicProxy", parseBool(rawValue))
                "disable_quic_input" -> j.put("disableQuicInput", parseBool(rawValue))
                "proxy_forward_by_system" -> j.put("proxyForwardBySystem", parseBool(rawValue))
                "relay_all_peer_rpc" -> j.put("relayAllRpc", parseBool(rawValue))
                "enable_exit_node" -> j.put("exitNode", parseBool(rawValue))
                "latency_first" -> j.put("latencyFirst", parseBool(rawValue))
                "multi_thread" -> j.put("multiThread", parseBool(rawValue))
                "use_smoltcp" -> j.put("useSmoltcp", parseBool(rawValue))
                "mtu" -> j.put("mtu", value)
                "disable_encryption" -> j.put("disableEncrypt", parseBool(rawValue))
                "private_mode" -> j.put("privateMode", parseBool(rawValue))
                "accept_dns" -> j.put("acceptDns", parseBool(rawValue))
                "socks5" -> j.put("socks5", value)
                "encryption_algorithm" -> j.put("encryptAlgo", value)
                "compression" -> j.put("compression", value)
                "proxy_network_cidr" -> {
                    val existing = j.optString("proxyNetworks")
                    if (existing.isEmpty()) j.put("proxyNetworks", value)
                    else j.put("proxyNetworks", "$existing,$value")
                }
                "node" -> {
                    val existing = j.optString("exitNodes")
                    if (existing.isEmpty()) j.put("exitNodes", value)
                    else j.put("exitNodes", "$existing,$value")
                }
                "port_forward_protocol" -> j.put("_pfProto", value)
                "port_forward_local" -> j.put("_pfLocal", value)
                "port_forward_remote" -> {
                    val proto = j.optString("_pfProto", "udp")
                    val local = j.optString("_pfLocal", "")
                    j.put("portForward", "$proto://$local/$value")
                    j.remove("_pfLocal")
                    j.remove("_pfProto")
                }
            }
        }
        return j
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

    /** Boolean-ish config keys (normalized) → ConfigState slot name. */
    private val BOOL_KEYS = mapOf(
        "dhcp" to "dhcp",
        "no_tun" to "noTun",
        "notun" to "noTun",
        "disable_ipv6" to "disableIpv6",
        "disable_p2p" to "disableP2p",
        "p2p_only" to "p2pOnly",
        "lazy_p2p" to "lazyP2p",
        "need_p2p" to "needP2p",
        "aggressive_p2p" to "aggressiveP2p",
        "disable_tcp_hole_punching" to "disableTcpHp",
        "disable_udp_hole_punching" to "disableUdpHp",
        "disable_sym_hole_punching" to "disableSymHp",
        "enable_kcp_proxy" to "enableKcpProxy",
        "disable_kcp_input" to "disableKcpInput",
        "enable_quic_proxy" to "enableQuicProxy",
        "disable_quic_input" to "disableQuicInput",
        "proxy_forward_by_system" to "proxyForwardBySystem",
        "relay_all_peer_rpc" to "relayAllRpc",
        "enable_exit_node" to "exitNode",
        "latency_first" to "latencyFirst",
        "multi_thread" to "multiThread",
        "use_smoltcp" to "useSmoltcp",
        "disable_encryption" to "disableEncrypt",
        "private_mode" to "privateMode",
        "accept_dns" to "acceptDns"
    )

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

        // Peers first: every URI in the whole text counts, structure ignored.
        for (m in URI_REGEX.findAll(text)) {
            val node = nodeFromUri(m.value, warnings) ?: continue
            addNode(nodes, node)
        }

        for (rawLine in text.lines()) {
            val line = stripInlineComment(rawLine).trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("[") && line.endsWith("]")) continue   // [section] / [[peer]]
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
                    "ip_prefix", "ipv4_prefix", "prefix", "netmask", "cidr", "subnet" -> {
                        cfg = cfg.copy(ipPrefix = value.trimStart('/')); consumed = true
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
                    "bind_device" -> {
                        cfg = cfg.copy(bindDevice = value); consumed = true
                    }
                    "socks5", "socks5_proxy" -> {
                        cfg = cfg.copy(socks5 = value); consumed = true
                    }
                    "encryption_algorithm", "encrypt_algo", "encrypt", "cipher" -> {
                        cfg = cfg.copy(encryptAlgo = value); consumed = true
                    }
                    "compression", "compress" -> {
                        cfg = cfg.copy(compression = value); consumed = true
                    }
                    "dns", "dns_server", "dns_servers", "dns_server_list" -> {
                        cfg = cfg.copy(dnsServer = value); consumed = true
                    }
                    "proxy_network", "proxy_networks", "proxy_network_cidr" -> {
                        cfg = cfg.copy(proxyNetworks = appendCsv(cfg.proxyNetworks, value)); consumed = true
                    }
                    "exit_nodes", "exit_node", "exit_node_vip" -> {
                        cfg = cfg.copy(exitNodes = appendCsv(cfg.exitNodes, value)); consumed = true
                    }
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

    /** true/false/1/0/yes/no/on/off/是/否 … null when unparseable. */
    private fun lenientBool(v: String): Boolean? =
        when (v.trim().lowercase()) {
            "true", "1", "yes", "on", "enable", "enabled", "是", "开" -> true
            "false", "0", "no", "off", "disable", "disabled", "否", "关" -> false
            else -> null
        }

    private fun setBool(c: ConfigState, slot: String, b: Boolean): ConfigState = when (slot) {
        "dhcp" -> c.copy(dhcp = b)
        "noTun" -> c.copy(noTun = b)
        "disableIpv6" -> c.copy(disableIpv6 = b)
        "disableP2p" -> c.copy(disableP2p = b)
        "p2pOnly" -> c.copy(p2pOnly = b)
        "lazyP2p" -> c.copy(lazyP2p = b)
        "needP2p" -> c.copy(needP2p = b)
        "aggressiveP2p" -> c.copy(aggressiveP2p = b)
        "disableTcpHp" -> c.copy(disableTcpHp = b)
        "disableUdpHp" -> c.copy(disableUdpHp = b)
        "disableSymHp" -> c.copy(disableSymHp = b)
        "enableKcpProxy" -> c.copy(enableKcpProxy = b)
        "disableKcpInput" -> c.copy(disableKcpInput = b)
        "enableQuicProxy" -> c.copy(enableQuicProxy = b)
        "disableQuicInput" -> c.copy(disableQuicInput = b)
        "proxyForwardBySystem" -> c.copy(proxyForwardBySystem = b)
        "relayAllRpc" -> c.copy(relayAllRpc = b)
        "exitNode" -> c.copy(exitNode = b)
        "latencyFirst" -> c.copy(latencyFirst = b)
        "multiThread" -> c.copy(multiThread = b)
        "useSmoltcp" -> c.copy(useSmoltcp = b)
        "disableEncrypt" -> c.copy(disableEncrypt = b)
        "privateMode" -> c.copy(privateMode = b)
        "acceptDns" -> c.copy(acceptDns = b)
        else -> c
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
        if (s.needP2p) out.add("需要 P2P")
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
