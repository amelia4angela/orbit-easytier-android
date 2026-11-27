package com.easytier.ui.glass

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography for the glass layer.
 *
 * The app has no custom font system — it styles `Text` inline (fontSize/fontWeight at call
 * sites). Rather than inventing a type scale that would fight the existing screens, this is a
 * thin set of styles the glass components share, expressed with the same sp/weight values the
 * current UI already uses (14sp Medium titles, 12sp tertiary hints).
 */
object GlassTypography {
    /** Dialog / sheet / top-bar title. */
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    /** Card headline. */
    val headline = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

    /** Default body text. */
    val body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)

    /** Row label inside a card. */
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)

    /** Hint / caption. */
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)

    /** Button label. */
    val button = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    /** Nav bar item label. */
    val navLabel = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
}
