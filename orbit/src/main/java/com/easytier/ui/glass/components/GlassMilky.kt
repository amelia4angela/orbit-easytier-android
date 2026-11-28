package com.easytier.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easytier.ui.glass.GlassLevel
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.LocalGlassLevel
import com.easytier.ui.glass.effects.glassSurface
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape

/**
 * 「乳白玻璃」填充 —— 全 App 的视觉基准由 [com.easytier.ui.ProxyLaunchCard] 定义。
 *
 * 启动卡片是把一层更实的高白度渐变压在画布上，再叠 [glassSurface] 的顶部高光/描边，
 * 而不是靠 glassSurface 自己的低 α 填充。这个函数把那层渐变固化成一个可复用值：
 *
 *   浅色  白 78% → 56%（玻璃后面是亮色壁纸/渐变，必须够白，深色文字才站得住）
 *   深色  白 10% → 5%（只有一层薄白雾，面板仍是深色，白字依然可读）
 *
 * 弹窗/顶栏/底栏这些"浮起来"的面板都用它，纹理与启动卡片一致。
 */
@Composable
fun milkyFillBrush(isDark: Boolean): Brush = remember(isDark) {
    if (isDark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.56f))
        )
    }
}

/**
 * 乳白玻璃材质（不模糊）：阴影 + 乳白填充 + [glassSurface] 的高光/描边。
 *
 * 用于**无法采样背景**的容器 —— Dialog 是独立窗口，里面没有 `GlassSky`，模糊只会糊掉
 * scrim 本身。所以弹窗面板一律走这条路径：靠更实的白色填充保证文字对比度，再靠
 * glassSurface 的 specular + 0.5dp 描边给出玻璃感。需要真实背景模糊的面板（导航栏、
 * 顶栏）请用 [GlassSurface]，它自带一层（且只有一层）背景模糊。
 *
 * @param intensity 0..1 的玻璃强度旋钮，透传给 [glassSurface]
 * @param shadowElevation 柔和外阴影（ambient 低透明），0 = 不投影
 */
@Composable
fun Modifier.milkyGlass(
    radius: Dp = GlassRadius.lg,
    shape: Shape = glassShape(radius),
    intensity: Float = 1f,
    shadowElevation: Dp = 0.dp,
    level: GlassLevel = LocalGlassLevel.current
): Modifier {
    val colors = glassColors()
    val fill = milkyFillBrush(colors.isDark)
    var result = this
    if (shadowElevation > 0.dp) {
        result = result.shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow
        )
    }
    return result
        .clip(shape)
        .background(fill, shape)
        .glassSurface(level = level, radius = radius, shape = shape, intensity = intensity)
}
