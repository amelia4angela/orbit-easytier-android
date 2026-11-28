package com.easytier.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Physical transport the app is currently on.
 *
 * [OTHER] exists because a VPN (our own EasyTier TUN, or another app's) can be the active
 * network while no Wi-Fi/Cellular/Ethernet transport can be resolved — labelling that "无网络"
 * would be a lie, so it gets its own neutral label instead.
 */
enum class NetworkType(val labelZh: String, val labelEn: String) {
    WIFI("Wi-Fi", "Wi-Fi"),
    CELLULAR("移动网络", "Cellular"),
    ETHERNET("以太网", "Ethernet"),
    OTHER("其他网络", "Other"),
    NONE("无网络", "No network")
}

/**
 * What the launch card / 出站 IP 面板 shows about the exit node.
 *
 * Every field is nullable on purpose: `null` means "unknown right now", and the UI renders `--`
 * (card) or 未知 / 暂无数据 (sheet). Nothing is invented: a field the source API did not answer
 * stays `null` and the UI says so.
 *
 * [organization] [asn] [ipType] [riskTags] [apiSource] [level] are only filled by the detailed
 * probe ([fetchDetailedNetworkInfo]); the lightweight 60 s card probe leaves them `null`.
 */
data class ProxyNetworkInfo(
    val countryCode: String? = null,
    val ip: String? = null,
    val networkType: NetworkType = NetworkType.NONE,
    /** ISP / 组织名，如 `Chinanet SN`。 */
    val organization: String? = null,
    /** `AS4134` 形式；源只给数字时补 `AS` 前缀。 */
    val asn: String? = null,
    /** 规范化 token：`residential` / `datacenter` / `mobile` / `proxy`；见 [ipTypeLabel]。 */
    val ipType: String? = null,
    /** 命中标记 token：`proxy` / `hosting` / `mobile` / `vpn` / `tor`；见 [riskTagLabel]。 */
    val riskTags: List<String> = emptyList(),
    /** 本次数据来源（如 `ip-api.com`），UI 显示「采用来源」。 */
    val apiSource: String? = null,
    /** IP 质量等级。当前免费源都不提供 → 保持 null，UI 显示「暂无数据」。 */
    val level: String? = null
)

/** [ProxyNetworkInfo.ipType] 的 token → 文案。未知 token 原样返回。 */
fun ipTypeLabel(token: String?, langZh: Boolean): String? = when (token) {
    null -> null
    "residential" -> T("住宅/企业", "Residential", langZh)
    "datacenter" -> T("机房", "Datacenter", langZh)
    "mobile" -> T("移动网络", "Mobile", langZh)
    "proxy" -> T("代理", "Proxy", langZh)
    else -> token
}

/** [ProxyNetworkInfo.riskTags] 的 token → 短标签。未知 token 原样返回。 */
fun riskTagLabel(tag: String, langZh: Boolean): String = when (tag) {
    "proxy" -> T("代理", "Proxy", langZh)
    "hosting" -> T("机房", "Hosting", langZh)
    "mobile" -> T("移动", "Mobile", langZh)
    "vpn" -> T("VPN", "VPN", langZh)
    "tor" -> T("Tor", "Tor", langZh)
    else -> tag
}

/**
 * 隐藏 IP（脱敏显示）。
 *
 * 只改**显示**，[ProxyNetworkInfo] 里的真实 IP 与代理链路完全不受影响。
 * 规则（IPv4 / IPv6 同一套）：前 3 字符 + `...` + 后 2 字符 —— `113.5.6.60` → `113...60`。
 */
object IpMask {

    private const val PREFS_STATE = "easytier_state"

    /** 与 auto_check_update 同一个 prefs 文件。 */
    const val KEY_HIDE_IP = "hide_ip"

    fun isHidden(context: Context): Boolean =
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_IP, false)

    fun setHidden(context: Context, hidden: Boolean) {
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_IP, hidden)
            .apply()
    }

    /** `null` 进 `null` 出；短到无法「首尾各留」时只留首字符。 */
    fun mask(ip: String?): String? {
        val value = ip?.trim().orEmpty()
        if (value.isEmpty()) return ip
        if (value.length <= 6) return value.take(1) + "..."
        return value.take(3) + "..." + value.takeLast(2)
    }
}

/**
 * Real exit-IP probe.
 *
 * No OkHttp in this project (and none is being added): plain [HttpURLConnection] on
 * [Dispatchers.IO]. Every request is capped at 5 s connect + 5 s read, and the endpoint list is
 * abandoned after a 12 s soft deadline, so a hostile network can delay the row but never freeze
 * the UI — the card simply keeps showing `--`.
 */
object ProxyNetworkProbe {

    /** Per-request socket timeout (connect and read). */
    private const val TIMEOUT_MS = 5_000

    /** Stop trying further endpoints after this long, even if none answered. */
    private const val DEADLINE_MS = 12_000L

    /**
     * IP sources, tried in order. The first two are plain text (one IP per body); ipip.net
     * answers JSON with `data.ip`, which [parseIp] also understands.
     */
    private val IP_ENDPOINTS = listOf(
        "https://api.ipify.org",
        "https://myip.ipip.net/json",
        "https://api.ip.sb/ip"
    )

    /**
     * Country-code sources (`%s` = the IP we just resolved). Both are HTTPS-only.
     */
    private val COUNTRY_ENDPOINTS = listOf(
        "https://ipapi.co/%s/json/",
        "https://ipwho.is/%s"
    )

    /**
     * 明细源（IP + 组织 / ASN / 类型 / 风险标记），按「字段丰富度」排序，逐个尝试、首个成功即用。
     *
     * `ip-api.com` 免费版**只有 HTTP**（HTTPS 需付费），所以 Android 9+ 的明文限制靠
     * `res/xml/network_security_config.xml` 里**只对 ip-api.com 域名**开白名单解决 ——
     * 不设 `android:usesCleartextTraffic="true"`，其它域名仍然禁明文。
     */
    private val DETAIL_SOURCES: List<DetailSource> = listOf(
        DetailSource("http://ip-api.com/json/?fields=status,countryCode,isp,org,as,asname,proxy,hosting,mobile,query", ::parseIpApi),
        DetailSource("https://ipapi.co/json/", ::parseIpApiCo),
        DetailSource("https://ipwho.is/", ::parseIpWhoIs)
    )

    /** 明细链整体软截止：三个源都不答也只是让面板继续显示旧值。 */
    private const val DETAIL_DEADLINE_MS = 16_000L

    /** 非强制刷新时，这份缓存能复用多久（面板反复开关不重复打 API）。 */
    private const val DETAIL_CACHE_MS = 20_000L

    /** IP literal guard: hex digits + '.'/':' only, so a captive-portal HTML page is rejected. */
    private val IP_LITERAL = Regex("^[0-9A-Fa-f:.]+$")

    /** `AS4134` 形式校验。 */
    private val ASN_LITERAL = Regex("^AS\\d{1,10}$")

    /** 上一次成功拿到的明细（只在内存里；刷新失败时原样返回，UI 不闪空）。 */
    @Volatile
    private var lastGood: ProxyNetworkInfo? = null

    @Volatile
    private var lastGoodAt: Long = 0L

    suspend fun probe(context: Context): ProxyNetworkInfo = withContext(Dispatchers.IO) {
        val networkType = networkTypeOf(context)
        if (networkType == NetworkType.NONE) {
            return@withContext ProxyNetworkInfo(networkType = NetworkType.NONE)
        }
        val ip = fetchIp()
        // Country only makes sense together with an IP; skip the extra request if we have none.
        val country = if (ip != null) fetchCountryCode(ip) else null
        ProxyNetworkInfo(countryCode = country, ip = ip, networkType = networkType)
    }

    /**
     * 明细探测（面板用）。与 [probe] 共用同一套 [httpGet] / IP 校验，不是第二条请求链路。
     *
     * 降级链：ip-api.com → ipapi.co → ipwho.is → 原有 IP 端点（只拿 IP）。
     * 全部失败时返回[上一次成功值]（网络类型取当前实时值），因此面板不会因为一次刷新失败而清空。
     *
     * @param forceRefresh true = 跳过 20 s 内存缓存（下拉/点刷新）
     */
    suspend fun probeDetailed(context: Context, forceRefresh: Boolean): ProxyNetworkInfo =
        withContext(Dispatchers.IO) {
            val transport = networkTypeOf(context)
            val cached = lastGood
            if (!forceRefresh && cached != null &&
                System.currentTimeMillis() - lastGoodAt < DETAIL_CACHE_MS
            ) {
                return@withContext cached.copy(networkType = transport)
            }
            if (transport == NetworkType.NONE) {
                // 没网就别打请求；有旧值就把旧值挂着（网络类型显示无网络）。
                return@withContext (cached ?: ProxyNetworkInfo()).copy(networkType = NetworkType.NONE)
            }

            val fresh = fetchDetailed()
            if (fresh != null) {
                val result = fresh.copy(networkType = transport)
                lastGood = result
                lastGoodAt = System.currentTimeMillis()
                return@withContext result
            }

            // 明细源全挂 → 最后兜底只拿 IP（原有三个端点）。
            val legacy = fetchIpWithSource()
            if (legacy != null) {
                val (ip, source) = legacy
                // 同一个 IP：保留旧的组织/ASN（它们来自更丰富的源），只更新网络类型。
                val sameIp = cached?.takeIf { it.ip == ip }
                val result = if (sameIp != null) {
                    sameIp.copy(networkType = transport)
                } else {
                    ProxyNetworkInfo(ip = ip, apiSource = source, networkType = transport)
                }
                lastGood = result
                lastGoodAt = System.currentTimeMillis()
                return@withContext result
            }

            (cached ?: ProxyNetworkInfo()).copy(networkType = transport)
        }

    private fun fetchDetailed(): ProxyNetworkInfo? {
        val deadline = System.currentTimeMillis() + DETAIL_DEADLINE_MS
        for (source in DETAIL_SOURCES) {
            if (System.currentTimeMillis() >= deadline) break
            val body = httpGet(source.url) ?: continue
            val parsed = source.parse(body) ?: continue
            if (parsed.ip != null) return parsed
        }
        return null
    }

    private fun fetchIp(): String? = fetchIpWithSource()?.first

    /** IP + 应答它的端点主机名（用于 apiSource）。 */
    private fun fetchIpWithSource(): Pair<String, String>? {
        val deadline = System.currentTimeMillis() + DEADLINE_MS
        for (endpoint in IP_ENDPOINTS) {
            if (System.currentTimeMillis() >= deadline) break
            val body = httpGet(endpoint) ?: continue
            val ip = parseIp(body) ?: continue
            val host = runCatching { URL(endpoint).host }.getOrDefault(endpoint)
            return ip to host
        }
        return null
    }

    private fun fetchCountryCode(ip: String): String? {
        for (template in COUNTRY_ENDPOINTS) {
            val body = httpGet(String.format(template, ip)) ?: continue
            val code = runCatching {
                val json = JSONObject(body)
                json.optString("country_code").ifBlank { json.optString("countryCode") }
            }.getOrNull()
            if (!code.isNullOrBlank() && code.length == 2 && code.all { it.isLetter() }) {
                return code.uppercase()
            }
        }
        return null
    }

    // ── 明细源解析（字段容错：缺字段 = null / 空列表，绝不补默认值）────────────────

    /**
     * ip-api.com（首选，字段最全）。真实应答（2026-11 实测）：
     * `{"status":"success","countryCode":"CN","isp":"Chinanet","org":"Chinanet SN",
     *   "as":"AS4134 CHINANET BACKBONE","mobile":false,"proxy":false,"hosting":false,
     *   "query":"1.81.129.30"}`
     *
     * 免费版**不提供** IP 质量等级（`level` 恒为 null）与风险评分，只有三个布尔标记。
     */
    private fun parseIpApi(body: String): ProxyNetworkInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optString("status") != "success") return null
        val ip = json.optString("query").takeIf { looksLikeIp(it) } ?: return null
        val mobile = json.optBoolean("mobile", false)
        val hosting = json.optBoolean("hosting", false)
        val proxy = json.optBoolean("proxy", false)
        val tags = buildList {
            if (proxy) add("proxy")
            if (hosting) add("hosting")
            if (mobile) add("mobile")
        }
        return ProxyNetworkInfo(
            countryCode = normalizeCountryCode(json.optString("countryCode")),
            ip = ip,
            organization = json.optString("org").ifBlank { json.optString("isp") }.ifBlank { null },
            asn = normalizeAsn(json.optString("as").substringBefore(' ')),
            ipType = when {
                mobile -> "mobile"
                // hosting = 机房/云主机；三个标记都为 false 时按「非机房」归到住宅/企业。
                hosting -> "datacenter"
                proxy -> "proxy"
                else -> "residential"
            },
            riskTags = tags,
            apiSource = "ip-api.com"
        )
    }

    /** ipapi.co（HTTPS 兜底）：`ip` / `country_code` / `org`（"AS15169 Google LLC"）/ `asn` / `type`。 */
    private fun parseIpApiCo(body: String): ProxyNetworkInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optBoolean("error", false)) return null
        val ip = json.optString("ip").takeIf { looksLikeIp(it) } ?: return null
        val org = json.optString("org").ifBlank { null }
        val asn = normalizeAsn(
            json.optString("asn").ifBlank { org?.substringBefore(' ').orEmpty() }
        )
        return ProxyNetworkInfo(
            countryCode = normalizeCountryCode(json.optString("country_code")),
            ip = ip,
            organization = org,
            asn = asn,
            ipType = mapApiTypeToken(json.optString("type")),
            apiSource = "ipapi.co"
        )
    }

    /**
     * ipwho.is（HTTPS 兜底）：`ip` / `country_code` / `connection.{asn,org,isp}`，
     * 部分线路还会给 `security.{proxy,vpn,hosting,tor}`。
     * 注意它的顶层 `type` 是 **IP 版本**（IPv4/IPv6），不是机房/住宅类型，不能当 ipType 用。
     */
    private fun parseIpWhoIs(body: String): ProxyNetworkInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!json.optBoolean("success", true)) return null
        val ip = json.optString("ip").takeIf { looksLikeIp(it) } ?: return null
        val connection = json.optJSONObject("connection")
        val isp = connection?.optString("isp").orEmpty()
        val org = connection?.optString("org").orEmpty()
        val asnNumber = connection?.optInt("asn", 0) ?: 0
        val asn = if (asnNumber > 0) "AS$asnNumber" else normalizeAsn(connection?.optString("asn").orEmpty())
        val security = json.optJSONObject("security")
        val tags = buildList {
            if (security?.optBoolean("proxy", false) == true) add("proxy")
            if (security?.optBoolean("vpn", false) == true) add("vpn")
            if (security?.optBoolean("hosting", false) == true) add("hosting")
            if (security?.optBoolean("tor", false) == true) add("tor")
        }
        return ProxyNetworkInfo(
            countryCode = normalizeCountryCode(json.optString("country_code")),
            ip = ip,
            // org 更具体（信通院/机房名），isp 更偏运营商；优先 org。
            organization = org.ifBlank { isp }.ifBlank { null },
            asn = asn,
            ipType = when {
                security?.optBoolean("hosting", false) == true -> "datacenter"
                security?.optBoolean("proxy", false) == true ||
                    security?.optBoolean("vpn", false) == true ||
                    security?.optBoolean("tor", false) == true -> "proxy"
                else -> null
            },
            riskTags = tags,
            apiSource = "ipwho.is"
        )
    }

    /** ipapi.co 的 `type` 只认已知取值，别的字符串一律忽略（不猜）。 */
    private fun mapApiTypeToken(raw: String): String? = when (raw.trim().lowercase()) {
        "residential" -> "residential"
        "business" -> "residential"
        "hosting" -> "datacenter"
        "datacenter" -> "datacenter"
        "mobile" -> "mobile"
        "proxy" -> "proxy"
        else -> null
    }

    private fun normalizeCountryCode(raw: String): String? {
        val code = raw.trim()
        return code.takeIf { it.length == 2 && it.all { ch -> ch.isLetter() } }?.uppercase()
    }

    private fun normalizeAsn(raw: String): String? {
        val value = raw.trim().uppercase()
        if (value.isEmpty()) return null
        val withPrefix = if (value.startsWith("AS")) value else "AS$value"
        return withPrefix.takeIf { ASN_LITERAL.matches(it) }
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Orbit/Android")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            // Any network/parse/DNS failure is "unknown", never an exception the UI must handle.
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** Plain IP body first, then the JSON shapes ipip.net / ipify-style services return. */
    private fun parseIp(raw: String): String? {
        val body = raw.trim()
        if (body.isEmpty()) return null
        if (looksLikeIp(body)) return body
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        json.optString("ip").takeIf { looksLikeIp(it) }?.let { return it }
        json.optJSONObject("data")?.optString("ip")?.takeIf { looksLikeIp(it) }?.let { return it }
        return null
    }

    private fun looksLikeIp(value: String): Boolean =
        value.length in 3..64 &&
            IP_LITERAL.matches(value) &&
            (value.contains('.') || value.contains(':'))
}

/**
 * Current transport, API-gated:
 *  - [ConnectivityManager.getNetworkCapabilities] (API 21+) is the transport source;
 *  - while EasyTier's TUN is up the *active* network is a VPN, whose capabilities do not name a
 *    physical transport, so we fall back to scanning `allNetworks` for the real uplink;
 *  - `activeNetwork` needs API 23 — minSdk is 23, so there is no lower branch to guard.
 */
@Suppress("DEPRECATION") // allNetworks is deprecated on API 31 but still the only way to look through a VPN.
fun networkTypeOf(context: Context): NetworkType {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkType.NONE
    val active = cm.activeNetwork ?: return NetworkType.NONE
    val activeCaps = cm.getNetworkCapabilities(active)
    if (activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        transportOf(activeCaps)?.let { return it }
    }
    for (network in cm.allNetworks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
        transportOf(caps)?.let { return it }
    }
    return NetworkType.OTHER
}

private fun transportOf(caps: NetworkCapabilities): NetworkType? = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
    else -> null
}

private const val IP_REFRESH_INTERVAL_MS = 60_000L

/** 一个明细端点 + 它自己的解析器（每个源的 JSON 形状都不一样，解析器不外置）。 */
private class DetailSource(
    val url: String,
    val parse: (String) -> ProxyNetworkInfo?
)

/**
 * 出站 IP 面板的数据入口。
 *
 * 与启动卡片的 [rememberProxyNetworkInfo] 共用 [ProxyNetworkProbe] 的 HTTP 与 IP 校验，
 * 只是走「字段丰富」的那条降级链。失败返回上一次成功值（[ProxyNetworkProbe] 内的内存缓存），
 * 所以面板刷新失败不会清空已有数据，也不会伪造任何字段。
 *
 * @param forceRefresh true = 用户点了刷新，跳过 20 s 缓存
 */
suspend fun fetchDetailedNetworkInfo(
    context: Context,
    forceRefresh: Boolean = false
): ProxyNetworkInfo = ProxyNetworkProbe.probeDetailed(context, forceRefresh)

/**
 * Wires [ProxyNetworkProbe] into composition.
 *
 * @param active true while the tunnel is up. On `false` the info is cleared (no leftover exit IP
 *   after a disconnect) and no request is made; on `true` it probes immediately — that is the
 *   "拉一次 after a successful start" — then refreshes every 60 s.
 */
@Composable
fun rememberProxyNetworkInfo(active: Boolean): ProxyNetworkInfo {
    val context = LocalContext.current
    var info by remember {
        mutableStateOf(ProxyNetworkInfo(networkType = networkTypeOf(context)))
    }
    LaunchedEffect(active) {
        if (!active) {
            info = ProxyNetworkInfo(networkType = networkTypeOf(context))
            return@LaunchedEffect
        }
        while (true) {
            info = ProxyNetworkProbe.probe(context)
            delay(IP_REFRESH_INTERVAL_MS)
        }
    }
    return info
}
