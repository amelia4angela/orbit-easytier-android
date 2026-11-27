package com.easytier.ui

/**
 * A single server node (peer) entry edited in the config form.
 * [protocol] is "tcp" or "udp"; [address] keeps IPv6 brackets as typed.
 */
data class PeerNode(
    val protocol: String = "tcp",   // tcp | udp
    val address: String = "",
    val port: String = ""
)

/**
 * UI state for all config form fields.
 * Mirrors the fields in MainActivity's existing XML layout.
 * Used by Compose to read/write values without touching Android Views.
 */
data class ConfigState(
    // Primary fields
    val networkName: String = "",
    val secret: String = "",
    val server: String = "",
    val port: String = "",
    val ipAddress: String = "",
    val ipPrefix: String = "",

    // Instance identity
    val instanceName: String = "",
    val hostname: String = "",

    // Network
    val dhcp: Boolean = false,
    val disableIpv6: Boolean = false,
    val noTun: Boolean = false,

    // P2P
    val disableP2p: Boolean = false,
    val p2pOnly: Boolean = false,
    val lazyP2p: Boolean = false,
    /**
     * 激进 P2P（默认开启）：生成 `[flags] need_p2p = true`，且不输出
     * `lazy_p2p` / `disable_*_hole_punching` —— 即立即并发打洞，
     * 宁可直连失败也不默默走中继。
     *
     * 原来的独立「需要 P2P」开关已合并到这里（两者映射的都是 need_p2p）。
     */
    val aggressiveP2p: Boolean = true,

    // NAT / Hole Punch
    val disableTcpHp: Boolean = false,
    val disableUdpHp: Boolean = false,
    val disableSymHp: Boolean = false,

    // Proxy / KCP / QUIC
    val enableKcpProxy: Boolean = false,
    val disableKcpInput: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val disableQuicInput: Boolean = false,
    val proxyForwardBySystem: Boolean = false,
    val relayAllRpc: Boolean = false,
    val exitNode: Boolean = false,

    // Performance
    val latencyFirst: Boolean = false,
    val multiThread: Boolean = false,
    val useSmoltcp: Boolean = false,

    // Security
    val disableEncrypt: Boolean = false,
    val privateMode: Boolean = false,
    val acceptDns: Boolean = false,

    // Extra text fields
    val bindDevice: String = "",
    val mtu: String = "",
    val socks5: String = "",
    val portForward: String = "",
    val encryptAlgo: String = "",
    val compression: String = "",
    val proxyNetworks: String = "",
    val exitNodes: String = "",
    // Custom DNS server pushed to the TUN interface (empty = system DNS).
    // Pre-filled with the usual EasyTier gateway; stored configs keep their own value.
    val dnsServer: String = "10.144.144.1",
    val dnsEnabled: Boolean = false,
    // Additional peer URIs (e.g. tcp://host:port), beyond the main server+port
    val extraServers: List<String> = emptyList(),
    // Structured server node list (primary UI). Legacy server/port/extraServers
    // above are kept in sync for older code paths.
    val nodes: List<PeerNode> = emptyList(),

    // ── 运行时调节（随配置持久化，MainActivity 会镜像到 SharedPreferences）──
    /** 省电模式：后台/熄屏时降低状态轮询与通知刷新频率，并给 EasyTier 注入省电 flags。 */
    val powerSaving: Boolean = false,
    /** 内存限制档位："standard" | "strict" | "off"（见 RuntimeTuning）。 */
    val memoryLimit: String = "standard"
)
