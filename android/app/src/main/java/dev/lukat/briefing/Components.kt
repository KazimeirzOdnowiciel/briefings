package dev.lukat.briefing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/* ---------- how a section is presented ---------- */

internal enum class Kind { Digest, News, Sport, LongRead }

internal fun kindOf(section: Section): Kind {
    val n = section.name.lowercase()
    return when {
        section.kind == "scores" || section.kind == "fixtures" -> Kind.Sport
        "tl;dr" in n || "tldr" in n || "what changed" in n || "following" in n -> Kind.Digest
        "long read" in n -> Kind.LongRead
        else -> Kind.News
    }
}

/** "🇵🇱 Polska" -> "Polska" */
internal fun cleanName(name: String): String = name.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()

/** "🇵🇱 Polska" -> "🇵🇱". The 📅 glyph prints a fixed "July 17" on most fonts, so Tonight gets a stadium. */
internal fun emojiOf(name: String): String =
    Regex("^([^\\p{L}\\p{N}]+)").find(name)?.value?.trim().orEmpty().let { if (it.startsWith("📅")) "🏟️" else it }

/** Extracted article text: tidy stray spaces before punctuation, and break it into paragraphs.
 *  Newer editions separate paragraphs with blank lines; older ones are one block, so those are
 *  split every few sentences to keep the reader from facing a wall of text. */
internal fun paragraphsOf(body: String): List<String> {
    val tidy = body
        .replace(Regex("[ \\t]+([,.;:!?)\\]])"), "$1")
        .replace(Regex("([(\\[])[ \\t]+"), "$1")
        .replace(Regex("[ \\t]{2,}"), " ")
    if ("\n" in tidy) return tidy.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val sentences = Regex("(?<=[.!?][\"”’']?)\\s+(?=[\"“‘A-ZĄĆĘŁŃÓŚŹŻ0-9])").split(tidy.trim())
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    sentences.forEach { s ->
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(s)
        if (cur.length > 380) { out.add(cur.toString()); cur.clear() }
    }
    if (cur.isNotBlank()) out.add(cur.toString())
    return out
}

/** "the general field…" -> "The general field…" */
internal fun sentenceCase(text: String) = text.trim().replaceFirstChar { it.uppercase() }

internal fun storiesOf(section: Section): List<Item> = section.items.filter { it.type == "story" }

/** One-line state rows (market tape, Dalamud version): no link, no summary. */
internal fun isStatusLine(item: Item) = item.type == "story" && item.url == null && item.summary == null && item.body == null

internal fun readableStories(section: Section): List<Item> = storiesOf(section).filterNot(::isStatusLine)

/** The first source picture that is a photograph rather than a generated social card. */
internal fun photoOf(item: Item): String? = item.image?.takeIf { url ->
    url.isNotBlank() && listOf("og-image", "og_image", "social", "-card", "card-", "twitter", "share-image")
        .none { url.lowercase().contains(it) }
}

internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/* ---------- read tracking: which stories this phone has opened ---------- */

internal object ReadStore {
    private const val KEY = "read_items"

    fun key(date: String, item: Item) = "$date|${(item.url ?: item.headline.orEmpty()).hashCode()}"

    fun all(context: Context): Set<String> =
        context.getSharedPreferences("briefing", Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet()
            ?: emptySet()

    fun mark(context: Context, date: String, item: Item) {
        val prefs = context.getSharedPreferences("briefing", Context.MODE_PRIVATE)
        val set = all(context).toMutableSet()
        if (set.add(key(date, item))) {
            // keep roughly the last month of editions
            val trimmed = if (set.size > 1500) set.sortedDescending().take(1200).toSet() else set
            prefs.edit().putStringSet(KEY, trimmed).apply()
        }
    }
}

/* ---------- dates ---------- */

internal fun longDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))
}.getOrDefault(iso)

internal fun shortDay(iso: String): Pair<String, String> = runCatching {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.UK) to d.dayOfMonth.toString()
}.getOrDefault(iso to "")

/* ---------- small pieces ---------- */

private val tnum = TextStyle(fontFeatureSettings = "tnum")

/** A rounded label: neutral, or accented when [on]. */
@Composable
internal fun Chip(label: String, on: Boolean = false, warn: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        label,
        color = when { warn -> Amber; on -> Accent; else -> Body },
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        style = tnum,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) AccentSoft else Panel2)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** Small uppercase label with a coloured dot, used as a kicker above headlines. */
@Composable
internal fun Kicker(text: String, color: Color, dot: Color = color, size: Float = 11f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(
            text.uppercase(),
            color = color,
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1
        )
    }
}

/** The sunrise mark from the launcher icon, drawn so it follows the theme. */
@Composable
internal fun SunriseMark(size: Dp = 30.dp) {
    val ink = Ink
    val accent = Accent
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        drawRoundRect(ink, cornerRadius = CornerRadius(s * 0.28f, s * 0.28f))
        val r = s * 0.29f
        val cy = s * 0.62f
        drawArc(accent, 180f, 180f, true, topLeft = Offset(s / 2 - r, cy - r), size = Size(r * 2, r * 2))
        drawRoundRect(accent, topLeft = Offset(s * 0.16f, cy + s * 0.05f), size = Size(s * 0.68f, s * 0.06f),
            cornerRadius = CornerRadius(s * 0.03f))
        drawRoundRect(accent.copy(alpha = 0.55f), topLeft = Offset(s * 0.28f, cy + s * 0.15f),
            size = Size(s * 0.44f, s * 0.05f), cornerRadius = CornerRadius(s * 0.03f))
    }
}

@Composable
internal fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Panel2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Wordmark on the left; search, saved and an overflow menu on the right. */
@Composable
internal fun TopBar(
    savedCount: Int,
    onSearch: () -> Unit,
    onSaved: () -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        SunriseMark(30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Briefing",
            color = Ink,
            fontSize = 23.sp,
            fontFamily = Serif,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(onSearch) { Icon(Icons.Filled.Search, "Search", tint = Ink, modifier = Modifier.size(21.dp)) }
            Box {
                RoundIcon(onSaved) { Icon(Icons.Filled.Star, "Saved", tint = if (savedCount > 0) Accent else Ink, modifier = Modifier.size(20.dp)) }
                if (savedCount > 0) {
                    Text(
                        "$savedCount",
                        color = AccentInk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Accent)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Box {
                RoundIcon({ menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Ink, modifier = Modifier.size(21.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Panel) {
                    DropdownMenuItem(
                        text = { Text(if (Palette.light) "Dark theme" else "Light theme", color = Ink) },
                        onClick = {
                            menu = false
                            Palette.light = !Palette.light
                            context.getSharedPreferences("briefing", Context.MODE_PRIVATE)
                                .edit().putBoolean("light_theme", Palette.light).apply()
                        }
                    )
                    if (canPinWidget(context)) {
                        DropdownMenuItem(text = { Text("Add home-screen widget", color = Ink) },
                            onClick = { menu = false; pinWidget(context) })
                    }
                    DropdownMenuItem(
                        text = { Text("Share the briefing", color = Ink) },
                        onClick = {
                            menu = false
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Today's briefing — $SITE_BASE")
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "Share the briefing")) }
                        }
                    )
                    DropdownMenuItem(text = { Text("Reload", color = Ink) }, onClick = { menu = false; onReload() })
                    DropdownMenuItem(text = { Text("Open the website", color = Ink) },
                        onClick = { menu = false; openUrl(context, SITE_BASE + "app/") })
                }
            }
        }
    }
}

/** A thin progress line in the section's colour. */
@Composable
internal fun ReadBar(fraction: Float, hue: Color, height: Dp = 3.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(Line)) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(hue)
            )
        }
    }
}
