package dev.lukat.briefing

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Hit(val date: String, val section: String, val item: Item)

/**
 * Search across every edition stored on the phone.
 *
 * Deliberately a linear scan over the cached JSON rather than SQLite: thirty editions
 * is under a megabyte of text, so the scan is instant, and it costs no query layer,
 * no schema and no migration path. Room becomes worth it when the archive runs to
 * hundreds of editions — the search function below is the only thing that would change.
 */
object SearchIndex {
    suspend fun find(context: Context, query: String, limit: Int = 120): List<Hit> =
        withContext(Dispatchers.IO) {
            val needle = query.trim().lowercase()
            if (needle.length < 2) return@withContext emptyList()
            val hits = mutableListOf<Hit>()
            for (date in EditionStore.dates(context)) {
                val raw = EditionStore.load(context, date) ?: continue
                val feed = runCatching { parseFeed(raw) }.getOrNull() ?: continue
                for (section in feed.sections) {
                    for (item in section.items) {
                        val hay = listOfNotNull(
                            item.headline, item.summary, item.matters, item.body, item.home, item.away
                        ).joinToString(" ").lowercase()
                        if (hay.contains(needle)) {
                            hits += Hit(date, section.name, item)
                            if (hits.size >= limit) return@withContext hits
                        }
                    }
                }
            }
            hits
        }
}

@Composable
fun SearchScreen(onClose: () -> Unit, onOpen: (Item) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        busy = true
        hits = SearchIndex.find(context, query)
        busy = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchChip("← BACK") { onClose() }
            Spacer(Modifier.width(10.dp))
            Text(
                if (busy) "SEARCHING…" else "${hits.size} HITS",
                color = Dim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Text(
            "Search",
            color = Ink,
            fontFamily = Serif,
            fontSize = 28.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search every stored edition", color = Faint, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(999.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Panel2,
                unfocusedContainerColor = Panel2,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                cursorColor = Accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .height(52.dp)
        )

        when {
            query.trim().length < 2 -> {
                SearchEmpty("Type at least two letters — the whole archive on this phone is searched, not just today.")
            }
            hits.isEmpty() && !busy -> {
                SearchEmpty("Nothing in the archive matches “${query.trim()}”.")
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(18.dp)),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(hits, key = { index, hit -> "$index-${hit.date}" }) { index, hit ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(hit.item) }
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Text(
                                "${hit.date} · ${hit.item.source ?: hit.section}",
                                color = Dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                searchHighlight(hit.item.headline ?: hit.item.text ?: "", query),
                                color = Ink,
                                fontFamily = Serif,
                                fontSize = 16.5.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            (hit.item.matters ?: hit.item.summary)?.let {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    searchHighlight(it, query),
                                    color = Body,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 2
                                )
                            }
                        }
                        if (index < hits.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Line)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Matched runs get a warm highlight; everything else stays as it was. */
private fun searchHighlight(text: String, needle: String): AnnotatedString {
    val target = needle.trim()
    if (target.length < 2) return buildAnnotatedString { append(text) }
    val hay = text.lowercase()
    val find = target.lowercase()
    return buildAnnotatedString {
        var at = 0
        while (at < text.length) {
            val hit = hay.indexOf(find, at)
            if (hit < 0) {
                append(text.substring(at))
                break
            }
            if (hit > at) append(text.substring(at, hit))
            withStyle(SpanStyle(background = AccentSoft, color = Ink)) {
                append(text.substring(hit, hit + find.length))
            }
            at = hit + find.length
        }
    }
}

@Composable
private fun SearchEmpty(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Dim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Accent,
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
