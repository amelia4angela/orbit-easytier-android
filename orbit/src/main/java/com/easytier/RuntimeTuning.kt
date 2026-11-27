package com.easytier

import android.content.Context

/**
 * 省电模式 / 内存限制档位 —— 应用级运行时调节。
 *
 * 开关值持久化在 [com.easytier.ui.ConfigState]（随配置文件走）。MainActivity 在配置
 * 发生变化时把它同步到这里，这样没有 ConfigState 的组件（[com.easytier.vpn.EasyTierVpnService]
 * 的通知刷新、[AppLogger] 的缓冲上限）也能读到；同时落盘到 SharedPreferences，
 * 进程被杀重启后仍然生效。
 *
 * 读接口都是 [Volatile] 字段 + 纯计算，不碰 IO，可以在轮询循环里每轮读取。
 */
object RuntimeTuning {

    private const val PREFS = "easytier_tuning"
    private const val KEY_POWER_SAVING = "power_saving"
    private const val KEY_MEMORY_LIMIT = "memory_limit"

    // 内存限制档位取值
    const val MEMORY_STANDARD = "standard"
    const val MEMORY_STRICT = "strict"
    const val MEMORY_OFF = "off"

    // ── 轮询节奏 ──
    /** 前台状态轮询：1s（现状） */
    private const val POLL_FOREGROUND_MS = 1_000L
    /** 严格档前台轮询：2s（更慢） */
    private const val POLL_STRICT_MS = 2_000L
    /** 省电模式 + 后台/熄屏：60s */
    private const val POLL_POWER_SAVING_BG_MS = 60_000L
    /** 通知刷新：5s（现状）/ 严格 10s / 省电 60s */
    private const val NOTIFY_DEFAULT_MS = 5_000L
    private const val NOTIFY_STRICT_MS = 10_000L
    private const val NOTIFY_POWER_SAVING_MS = 60_000L
    /** 日志页刷新：2s（现状）/ 严格 5s */
    private const val LOGS_DEFAULT_MS = 2_000L
    private const val LOGS_STRICT_MS = 5_000L

    // ── 日志上限 ──
    private const val LOG_LINES_STANDARD = 100
    private const val LOG_LINES_STRICT = 30
    private const val LOG_AGE_STANDARD_MS = 3L * 24 * 60 * 60 * 1000   // 3 天
    private const val LOG_AGE_STRICT_MS = 6L * 60 * 60 * 1000          // 6 小时

    @Volatile
    var powerSaving: Boolean = false
        private set

    @Volatile
    var memoryLimit: String = MEMORY_STANDARD
        private set

    /** 是否处于「严格」内存档 */
    val strict: Boolean get() = memoryLimit == MEMORY_STRICT

    /** 从 SharedPreferences 恢复（进程启动时调用一次）。 */
    fun load(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        powerSaving = p.getBoolean(KEY_POWER_SAVING, false)
        memoryLimit = normalize(p.getString(KEY_MEMORY_LIMIT, MEMORY_STANDARD))
        applyToLogger()
    }

    fun setPowerSaving(ctx: Context, on: Boolean) {
        powerSaving = on
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_POWER_SAVING, on).apply()
    }

    fun setMemoryLimit(ctx: Context, level: String) {
        val v = normalize(level)
        memoryLimit = v
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MEMORY_LIMIT, v).apply()
        applyToLogger()
    }

    /** 把当前档位对应到 AppLogger 的条数 / 保留时长。 */
    fun applyToLogger() {
        if (strict) {
            AppLogger.setLimits(LOG_LINES_STRICT, LOG_AGE_STRICT_MS)
        } else {
            AppLogger.setLimits(LOG_LINES_STANDARD, LOG_AGE_STANDARD_MS)
        }
    }

    private fun normalize(level: String?): String = when (level) {
        MEMORY_STRICT, MEMORY_OFF, MEMORY_STANDARD -> level
        else -> MEMORY_STANDARD
    }

    // ═══════════════════════════════════════════════
    //  Interval readers
    // ═══════════════════════════════════════════════

    /**
     * 状态轮询间隔。省电模式只在后台/熄屏（[foreground] = false）时降频到 60s，
     * 回到前台立刻恢复 1s（严格档 2s）。
     */
    fun pollIntervalMs(foreground: Boolean): Long = when {
        powerSaving && !foreground -> POLL_POWER_SAVING_BG_MS
        strict -> POLL_STRICT_MS
        else -> POLL_FOREGROUND_MS
    }

    /** 常驻通知刷新间隔（VPN 服务在后台跑，省电模式下 60s）。 */
    val notificationIntervalMs: Long
        get() = when {
            powerSaving -> NOTIFY_POWER_SAVING_MS
            strict -> NOTIFY_STRICT_MS
            else -> NOTIFY_DEFAULT_MS
        }

    /** 日志页自动刷新间隔。 */
    val logsRefreshMs: Long
        get() = if (strict) LOGS_STRICT_MS else LOGS_DEFAULT_MS
}
