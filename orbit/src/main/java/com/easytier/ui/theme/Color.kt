package com.easytier.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════
//  EasyTier Color Palette — Light & Dark Modes
// ════════════════════════════════════════════════════

// ── Light Theme (FlClash style — flat light grey + Android blue accent) ──
val LightBgStart = Color(0xFFF4F4F5)         // Flat light grey (no gradient)
val LightBgMid = Color(0xFFF4F4F5)
val LightBgEnd = Color(0xFFF4F4F5)

val LightSurface = Color(0xFFFFFFFF)           // White
val LightSurfaceDim = Color(0xFFF8F8F8)        // Near-white for secondary cards
val LightCardBorder = Color(0x1A4285F4)        // Subtle Android-blue border

val LightTextPrimary = Color(0xFF1C1B1F)       // Near-black
val LightTextSecondary = Color(0xFF6B6B70)     // Muted grey
val LightTextTertiary = Color(0xFF9E9EA3)      // Light muted

// ── Light Accent (Android / Google blue) ──
val LightAccentStart = Color(0xFF4285F4)       // Google Blue 500
val LightAccentEnd = Color(0xFF1A73E8)         // Google Blue 600 (one step deeper)
val LightAccent = Color(0xFF4285F4)            // Solid accent
val LightAccentLight = Color(0xFF8AB4F8)       // Blue 200/300 light
val LightAccentContainer = Color(0xFFD3E3FD)   // Blue 50 container

val LightInputBg = Color(0xFFF4F4F5)           // Flat light grey
val LightInputBorder = Color(0xFFE0E0E0)
val LightInputFocusBorder = Color(0xFF1A73E8)

// ── Dark Theme (FlClash style — flat #121212 + Android blue accent) ──
val DarkBgStart = Color(0xFF121212)            // Flat near-black (no gradient)
val DarkBgMid = Color(0xFF121212)
val DarkBgEnd = Color(0xFF121212)

val DarkGlass = Color(0xFF1E1E1E)              // Card fill (flat)
val DarkGlassStrong = Color(0xFF2C2C2C)        // Stronger card
val DarkGlassBorder = Color(0x26FFFFFF)        // 15% white border
val DarkNavBar = Color(0xFF1E1E1E)             // Bottom nav bg

val DarkTextPrimary = Color(0xE6FFFFFF)        // 90% white
val DarkTextSecondary = Color(0x8CFFFFFF)      // 55% white
val DarkTextTertiary = Color(0x59FFFFFF)       // 35% white

val DarkAccent = Color(0xFF4285F4)             // Google Blue 500
val DarkAccentDark = Color(0xFF1967D2)         // Google Blue 700
val DarkAccentLight = Color(0xFF8AB4F8)        // Blue 200/300 light

val DarkInputBg = Color(0x0FFFFFFF)            // 6% white
val DarkInputBorder = Color(0x1AFFFFFF)        // 10% white
val DarkInputFocusBorder = Color(0x804285F4)   // 50% accent

// ── Status (shared between themes) ──
val StatusGood = Color(0xFF4ADE80)             // Green-400
val StatusWarn = Color(0xFFFBBF24)             // Amber-400
val StatusBad = Color(0xFFF87171)              // Red-400
val StatusGoodGlow = Color(0x664ADE80)         // Glow for green
val StatusConnectedBg = Color(0x264ADE80)      // Connected banner bg
val StatusConnectedBgLight = Color(0xE8F5E9)   // Light green bg for light mode

// ── Material 3 tokens — Dark ──
val md_theme_dark_primary = DarkAccentLight
val md_theme_dark_onPrimary = Color(0xFF062E6F)
val md_theme_dark_primaryContainer = DarkAccent.copy(alpha = 0.3f)
val md_theme_dark_onPrimaryContainer = DarkAccentLight
val md_theme_dark_secondary = Color(0xFFC5C4DD)
val md_theme_dark_onSecondary = Color(0xFF2E2F42)
val md_theme_dark_secondaryContainer = Color(0xFF454559)
val md_theme_dark_onSecondaryContainer = Color(0xFFE1E0F9)
val md_theme_dark_tertiary = Color(0xFFD5BEC8)
val md_theme_dark_onTertiary = Color(0xFF3B2832)
val md_theme_dark_background = DarkBgStart
val md_theme_dark_onBackground = DarkTextPrimary
val md_theme_dark_surface = DarkGlass
val md_theme_dark_onSurface = DarkTextPrimary
val md_theme_dark_surfaceVariant = DarkGlass
val md_theme_dark_onSurfaceVariant = DarkTextSecondary
val md_theme_dark_outline = Color(0xFF908F9A)
val md_theme_dark_outlineVariant = Color(0xFF46464F)
val md_theme_dark_error = Color(0xFFFFB4AB)

// ── Material 3 tokens — Light ──
val md_theme_light_primary = LightAccent
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = LightAccentContainer
val md_theme_light_onPrimaryContainer = Color(0xFF041E49)
val md_theme_light_secondary = Color(0xFF5E5C72)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE3E0F9)
val md_theme_light_onSecondaryContainer = Color(0xFF1B1A2C)
val md_theme_light_tertiary = Color(0xFF78536B)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_background = LightBgEnd
val md_theme_light_onBackground = LightTextPrimary
val md_theme_light_surface = LightSurface
val md_theme_light_onSurface = LightTextPrimary
val md_theme_light_surfaceVariant = LightSurfaceDim
val md_theme_light_onSurfaceVariant = LightTextSecondary
val md_theme_light_outline = Color(0xFFE0E0E0)
val md_theme_light_outlineVariant = Color(0xFFF0F0F0)
val md_theme_light_error = Color(0xFFBA1A1A)
