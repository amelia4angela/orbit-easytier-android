package com.easytier.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.theme.AppColors
import com.easytier.ui.theme.currentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ═══════════════════════════════════════════════════
//  Privacy policy — 在线 Markdown（GitHub raw + 镜像 + 内置兜底）
// ═══════════════════════════════════════════════════

/** 政策正文的权威地址：仓库根 PRIVACY.md（main 分支）。 */
private const val PRIVACY_MD_RAW =
    "https://raw.githubusercontent.com/amelia4angela/orbit-easytier-android/main/PRIVACY.md"

/**
 * 拉取顺序：GitHub raw → ghfast 加速 → gitmirror 镜像。
 * 国内直连 raw 经常超时，所以每个地址单独 10s 超时，任一成功即返回。
 */
private val PRIVACY_MD_URLS = listOf(
    PRIVACY_MD_RAW,
    "https://ghfast.top/$PRIVACY_MD_RAW",
    "https://raw.gitmirror.com/amelia4angela/orbit-easytier-android/main/PRIVACY.md"
)

/** 中英分节标记：正文用它切开中文段 / 英文段，渲染时只取当前语言那一段。 */
private const val PRIVACY_LANG_MARK = "<!-- lang:en -->"

/** 离线兜底副本（assets/PRIVACY.md，与仓库根 PRIVACY.md 内容一致）。 */
private const val PRIVACY_ASSET = "PRIVACY.md"

/**
 * 隐私政策的拉取 / 缓存 / 版本比对。所有联网函数都是阻塞调用，必须在
 * Dispatchers.IO 上跑。
 */
object PrivacyMd {
    private const val PREFS_STATE = "easytier_state"

    /** 上次成功拉到的正文（离线进入政策页时先用它）。 */
    private const val KEY_CACHE = "privacy_md_cache"

    /** 用户同意/确认过的那一版正文的 hash；变化了才弹「隐私政策已更新」。 */
    private const val KEY_HASH = "privacy_md_hash"

    /**
     * 本次进程是否已经做过 hash 比对。Activity 重建（旋转屏幕）会重跑启动 effect，
     * 这个标记保证「隐私政策已更新」一进程最多提示一次（「稍后」= 下次启动再说）。
     */
    @Volatile
    var comparedThisProcess: Boolean = false
        private set

    /** 标记本次进程已比对；返回 true 表示这是第一次（调用方据此决定要不要弹）。 */
    fun markCompared(): Boolean {
        if (comparedThisProcess) return false
        comparedThisProcess = true
        return true
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)

    /** 阻塞拉取，返回 null = 三个地址都失败（调用方静默处理）。 */
    fun fetch(): String? {
        for (url in PRIVACY_MD_URLS) {
            val text = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "Orbit-Android")
                conn.setRequestProperty("Accept", "text/plain")
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) null
                    else conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            }
            // 太短的内容大概率是镜像的错误页，不算成功
            if (text != null && text.trim().length > 200) return text
        }
        return null
    }

    /** 内容指纹：SHA-256 取前 16 位十六进制。 */
    fun hashOf(md: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(md.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun cached(ctx: Context): String? =
        prefs(ctx).getString(KEY_CACHE, null)?.takeIf { it.isNotBlank() }

    fun saveCache(ctx: Context, md: String) {
        prefs(ctx).edit().putString(KEY_CACHE, md).apply()
    }

    /** 已同意版本的 hash；null = 从未记录（首次同意前的老装机 / 升级上来的老用户）。 */
    fun acceptedHash(ctx: Context): String? =
        prefs(ctx).getString(KEY_HASH, null)?.takeIf { it.isNotBlank() }

    /** 用户点了同意 —— 把当前展示的正文记成「已同意版本」。 */
    fun markAccepted(ctx: Context, md: String) {
        prefs(ctx).edit().putString(KEY_HASH, hashOf(md)).apply()
    }

    /** 内置离线副本；连 assets 都读不到时退回一句最小说明。 */
    fun fallback(ctx: Context): String = try {
        ctx.assets.open(PRIVACY_ASSET).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "# Orbit\n\n" + T(
            "隐私政策暂时无法加载，请稍后联网重试，或在浏览器打开 github.com/amelia4angela/orbit-easytier-android",
            "The privacy policy could not be loaded. Retry with a connection, or open github.com/amelia4angela/orbit-easytier-android in a browser.",
            true
        )
    }

    /** 按当前语言切出中文段 / 英文段；没有语言标记就整篇显示。 */
    fun localized(md: String, langZh: Boolean): String {
        val idx = md.indexOf(PRIVACY_LANG_MARK)
        if (idx < 0) return md
        val zh = md.substring(0, idx)
        val en = md.substring(idx + PRIVACY_LANG_MARK.length)
        return if (langZh) zh else en
    }
}

/**
 * 隐私政策页，两种用法：
 *  - [forced] = true：首次启动的强制门，没有返回键，只能「同意」或「不同意」。
 *  - [forced] = false：从关于页打开（[showAgree] = false 只读）；
 *    政策更新后从弹窗进来（[showAgree] = true，底部多一个「已阅读并同意」）。
 *
 * 正文优先用 SharedPreferences 里的在线缓存，进页面后再后台刷新一次；
 * 拉取全失败且没有缓存时用内置离线副本，并在标题栏标「离线版本」。
 */
@Composable
fun PrivacyDocPage(
    langZh: Boolean,
    forced: Boolean,
    showAgree: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val colors = currentColors()
    if (forced) BackHandler { /* consume：强制门不能用系统返回跳过 */ }

    val cached = remember { PrivacyMd.cached(ctx) }
    var markdown by remember { mutableStateOf(cached ?: PrivacyMd.fallback(ctx)) }
    var offline by remember { mutableStateOf(cached == null) }
    var refreshing by remember { mutableStateOf(true) }

    // 进页面刷新一次：成功 → 覆盖缓存并切到在线正文；失败 → 保留缓存 / 已回落离线副本。
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { PrivacyMd.fetch() }
        if (fresh != null) {
            PrivacyMd.saveCache(ctx, fresh)
            markdown = fresh
            offline = false
        }
        refreshing = false
    }

    val body = remember(markdown, langZh) { PrivacyMd.localized(markdown, langZh) }
    val accept = {
        PrivacyMd.markAccepted(ctx, markdown)   // 「同意」的那一版才记 hash
        onAccept()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.bgStart, colors.bgMid, colors.bgEnd))
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (!forced) {
                HmBackGlyph(
                    onBack = onBack,
                    colors = colors,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    T("隐私政策", "Privacy Policy", langZh),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (refreshing) {
                    Text(
                        T("正在同步最新版本…", "Syncing latest version…", langZh),
                        color = colors.textTertiary,
                        fontSize = 10.sp
                    )
                }
            }
            if (offline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceDim)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        T("离线版本", "Offline", langZh),
                        color = colors.textTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (forced) {
                Text(
                    T(
                        "使用 Orbit 前请阅读并同意以下说明。",
                        "Please read and accept the following before using Orbit.",
                        langZh
                    ),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                MarkdownBody(md = body, colors = colors)
            }
        }

        if (forced) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HmDialogPrimaryButton(
                    text = T("同意并继续", "Agree & Continue", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = accept
                )
                HmDialogSecondaryButton(
                    text = T("不同意", "Decline", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                )
                Text(
                    T(
                        "不同意将退出应用，Orbit 无法在未同意隐私政策的情况下使用。",
                        "Declining exits the app — Orbit cannot run without accepting this policy.",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 11.sp
                )
            }
        } else if (showAgree) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HmDialogPrimaryButton(
                    text = T("已阅读并同意", "I have read and agree", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = accept
                )
                HmDialogSecondaryButton(
                    text = T("返回", "Back", langZh),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }
    }
}

/**
 * 极简 Markdown 渲染：只处理标题 / 列表 / 分隔线 / 表格行 / 段落，
 * 行内的 **粗体**、`代码`、[链接](url) 都退化成纯文本。够读就行，不引引擎。
 */
@Composable
private fun MarkdownBody(md: String, colors: AppColors) {
    val lines = remember(md) { md.lines().map { it.trimEnd() } }
    val numbered = remember { Regex("^\\d+\\.\\s+.*") }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(4.dp))

                // HTML 注释 / 语言标记不显示
                line.startsWith("<!--") -> Unit

                // 文档大标题就是页面标题，跳过
                line.startsWith("# ") -> Unit

                line.startsWith("## ") -> Text(
                    cleanInline(line.removePrefix("## ")),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                line.startsWith("### ") -> Text(
                    cleanInline(line.removePrefix("### ")),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                line == "---" || line == "***" || line == "___" -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(0.5.dp)
                        .background(colors.surfaceBorder)
                )

                // 表格按行原样显示（`| a | b |` → a · b）
                line.startsWith("|") -> {
                    val cells = line.trim('|').split('|').map { it.trim() }
                    if (cells.all { it.all { c -> c == '-' || c == ':' } }) Unit   // 分隔行
                    else Text(
                        cleanInline(cells.joinToString("  ·  ")),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> Row(
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text("•", color = colors.accentLight, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        cleanInline(line.drop(2)),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                numbered.matches(line) -> Row(modifier = Modifier.padding(start = 2.dp)) {
                    val dot = line.indexOf('.')
                    Text(line.substring(0, dot + 1), color = colors.accentLight, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        cleanInline(line.substring(dot + 1).trim()),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                else -> Text(
                    cleanInline(line),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** 去掉行内标记，只留可读文字。 */
private fun cleanInline(s: String): String = s
    .replace(Regex("\\[([^\\]]+)]\\(([^)]*)\\)"), "${'$'}1")
    .replace("**", "")
    .replace("`", "")
