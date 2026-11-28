package com.easytier.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 全 App 的两套外观，只有这两个值。
 *
 * | 模式 | 何时可用 | 渲染 |
 * |------|----------|------|
 * | [LIQUID_GLASS] | Android 13 (API 33) 及以上 | 液态玻璃：背景模糊 + 高光/描边/折射 |
 * | [UNIVERSAL] | 所有版本 | 通用现代 UI：实色/极浅渐变 + 清晰描边 + 轻阴影，零模糊 |
 *
 * [UNIVERSAL] 是**兜底**而不是「弱化版玻璃」：<33 的设备强制走它，33+ 的用户也可以主动关掉玻璃。
 */
enum class ThemeMode {
    LIQUID_GLASS,
    UNIVERSAL
}

/**
 * 主题管理：**全 App 唯一**判断「能不能上玻璃」的地方。
 *
 * 约束（故意写死在这里，业务/组件代码不要再自己判断版本）：
 *  - 玻璃能力 = `SDK_INT >= 33`（`Modifier.blur` / `RuntimeShader` 的成本线）。
 *  - <33 一律 [ThemeMode.UNIVERSAL]，**忽略**存储值 —— 免得用户在 33 上关/开后换机出现怪状态。
 *  - 33+ 默认 [ThemeMode.LIQUID_GLASS]（本项目的主视觉），用户可选择关闭。
 *  - 持久化复用既有的 `easytier_theme` SharedPreferences（与 dark_theme / bg_mode 同一个文件），
 *    不引入 DataStore。
 */
object AppThemeManager {

    /** 与 `dark_theme` / `dynamic_color` / `bg_mode` 共用的 prefs 文件。 */
    private const val PREFS = "easytier_theme"

    /** 外观模式键：`LIQUID_GLASS` / `UNIVERSAL`（enum name）。 */
    const val KEY_MODE = "ui_theme_mode"

    /** 33+ 且从未选择过的默认值。 */
    val DEFAULT_MODE = ThemeMode.LIQUID_GLASS

    /** 唯一的能力判断：`Modifier.blur` + AGSL 折射的可用线。 */
    fun isGlassCapable(): Boolean = Build.VERSION.SDK_INT >= 33

    /** 动态取色（Android 8.1+ 的 accent_color）。只是取色的版本闸门，与玻璃无关。 */
    fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= 27

    /**
     * 读取用户*存过*的模式。<33 强制 [ThemeMode.UNIVERSAL]，不看存储值。
     * 已存但解析不出来（跨版本改名/脏数据）时回落默认值。
     */
    fun readMode(ctx: Context): ThemeMode {
        if (!isGlassCapable()) return ThemeMode.UNIVERSAL
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null) ?: return DEFAULT_MODE
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(DEFAULT_MODE)
    }

    /** 持久化用户选择（异步 apply，不阻塞 UI 线程）。 */
    fun writeMode(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /** 真正生效的模式 = 能力闸门 + 存储值。 */
    fun effectiveMode(ctx: Context): ThemeMode =
        if (!isGlassCapable()) ThemeMode.UNIVERSAL else readMode(ctx)
}

/**
 * 当前生效的 [ThemeMode]。
 *
 * 默认值是 [ThemeMode.UNIVERSAL]：任何没有显式提供该 Local 的组合（预览、被遗忘的入口、
 * 独立 Activity）都拿到**最安全**的那套渲染 —— 不会意外构造 RuntimeShader。
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.UNIVERSAL }

/**
 * 切换入口。设置页开关只调用它，不持有任何状态：
 * 状态住在 [rememberThemeModeController]（挂在 setContent 顶层），所以切换只触发重组，
 * **不重建 Activity**、不丢 pageStack / subPage。
 */
val LocalSetThemeMode = staticCompositionLocalOf<(ThemeMode) -> Unit> { { } }

/**
 * 主题状态持有者。[mode] 写入即持久化，同时作为 [LocalThemeMode] 的来源。
 */
@Stable
class ThemeModeController internal constructor(
    private val state: MutableState<ThemeMode>,
    private val ctx: Context
) {
    /** 当前生效模式（<33 恒为 UNIVERSAL）。 */
    val mode: ThemeMode get() = state.value

    val glassCapable: Boolean get() = AppThemeManager.isGlassCapable()

    /** 用户点开关：改状态（触发全 App 重组）+ 落盘。 */
    fun set(next: ThemeMode) {
        if (!glassCapable) return
        state.value = next
        AppThemeManager.writeMode(ctx, next)
    }

    fun toggle() = set(if (mode == ThemeMode.LIQUID_GLASS) ThemeMode.UNIVERSAL else ThemeMode.LIQUID_GLASS)
}

/** 把主题状态放到当前组合的根上（MainActivity / ConfigEditActivity 的 setContent 各一次）。 */
@Composable
fun rememberThemeModeController(): ThemeModeController {
    val ctx = LocalContext.current
    val state = remember(ctx) { mutableStateOf(AppThemeManager.effectiveMode(ctx)) }
    return remember(ctx, state) { ThemeModeController(state, ctx) }
}

/**
 * 提供 [LocalThemeMode] / [LocalSetThemeMode]。
 *
 * 必须包在 `EasyTierTheme` **外层**（或与其同级的最外层）：整棵树（含 Dialog/Popup 子组合）
 * 都要能读到，切换才会即时生效。
 */
@Composable
fun ProvideThemeMode(content: @Composable () -> Unit) {
    val controller = rememberThemeModeController()
    CompositionLocalProvider(
        LocalThemeMode provides controller.mode,
        LocalSetThemeMode provides { next -> controller.set(next) },
        content = content
    )
}

/**
 * 过渡时长（ms）：UNIVERSAL 下按 60% 缩短（260 → 156、240 → 144 …），玻璃模式原样返回。
 *
 * 直接写在 `tween(...)` 里即可，保证「Universal 动效减半、任何过渡 ≤ 200ms」这条规则
 * 只在一个地方定义。
 */
@Composable
fun motionMs(baseMs: Int): Int =
    if (LocalThemeMode.current == ThemeMode.UNIVERSAL) baseMs * 6 / 10 else baseMs
