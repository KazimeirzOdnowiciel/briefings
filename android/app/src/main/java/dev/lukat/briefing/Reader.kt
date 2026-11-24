package dev.lukat.briefing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The longest comfortable line for body text; on the unfolded screen the column centres. */
private val Measure = 640.dp

/**
 * A story read in the app: picture, headline, standfirst, why it matters, then the extracted
 * text as real paragraphs. Save and share sit in the top bar so they are there without scrolling.
 */
@Composable
internal fun Reader(
    item: Item,
    edition: String,
    section: String,
    siblings: List<Item> = emptyList(),
    onNavigate: (Item) -> Unit = {},
    onClose: () -> Unit
) {
    val at = siblings.indexOfFirst { it.url == item.url }
    val previous = if (at > 0) siblings[at - 1] else null
    val next = if (at >= 0 && at < siblings.size - 1) siblings[at + 1] else null
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("briefing", android.content.Context.MODE_PRIVATE) }
    var textStep by remember { mutableIntStateOf(prefs.getInt("reader_text", 1)) }
    val bodySize = when (textStep) { 0 -> 15.5.sp; 2 -> 19.sp; else -> 17.sp }
    val bodyLeading = when (textStep) { 0 -> 25.sp; 2 -> 30.sp; else -> 27.sp }
    var saved by remember(item.url) {
        mutableStateOf(item.url?.let { SavedStore.isSaved(context, it) } ?: false)
    }
    val hue = sectionHue(section)
    val paragraphs = remember(item.url) { paragraphsOf(item.body ?: item.summary.orEmpty()) }
    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${item.headline}\n\n${item.url ?: SITE_BASE}")
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share this story")) }
    }
    val column = Modifier.widthIn(max = Measure).fillMaxWidth()
    val list = rememberLazyListState()
    // how far through the piece: whole items passed plus the share of the current one
    val progress by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            when {
                info.totalItemsCount == 0 || first == null -> 0f
                !list.canScrollForward -> 1f
                else -> ((first.index + list.firstVisibleItemScrollOffset / first.size.coerceAtLeast(1).toFloat())
                    / info.totalItemsCount).coerceIn(0f, 1f)
            }
        }
    }
    val minutes = remember(item.url) {
        val words = (item.body ?: item.summary.orEmpty()).split(Regex("\\s+")).size
        (words / 200f).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    }

    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // top bar: back on the left; text size, save and share on the right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
        ) {
            RoundIcon(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.weight(1f))
            RoundIcon({
                textStep = (textStep + 1) % 3
                prefs.edit().putInt("reader_text", textStep).apply()
            }) {
                Text("Aa", color = Ink, fontSize = when (textStep) { 0 -> 13.sp; 2 -> 18.sp; else -> 15.sp }, fontFamily = Serif, fontWeight = FontWeight.SemiBold)
            }
            RoundIcon({ saved = SavedStore.toggle(context, item, edition, section) }) {
                Icon(Icons.Filled.Star, if (saved) "Saved" else "Save", tint = if (saved) Accent else Dim, modifier = Modifier.size(20.dp))
            }
            RoundIcon(share) { Icon(Icons.Filled.Share, "Share", tint = Ink, modifier = Modifier.size(19.dp)) }
        }
        ReadBar(progress, hue, 2.dp)

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 44.dp)
        ) {
            item {
                Column(column) {
                    item.image?.takeIf { it.isNotBlank() }?.let { url ->
                        StoryImage(url, item.url, thumb = false)
                        Spacer(Modifier.height(20.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Kicker(
                                listOfNotNull(
                                    item.source?.takeIf { it.isNotBlank() }, cleanName(section).takeIf { it.isNotBlank() },
                                    "$minutes min read".takeIf { !item.body.isNullOrBlank() }
                                )
                                    .joinToString(" · "),
                                Dim, dot = hue
                            )
                        }
                        if (item.paywall) Text("PAYWALL", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.headline.orEmpty(),
                        color = Ink, fontFamily = Serif, fontSize = 28.sp, lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp
                    )
                    // the standfirst, unless the body is only the summary again
                    item.summary?.takeIf { !item.body.isNullOrBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(sentenceCase(it), color = Body, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = Serif)
                    }
                    item.matters?.takeIf { it.isNotBlank() }?.let { matters ->
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(Panel2)) {
                            Box(Modifier.width(4.dp).fillMaxHeight().background(hue))
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text("WHY IT MATTERS", color = hue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(sentenceCase(matters), color = Body, fontSize = 14.5.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(hue))
                    Spacer(Modifier.height(20.dp))
                }
            }
            items(paragraphs) { para ->
                Text(
                    para,
                    color = Body, fontSize = bodySize, lineHeight = bodyLeading, fontFamily = Serif,
                    modifier = column.padding(bottom = 16.dp)
                )
            }
            item {
                Column(column) {
                    Spacer(Modifier.height(12.dp))
                    item.url?.let { url ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Accent)
                                .clickable { openUrl(context, url) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Read the full article on ${item.source ?: "the site"} ↗", color = AccentInk, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (previous != null || next != null) {
                        Spacer(Modifier.height(22.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            previous?.let { p ->
                                ReaderNeighbour("← PREVIOUS", p.headline ?: "Previous story", Modifier.weight(1f)) { onNavigate(p) }
                            } ?: Spacer(Modifier.weight(1f))
                            next?.let { n ->
                                ReaderNeighbour("NEXT →", n.headline ?: "Next story", Modifier.weight(1f)) { onNavigate(n) }
                            } ?: Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "The opening of the article, extracted by the briefing pipeline. The full piece may differ.",
                        color = Faint, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/** The story either side of this one, as a card with a kicker. */
@Composable
private fun ReaderNeighbour(
    label: String,
    headline: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, color = Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            headline, color = Ink, fontFamily = Serif, fontSize = 15.sp, lineHeight = 19.sp,
            fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis
        )
    }
}
