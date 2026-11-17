package dev.lukat.briefing

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
import androidx.compose.ui.text.style.TextAlign
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
                .padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SavedChip("← BACK") { onClose() }
            Spacer(Modifier.width(10.dp))
            Text(
                if (items.isEmpty()) "NOTHING SAVED" else "${items.size} SAVED",
                color = Dim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Text(
            "Saved",
            color = Ink,
            fontFamily = Serif,
            fontSize = 28.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )
        Text(
            "Kept on this phone, readable offline.",
            color = Dim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tap ☆ SAVE while reading a story and it lands here, readable offline.",
                    color = Dim,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(18.dp)),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(items, key = { index, saved -> saved.url.ifBlank { "saved-$index" } }) { index, saved ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(SavedStore.asItem(saved)) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${saved.edition} · ${saved.source ?: saved.section}",
                                color = Dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                saved.headline,
                                color = Ink,
                                fontFamily = Serif,
                                fontSize = 16.5.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            saved.matters?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    it,
                                    color = Body,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 2
                                )
                            }
                        }
                        Text(
                            "✕",
                            color = Faint,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable {
                                    SavedStore.remove(context, saved.url)
                                    items = SavedStore.all(context)
                                }
                                .padding(start = 14.dp, end = 2.dp)
                        )
                    }
                    if (index < items.lastIndex) {
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

@Composable
private fun SavedChip(label: String, onClick: () -> Unit) {
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
