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
 * - **最多 100 条**：环形缓冲，超过 100 丢最旧。
 * - **只保留最近 3 天**：每条日志带时间戳，[getLogs] / [getLatestLines] 按时间过滤，
 *   再截取最新的若干条。
 */
object AppLogger {
    /** 环形缓冲区上限 */
    private const val MAX_LINES = 100
    /** 日志保留时长：3 天 */
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    private class Entry(val timestamp: Long, val text: String)

    @Volatile
    private var _enabled = false

    /** 是否正在收集日志（默认关）。UI 可读此值决定是否提示「请在设置中开启」。 */
    val enabled: Boolean get() = _enabled

    private val buffer = ArrayDeque<Entry>(MAX_LINES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 打开/关闭日志收集。关闭后新日志不再进入缓冲区（已有记录保留，按 3 天规则老化）。 */
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
        val cutoff = now - MAX_AGE_MS
        while (buffer.isNotEmpty() && buffer.first().timestamp < cutoff) {
            buffer.removeFirst()
        }
        buffer.addLast(Entry(now, "$time $level/$tag: $msg"))
        while (buffer.size > MAX_LINES) {
            buffer.removeFirst()
        }
    }

    /** 3 天内的记录，按时间从旧到新。 */
    private fun recent(): List<Entry> {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
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
