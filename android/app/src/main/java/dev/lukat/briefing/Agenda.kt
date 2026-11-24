package dev.lukat.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/* The week ahead: "🔜 Coming up" as a calendar, not a list of stories. */

internal fun events(section: Section): List<Item> =
    section.items.filter { it.type == "event" && it.date != null }.sortedWith(compareBy({ it.date }, { it.time ?: "99:99" }))

/** Entries grouped under their day, in order. */
internal fun byDay(section: Section): List<Pair<String, List<Item>>> =
    events(section).groupBy { it.date!! }.toList()

/** "Tomorrow", "Thursday", or "Thu 3 Dec" once it is more than a week out — relative to the edition, not the clock. */
internal fun dayLabel(date: String, edition: String): Pair<String, String> = runCatching {
    val d = LocalDate.parse(date)
    val from = runCatching { LocalDate.parse(edition) }.getOrDefault(LocalDate.now())
    val days = ChronoUnit.DAYS.between(from, d)
    val top = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        in 2L..6L -> d.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
        else -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
    }
    top to d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
}.getOrDefault(date to "")

/** Home: the next few entries, one line each under a small day label. */
@Composable
internal fun AgendaCard(section: Section, edition: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hue = sectionHue(section.name)
    val days = remember(section) { byDay(section) }
    val total = days.sumOf { it.second.size }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emojiOf(section.name), fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(cleanName(section.name), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("next 7 days", color = hue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        var shown = 0
        days.forEach { (date, items) ->
            if (shown >= 6) return@forEach
            val (top, sub) = dayLabel(date, edition)
            Row(Modifier.padding(vertical = 5.dp)) {
                Column(Modifier.width(76.dp)) {
                    Text(top.uppercase(), color = hue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
                    Text(sub, color = Dim, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f)) {
                    items.take(6 - shown).forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                            Text(
                                e.text.orEmpty(), color = Ink, fontSize = 13.5.sp, lineHeight = 18.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                            )
                            e.time?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(it, color = Dim, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                            }
                        }
                        shown++
                    }
                }
            }
        }
        if (total > shown) {
            Spacer(Modifier.height(4.dp))
            Text("+${total - shown} more", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The category screen: a timeline, one block per day, each entry with its time, why it matters and its link. */
@Composable
internal fun AgendaBoard(section: Section, edition: String) {
    val context = LocalContext.current
    val hue = sectionHue(section.name)
    val days = remember(section) { byDay(section) }
    val notes = remember(section) { section.items.filter { it.type == "note" }.mapNotNull { it.text } }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        if (days.isEmpty()) {
            item { Text("Nothing dated for the week ahead.", color = Dim, modifier = Modifier.padding(24.dp)) }
        }
        items(days, key = { it.first }) { (date, items) ->
            val (top, sub) = dayLabel(date, edition)
            Row(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(20.dp))
            ) {
                // the day as a tab down the left edge, in the section colour
                Column(
                    Modifier.width(78.dp).fillMaxHeight().background(hue.copy(alpha = 0.12f)).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(top.uppercase().take(9), color = hue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(sub.substringBefore(" "), color = Ink, fontSize = 26.sp, fontFamily = Serif, fontWeight = FontWeight.SemiBold)
                    Text(sub.substringAfter(" ").uppercase(), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    items.forEachIndexed { i, e ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Line))
                        Column(Modifier.fillMaxWidth().clickable(enabled = e.url != null) { e.url?.let { openUrl(context, it) } }.padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    e.text.orEmpty(), color = Ink, fontSize = 15.sp, lineHeight = 20.sp,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                                )
                                e.time?.let {
                                    Spacer(Modifier.width(8.dp))
                                    TimeChip(it, hue)
                                }
                            }
                            e.note?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(sentenceCase(it), color = Body, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
        items(notes) { Text(it, color = Dim, fontSize = 13.sp, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) }
    }
}

@Composable
private fun TimeChip(time: String, hue: Color) {
    Text(
        time, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(hue.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
