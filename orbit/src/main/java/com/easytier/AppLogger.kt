package com.easytier

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.ArrayDeque

/**
 * 日志缓冲区：同时在 logcat + 内存环形缓冲区输出，方便在 App 内查看和复制。
 *
 * 治理规则：
 * - **默认关闭**：[enabled] 为 false 时 d/i/w/e 直接 return，既不进 logcat 也不进缓冲。
 * - **条数 / 时长可调**：[setLimits] 由「设置 → 常规 → 内存限制」写入
 *   （标准 100 条 / 3 天，严格 30 条 / 6 小时）。
 * - **环形缓冲**：超过上限丢最旧；[getLogs] / [getLatestLines] 按保留时长过滤后再截尾。
 */
object AppLogger {
    /** 默认：环形缓冲区 100 条 */
    const val DEFAULT_MAX_LINES = 100
    /** 默认：日志保留 3 天 */
    const val DEFAULT_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    @Volatile
    private var maxLines = DEFAULT_MAX_LINES

    @Volatile
    private var maxAgeMs = DEFAULT_MAX_AGE_MS

    private class Entry(val timestamp: Long, val text: String)

    @Volatile
    private var _enabled = false

    /** 是否正在收集日志（默认关）。UI 可读此值决定是否提示「请在设置中开启」。 */
    val enabled: Boolean get() = _enabled

    private val buffer = ArrayDeque<Entry>(DEFAULT_MAX_LINES)

    /**
     * 调整缓冲上限（内存限制档位）。收紧时立即丢掉超出的旧记录，
     * 之后 [append] 按新上限滚动。
     */
    @Synchronized
    fun setLimits(lines: Int, ageMs: Long) {
        maxLines = lines.coerceIn(1, 10_000)
        maxAgeMs = ageMs.coerceAtLeast(60_000L)
        trim()
    }

    /** 当前生效的上限（UI 展示用） */
    val limitLines: Int get() = maxLines

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 打开/关闭日志收集。关闭后新日志不再进入缓冲区（已有记录保留，按保留时长老化）。 */
    fun setEnabled(on: Boolean) {
        _enabled = on
    }

    @Synchronized
    fun d(tag: String, msg: String) {
        if (!_enabled) return
        Log.d(tag, msg)
        append("D", tag, msg)
    }

    @Synchronized
    fun i(tag: String, msg: String) {
        if (!_enabled) return
        Log.i(tag, msg)
        append("I", tag, msg)
    }

    @Synchronized
    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (!_enabled) return
        if (t != null) {
            Log.w(tag, msg, t)
            append("W", tag, "$msg\n${stackTrace(t)}")
        } else {
            Log.w(tag, msg)
            append("W", tag, msg)
        }
    }

    @Synchronized
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!_enabled) return
        if (t != null) {
            Log.e(tag, msg, t)
            append("E", tag, "$msg\n${stackTrace(t)}")
        } else {
            Log.e(tag, msg)
            append("E", tag, msg)
        }
    }

    private fun stackTrace(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    private fun append(level: String, tag: String, msg: String) {
        val now = System.currentTimeMillis()
        val time = dateFormat.format(Date(now))
        // Drop entries older than the retention window so they don't hold ring slots.
        val cutoff = now - maxAgeMs
        while (buffer.isNotEmpty() && buffer.first().timestamp < cutoff) {
            buffer.removeFirst()
        }
        buffer.addLast(Entry(now, "$time $level/$tag: $msg"))
        while (buffer.size > maxLines) {
            buffer.removeFirst()
        }
    }

    /** 按当前上限裁剪（换档时调用，已在锁内）。 */
    private fun trim() {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        while (buffer.isNotEmpty() && buffer.first().timestamp < cutoff) {
            buffer.removeFirst()
        }
        while (buffer.size > maxLines) {
            buffer.removeFirst()
        }
    }

    /** 保留时长内的记录，按时间从旧到新。 */
    private fun recent(): List<Entry> {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        return buffer.filter { it.timestamp >= cutoff }
    }

    @Synchronized
    fun getLogs(): String = recent().joinToString("\n") { it.text }

    /**
     * 返回最近 [count] 条日志（先按 3 天过滤，再截尾），避免全部加载导致卡顿。
     */
    @Synchronized
    fun getLatestLines(count: Int): String {
        val list = recent()
        val tail = if (list.size <= count) list else list.subList(list.size - count, list.size)
        return tail.joinToString("\n") { it.text }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }

    @Synchronized
    fun size(): Int = buffer.size
}
