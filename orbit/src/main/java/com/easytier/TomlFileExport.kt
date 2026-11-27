package com.easytier

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「导出配置 → 真正的 .toml 文件」。
 *
 * 走 Storage Access Framework：ACTION_CREATE_DOCUMENT + CATEGORY_OPENABLE，用户自己
 * 选保存位置与文件名，`onActivityResult` 里用 `contentResolver.openOutputStream(uri)`
 * 写入 [TomlUtils.buildToml] 的输出。
 *
 * 为什么不用 FileProvider + ACTION_SEND：SAF 是「保存到用户选的位置」，语义就是导出
 * 文件本身；不用申请任何存储权限，也不需要额外 provider 配置。
 */
object TomlFileExport {

    /** 建议文件名：`<配置名>.toml`，把文件名非法字符换掉，空名回退成 config。 */
    fun fileName(configName: String): String {
        val base = configName.trim()
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .ifEmpty { "config" }
            .take(64)
        return if (base.endsWith(".toml", ignoreCase = true)) base else "$base.toml"
    }

    /**
     * 系统文件保存器的 Intent（MIME 用 text/plain —— 各家文件管理器都认的纯文本类型）。
     */
    fun createDocumentIntent(suggestedName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }

    /**
     * 把 [toml] 写进 SAF 返回的 [uri]。
     *
     * @return null = 成功；非 null = 给用户看的错误信息。
     */
    fun write(ctx: Context, uri: Uri, toml: String): String? = try {
        // "wt" = 截断后写入（覆盖已存在的同名文件）；个别 provider 不认 "wt"，退回 "w"。
        val stream = ctx.contentResolver.openOutputStream(uri, "wt")
            ?: ctx.contentResolver.openOutputStream(uri)
        if (stream == null) {
            "openOutputStream returned null"
        } else {
            stream.use { it.write(toml.toByteArray(Charsets.UTF_8)) }
            null
        }
    } catch (t: Throwable) {
        AppLogger.e("TomlExport", "Write failed", t)
        t.message ?: t.toString()
    }
}
