package com.easytier.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.T
import com.easytier.ui.glass.components.GlassBottomSheet
import com.easytier.ui.glass.components.GlassButton
import com.easytier.ui.glass.components.GlassButtonTone
import com.easytier.ui.glass.components.GlassCard
import com.easytier.ui.glass.components.GlassCardElevation
import com.easytier.ui.glass.components.GlassChip
import com.easytier.ui.glass.components.GlassDialog
import com.easytier.ui.glass.components.GlassIconButton
import com.easytier.ui.glass.components.GlassMenu
import com.easytier.ui.glass.components.GlassNavItem
import com.easytier.ui.glass.components.GlassNavigationBar
import com.easytier.ui.glass.components.GlassSegmentedControl
import com.easytier.ui.glass.components.GlassSlider
import com.easytier.ui.glass.components.GlassSurface
import com.easytier.ui.glass.components.GlassSwitch
import com.easytier.ui.glass.components.GlassTextField
import com.easytier.ui.glass.components.GlassTopBar
import com.easytier.ui.glass.effects.GlassRefractionSupport
import com.easytier.ui.glass.effects.GlassShaderSpec
import com.easytier.ui.glass.effects.GlassSky
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.effects.rememberGlassPerformance
import kotlin.math.roundToInt

/**
 * Interactive gallery for the glass design system.
 *
 * Every control below is the real component (no mock-ups): the nav bar slides, the dialog
 * springs in, the slider drags, the quality switch re-renders the whole page, and the theme
 * switch flips the app's existing light/dark state (passed in from `MainScreen`).
 *
 * Entry point is temporary: Settings → ✨ Glass Showcase.
 */
@Composable
fun GlassShowcase(
    langZh: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val level = rememberGlassLevel()

    GlassTheme(level = level.value) {
        val colors = glassColors()

        // ── Demo state ────────────────────────────────────────────────
        var navIndex by remember { mutableStateOf(0) }
        var dialogVisible by remember { mutableStateOf(false) }
        var sheetVisible by remember { mutableStateOf(false) }
        var switchOn by remember { mutableStateOf(true) }
        var sliderValue by remember { mutableStateOf(0.45f) }
        var menuIndex by remember { mutableStateOf(1) }
        var segmentIndex by remember { mutableStateOf(0) }
        var blurIndex by remember { mutableStateOf(2) }
        var text by remember { mutableStateOf("") }
        var chipIndex by remember { mutableStateOf(0) }
        var refraction by remember { mutableStateOf(0.55f) }

        val blurSteps = listOf(0, 10, 20, 40)

        val navItems = listOf(
            GlassNavItem(Icons.Rounded.Dashboard, "概览", "Overview"),
            GlassNavItem(Icons.Rounded.Speed, "测速", "Speed"),
            GlassNavItem(Icons.Rounded.Settings, "设置", "Settings")
        )

        GlassSky(modifier = Modifier.fillMaxSize(), colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GlassSpace.lg)
                    .padding(top = GlassSpace.md, bottom = GlassSpace.xxxl),
                verticalArrangement = Arrangement.spacedBy(GlassSpace.md)
            ) {
                // ── Top bar: title + dark/light toggle (item 11) ───────
                GlassTopBar(
                    title = T("液态玻璃", "Liquid Glass", langZh),
                    onBack = onBack,
                    actions = {
                        GlassIconButton(
                            icon = if (isDarkTheme) "☀" else "☾",
                            onClick = onToggleTheme
                        )
                    }
                )

                Text(
                    text = T(
                        "实时渲染 · 全部可交互",
                        "Live rendering · every control is real",
                        langZh
                    ),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = GlassSpace.xs)
                )

                // ── 1. Card comparison: stable material vs real glass ──
                Row(horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)) {
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        elevation = GlassCardElevation.STABLE,
                        verticalArrangement = Arrangement.spacedBy(GlassSpace.xs)
                    ) {
                        Text(
                            T("稳定材质", "Stable", langZh),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            T("内容卡：不模糊，保证可读", "Content card: no blur, stays legible", langZh),
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        elevation = GlassCardElevation.FLOATING,
                        verticalArrangement = Arrangement.spacedBy(GlassSpace.xs)
                    ) {
                        Text(
                            T("真玻璃", "Real glass", langZh),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            T("悬浮卡：采样背景并模糊", "Floating card: samples + blurs backdrop", langZh),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // ── 2. Buttons: three tones + disabled ─────────────────
                SectionCard(title = T("按钮", "Buttons", langZh), langZh = langZh) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)) {
                        GlassButton(
                            text = T("主要", "Primary", langZh),
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = T("次要", "Secondary", langZh),
                            onClick = {},
                            tone = GlassButtonTone.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = T("危险", "Danger", langZh),
                            onClick = {},
                            tone = GlassButtonTone.DANGER,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    GlassButton(
                        text = T("禁用状态", "Disabled", langZh),
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 3. Navigation bar with sliding indicator ───────────
                SectionCard(title = T("导航栏", "Navigation bar", langZh), langZh = langZh) {
                    GlassNavigationBar(
                        items = navItems,
                        selectedIndex = navIndex,
                        onSelect = { navIndex = it },
                        langZh = langZh
                    )
                }

                // ── 4/5. Dialog + switch ───────────────────────────────
                SectionCard(title = T("弹窗与开关", "Dialog & switch", langZh), langZh = langZh) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)
                    ) {
                        GlassButton(
                            text = T("打开弹窗", "Open dialog", langZh),
                            onClick = { dialogVisible = true },
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = T("底部弹层", "Sheet", langZh),
                            onClick = { sheetVisible = true },
                            tone = GlassButtonTone.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            T("玻璃开关", "Glass switch", langZh),
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassSwitch(checked = switchOn, onCheckedChange = { switchOn = it })
                    }
                }

                // ── 6. Slider with live value ──────────────────────────
                SectionCard(title = T("滑块", "Slider", langZh), langZh = langZh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            T("数值", "Value", langZh),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(sliderValue * 100).roundToInt()}%",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    GlassSlider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 7. Menu (anchored glass dropdown) ──────────────────
                SectionCard(title = T("菜单", "Menu", langZh), langZh = langZh) {
                    GlassMenu(
                        label = T("渲染模式", "Render mode", langZh),
                        options = listOf(
                            T("自动", "Auto", langZh),
                            T("性能优先", "Performance", langZh),
                            T("画质优先", "Quality", langZh)
                        ),
                        selectedIndex = menuIndex,
                        onSelect = { menuIndex = it },
                        langZh = langZh
                    )
                    Text(
                        text = T("已选：", "Selected: ", langZh) +
                            listOf(
                                T("自动", "Auto", langZh),
                                T("性能优先", "Performance", langZh),
                                T("画质优先", "Quality", langZh)
                            )[menuIndex],
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                }

                // ── 8. Segmented control ───────────────────────────────
                SectionCard(title = T("分段选择", "Segmented", langZh), langZh = langZh) {
                    GlassSegmentedControl(
                        options = listOf(
                            T("日", "Day", langZh),
                            T("周", "Week", langZh),
                            T("月", "Month", langZh)
                        ),
                        selectedIndex = segmentIndex,
                        onSelect = { segmentIndex = it }
                    )
                    GlassChip(
                        text = T("示例标签", "Sample chip", langZh),
                        selected = chipIndex == 0,
                        onClick = { chipIndex = if (chipIndex == 0) 1 else 0 }
                    )
                }

                // ── 9. Corner radius ladder ────────────────────────────
                SectionCard(title = T("圆角档位", "Corner radii", langZh), langZh = langZh) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)
                    ) {
                        listOf(12, 16, 24, 28).forEach { r ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(GlassSpace.xs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .glassSurface(radius = r.dp)
                                )
                                Text(
                                    "$r",
                                    color = colors.textTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // ── 10. Blur radius comparison ─────────────────────────
                SectionCard(title = T("模糊强度", "Blur strength", langZh), langZh = langZh) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)) {
                        blurSteps.forEachIndexed { index, r ->
                            GlassChip(
                                text = if (r == 0) T("无", "0", langZh) else "$r",
                                selected = blurIndex == index,
                                onClick = { blurIndex = index },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        radius = GlassRadius.md,
                        blurRadius = blurSteps[blurIndex].dp,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(GlassSpace.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                T("背景模糊半径", "Backdrop blur radius", langZh),
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${blurSteps[blurIndex]} px · " + T(
                                    "观察条纹与色块的柔化程度",
                                    "watch the stripes and blobs soften",
                                    langZh
                                ),
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // ── 10b. AGSL refraction (HIGH + API 33+) ──────────────
                SectionCard(title = T("折射 Refraction", "Refraction", langZh), langZh = langZh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            T("边缘折射强度", "Rim refraction strength", langZh),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(refraction * 100).roundToInt()}%",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    GlassSlider(
                        value = refraction,
                        onValueChange = { refraction = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val spec = GlassShaderSpec(refractionStrength = refraction)
                    Row(horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)) {
                        RefractionPreview(
                            modifier = Modifier.weight(1f),
                            spec = spec,
                            label = T("开", "On", langZh)
                        )
                        RefractionPreview(
                            modifier = Modifier.weight(1f),
                            spec = spec.copy(enabled = false),
                            label = T("关", "Off", langZh)
                        )
                    }
                    Text(
                        text = when {
                            !GlassRefractionSupport.isAvailable -> T(
                                "系统 < Android 13：AGSL RuntimeShader 不可用，本区完全跳过（不构造 shader，零开销）",
                                "API < 33: RuntimeShader unavailable — nothing is constructed, zero cost",
                                langZh
                            )
                            level.value != GlassLevel.HIGH -> T(
                                "仅在「高」档 + Android 13+ 生效，切到高档即可看到差异",
                                "Only on HIGH + Android 13+ — switch to HIGH to see the difference",
                                langZh
                            )
                            else -> T(
                                "真实折射：按圆角边缘把已模糊的背景弯折，R/B 色散 ~1.1px；文字永远不被扭曲",
                                "Real refraction of the blurred backdrop at the rim, R/B split ~1.1px; text is never distorted",
                                langZh
                            )
                        },
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                // ── 11 + 12. Theme & render quality ────────────────────
                SectionCard(title = T("主题与渲染质量", "Theme & quality", langZh), langZh = langZh) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)
                    ) {
                        Text(
                            T("深色模式", "Dark mode", langZh),
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = if (isDarkTheme) T("切到浅色", "To light", langZh)
                            else T("切到深色", "To dark", langZh),
                            onClick = onToggleTheme,
                            tone = GlassButtonTone.SECONDARY
                        )
                    }
                    Text(
                        T("渲染质量", "Render quality", langZh),
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    GlassSegmentedControl(
                        options = listOf(
                            T("高", "HIGH", langZh),
                            T("均衡", "BAL", langZh),
                            T("轻量", "LITE", langZh)
                        ),
                        selectedIndex = level.value.ordinal,
                        onSelect = { idx ->
                            level.value = GlassLevel.values()[idx]
                        }
                    )
                    val perf = rememberGlassPerformance()
                    Text(
                        text = T("当前：", "Active: ", langZh) +
                            perf.effective.labelEn +
                            if (perf.effective != level.value) {
                                T("（设备降级）", " (downgraded on this device)", langZh)
                            } else {
                                ""
                            } +
                            if (perf.reducedMotion) {
                                T(" · 已减少动效", " · reduced motion", langZh)
                            } else {
                                ""
                            },
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                    Text(
                        T(
                            "切换后整页的模糊、描边与投影会立即变化（并持久化）。",
                            "Switching re-renders blur, rims and shadows immediately (and persists).",
                            langZh
                        ),
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                // ── Extras: text field ─────────────────────────────────
                SectionCard(title = T("输入框", "Text field", langZh), langZh = langZh) {
                    GlassTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = T("输入一些文字…", "Type something…", langZh),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(GlassSpace.lg))
                Text(
                    text = T(
                        "阶段 3-5 · 设计系统 / 玻璃组件 / Showcase",
                        "Phase 3-5 · design system / glass components / showcase",
                        langZh
                    ),
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // ── Dialog demo ───────────────────────────────────────────
            GlassDialog(
                visible = dialogVisible,
                onDismiss = { dialogVisible = false },
                title = T("玻璃弹窗", "Glass Dialog", langZh)
            ) {
                Text(
                    T(
                        "这个面板使用真实玻璃材质，出现时带弹性缩放。",
                        "This pane is real glass and springs in with an overshoot.",
                        langZh
                    ),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)) {
                    GlassButton(
                        text = T("确定", "OK", langZh),
                        onClick = { dialogVisible = false },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = T("取消", "Cancel", langZh),
                        onClick = { dialogVisible = false },
                        tone = GlassButtonTone.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Bottom sheet demo ─────────────────────────────────────
            GlassBottomSheet(
                visible = sheetVisible,
                onDismiss = { sheetVisible = false },
                title = T("底部弹层", "Bottom Sheet", langZh)
            ) {
                Text(
                    T(
                        "从底部弹性滑入的玻璃面板。",
                        "A glass panel that springs up from the bottom edge.",
                        langZh
                    ),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                GlassButton(
                    text = T("关闭", "Close", langZh),
                    onClick = { sheetVisible = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Stable content container used to group demo sections. */
@Composable
private fun SectionCard(
    title: String,
    langZh: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = glassColors()
    GlassCard(
        elevation = GlassCardElevation.STABLE,
        verticalArrangement = Arrangement.spacedBy(GlassSpace.sm)
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

/**
 * One half of the refraction A/B pair: the same real [GlassSurface] with the shader spec on or
 * off. The label sits *above* the material, so it also demonstrates that the refraction pass only
 * touches the backdrop slice and never the pane's own content.
 */
@Composable
private fun RefractionPreview(
    spec: GlassShaderSpec,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = glassColors()
    GlassSurface(
        modifier = modifier.height(88.dp),
        radius = GlassRadius.md,
        blurRadius = 18.dp,
        intensity = 0.9f,
        contentPadding = PaddingValues(GlassSpace.sm),
        refraction = spec
    ) {
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
