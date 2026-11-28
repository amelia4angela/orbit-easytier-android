package com.easytier.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 主界面背景模式（持久化在 `easytier_theme` → `bg_mode`，与深浅色同一个 prefs 文件）。
 *
 * - [GRADIENT] 主题渐变 + 两团柔光（改造前的默认背景，离线可用）
 * - [ONLINE]   在线随机二次元图片 + 主题暗化/亮化蒙版（失败自动回退渐变）
 * - [OFF]      纯色背景（最省电，无渐变无图片）
 */
enum class BgMode(val key: String) {
    GRADIENT("gradient"),
    ONLINE("online"),
    OFF("off");

    companion object {
        fun fromKey(key: String?): BgMode = entries.firstOrNull { it.key == key } ?: GRADIENT
    }
}

/**
 * 屏幕当前真正画出来的底色种类（MainScreen 提供）。
 *
 * 二级设置页据此决定自己的底色：渐变/图片背景时页面留一层整体底色（渐变时必须完全不透明，
 * 否则进出转场两张页面会互相透出来；图片背景时半透明让壁纸透出），纯色模式下跟着用纯色。
 */
enum class BgSurface {
    GRADIENT,
    IMAGE,
    FLAT
}

val LocalBgSurface = staticCompositionLocalOf { BgSurface.GRADIENT }

private const val PREFS = "easytier_theme"
private const val KEY_BG_MODE = "bg_mode"
private const val CACHE_DIR = "bg"

/** 磁盘缓存最多保留几张（按 lastModified LRU 淘汰最旧的）。 */
private const val MAX_CACHE_FILES = 5

/**
 * 单张图的下载上限，防止端点返回异常大的文件把 2GB 机器撑爆。
 * 实测 loliapi 的直出 webp 单张约 10.7MB（4096px 宽），所以上限留 12MB；
 * 超过就直接跳过这个端点（降级链继续往下走）。
 */
private const val MAX_BYTES = 12L * 1024 * 1024

private const val TIMEOUT_MS = 8_000
private const val MAX_REDIRECTS = 4

/** 解码目标宽度上限：超过 1080px 宽的图在手机屏上没有意义，只会吃内存。 */
private const val MAX_TARGET_WIDTH_PX = 1080
private const val MIN_TARGET_WIDTH_PX = 720

/**
 * 直出图片的两个端点（国内可访问优先；都会 302 到真正的图片地址）。
 * 第三个端点是 JSON API，兜底用。
 */
private val DIRECT_ENDPOINTS = listOf(
    "https://t.alcy.cc/pc",
    "https://www.loliapi.com/acg/"
)
private const val JSON_ENDPOINT = "https://api.waifu.pics/sfw/waifu"

/**
 * 在线背景图的下载 / 解码 / 磁盘缓存。
 *
 * 线程约定：所有方法都是阻塞 IO（HttpURLConnection + BitmapFactory + 文件读写），
 * **必须在 Dispatchers.IO 上调用**，本对象不加锁也不切线程，方便调用方整体放进
 * `withContext(Dispatchers.IO)`。
 *
 * 内存约定：同一时刻只向上返回一张已经降采样的 Bitmap，旧的由调用方（MainScreen）
 * 在换图后释放。
 */
object BgImageRepository {

    // ── 偏好读写 ────────────────────────────────────────────────────────────

    fun readMode(ctx: Context): BgMode {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BgMode.fromKey(prefs.getString(KEY_BG_MODE, null))
    }

    fun writeMode(ctx: Context, mode: BgMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BG_MODE, mode.key).apply()
    }

    // ── 缓存 ───────────────────────────────────────────────────────────────

    fun cacheDir(ctx: Context): File = File(ctx.filesDir, CACHE_DIR)

    /** 缓存里的所有有效图片文件，最新（lastModified 最大）在前。 */
    private fun cachedFiles(ctx: Context): List<File> =
        cacheDir(ctx).listFiles()
            ?.filter { it.isFile && it.name.startsWith("bg_") && it.name.endsWith(".img") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /**
     * 启动时先用缓存里最新的一张立即上屏（断网也有图）。
     * @return 解码后的 Bitmap；没有缓存 / 缓存损坏 → null（调用方回退渐变）
     */
    fun loadNewestCached(ctx: Context, targetWidthPx: Int): Bitmap? {
        val newest = cachedFiles(ctx).firstOrNull() ?: return null
        val bmp = decode(newest, targetWidthPx)
        if (bmp == null) {
            // 缓存文件坏了（半截下载 / 不是图片）：删掉，别每次都白解一遍
            runCatching { newest.delete() }
        }
        return bmp
    }

    /**
     * 依次尝试端点下载一张新图，落盘（去重 + LRU）后解码。
     *
     * 降级链：t.alcy.cc → loliapi.com → api.waifu.pics（JSON 取 url 再下）。
     * 全部失败 / 无网络 → 返回 null，调用方保留当前图或回退渐变，绝不黑屏。
     */
    fun downloadAndDecode(ctx: Context, targetWidthPx: Int): Bitmap? {
        val bytes = downloadBytes() ?: return null
        // 端点偶尔会用 200 返回一个 HTML 错误页：先看文件头，别把垃圾写进缓存
        if (!looksLikeImage(bytes)) return null
        val file = store(ctx, bytes) ?: return null
        val bmp = decode(file, targetWidthPx)
        if (bmp == null) runCatching { file.delete() }   // 解不出来（损坏/不支持的编码）就别留着
        return bmp
    }

    private fun downloadBytes(): ByteArray? {
        for (endpoint in DIRECT_ENDPOINTS) {
            val bytes = fetch(endpoint) ?: continue
            // 200 + HTML 错误页也算这个端点失败，继续下一个（而不是整条链断掉）
            if (looksLikeImage(bytes)) return bytes
        }
        // 兜底：JSON 端点返回 {"url":"https://..."}，再下那个 url
        val json = fetch(JSON_ENDPOINT) ?: return null
        val imageUrl = runCatching {
            JSONObject(String(json, Charsets.UTF_8)).optString("url")
        }.getOrNull().orEmpty()
        if (!imageUrl.startsWith("http")) return null
        val bytes = fetch(imageUrl) ?: return null
        return if (looksLikeImage(bytes)) bytes else null
    }

    /**
     * GET 一个地址拿到原始字节。
     *
     * `instanceFollowRedirects = true` 处理同协议的 302（前两个端点都是直出图片的
     * 302 跳转）；HttpURLConnection **不会**自动跨协议跳转（http→https），所以 3xx
     * 走到这里时再手动跟一次 Location，最多 [MAX_REDIRECTS] 跳。
     */
    private fun fetch(url: String): ByteArray? {
        var current = url
        var hop = 0
        while (hop <= MAX_REDIRECTS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/*,application/json;q=0.9,*/*;q=0.5")
                    setRequestProperty("User-Agent", "Orbit-Android/1.0")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) return null
                    current = if (location.startsWith("http")) location
                    else URL(URL(current), location).toString()
                    hop++
                    continue
                }
                if (code !in 200..299) return null
                // 用 contentLength（Int, API 1）而不是 contentLengthLong（API 24）：
                // minSdk 23，超过 2GB 的图片本来就被 MAX_BYTES 拒绝
                if (conn.contentLength > MAX_BYTES) return null

                val out = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                conn.inputStream.use { input ->
                    var total = 0L
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return null
                        out.write(chunk, 0, n)
                    }
                }
                return out.toByteArray()
            } catch (_: Exception) {
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * 落盘：内容 SHA-1 前 10 位进文件名。
     *
     * 同一个内容只会有一份文件（端点偶发返回同一张图时直接复用，不写第二份），
     * 写完按 lastModified 淘汰到只剩 [MAX_CACHE_FILES] 张。
     */
    private fun store(ctx: Context, bytes: ByteArray): File? {
        val dir = cacheDir(ctx)
        if (!dir.exists() && !dir.mkdirs()) return null
        val hash = sha1(bytes).take(10)
        val existing = cachedFiles(ctx).firstOrNull { it.name.endsWith("_$hash.img") }
        if (existing != null) {
            existing.setLastModified(System.currentTimeMillis())
            return existing
        }
        val target = File(dir, "bg_${System.currentTimeMillis()}_$hash.img")
        val tmp = File(dir, target.name + ".tmp")
        return try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                null
            } else {
                trim(ctx)
                target
            }
        } catch (_: Exception) {
            runCatching { tmp.delete() }
            null
        }
    }

    /** LRU 淘汰：按 lastModified 只保留最新的 [keep] 张。 */
    fun trim(ctx: Context, keep: Int = MAX_CACHE_FILES) {
        val files = cachedFiles(ctx)
        if (files.size <= keep) return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }

    /** 「关闭」模式：清掉磁盘缓存，不占空间。 */
    fun clearCache(ctx: Context) {
        cacheDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
        runCatching { cacheDir(ctx).delete() }
    }

    // ── 解码 ───────────────────────────────────────────────────────────────

    /**
     * 按屏宽降采样解码，避免 4K 大图直接进内存。
     *
     * 两步走：
     *  1. `inSampleSize` 是 2 的幂，取「解码后宽度仍 ≥ 目标宽度」的最大采样率 —— 4K 原图
     *     （4096px）在 1080px 屏上直接按 1/2 解，4096x2371 只占 9.3MB 而不是 38MB。
     *  2. 采样后宽度仍明显超过目标（比如 1440x3120 的竖图只能取 1/1）再等比缩到目标宽，
     *     把常驻内存压到 ~1080 宽的量级；缩放失败就退回采样结果，不因此丢背景。
     *
     * 解码失败 / OOM 返回 null（调用方回退渐变，绝不崩）。
     */
    fun decode(file: File, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcWidth = bounds.outWidth
        if (srcWidth <= 0 || bounds.outHeight <= 0) return null

        val target = targetWidthPx.coerceIn(MIN_TARGET_WIDTH_PX, MAX_TARGET_WIDTH_PX)
        var sample = 1
        while (srcWidth / (sample * 2) >= target) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: OutOfMemoryError) {
            // 极端情况下（解码器不受 inSampleSize 约束）宁可没有背景，也不能崩
            runCatching { file.delete() }
            null
        } catch (_: Exception) {
            null
        } ?: return null

        // 采样不够细（不是 2 的幂导致的余量）：等比比到目标宽，常驻内存再降一档
        if (sampled.width <= target + target / 2) return sampled
        val scaledHeight = (sampled.height.toFloat() * target / sampled.width)
            .roundToInt().coerceAtLeast(1)
        val scaled = try {
            Bitmap.createScaledBitmap(sampled, target, scaledHeight, true)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
        if (scaled != null && scaled !== sampled) {
            runCatching { sampled.recycle() }
            return scaled
        }
        return sampled
    }
}

/** 解码目标宽度：屏幕真实像素宽，夹在 720–1080 之间。 */
fun bgTargetWidthPx(ctx: Context): Int {
    val width = ctx.resources.displayMetrics.widthPixels
    return width.coerceIn(MIN_TARGET_WIDTH_PX, MAX_TARGET_WIDTH_PX)
}

/**
 * 文件头嗅探：只接受 Android 原生能解的四种格式。
 *
 * HTTP 200 + text/html（被墙/限流的错误页）在移动端很常见，存下来只会在解码时白跑一遍。
 */
private fun looksLikeImage(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    fun u(i: Int) = bytes[i].toInt() and 0xFF
    // JPEG: FF D8 FF
    if (u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF) return true
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return true
    // GIF: "GIF8"
    if (u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x38) return true
    // WebP: "RIFF"…"WEBP"（两个端点实测都是 webp）
    if (u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
        u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50
    ) return true
    return false
}

private fun sha1(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) sb.append(String.format(Locale.US, "%02x", b))
    return sb.toString()
}
