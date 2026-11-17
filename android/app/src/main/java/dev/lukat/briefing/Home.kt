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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    val columns = if (wide) 4 else 2
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
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
            val half: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(if (wide && digest.isNotEmpty()) maxLineSpan / 2 else maxLineSpan) }
            item(span = half) { Masthead(feed, offline, wide, news, read) }
            if (digest.isNotEmpty()) item(span = half) { CatchUp(digest, feed.title) }

            label("Sections", "${news.size}")
            // with an odd number of categories the first becomes a full-width feature, so no card sits alone
            val featured = news.size % 2 == 1 || wide
            itemsIndexed(
                news,
                key = { _, it -> "n-" + it.name },
                span = { i, _ -> GridItemSpan(if (i == 0 && featured) maxLineSpan else 1) }
            ) { i, section ->
                CategoryCard(section, feed.date, read, featured = i == 0 && featured) { onCategory(section.name) }
            }

            if (sport.isNotEmpty()) {
                label("Sport", null)
                items(sport, key = { "s-" + it.name }, span = { GridItemSpan(if (wide) 2 else maxLineSpan) }) { section ->
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

@Composable
private fun Masthead(feed: Feed, offline: Boolean, wide: Boolean, news: List<Section>, read: Set<String>) {
    val hero = remember(feed) { news.asSequence().flatMap { readableStories(it).asSequence() }.mapNotNull(::photoOf).firstOrNull() }
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
                fontSize = if (wide) 27.sp else 25.sp,
                lineHeight = if (wide) 32.sp else 30.sp,
                fontFamily = Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
private fun CategoryCard(section: Section, date: String, read: Set<String>, featured: Boolean = false, onClick: () -> Unit) {
    val hue = sectionHue(section.name)
    val stories = remember(section) { readableStories(section) }
    val photo = remember(section) { stories.firstNotNullOfOrNull(::photoOf) }
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
        Spacer(Modifier.height(10.dp))
        if (scores.isNotEmpty()) {
            scores.take(3).forEach { ScoreLine(it) }
        } else {
            lines.take(3).forEach {
                Text(it, color = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
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
            color = Ink,
            fontSize = if (big) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Panel2)
                .padding(horizontal = 9.dp, vertical = 3.dp)
                .width(if (big) 58.dp else 50.dp)
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
            Text(it.replaceFirstChar { c -> c.uppercase() }, color = Bg.copy(alpha = 0.75f), fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3)
        }
    }
}
