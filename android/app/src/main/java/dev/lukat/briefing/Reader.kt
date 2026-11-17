package dev.lukat.briefing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Extracted from MainActivity; body unchanged.
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
    val bodySize = when (textStep) { 0 -> 15.sp; 2 -> 18.5.sp; else -> 16.5.sp }
    val bodyLeading = when (textStep) { 0 -> 24.5.sp; 2 -> 28.sp; else -> 26.sp }
    var saved by remember(item.url) {
        mutableStateOf(item.url?.let { SavedStore.isSaved(context, it) } ?: false)
    }
    val hue = sectionHue(section)
    val openOriginal: () -> Unit = {
        item.url?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 44.dp)
    ) {
        item {
            item.image?.takeIf { it.isNotBlank() }?.let { url ->
                StoryImage(url, item.url, thumb = false)
                Spacer(Modifier.height(18.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderChip("← BACK", accent = true) { onClose() }
                Spacer(Modifier.weight(1f))
                if (item.paywall) {
                    Text(
                        "PAYWALL",
                        color = Faint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(Modifier.width(10.dp))
                }
                ReaderChip("A+") {
                    textStep = (textStep + 1) % 3
                    prefs.edit().putInt("reader_text", textStep).apply()
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(hue)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        item.source?.takeIf { it.isNotBlank() },
                        section.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").uppercase(),
                    color = Dim,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.headline ?: "",
                color = Ink,
                fontFamily = Serif,
                fontSize = 26.sp,
                lineHeight = 30.7.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            item.matters?.takeIf { it.isNotBlank() }?.let { matters ->
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel2)
                        .padding(14.dp)
                ) {
                    Text(
                        "WHY IT MATTERS",
                        color = hue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        matters,
                        color = Body,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        item {
            Text(
                item.body ?: item.summary.orEmpty(),
                color = Body,
                fontSize = bodySize,
                lineHeight = bodyLeading,
                fontFamily = Serif
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderAction(
                    "OPEN ORIGINAL →",
                    modifier = Modifier.weight(1f),
                    primary = true
                ) { openOriginal() }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderAction(
                    if (saved) "★ SAVED" else "☆ SAVE",
                    modifier = Modifier.weight(1f),
                    active = saved
                ) {
                    saved = SavedStore.toggle(context, item, edition, section)
                }
                ReaderAction("SHARE", modifier = Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${item.headline}\n\n${item.url ?: SITE_BASE}"
                        )
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Share this story"))
                    }
                }
            }
            if (previous != null || next != null) {
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    previous?.let { p ->
                        ReaderNeighbour(
                            "PREVIOUS",
                            p.headline ?: "Previous story",
                            Modifier.weight(1f)
                        ) { onNavigate(p) }
                    }
                    next?.let { n ->
                        ReaderNeighbour(
                            "NEXT",
                            n.headline ?: "Next story",
                            Modifier.weight(1f)
                        ) { onNavigate(n) }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Text extracted by the briefing pipeline — the full article may differ.",
                color = Faint,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/** Small pill for the reader's chrome: back, text size. */
@Composable
private fun ReaderChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (accent) Accent else Dim,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Panel2)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

/** Filled primary action, or a quiet secondary one; Save lights up when saved. */
@Composable
private fun ReaderAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val surface = when {
        primary -> Accent
        active -> AccentSoft
        else -> Panel2
    }
    val ink = when {
        primary -> AccentInk
        active -> Accent
        else -> Ink
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = ink,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1
        )
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
            .clip(RoundedCornerShape(14.dp))
            .background(Panel2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = Dim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(Modifier.height(5.dp))
        Text(
            headline,
            color = Ink,
            fontFamily = Serif,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}
