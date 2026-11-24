package dev.lukat.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Standings and leaders: plain tables, laid out for a phone and two-up on the unfolded screen. */

/** The tabs a section offers: its own view first ("Results", "News"…), then one per kind of table. */
internal fun tabsOf(section: Section, first: String): List<String> =
    listOf(first) + section.tables.map { it.tab }.distinct()

/** Pill tabs under the category strip, in the section's colour. */
@Composable
internal fun SectionTabs(tabs: List<String>, selected: Int, hue: Color, onSelect: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(tabs) { i, t ->
            val on = i == selected
            Text(
                t, color = if (on) Ink else Dim, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) hue.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
internal fun TablesBoard(tables: List<StatTable>, hue: Color) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(340.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        items(tables, key = { it.tab + it.title }) { StatTableCard(it, hue) }
    }
}

@Composable
private fun StatTableCard(table: StatTable, hue: Color) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        Kicker(table.title, hue)
        Spacer(Modifier.height(10.dp))
        TableRow(table, table.columns, header = true, lead = false)
        table.rows.forEachIndexed { i, row ->
            val cut = i in table.cuts
            Box(Modifier.fillMaxWidth().height(if (cut) 2.dp else 1.dp).background(if (cut) hue.copy(alpha = 0.55f) else Line))
            TableRow(table, row, header = false, lead = i == 0)
        }
    }
}

@Composable
private fun TableRow(table: StatTable, cells: List<String>, header: Boolean, lead: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = if (header) 4.dp else 7.dp)) {
        cells.forEachIndexed { c, text ->
            val right = table.align.getOrNull(c) == 'r'
            Cell(
                text = if (header) text.uppercase() else text,
                right = right,
                kind = when {
                    c == 0 -> CellKind.Rank
                    c == 1 -> CellKind.Name
                    right -> CellKind.Number
                    else -> CellKind.Detail
                },
                header = header,
                lead = lead,
            )
        }
    }
}

private enum class CellKind { Rank, Name, Detail, Number }

@Composable
private fun RowScope.Cell(text: String, right: Boolean, kind: CellKind, header: Boolean, lead: Boolean) {
    val modifier = when (kind) {
        CellKind.Rank -> Modifier.width(28.dp)
        CellKind.Name -> Modifier.weight(1.5f)
        CellKind.Detail -> Modifier.weight(1f).padding(start = 8.dp)
        CellKind.Number -> Modifier.width(50.dp)
    }
    Text(
        text,
        color = when {
            header -> Faint
            kind == CellKind.Name || kind == CellKind.Number && lead -> Ink
            kind == CellKind.Number -> Body
            else -> Dim
        },
        fontSize = if (header) 10.sp else if (kind == CellKind.Name) 14.sp else 13.sp,
        fontWeight = when {
            header -> FontWeight.Bold
            lead && (kind == CellKind.Name || kind == CellKind.Number) -> FontWeight.Bold
            kind == CellKind.Number -> FontWeight.SemiBold
            else -> FontWeight.Normal
        },
        letterSpacing = if (header) 0.8.sp else 0.sp,
        style = TextStyle(fontFeatureSettings = "tnum"),
        textAlign = if (right) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
