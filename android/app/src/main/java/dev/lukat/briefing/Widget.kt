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
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WInk = ColorProvider(Color(0xFFE8EEF7))
private val WDim = ColorProvider(Color(0xFF8D9BAD))
private val WAccent = ColorProvider(Color(0xFF5EEAD4))
private val WAmber = ColorProvider(Color(0xFFFBBF24))
private val WFaint = ColorProvider(Color(0xFF5F7183))
private val WPanel = ColorProvider(Color(0xFF111823))

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
        val compact = size.width < 200.dp || size.height < 70.dp
        val unseen = feed != null && !Seen.isSeen(context, feed.date)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WPanel)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            if (feed == null) {
                Text(
                    "Open Briefing once to load today's edition",
                    style = TextStyle(color = WDim, fontSize = 13.sp)
                )
                return@Column
            }

            Text(
                "DAILY BRIEFING · ${feed.date}" + if (unseen) " · NEW" else "",
                style = TextStyle(
                    color = if (unseen) WAmber else WAccent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                feed.title ?: "Briefing",
                style = TextStyle(color = WInk, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = if (compact) 3 else 2
            )
            if (compact) return@Column
            Spacer(GlanceModifier.height(8.dp))

            headlines(feed).take(3).forEach { (section, headline) ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        section,
                        style = TextStyle(color = WFaint, fontSize = 10.sp),
                        modifier = GlanceModifier.padding(end = 6.dp)
                    )
                    Text(
                        headline,
                        style = TextStyle(color = WDim, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }

            feed.workout?.let {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    it.take(58),
                    style = TextStyle(color = WAccent, fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }

    /** Real stories only, in section order — skips TL;DR / what changed / following. */
    private fun headlines(feed: Feed): List<Pair<String, String>> =
        feed.sections
            .filter { it.kind == "stories" }
            .drop(3)
            .flatMap { section ->
                section.items
                    .filter { it.type == "story" && !it.headline.isNullOrBlank() }
                    .map { section.name.take(2) to (it.headline ?: "") }
            }
}

class BriefingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BriefingWidget()
}
