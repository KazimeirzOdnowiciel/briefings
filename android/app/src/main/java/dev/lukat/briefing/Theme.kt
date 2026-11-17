package dev.lukat.briefing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * "Morning edition" design language, shared with the web app (app/app.css):
 * warm paper by day, warm night by night, one sunrise accent, serif headlines.
 *
 * Colours are state-backed getters, so flipping the theme recomposes the whole tree
 * without threading a CompositionLocal through sixty call sites.
 */
internal object Palette {
    var light by mutableStateOf(false)

    // surfaces
    val bg get() = if (light) Color(0xFFF5F1E8) else Color(0xFF100F0D)
    val panel get() = if (light) Color(0xFFFFFCF5) else Color(0xFF1A1815)
    val panel2 get() = if (light) Color(0xFFEFE9DC) else Color(0xFF232019)
    val panel3 get() = if (light) Color(0xFFE6DFD0) else Color(0xFF2D2A22)
    val line get() = if (light) Color(0xFFE0D8C8) else Color(0xFF2F2B24)

    // text
    val ink get() = if (light) Color(0xFF1C1A16) else Color(0xFFF3ECE0)
    val body get() = if (light) Color(0xFF48443C) else Color(0xFFD2CABB)
    val dim get() = if (light) Color(0xFF7A7468) else Color(0xFF9A9284)
    val faint get() = if (light) Color(0xFF9A9285) else Color(0xFF6F685C)

    // accents
    val accent get() = if (light) Color(0xFFC4501B) else Color(0xFFFF8F4A)
    val accentInk get() = if (light) Color(0xFFFFFFFF) else Color(0xFF1E1006)
    val accentSoft get() = if (light) Color(0xFFF6E2D2) else Color(0xFF3A2416)
    val amber get() = if (light) Color(0xFFB7791F) else Color(0xFFF5A524)
    val up get() = if (light) Color(0xFF1F8A4C) else Color(0xFF5FD38D)
    val down get() = if (light) Color(0xFFC93A3A) else Color(0xFFFF7A70)
}

internal val Bg: Color get() = Palette.bg
internal val Panel: Color get() = Palette.panel
internal val Panel2: Color get() = Palette.panel2
internal val Panel3: Color get() = Palette.panel3
internal val Line: Color get() = Palette.line
internal val Ink: Color get() = Palette.ink
internal val Dim: Color get() = Palette.dim
internal val Body: Color get() = Palette.body
internal val Accent: Color get() = Palette.accent
internal val AccentInk: Color get() = Palette.accentInk
internal val AccentSoft: Color get() = Palette.accentSoft
internal val Amber: Color get() = Palette.amber
internal val Faint: Color get() = Palette.faint
internal val Up: Color get() = Palette.up
internal val Down: Color get() = Palette.down

/** Headlines and reading text are serif; UI chrome stays sans. */
internal val Serif: FontFamily = FontFamily.Serif

/**
 * One fixed hue per section, the same as the web app, so World is always blue and
 * Polska always red on both surfaces. Unknown sections fall back to the accent.
 */
internal fun sectionHue(name: String): Color {
    val n = name.lowercase()
    return when {
        "warcraft" in n || Regex("\\bwow\\b").containsMatchIn(n) -> Color(0xFFE09A1A)
        n.contains("world") -> Color(0xFF4F7FE0)
        "polska" in n || "poland" in n -> Color(0xFFE5484D)
        "money" in n || "market" in n -> Color(0xFF2F9E66)
        Regex("\\bai\\b").containsMatchIn(n) || "tech" in n -> Color(0xFF8E4EC6)
        "games" in n || "dev" in n -> Color(0xFF12A594)
        "music" in n -> Color(0xFFD6409F)
        "culture" in n || "film" in n -> Color(0xFFF76B15)
        "brussels" in n -> Color(0xFF3E63DD)
        "tonight" in n || "football" in n || "nba" in n || "sport" in n -> Color(0xFF65A30D)
        "long read" in n -> Color(0xFFA18072)
        else -> Palette.accent
    }
}
