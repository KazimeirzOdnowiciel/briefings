package dev.lukat.briefing

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.action.clickable
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Morning edition, mirrored from Theme.kt: Glance resolves these per ui mode itself, so it
// cannot read the Compose state getters and keeps its own file-local day/night pairs.
private val WInk = ColorProvider(Color(0xFF1C1A16), Color(0xFFF3ECE0))
private val WDim = ColorProvider(Color(0xFF7A7468), Color(0xFF9A9284))
private val WAccent = ColorProvider(Color(0xFFC4501B), Color(0xFFFF8F4A))
private val WAmber = ColorProvider(Color(0xFFB7791F), Color(0xFFF5A524))
private val WFaint = ColorProvider(Color(0xFF9A9285), Color(0xFF6F685C))
private val WPanel = ColorProvider(Color(0xFFF5F1E8), Color(0xFF1A1815))
private val WBody = ColorProvider(Color(0xFF3D3933), Color(0xFFD6CEC1))
private val WOnAccent = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1A16))

/** Home-screen widget: the day, three headlines, and what you are training. */
class BriefingWidget : GlanceAppWidget() {

    /** Tell the launcher which sizes exist, so each gets its own layout and slot. */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 60.dp),    // 2x1 — kicker + headline
            DpSize(180.dp, 110.dp),   // 2x2
            DpSize(250.dp, 110.dp),   // 4x2 — the full card
            DpSize(250.dp, 220.dp),   // 4x4
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val feed = FeedStore.cached(context)
        provideContent { Content(feed, context) }
    }

    @Composable
    private fun Content(feed: Feed?, context: Context) {
        val size = LocalSize.current
        val compact = size.width < 200.dp || size.height < 100.dp
        val unseen = feed != null && !Seen.isSeen(context, feed.date)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WPanel)
                .cornerRadius(22.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            if (feed == null) {
                Text("Open Briefing once to load today's edition", style = TextStyle(color = WDim, fontSize = 13.sp))
                return@Column
            }

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    widgetDate(feed.date),
                    style = TextStyle(color = WAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (unseen) {
                    Text(
                        "NEW",
                        style = TextStyle(color = WOnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.background(WAccent).cornerRadius(8.dp).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                feed.title ?: "Briefing",
                style = TextStyle(color = WInk, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Serif),
                maxLines = 3
            )
            if (compact) return@Column

            // one line per section, as many as the widget is tall enough for
            val room = ((size.height.value - 118f) / 24f).toInt().coerceIn(1, 8)
            Spacer(GlanceModifier.height(10.dp))
            leads(feed).take(room).forEach { (emoji, headline) ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, style = TextStyle(fontSize = 12.sp), modifier = GlanceModifier.padding(end = 8.dp))
                    Text(headline, style = TextStyle(color = WBody, fontSize = 13.sp), maxLines = 1)
                }
            }

            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    feed.workout?.let { compactWorkout(it) } ?: "",
                    style = TextStyle(color = WAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                feed.readMinutes?.let {
                    Text("$it min read", style = TextStyle(color = WFaint, fontSize = 11.sp))
                }
            }
        }
    }

    /** The lead story of each news section, skipping one that only repeats the edition title. */
    private fun leads(feed: Feed): List<Pair<String, String>> =
        feed.sections
            .filter { kindOf(it) == Kind.News }
            .mapNotNull { section ->
                readableStories(section).firstOrNull { !it.headline.isNullOrBlank() }
                    ?.let { emojiOf(section.name) to it.headline!! }
            }
            .filterNot { (_, h) -> feed.title?.let { repeats(h, it) } ?: false }

    /** "Iran says three tankers caught fire…" is the title again in other words: most of its words are in it. */
    private fun repeats(headline: String, title: String): Boolean {
        fun words(s: String) = Regex("[\\p{L}\\p{N}]{4,}").findAll(s.lowercase()).map { it.value.take(5) }.toSet()
        val h = words(headline)
        return h.isNotEmpty() && h.count { it in words(title) } * 2 >= h.size
    }

    /** "2026-11-24" -> "TUESDAY 24 NOV" */
    private fun widgetDate(iso: String): String = runCatching {
        java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.ENGLISH)).uppercase()
    }.getOrDefault(iso)
}

class BriefingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BriefingWidget()
}
