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
}
