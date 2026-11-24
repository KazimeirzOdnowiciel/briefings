package dev.lukat.briefing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val CardShape = RoundedCornerShape(22.dp)

/**
 * Today: the edition as a set of doors, not a scroll. A masthead, a three-tab catch-up
 * card, then one card per category; tapping a card opens that category's story deck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    feed: Feed,
    wide: Boolean,
    offline: Boolean,
    dates: List<String>,
    selected: String,
    read: Set<String>,
    savedCount: Int,
    updateLabel: String?,
    updateError: String?,
    onUpdate: () -> Unit,
    onSelectDate: (String) -> Unit,
    onLatest: () -> Unit,
    onCategory: (String) -> Unit,
    onSearch: () -> Unit,
    onSaved: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val news = remember(feed) { feed.sections.filter { kindOf(it) == Kind.News } }
    val sport = remember(feed) { feed.sections.filter { kindOf(it) == Kind.Sport } }
    val digest = remember(feed) { feed.sections.filter { kindOf(it) == Kind.Digest } }
    val longRead = remember(feed) { feed.sections.firstOrNull { kindOf(it) == Kind.LongRead } }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    // the masthead's photo is not repeated on the category card right below it
    val hero = remember(feed) { heroPhoto(news) }
    // the market tape from Money, as figures across the top rather than a card nobody opens
    val tape = remember(feed) {
        news.flatMap { storiesOf(it).filter(::isStatusLine) }.mapNotNull { it.headline }
            .filter { "WIG20" in it || "USD/PLN" in it }
    }
    // live weather belongs to today's edition, not to last week's or a stale offline copy
    val latest = feed.date == java.time.LocalDate.now().toString()
    val weather by produceState<Weather?>(null, latest, refreshing) {
        if (latest && !refreshing) value = fetchWeather() ?: value
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
      BoxWithConstraints {
        // 2 columns on a phone, 3 on a small tablet or split screen, 4 on the unfolded inner screen
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        val sideBySide = columns >= 4 && digest.isNotEmpty()
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = full) { TopBar(savedCount, onSearch, onSaved, onLatest) }
            if (dates.size > 1) item(span = full) { DateStrip(dates, selected, onSelectDate, onLatest) }
            if (updateLabel != null) item(span = full) { UpdateCard(updateLabel, updateError, onUpdate) }
            // unfolded: masthead and catch-up side by side instead of two 840dp-wide slabs
            val half: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(if (sideBySide) maxLineSpan / 2 else maxLineSpan) }
            item(span = half) { Masthead(feed, hero, offline, wide, news, read, weather.takeIf { latest }?.line) }
            if (digest.isNotEmpty()) item(span = half) { CatchUp(digest, feed.title) }
            if (tape.isNotEmpty()) item(span = full) { Ticker(tape, sectionHue("Money"), edge = 0.dp) }

            label("Sections", "${news.size}")
            // the first card widens just enough that every row is full: no card ever sits alone
            val lead = leadSpan(news.size, columns)
            itemsIndexed(
                news,
                key = { _, it -> "n-" + it.name },
                span = { i, _ -> GridItemSpan(if (i == 0) lead else 1) }
            ) { i, section ->
                CategoryCard(section, feed.date, read, exclude = hero, featured = i == 0 && lead == columns) { onCategory(section.name) }
            }

            if (sport.isNotEmpty()) {
                label("Sport", null)
                val spans = rowSpans(sport.size, columns, if (columns >= 3) columns / 2 else columns)
                itemsIndexed(sport, key = { _, it -> "s-" + it.name }, span = { i, _ -> GridItemSpan(spans[i]) }) { _, section ->
                    SportCard(section) { onCategory(section.name) }
                }
            }
            if (longRead != null && readableStories(longRead).isNotEmpty()) {
                item(span = full) { LongReadCard(longRead) { onCategory(longRead.name) } }
            }
            item(span = full) {
                Text(
                    "Written each morning by the briefing pipeline. Pull down to refresh.",
                    color = Faint,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }
      }
    }
}

internal fun heroPhoto(news: List<Section>): String? =
    news.asSequence().flatMap { readableStories(it).asSequence() }.mapNotNull(::photoOf).firstOrNull()

/** Span of the first card so the remaining ones fill whole rows: 9 cards in 2 columns -> 2 (a full-width lead). */
internal fun leadSpan(n: Int, columns: Int): Int {
    val r = n % columns
    return if (r == 0 || n == 0) 1 else columns - r + 1
}

/** Spans for [n] cards that prefer [preferred] columns each; the last row stretches to fill. */
internal fun rowSpans(n: Int, columns: Int, preferred: Int): List<Int> {
    val p = preferred.coerceIn(1, columns)
    val perRow = columns / p
    val full = n - n % perRow
    return List(n) { i ->
        if (i < full) p else {
            val r = n - full
            val base = columns / r
            if (i == n - 1) columns - base * (r - 1) else base
        }
    }
}

private fun LazyGridScope.label(text: String, count: String?) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)) {
            Text(
                text,
                color = Ink,
                fontSize = 21.sp,
                fontFamily = Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.weight(1f)
            )
            if (count != null) Text(count, color = Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Latest plus one chip per stored edition; an amber dot marks editions never opened. */
@Composable
private fun DateStrip(dates: List<String>, selected: String, onSelect: (String) -> Unit, onLatest: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seen = remember(dates, selected) { Seen.dates(context) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates, key = { it }) { date ->
            val (dow, day) = shortDay(date)
            val newest = date == dates.first()
            DayChip(if (newest) "Latest" else dow, day, date == selected, !seen.contains(date)) {
                if (newest) onLatest() else onSelect(date)
            }
        }
    }
}

@Composable
private fun DayChip(top: String, day: String, active: Boolean, unseen: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Ink else Panel)
            .border(1.dp, if (active) Ink else Line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(top.uppercase(), color = if (active) Bg else Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (day.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day, color = if (active) Bg else Ink, fontSize = 17.sp, fontFamily = Serif, fontWeight = FontWeight.SemiBold)
                if (unseen) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Amber))
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(label: String, error: String?, onUpdate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AccentSoft)
            .clickable { onUpdate() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("New version of the app", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(error ?: "Tap to download and install $label", color = if (error != null) Amber else Body, fontSize = 12.5.sp)
        }
        Text("UPDATE", color = AccentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Accent).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Masthead(feed: Feed, hero: String?, offline: Boolean, wide: Boolean, news: List<Section>, read: Set<String>, weather: String?) {
    val total = remember(feed) { news.sumOf { readableStories(it).size } }
    val done = news.sumOf { s -> readableStories(s).count { read.contains(ReadStore.key(feed.date, it)) } }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Panel)
            .border(1.dp, Line, CardShape)
    ) {
        if (hero != null) {
            AsyncImage(
                model = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(if (wide) 220.dp else 190.dp).background(Panel2)
            )
        }
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
            Kicker(longDate(feed.date) + " · Warsaw", Accent)
            Spacer(Modifier.height(10.dp))
            Text(
                feed.title ?: "Daily Briefing",
                color = Ink,
                // the edition title is a full sentence; a long one steps down a size instead of running to six lines
                fontSize = if ((feed.title?.length ?: 0) > 110) (if (wide) 24.sp else 22.sp) else if (wide) 27.sp else 25.sp,
                lineHeight = if ((feed.title?.length ?: 0) > 110) (if (wide) 29.sp else 27.sp) else if (wide) 32.sp else 30.sp,
                fontFamily = Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                weather?.let { Chip(it) }
                feed.workout?.let { Chip(compactWorkout(it), on = it.startsWith("💪")) }
                if (offline) Chip("Offline copy", warn = true)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done == 0) "$total stories" + (feed.readMinutes?.let { " · $it min" } ?: "")
                    else "$done of $total read",
                    color = Dim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(118.dp)
                )
                Box(Modifier.weight(1f)) { ReadBar(if (total == 0) 0f else done / total.toFloat(), Accent, 4.dp) }
            }
        }
    }
}

/** TL;DR, What changed and Following as three tabs of one card. */
@Composable
private fun CatchUp(digest: List<Section>, title: String?) {
    var tab by remember(digest) { mutableIntStateOf(0) }
    val current = digest.getOrNull(tab) ?: digest.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Panel)
            .border(1.dp, Line, CardShape)
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            digest.forEachIndexed { i, s ->
                val label = when {
                    "tl" in s.name.lowercase() -> "In short"
                    "changed" in s.name.lowercase() -> "What changed"
                    else -> cleanName(s.name)
                }
                Chip(label, on = i == tab) { tab = i }
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(targetState = current, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            contentKey = { it.name }, label = "catchup") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the first TL;DR line is usually the masthead headline itself; don't print it twice
                s.items.filter { it.type == "story" && it.headline?.trimEnd('.') != title?.trimEnd('.') }.forEachIndexed { i, item ->
                    Row {
                        Text(
                            "${i + 1}",
                            color = Accent,
                            fontSize = 15.sp,
                            fontFamily = Serif,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.headline.orEmpty(),
                                color = Ink,
                                fontSize = 15.5.sp,
                                lineHeight = 21.sp,
                                fontFamily = Serif,
                                fontWeight = FontWeight.Medium
                            )
                            item.summary?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it.replaceFirstChar { c -> c.uppercase() }, color = Body, fontSize = 13.5.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    return scale
}

/** One door per category: picture (or the section's colour), name, lead headline, read progress. */
@Composable
private fun CategoryCard(section: Section, date: String, read: Set<String>, exclude: String?, featured: Boolean = false, onClick: () -> Unit) {
    val hue = sectionHue(section.name)
    val stories = remember(section) { readableStories(section) }
    val photo = remember(section, exclude) { stories.mapNotNull(::photoOf).firstOrNull { it != exclude } }
    val done = stories.count { read.contains(ReadStore.key(date, it)) }
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(Panel)
            .border(1.dp, Line, CardShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) { onClick() }
    ) {
        Box(Modifier.fillMaxWidth().height(if (featured) 170.dp else 96.dp)) {
            if (photo != null) {
                AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(hue.copy(alpha = 0.2f)))
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(hue.copy(alpha = 0.32f), hue.copy(alpha = 0.10f)))),
                    contentAlignment = Alignment.Center
                ) { Text(emojiOf(section.name), fontSize = 34.sp) }
            }
            // a colour band, not text, sits on top of the photograph
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(hue))
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emojiOf(section.name), fontSize = if (featured) 16.sp else 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    cleanName(section.name),
                    color = Ink,
                    fontSize = if (featured) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (done > 0) "$done/${stories.size}" else "${stories.size}",
                    color = if (done == stories.size && done > 0) hue else Faint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stories.firstOrNull()?.headline.orEmpty(),
                color = if (featured) Ink else Body,
                fontSize = if (featured) 19.sp else 13.5.sp,
                lineHeight = if (featured) 24.sp else 18.sp,
                fontFamily = Serif,
                fontWeight = if (featured) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = if (featured) 2 else 3,
                minLines = if (featured) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            ReadBar(if (stories.isEmpty()) 0f else done / stories.size.toFloat(), hue)
        }
    }
}

/** Sport preview: the first few results or fixtures, scoreboard style. */
@Composable
private fun SportCard(section: Section, onClick: () -> Unit) {
    val hue = sectionHue(section.name)
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val scores = section.items.filter { it.type == "score" }
    val lines = section.items.filter { it.type == "fixture" || it.type == "note" }.mapNotNull { it.text }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(Panel)
            .border(1.dp, Line, CardShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emojiOf(section.name), fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(cleanName(section.name), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (scores.isNotEmpty()) "${scores.size} results" else "${lines.size}",
                color = hue, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        val shown = if (scores.isNotEmpty()) 3 else 4
        if (scores.isNotEmpty()) {
            scores.take(shown).forEachIndexed { i, it ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                MiniScore(it)
            }
        } else {
            lines.take(shown).forEach { MiniFixture(it) }
        }
        val more = (if (scores.isNotEmpty()) scores.size else lines.size) - shown
        if (more > 0) {
            Spacer(Modifier.height(6.dp))
            Text("+$more more", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A result as two stacked rows, team then score, the winner in full ink: names never get cut to "Griz…". */
@Composable
private fun MiniScore(item: Item) {
    val hs = item.homeScore ?: 0
    val aws = item.awayScore ?: 0
    Column(Modifier.padding(vertical = 7.dp)) {
        TeamRow(item.home.orEmpty(), hs, hs > aws)
        Spacer(Modifier.height(2.dp))
        TeamRow(item.away.orEmpty(), aws, aws > hs)
    }
}

@Composable
private fun TeamRow(name: String, score: Int, won: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, color = if (won) Ink else Dim, fontSize = 14.sp,
            fontWeight = if (won) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$score", color = if (won) Ink else Dim, fontSize = 14.sp,
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

/** "Champions League — Arsenal v Dortmund, Como v AEK, 21:00": the time, the competition, the first match. */
@Composable
private fun MiniFixture(text: String) {
    val f = remember(text) { parseFixture(text) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            f.time ?: "—", color = if (f.poland) AccentInk else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(58.dp).clip(RoundedCornerShape(8.dp)).background(if (f.poland) Accent else Panel2)
                .padding(vertical = 5.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val label = listOfNotNull(f.label, f.day).joinToString(" · ")
            if (label.isNotEmpty()) {
                Text(label.uppercase(), color = Dim, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.matches.firstOrNull().orEmpty(),
                    color = if (f.poland) Ink else Body, fontSize = 13.5.sp, lineHeight = 18.sp,
                    fontWeight = if (f.poland) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                // the count of other matches at this kick-off survives the ellipsis
                if (f.matches.size > 1) {
                    Text(" +${f.matches.size - 1}", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

internal data class Fixture(val label: String?, val matches: List<String>, val time: String?, val day: String?, val poland: Boolean)

/** Splits a Tonight line into competition, one entry per match, the kick-off time and a "(Wednesday)" day note. */
internal fun parseFixture(text: String): Fixture {
    val label = text.substringBefore(" — ", "").trim().takeIf { it.isNotEmpty() && it.length < 40 }
    var body = if (label != null) text.substringAfter(" — ") else text
    val day = Regex("\\((Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\\)").find(body)?.groupValues?.get(1)
    body = body.replace(Regex("\\s*\\((Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\\)"), "")
    val time = Regex("\\b\\d{1,2}:\\d{2}\\b").find(body)?.value
    body = body.replace(Regex(",?\\s*\\b\\d{1,2}:\\d{2}\\b"), "").replace(Regex("\\s+"), " ").trim().trimEnd(',', ';')
    val parts = body.split(Regex(",\\s+|\\s+and\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    val matches = if (parts.size > 1 && parts.all { " v " in it || " vs " in it }) parts else listOf(body)
    val poland = Regex("poland|polska", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return Fixture(label, matches, time, day, poland)
}

@Composable
internal fun ScoreLine(item: Item, big: Boolean = false) {
    val hs = item.homeScore ?: 0
    val aws = item.awayScore ?: 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = if (big) 6.dp else 3.dp)) {
        Text(
            item.home.orEmpty(),
            color = if (hs > aws) Ink else Dim,
            fontWeight = if (hs > aws) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (big) 15.sp else 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$hs – $aws",
            maxLines = 1,
            softWrap = false,
            color = Ink,
            fontSize = if (big) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Panel2)
                .widthIn(min = if (big) 76.dp else 64.dp)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        )
        Text(
            item.away.orEmpty(),
            color = if (aws > hs) Ink else Dim,
            fontWeight = if (aws > hs) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (big) 15.sp else 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LongReadCard(section: Section, onClick: () -> Unit) {
    val item = readableStories(section).first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Ink)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Kicker("Long read" + (item.source?.let { " · $it" } ?: ""), Bg.copy(alpha = 0.7f), dot = Accent)
        Spacer(Modifier.height(10.dp))
        Text(item.headline.orEmpty(), color = Bg, fontSize = 21.sp, lineHeight = 26.sp, fontFamily = Serif, fontWeight = FontWeight.SemiBold)
        item.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.replaceFirstChar { c -> c.uppercase() }, color = Bg.copy(alpha = 0.75f), fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
