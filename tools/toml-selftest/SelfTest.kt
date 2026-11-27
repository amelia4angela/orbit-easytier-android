import com.easytier.TomlUtils
import com.easytier.ui.ConfigState
import com.easytier.ui.PeerNode
import org.tomlj.Toml

/**
 * 自测：直接编译仓库里的 TomlUtils.kt + ConfigState.kt（不复制逻辑），
 * 用 tomlj 解析生成的 TOML，逐项确认字段落到了正确的段。
 */
var failures = 0

fun check(cond: Boolean, msg: String) {
    if (cond) println("ok   $msg") else { failures++; println("FAIL $msg") }
}

/** 官方 2.6.4 Config 顶层字段（easytier/src/common/config.rs）。 */
val OFFICIAL_TOP = setOf(
    "netns", "hostname", "instance_name", "instance_id", "ipv4", "ipv6",
    "ipv6_public_addr_provider", "ipv6_public_addr_auto", "ipv6_public_addr_prefix",
    "dhcp", "network_identity", "listeners", "mapped_listeners", "exit_nodes",
    "peer", "proxy_network", "vpn_portal_config", "routes", "socks5_proxy",
    "port_forward", "secure_mode", "flags", "acl", "tcp_whitelist", "udp_whitelist",
    "stun_servers", "stun_servers_v6", "credential_file", "source"
)

fun fullConfig() = ConfigState(
    networkName = "nas-net",
    secret = "s3cret",
    ipAddress = "10.144.144.5",
    ipPrefix = "24",
    instanceName = "orbit",
    hostname = "iqoo",
    dhcp = false,
    disableIpv6 = true,
    noTun = true,
    bindDevice = TomlUtils.BIND_DEVICE_ON,
    p2pOnly = true,
    lazyP2p = true,               // 激进 P2P 打开时不该输出
    aggressiveP2p = true,
    disableTcpHp = true,          // 同上
    disableUdpHp = true,
    disableSymHp = true,
    enableKcpProxy = true,
    disableKcpInput = true,
    enableQuicProxy = true,
    disableQuicInput = true,
    proxyForwardBySystem = true,
    relayAllRpc = true,
    exitNode = true,
    latencyFirst = true,
    multiThread = true,
    useSmoltcp = true,
    disableEncrypt = true,
    privateMode = true,
    acceptDns = true,
    mtu = "1300",
    socks5 = "1080",
    portForward = "udp://0.0.0.0:12345/10.126.126.1:23456",
    encryptAlgo = "aes-256-gcm",
    compression = "zstd",
    proxyNetworks = "192.168.1.0/24, 10.0.0.0/8->172.16.0.0/16",
    exitNodes = "10.144.144.6",
    nodes = listOf(
        PeerNode("tcp", "2001:db8::1", "11010"),
        PeerNode("udp", "peer.example.com", "11010")
    ),
    powerSaving = true
)

fun main() {
    // ── 1) 全开配置 ──
    val text = TomlUtils.buildToml(fullConfig())
    println("══════ generated toml ══════")
    println(text)
    println("════════════════════════════")

    val toml = Toml.parse(text)
    check(!toml.hasErrors(), "TOML 语法合法 ${toml.errors()}")
    if (toml.hasErrors()) { println("ABORT"); return }

    val top = toml.keySet().sorted()
    check(OFFICIAL_TOP.containsAll(top), "顶层只有官方 Config 字段: $top")

    val flags = toml.getTable("flags")
    check(flags != null, "存在 [flags] 段")

    if (flags != null) {
        fun b(k: String) = flags.getBoolean(k)
        fun s(k: String) = flags.get(k)
        check(b("enable_ipv6") == false, "disableIpv6 → flags.enable_ipv6 = false")
        check(b("enable_encryption") == false, "disableEncrypt → flags.enable_encryption = false")
        check(b("bind_device") == true, "bindDevice → flags.bind_device = true (布尔)")
        check(b("need_p2p") == true, "aggressiveP2p → flags.need_p2p = true")
        check(!flags.contains("lazy_p2p"), "激进 P2P 时不输出 lazy_p2p")
        check(!flags.contains("disable_tcp_hole_punching"), "激进 P2P 时不输出 disable_tcp_*")
        check(b("no_tun") == true, "noTun → flags.no_tun")
        check(b("p2p_only") == true, "p2pOnly → flags.p2p_only")
        check(b("enable_kcp_proxy") == true, "enableKcpProxy → flags.enable_kcp_proxy")
        check(b("disable_kcp_input") == true, "disableKcpInput → flags.disable_kcp_input")
        check(b("enable_quic_proxy") == true, "enableQuicProxy → flags.enable_quic_proxy")
        check(b("disable_quic_input") == true, "disableQuicInput → flags.disable_quic_input")
        check(b("proxy_forward_by_system") == true, "proxyForwardBySystem → flags.proxy_forward_by_system")
        check(b("relay_all_peer_rpc") == true, "relayAllRpc → flags.relay_all_peer_rpc")
        check(b("enable_exit_node") == true, "exitNode → flags.enable_exit_node")
        check(b("latency_first") == true, "latencyFirst → flags.latency_first")
        check(b("multi_thread") == true, "multiThread → flags.multi_thread")
        check(b("use_smoltcp") == true, "useSmoltcp → flags.use_smoltcp")
        check(b("private_mode") == true, "privateMode → flags.private_mode")
        check(b("accept_dns") == true, "acceptDns → flags.accept_dns")
        check(b("disable_upnp") == true, "powerSaving → flags.disable_upnp")
        check(flags.getLong("mtu") == 1300L, "mtu 是整数 1300")
        check(s("encryption_algorithm") == "aes-256-gcm", "encryptAlgo → flags.encryption_algorithm")
        check(flags.getLong("data_compress_algo") == 2L, "compression=zstd → data_compress_algo = 2 (整数)")

        // 核心会 panic 的类型错误：所有 flags 值只能是 bool / long / string
        for (k in flags.keySet()) {
            val v = flags.get(k)
            check(v is Boolean || v is Long || v is String, "flags.$k 类型合法 (${v?.javaClass?.simpleName})")
        }
    }

    // 顶层不能有裸 flag
    val bareFlag = top.filter { it in flags?.keySet().orEmpty() }
    check(bareFlag.isEmpty(), "顶层没有裸 flag: $bareFlag")

    // 其它段
    check(toml.getString("instance_name") == "orbit", "instance_name")
    check(toml.getString("hostname") == "iqoo", "hostname")
    check(toml.getString("ipv4") == "10.144.144.5/24", "ipv4 带前缀")
    check(toml.getString("socks5_proxy") == "socks5://0.0.0.0:1080", "socks5 端口 → socks5_proxy URL")
    check(toml.getArray("exit_nodes")?.toList() == listOf("10.144.144.6"), "exitNodes → 顶层 exit_nodes 数组")

    val ident = toml.getTable("network_identity")
    check(ident?.getString("network_name") == "nas-net", "[network_identity] network_name")
    check(ident?.getString("network_secret") == "s3cret", "[network_identity] network_secret")

    val peers = toml.getArray("peer")
    check(peers?.size() == 2, "[[peer]] 两个节点")
    check(toml.getArray("peer")?.getTable(0)?.getString("uri") == "tcp://[2001:db8::1]:11010",
        "IPv6 peer 自动加方括号")
    check(toml.getArray("peer")?.getTable(1)?.getString("uri") == "udp://peer.example.com:11010",
        "第二个 peer 协议/端口正确")

    val proxies = toml.getArray("proxy_network")
    check(proxies?.size() == 2, "[[proxy_network]] 两条")
    check(proxies?.getTable(0)?.getString("cidr") == "192.168.1.0/24", "proxy_network.cidr")
    check(proxies?.getTable(1)?.getString("mapped_cidr") == "172.16.0.0/16", "proxy_network.mapped_cidr (a->b)")

    val pf = toml.getArray("port_forward")?.getTable(0)
    check(pf?.getString("bind_addr") == "0.0.0.0:12345", "port_forward.bind_addr")
    check(pf?.getString("dst_addr") == "10.126.126.1:23456", "port_forward.dst_addr")
    check(pf?.getString("proto") == "udp", "port_forward.proto")

    // ── 2) 回读（官方格式 → ConfigState）──
    val back = TomlUtils.jsonToConfigState(TomlUtils.parseTomlToJson(text))
    check(back.disableIpv6, "回读 enable_ipv6=false → disableIpv6")
    check(back.disableEncrypt, "回读 enable_encryption=false → disableEncrypt")
    check(back.noTun, "回读 no_tun")
    check(back.bindDevice == TomlUtils.BIND_DEVICE_ON, "回读 bind_device=true → 开启")
    check(back.aggressiveP2p, "回读 need_p2p → aggressiveP2p")
    check(back.mtu == "1300", "回读 mtu")
    check(back.compression == "zstd", "回读 data_compress_algo=2 → zstd")
    check(back.socks5 == "1080", "回读 socks5_proxy → 端口")
    check(back.portForward == "udp://0.0.0.0:12345/10.126.126.1:23456", "回读 [[port_forward]] 三字段 → CLI 串")
    check(back.proxyNetworks == "192.168.1.0/24,10.0.0.0/8->172.16.0.0/16", "回读 [[proxy_network]]")
    check(back.exitNodes == "10.144.144.6", "回读 exit_nodes 数组")
    check(back.nodes.size == 2 && back.nodes[0].address == "[2001:db8::1]", "回读 peer 节点(${back.nodes})")

    // ── 3) 空服务器：不能生成 tcp:// 这种非法 URI ──
    val minimalText = TomlUtils.buildToml(
        ConfigState(networkName = "n", secret = "s", ipAddress = "10.144.144.5")
    )
    val minimal = Toml.parse(minimalText)
    check(!minimal.hasErrors(), "最小配置 TOML 合法")
    check(minimal.getArray("peer") == null, "无服务器时不生成 [[peer]]")
    check(!minimalText.contains("tcp://\""), "无空 uri")
    check(!minimalText.contains("ipv4 = \"/24\""), "无非法 ipv4 \"/24\"")

    // ── 4) 旧格式（顶层裸 flag）仍能读 ──
    val legacy = """
        instance_name = "old"
        ipv4 = "10.144.144.5/24"
        disable_ipv6 = true
        no_tun = true
        need_p2p = true
        mtu = "1380"
        socks5 = "1080"

        [network_identity]
        network_name = "nas-net"
        network_secret = "s"

        [[peer]]
        uri = "tcp://1.2.3.4:11010"
    """.trimIndent()
    val old = TomlUtils.parseLenient(legacy)
    check(old.state.disableIpv6, "旧格式 disable_ipv6")
    check(old.state.noTun, "旧格式 no_tun")
    check(old.state.aggressiveP2p, "旧格式 need_p2p → aggressiveP2p")
    check(old.state.mtu == "1380", "旧格式 mtu")
    check(old.state.socks5 == "1080", "旧格式 socks5")
    check(old.state.nodes.size == 1, "旧格式 peer")

    // ── 5) 「需要 P2P」合并后 need_p2p 只出现一次 ──
    check(text.lines().count { it.trim().startsWith("need_p2p") } == 1, "need_p2p 只输出一次")

    // ── 6) 非激进 + 打洞开关：进 flags 且不被抑制 ──
    val relaxed = TomlUtils.buildToml(
        ConfigState(networkName = "n", secret = "s", aggressiveP2p = false,
            lazyP2p = true, disableTcpHp = true, disableUdpHp = true, disableSymHp = true)
    )
    val rt = Toml.parse(relaxed)
    val rf = rt.getTable("flags")
    check(rf?.getBoolean("lazy_p2p") == true, "非激进 → flags.lazy_p2p")
    check(rf?.getBoolean("disable_tcp_hole_punching") == true, "非激进 → flags.disable_tcp_hole_punching")
    check(rf?.getBoolean("disable_udp_hole_punching") == true, "非激进 → flags.disable_udp_hole_punching")
    check(rf?.getBoolean("disable_sym_hole_punching") == true, "非激进 → flags.disable_sym_hole_punching")
    check(rf?.contains("need_p2p") == false, "非激进 → 不输出 need_p2p")

    // ── 7) 官方格式经宽松导入（主导入路径 parseLenient）也要保住开关 ──
    val ls = TomlUtils.parseLenient(text).state
    check(ls.disableIpv6, "parseLenient: enable_ipv6=false → disableIpv6")
    check(ls.disableEncrypt, "parseLenient: enable_encryption=false → disableEncrypt")
    check(ls.noTun, "parseLenient: no_tun")
    check(ls.bindDevice == TomlUtils.BIND_DEVICE_ON, "parseLenient: bind_device")
    check(ls.aggressiveP2p, "parseLenient: need_p2p → aggressiveP2p")
    check(ls.p2pOnly, "parseLenient: p2p_only")
    check(ls.enableKcpProxy && ls.disableKcpInput && ls.enableQuicProxy && ls.disableQuicInput,
        "parseLenient: kcp/quic")
    check(ls.proxyForwardBySystem && ls.relayAllRpc && ls.exitNode, "parseLenient: proxy/rpc/exit")
    check(ls.latencyFirst && ls.multiThread && ls.useSmoltcp, "parseLenient: perf")
    check(ls.privateMode && ls.acceptDns, "parseLenient: security")
    check(ls.mtu == "1300", "parseLenient: mtu")
    check(ls.compression == "zstd", "parseLenient: data_compress_algo → zstd")
    check(ls.socks5 == "1080", "parseLenient: socks5_proxy → 端口")
    check(ls.portForward == "udp://0.0.0.0:12345/10.126.126.1:23456", "parseLenient: port_forward")
    check(ls.proxyNetworks == "192.168.1.0/24,10.0.0.0/8->172.16.0.0/16", "parseLenient: proxy_network (${ls.proxyNetworks})")
    check(ls.exitNodes == "10.144.144.6", "parseLenient: exit_nodes (${ls.exitNodes})")
    check(ls.powerSaving, "parseLenient: disable_upnp → powerSaving")
    check(ls.nodes.size == 2, "parseLenient: peers")

    println()
    if (failures == 0) println("ALL CHECKS PASSED") else println("FAILURES: $failures")
}
