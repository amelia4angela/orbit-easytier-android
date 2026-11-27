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
    val needP2p: Boolean = false,
    /**
     * 激进 P2P（默认开启）：强制 need_p2p = true、lazy_p2p 不输出、
     * 且不输出任何 disable_*_hole_punching = true —— 即立即并发打洞，
     * 宁可直连失败也不默默走中继。
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
    // Custom DNS server pushed to the TUN interface (empty = system DNS)
    val dnsServer: String = "",
    val dnsEnabled: Boolean = false,
    // Additional peer URIs (e.g. tcp://host:port), beyond the main server+port
    val extraServers: List<String> = emptyList(),
    // Structured server node list (primary UI). Legacy server/port/extraServers
    // above are kept in sync for older code paths.
    val nodes: List<PeerNode> = emptyList()
)
