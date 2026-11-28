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
 * What the launch card shows on its network row.
 *
 * Every field is nullable on purpose: `null` means "unknown right now", and the UI renders `--`.
 * Nothing here is cached across a disconnect — see [rememberProxyNetworkInfo].
 */
data class ProxyNetworkInfo(
    val countryCode: String? = null,
    val ip: String? = null,
    val networkType: NetworkType = NetworkType.NONE
)

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
     * Country-code sources (`%s` = the IP we just resolved). Both are HTTPS-only — the free
     * ip-api.com tier is plain HTTP, which Android 9+ blocks for targetSdk 34.
     */
    private val COUNTRY_ENDPOINTS = listOf(
        "https://ipapi.co/%s/json/",
        "https://ipwho.is/%s"
    )

    /** IP literal guard: hex digits + '.'/':' only, so a captive-portal HTML page is rejected. */
    private val IP_LITERAL = Regex("^[0-9A-Fa-f:.]+$")

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

    private fun fetchIp(): String? {
        val deadline = System.currentTimeMillis() + DEADLINE_MS
        for (endpoint in IP_ENDPOINTS) {
            if (System.currentTimeMillis() >= deadline) break
            val body = httpGet(endpoint) ?: continue
            parseIp(body)?.let { return it }
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
