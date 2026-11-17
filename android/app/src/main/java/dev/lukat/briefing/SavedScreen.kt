package dev.lukat.briefing

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Everything kept for later, newest first. */
@Composable
fun SavedScreen(onClose: () -> Unit, onOpen: (Item) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(SavedStore.all(context)) }

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
                if (items.isEmpty()) "NOTHING SAVED" else "${items.size} SAVED",
                color = Faint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (items.isEmpty()) {
            Text(
                "Tap ☆ SAVE while reading a story and it lands here, readable offline.",
                color = Dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(items, key = { it.url }) { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(SavedStore.asItem(saved)) }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${saved.edition} · ${saved.section}",
                            color = Faint,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            saved.headline,
                            color = Ink,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        saved.matters?.let {
                            Spacer(Modifier.height(3.dp))
                            Text(it, color = Dim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                        }
                    }
                    Text(
                        "✕",
                        color = Faint,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                SavedStore.remove(context, saved.url)
                                items = SavedStore.all(context)
                            }
                            .padding(start = 12.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}
