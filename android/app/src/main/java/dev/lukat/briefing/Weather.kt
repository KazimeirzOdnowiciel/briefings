package dev.lukat.briefing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/** Warsaw right now plus today's range. Live, so it is still true when the briefing is read at night. */
data class Weather(val icon: String, val now: Int, val high: Int, val low: Int, val rain: Int) {
    /** "☁️ 3° · 1–5° · 40% rain" — rain only when it is likely enough to matter. */
    val line: String
        get() = "$icon $now° · $low–$high°" + if (rain >= 30) " · $rain% rain" else ""
}

private val weatherHttp = OkHttpClient()

/** Open-Meteo needs no key. Null when offline; the masthead simply leaves the chip out. */
suspend fun fetchWeather(lat: Double = 52.23, lon: Double = 21.01): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=Europe%2FWarsaw&forecast_days=1"
        val request = Request.Builder().url(url).header("User-Agent", "briefing-app").build()
        weatherHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = feedJson.parseToJsonElement(response.body?.string() ?: return@use null) as JsonObject
            val current = root["current"]!!.jsonObject
            val daily = root["daily"]!!.jsonObject
            fun first(key: String) = daily[key]!!.jsonArray.first().jsonPrimitive
            Weather(
                icon = weatherIcon(current["weather_code"]!!.jsonPrimitive.int),
                now = current["temperature_2m"]!!.jsonPrimitive.double.roundToInt(),
                high = first("temperature_2m_max").double.roundToInt(),
                low = first("temperature_2m_min").double.roundToInt(),
                rain = runCatching { first("precipitation_probability_max").int }.getOrDefault(0),
            )
        }
    }.getOrNull()
}

/** WMO weather codes, as Open-Meteo reports them. */
internal fun weatherIcon(code: Int): String = when {
    code == 0 -> "☀️"
    code in 1..2 -> "🌤️"
    code == 3 -> "☁️"
    code in 45..48 -> "🌫️"
    code in 51..67 || code in 80..82 -> "🌦️"
    code in 71..77 || code in 85..86 -> "🌨️"
    code >= 95 -> "⛈️"
    else -> "🌡️"
}
