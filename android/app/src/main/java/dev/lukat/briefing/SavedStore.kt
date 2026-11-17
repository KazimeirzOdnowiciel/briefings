package dev.lukat.briefing

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A story kept for later, with everything needed to re-read it offline. */
@Serializable
data class Saved(
    val url: String = "",
    val headline: String = "",
    val edition: String = "",
    val section: String = "",
    val source: String? = null,
    val summary: String? = null,
    val matters: String? = null,
    val body: String? = null,
    val savedAt: Long = 0L,
)

/**
 * Save-for-later, one JSON file in the app's private storage.
 *
 * Same judgement as the edition cache and the search index: a list this small does not
 * need a database, and a file means a saved story survives an app update with no migration.
 */
object SavedStore {
    private const val FILE = "saved.json"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun all(context: Context): List<Saved> =
        runCatching {
            val f = file(context)
            if (!f.isFile) emptyList()
            else feedJson.decodeFromString<List<Saved>>(f.readText()).sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())

    fun isSaved(context: Context, url: String): Boolean = all(context).any { it.url == url }

    private fun write(context: Context, items: List<Saved>) {
        runCatching {
            file(context).writeText(feedJson.encodeToString(ListSerializer(Saved.serializer()), items))
        }
    }

    /** Returns true when the story ends up saved. */
    fun toggle(context: Context, item: Item, edition: String, section: String): Boolean {
        val url = item.url ?: return false
        val current = all(context)
        return if (current.any { it.url == url }) {
            write(context, current.filterNot { it.url == url })
            false
        } else {
            val entry = Saved(
                url = url,
                headline = item.headline.orEmpty(),
                edition = edition,
                section = section,
                source = item.source,
                summary = item.summary,
                matters = item.matters,
                body = item.body,
                savedAt = System.currentTimeMillis(),
            )
            write(context, current + entry)
            true
        }
    }

    fun remove(context: Context, url: String) {
        write(context, all(context).filterNot { it.url == url })
    }

    fun asItem(saved: Saved): Item = Item(
        type = "story",
        headline = saved.headline,
        summary = saved.summary,
        matters = saved.matters,
        url = saved.url,
        source = saved.source,
        body = saved.body,
    )
}
