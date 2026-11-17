package dev.lukat.briefing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the pipeline publishes the machine-readable edition. */
const val FEED_URL = "https://kazimeirzodnowiciel.github.io/briefings/feed.json"

@Serializable
data class Feed(
    val schema: Int = 1,
    val date: String = "",
    val greeting: String? = null,
    val title: String? = null,
    val workout: String? = null,
    val audio: String? = null,
    @SerialName("read_minutes") val readMinutes: Int? = null,
    val sections: List<Section> = emptyList(),
)

@Serializable
data class Section(
    val name: String = "",
    val kind: String = "stories",
    val items: List<Item> = emptyList(),
)

@Serializable
data class Item(
    val type: String = "story",
    val headline: String? = null,
    val summary: String? = null,
    val matters: String? = null,
    val url: String? = null,
    val source: String? = null,
    val image: String? = null,
    val body: String? = null,
    val day: Int? = null,
    val paywall: Boolean = false,
    val group: String? = null,
    val home: String? = null,
    val away: String? = null,
    @SerialName("home_score") val homeScore: Int? = null,
    @SerialName("away_score") val awayScore: Int? = null,
    val note: String? = null,
    val flag: String? = null,
    val text: String? = null,
)

private val http = OkHttpClient()

val feedJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Raw JSON, so the same bytes can be cached for the home-screen widget. */
suspend fun loadFeedRaw(url: String = FEED_URL): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "briefing-app/0.1")
        .build()
    http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        response.body?.string() ?: error("empty response")
    }
}

fun parseFeed(raw: String): Feed = feedJson.decodeFromString(raw)

/** Fetch the newest edition. Throws on network or parse failure; callers show the error. */
suspend fun loadFeed(url: String = FEED_URL): Feed = parseFeed(loadFeedRaw(url))

/** Tag of the newest published app release, e.g. "app-v0.6". Null when offline or none. */
suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/KazimeirzOdnowiciel/briefings/releases/latest")
            .header("User-Agent", "briefing-app")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return@use null
            val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"")
                .find(body)?.groupValues?.get(1)
            Release(tag, apk)
        }
    }.getOrNull()
}

data class Release(val tag: String, val apk: String?)

/** Kept for callers that only need the tag. */
suspend fun latestReleaseTag(): String? = latestRelease()?.tag
/** Which edition the reader has actually opened — drives the badge and the notification. */
object Seen {
    private const val PREFS = "briefing"
    private const val KEY = "seen_dates"

    fun mark(context: Context, date: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(date)) prefs.edit().putStringSet(KEY, set).apply()
    }

    fun dates(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun of(context: Context): String? = dates(context).maxOrNull()

    fun isSeen(context: Context, date: String): Boolean = dates(context).contains(date)
}

fun markSeen(context: Context, date: String) = Seen.mark(context, date)

object FeedStore {
    private const val PREFS = "briefing"
    private const val KEY = "last_feed"

    fun save(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, raw).apply()
    }

    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun cached(context: Context): Feed? =
        load(context)?.let { runCatching { parseFeed(it) }.getOrNull() }
}
