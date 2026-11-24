package dev.lukat.briefing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * One category at a time. A chip strip switches categories; news is a deck of story
 * cards you swipe through, sport is a scoreboard. Nothing here is a long feed.
 */
@Composable
internal fun CategoryScreen(
    feed: Feed,
    name: String,
    wide: Boolean,
    read: Set<String>,
    onSwitch: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (Item, String) -> Unit,
) {
    val browsable = remember(feed) { feed.sections.filter { kindOf(it) != Kind.Digest } }
    val section = browsable.firstOrNull { it.name == name } ?: browsable.firstOrNull() ?: return
    val index = browsable.indexOf(section)
    val hue = sectionHue(section.name)

    Column(Modifier.fillMaxSize()) {
        // header: back, position, name
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp)) {
            RoundIcon(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${index + 1} OF ${browsable.size} · ${longDate(feed.date).uppercase()}",
                    color = Dim, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1
                )
                AnimatedContent(
                    targetState = section.name,
                    transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally { it / 8 }) togetherWith fadeOut(tween(120)) },
                    label = "catTitle"
                ) { n ->
                    Text(
                        "${emojiOf(n)}  ${cleanName(n)}",
                        color = Ink, fontSize = 25.sp, fontFamily = Serif, fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CategoryStrip(browsable, section.name, onSwitch)
        Spacer(Modifier.height(14.dp))
        // sport sections carry standings and leaders: tabs beside the results
        val tabs = remember(section) {
            tabsOf(section, if (kindOf(section) == Kind.Sport) (if (section.kind == "fixtures") "Schedule" else "Results") else "News")
        }
        var tab by remember(section.name) { mutableIntStateOf(0) }
        if (tabs.size > 1) {
            SectionTabs(tabs, tab, hue) { tab = it }
            Spacer(Modifier.height(10.dp))
        }

        AnimatedContent(
            targetState = section to tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            contentKey = { (s, t) -> s.name + t },
            label = "catBody",
            modifier = Modifier.weight(1f)
        ) { (s, t) ->
            val shown = tabsOf(s, "").getOrNull(t)
            when {
                t > 0 && shown != null -> TablesBoard(s.tables.filter { it.tab == shown }, sectionHue(s.name))
                kindOf(s) == Kind.Sport -> SportBoard(s)
                kindOf(s) == Kind.Agenda -> AgendaBoard(s, feed.date)
                else -> StoryDeck(s, feed.date, wide, read, onOpen)
            }
        }
    }
}

@Composable
private fun CategoryStrip(sections: List<Section>, current: String, onSwitch: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val state = rememberLazyListState()
    val at = sections.indexOfFirst { it.name == current }.coerceAtLeast(0)
    LaunchedEffect(current) { state.animateScrollToItem((at - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(sections, key = { _, s -> s.name }) { _, s ->
            val on = s.name == current
            val hue = sectionHue(s.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) hue.copy(alpha = 0.18f) else Panel)
                    .border(1.dp, if (on) hue else Line, RoundedCornerShape(999.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSwitch(s.name)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(emojiOf(s.name), fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(cleanName(s.name), color = if (on) Ink else Body, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/* ---------------- news: a deck of cards ---------------- */

@Composable
private fun StoryDeck(section: Section, date: String, wide: Boolean, read: Set<String>, onOpen: (Item, String) -> Unit) {
    val context = LocalContext.current
    val hue = sectionHue(section.name)
    val status = remember(section) { storiesOf(section).filter(::isStatusLine) }
    val stories = remember(section) { readableStories(section) }
    val pager = rememberPagerState { stories.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        if (status.isNotEmpty()) {
            Ticker(status.mapNotNull { it.headline }, hue)
            Spacer(Modifier.height(12.dp))
        }
        if (stories.isEmpty()) {
            Text("Nothing in this section today.", color = Dim, modifier = Modifier.padding(24.dp))
            return@Column
        }
        HorizontalPager(
            state = pager,
            pageSize = if (wide) PageSize.Fixed(420.dp) else PageSize.Fill,
            contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f)
        ) { page ->
            val item = stories[page]
            StoryCard(
                item = item,
                hue = hue,
                section = section.name,
                isRead = read.contains(ReadStore.key(date, item)),
                next = stories.getOrNull(page + 1),
                onNext = { scope.launch { pager.animateScrollToPage(page + 1) } },
                onOpen = {
                    if (!item.body.isNullOrBlank()) onOpen(item, section.name)
                    else item.url?.let {
                        ReadStore.mark(context, date, item)
                        openUrl(context, it)
                    }
                },
                onOriginal = item.url?.let { url -> { ReadStore.mark(context, date, item); openUrl(context, url) } }
            )
        }
        // position: one segment per story, the current one stretched in the section colour
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp)
        ) {
            stories.forEachIndexed { i, item ->
                val active = i == pager.currentPage
                val w by animateDpAsState(if (active) 26.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "dot")
                val done = read.contains(ReadStore.key(date, item))
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(w)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) hue else if (done) hue.copy(alpha = 0.4f) else Line)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("${pager.currentPage + 1} / ${stories.size}", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"))
        }
    }
}

/** Market tape or a version line, broken into small figures instead of one dense sentence. */
@Composable
internal fun Ticker(lines: List<String>, hue: Color, edge: Dp = 16.dp) {
    val parts = remember(lines) { lines.flatMap { it.split(" · ") }.map { it.trim() }.filter { it.isNotEmpty() } }
    // a plain scrolling row, not a lazy one: it also sits inside an equal-height row on the unfolded home
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parts.forEach { p ->
            val m = Regex("^(.*?)\\s+(\\$?[\\d.,]+.*)$").find(p)
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (m != null) {
                    Text(m.groupValues[1].uppercase(), color = Dim, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    val value = m.groupValues[2]
                    val change = Regex("\\(([+\\-−][\\d.]+%)\\)").find(value)?.groupValues?.get(1)
                    Text(
                        buildAnnotatedString {
                            append(value.substringBefore(" (").trim())
                            if (change != null) {
                                append("  ")
                                withStyle(SpanStyle(color = if (change.startsWith("+")) Up else Down, fontSize = 11.sp)) { append(change) }
                            }
                        },
                        color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum")
                    )
                } else {
                    Text(p, color = Body, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * One story as a card. The picture grows with the screen; a story without one gets a poster:
 * its headline set large on the section's colour, so no card is a lonely emoji over dead space.
 * The foot of the card previews the next story, which is one tap (or a swipe) away.
 */
@Composable
private fun StoryCard(
    item: Item,
    hue: Color,
    section: String,
    isRead: Boolean,
    next: Item?,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onOriginal: (() -> Unit)?,
) {
    val photo = photoOf(item)
    val hasBody = !item.body.isNullOrBlank()
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .clickable { onOpen() }
        ) {
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                if (photo != null) {
                    Box(Modifier.fillMaxWidth().height((tall * 0.36f).coerceIn(170.dp, 340.dp))) {
                        AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Panel2))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(hue))
                    }
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                        CardKicker(item, hue, section, isRead)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            item.headline.orEmpty(),
                            color = Ink, fontSize = 23.sp, lineHeight = 28.sp, fontFamily = Serif,
                            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp
                        )
                    }
                } else {
                    Poster(item, hue, section, isRead, minHeight = (tall * 0.42f).coerceIn(200.dp, 380.dp))
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
                    item.summary?.let {
                        Text(sentenceCase(it), color = Body, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    item.matters?.let {
                        Spacer(Modifier.height(14.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel2)
                                .padding(14.dp)
                        ) {
                            Text("WHY IT MATTERS", color = hue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(sentenceCase(it), color = Body, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
            // more below: the text fades out instead of being sliced off mid-line
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Brush.verticalGradient(listOf(Panel.copy(alpha = 0f), Panel)))
                )
            }
            }
            next?.let { UpNext(it, hue, onNext) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 10.dp)
            ) {
                if (hasBody) {
                    ActionPill("Read here", primary = true, modifier = Modifier.weight(1f)) { onOpen() }
                    onOriginal?.let { ActionPill("Original ↗", primary = false, modifier = Modifier.weight(1f)) { it() } }
                } else if (onOriginal != null) {
                    ActionPill("Open article ↗", primary = true, modifier = Modifier.weight(1f)) { onOriginal() }
                }
            }
        }
    }
}

@Composable
private fun CardKicker(item: Item, hue: Color, section: String, isRead: Boolean, ink: Color = Dim) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Kicker(item.source ?: cleanName(section), ink, dot = hue) }
        if (item.day == 2) Badge("Day 2", hue)
        if (item.paywall) { Spacer(Modifier.width(6.dp)); Badge("Paywall", Amber) }
        if (isRead) { Spacer(Modifier.width(6.dp)); Badge("Read", Faint) }
    }
}

/** A story without a usable photograph: the headline is the picture. */
@Composable
private fun Poster(item: Item, hue: Color, section: String, isRead: Boolean, minHeight: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Brush.verticalGradient(listOf(hue.copy(alpha = 0.34f), hue.copy(alpha = 0.10f))))
    ) {
        // the section's emoji, oversized and faint, as a watermark in the corner
        Text(
            emojiOf(section), fontSize = 150.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-18).dp).rotate(-12f).alpha(0.16f)
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 22.dp)) {
            CardKicker(item, hue, section, isRead, ink = Ink.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            Text(
                item.headline.orEmpty(),
                color = Ink, fontSize = 29.sp, lineHeight = 34.sp, fontFamily = Serif,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp
            )
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(hue))
    }
}

/** The next story in the deck, one line, above the buttons. */
@Composable
private fun UpNext(item: Item, hue: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Line))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("NEXT", color = hue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                item.headline.orEmpty(), color = Body, fontSize = 14.sp, fontFamily = Serif,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("→", color = Dim, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text.uppercase(), color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionPill(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) Accent else Panel2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) AccentInk else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------- sport: a scoreboard ---------------- */

@Composable
private fun SportBoard(section: Section) {
    val hue = sectionHue(section.name)
    // group consecutive items under their league / competition label
    val groups = remember(section) {
        val out = mutableListOf<Pair<String?, MutableList<Item>>>()
        section.items.forEach { item ->
            val label = when (item.type) {
                "score" -> item.group
                "fixture" -> item.text?.substringBefore(" — ", "")?.takeIf { it.isNotBlank() && it.length < 40 }
                else -> null
            }
            if (out.isEmpty() || out.last().first != label) out.add(label to mutableListOf())
            out.last().second.add(item)
        }
        out
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(groups) { _, (label, items) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                if (label != null) {
                    Kicker(label, hue)
                    Spacer(Modifier.height(10.dp))
                }
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Line))
                    when (item.type) {
                        "score" -> {
                            ScoreLine(item, big = true)
                            Scorers(item)
                            val note = listOfNotNull(item.flag, item.note).joinToString(" · ")
                            if (note.isNotBlank()) {
                                Text(note, color = Dim, fontSize = 12.sp, lineHeight = 17.sp,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        "fixture" -> FixtureLine(item.text.orEmpty(), hue, hasLabel = label != null)
                        else -> Text(item.text.orEmpty(), color = Body, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

/** Goal scorers under each side, lined up with that side's name. */
@Composable
private fun Scorers(item: Item) {
    val home = item.homeScorers.orEmpty()
    val away = item.awayScorers.orEmpty()
    if (home.isEmpty() && away.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            home.forEach { Text(it, color = Dim, fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.End) }
        }
        // as wide as the score box between the two names (see ScoreLine)
        Spacer(Modifier.width(96.dp))
        Column(Modifier.weight(1f)) {
            away.forEach { Text(it, color = Dim, fontSize = 12.5.sp, lineHeight = 17.sp) }
        }
    }
}

/** "Nations League — Poland v Sweden, 20:45 (Wrocław)": kick-off times become chips. */
@Composable
internal fun FixtureLine(text: String, hue: Color, hasLabel: Boolean, compact: Boolean = false) {
    val body = if (hasLabel) text.substringAfter(" — ", text) else text
    val times = Regex("\\b\\d{1,2}:\\d{2}\\b").findAll(body).map { it.value }.toList()
    val parsed = parseFixture(body)
    // several matches at one kick-off read as a list, not one comma-separated sentence
    val clean = if (!compact && parsed.matches.size > 1) parsed.matches.joinToString("\n")
        else body.replace(Regex(",?\\s*\\b\\d{1,2}:\\d{2}\\b"), "").replace(Regex("\\s+"), " ").trim().trimEnd(',', ';')
    val poland = Regex("poland|polska", RegexOption.IGNORE_CASE).containsMatchIn(body)
    // a list of matches keeps its kick-off beside the first one, not floating mid-list
    Row(verticalAlignment = if ('\n' in clean) Alignment.Top else Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            clean, color = if (poland) Ink else Body, fontSize = if (compact) 13.5.sp else 14.5.sp, lineHeight = if (compact) 20.sp else 22.sp,
            fontWeight = if (poland) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f),
            maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis
        )
        times.distinct().forEach { t ->
            Spacer(Modifier.width(6.dp))
            Text(
                t, color = if (poland) AccentInk else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (poland) Accent else Panel2)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
