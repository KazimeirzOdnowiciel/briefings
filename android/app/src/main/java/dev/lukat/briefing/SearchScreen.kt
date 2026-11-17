package dev.lukat.briefing

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
                .padding(start = 20.dp, end = 20.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(
                if (busy) "SEARCHING…" else "${hits.size} HITS",
                color = Faint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search every stored edition", color = Faint, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                cursorColor = Accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(hits) { hit ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(hit.item) }
                        .padding(vertical = 10.dp, horizontal = 12.dp)
                ) {
                    Text(
                        "${hit.date} · ${hit.section}",
                        color = Faint,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        hit.item.headline ?: hit.item.text ?: "",
                        color = Ink,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    (hit.item.matters ?: hit.item.summary)?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, color = Dim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}
