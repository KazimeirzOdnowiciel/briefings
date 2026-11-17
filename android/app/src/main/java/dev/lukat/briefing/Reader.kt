package dev.lukat.briefing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 40.dp)
    ) {
        item {
            item.image?.takeIf { it.isNotBlank() }?.let { url ->
                StoryImage(url, item.url, thumb = false)
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "← BACK",
                    color = Accent,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Panel)
                        .clickable { onClose() }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    (item.source ?: "") + if (item.paywall) " · paywall" else "",
                    color = Faint,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "A+",
                    color = Dim,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Panel)
                        .clickable {
                            textStep = (textStep + 1) % 3
                            prefs.edit().putInt("reader_text", textStep).apply()
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                item.headline ?: "",
                color = Ink,
                fontSize = 25.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold
            )
            item.matters?.let {
                Spacer(Modifier.height(10.dp))
                Text("Why it matters: $it", color = Accent, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        item {
            Text(
                item.body ?: item.summary.orEmpty(),
                color = Body,
                fontSize = bodySize,
                lineHeight = 27.sp,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("OPEN ORIGINAL →", openOriginal)
                Pill(if (saved) "★ SAVED" else "☆ SAVE") {
                    saved = SavedStore.toggle(context, item, edition, section)
                }
                Pill("SHARE", {
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
                })
            }
            if (previous != null || next != null) {
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    previous?.let { p ->
                        Pill("← " + (p.headline ?: "PREVIOUS").take(22).uppercase()) { onNavigate(p) }
                    }
                    next?.let { n ->
                        Pill((n.headline ?: "NEXT").take(22).uppercase() + " →") { onNavigate(n) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Text extracted by the briefing pipeline — the full article may differ.",
                color = Faint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
