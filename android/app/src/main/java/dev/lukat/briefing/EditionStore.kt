package dev.lukat.briefing

import android.content.Context
import java.io.File

/**
 * Editions kept on the phone, one JSON file per day.
 *
 * Deliberately not Room: the only query needed is "newest" and "by date", which the
 * filesystem already answers. Room earns its place when full-text search over the
 * archive arrives.
 */
object EditionStore {
    private const val DIR = "editions"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun save(context: Context, raw: String) {
        val feed = runCatching { parseFeed(raw) }.getOrNull() ?: return
        val name = feed.date.ifBlank { java.time.LocalDate.now().toString() }
        runCatching { File(dir(context), "$name.json").writeText(raw) }
    }

    fun dates(context: Context): List<String> =
        runCatching {
            dir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.map { it.nameWithoutExtension }
                ?.sortedDescending()
                ?.take(30)
                .orEmpty()
        }.getOrDefault(emptyList())

    fun load(context: Context, date: String): String? =
        runCatching { File(dir(context), "$date.json").takeIf { it.isFile }?.readText() }.getOrNull()

    fun latest(context: Context): String? =
        dates(context).firstOrNull()?.let { load(context, it) }

    fun size(context: Context): Int = dates(context).size

    /**
     * Fill gaps in the last [days] days from the site's `editions/<date>.json`, so a phone that
     * was off (or an app that wasn't opened) still has every edition for the date strip and search.
     * Returns how many editions were added. Missing days (404) are simply skipped.
     */
    suspend fun backfill(context: Context, newest: String, days: Int = 14): Int {
        val end = runCatching { java.time.LocalDate.parse(newest) }.getOrNull() ?: return 0
        val have = dates(context).toSet()
        val missing = (1..days).map { end.minusDays(it.toLong()).toString() }.filterNot { it in have }
        var added = 0
        for (date in missing) {
            val raw = runCatching { loadFeedRaw(SITE_BASE + "editions/$date.json") }.getOrNull() ?: continue
            if (runCatching { parseFeed(raw).date }.getOrNull() == date) {
                save(context, raw)
                added++
            }
        }
        return added
    }
}
