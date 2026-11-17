package dev.lukat.briefing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Colours are state-backed getters, so flipping the theme recomposes the whole tree
 * without threading a CompositionLocal through sixty call sites. Dark stays the default:
 * the app is read in the morning and at night, not in bright sun.
 */
internal object Palette {
    var light by mutableStateOf(false)

    val bg get() = if (light) Color(0xFFF7F8FA) else Color(0xFF0A0E13)
    val panel get() = if (light) Color(0xFFFFFFFF) else Color(0xFF111823)
    val line get() = if (light) Color(0xFFE3E7ED) else Color(0xFF1E2A38)
    val ink get() = if (light) Color(0xFF11161D) else Color(0xFFE8EEF7)
    val dim get() = if (light) Color(0xFF57636F) else Color(0xFF8D9BAD)
    val body get() = if (light) Color(0xFF202833) else Color(0xFFCFD8E3)
    val accent get() = if (light) Color(0xFF0F766E) else Color(0xFF5EEAD4)
    val amber get() = if (light) Color(0xFFB45309) else Color(0xFFFBBF24)
    val faint get() = if (light) Color(0xFF97A3B0) else Color(0xFF5F7183)
}

internal val Bg: Color get() = Palette.bg
internal val Panel: Color get() = Palette.panel
internal val Line: Color get() = Palette.line
internal val Ink: Color get() = Palette.ink
internal val Dim: Color get() = Palette.dim
internal val Body: Color get() = Palette.body
internal val Accent: Color get() = Palette.accent
internal val Amber: Color get() = Palette.amber
internal val Faint: Color get() = Palette.faint
