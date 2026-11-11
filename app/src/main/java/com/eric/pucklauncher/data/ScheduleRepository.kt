package com.eric.pucklauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class Schedule(val games: List<Game>, val fetchedAt: Instant, val nhlOk: Boolean)

class ScheduleRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
) {
    private val ua = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun fetchOnHockeyHtml(): String {
        val req = Request.Builder()
            .url(OnHockeyParser.BASE + "schedule_table.php?_=" + System.currentTimeMillis())
            .header("Referer", OnHockeyParser.BASE)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", ua)
            .build()
        client.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            val text = String(bytes, Charset.forName("windows-1251"))
            if (!r.isSuccessful) throw IOException("onhockey.tv answered HTTP ${r.code}")
            if (text.contains("not allowed to access this page")) throw IOException("onhockey.tv refused the request")
            return text
        }
    }

    private fun fetchNhl(isoDate: String): List<NhlLive> {
        val req = Request.Builder().url(NhlApi.scoreUrl(isoDate)).header("User-Agent", ua).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("NHL API HTTP ${r.code}")
            return NhlApi.parseScore(r.body!!.string())
        }
    }

    suspend fun load(now: Instant = Instant.now()): Schedule = withContext(Dispatchers.IO) {
        val games = OnHockeyParser.parse(fetchOnHockeyHtml(), now)
        if (games.isEmpty()) return@withContext Schedule(games, now, true)
        val et = ZoneId.of("America/New_York")
        val dates = (games.map { it.startUtc.atZone(et).toLocalDate() } + now.atZone(et).toLocalDate())
            .toSortedSet().map { it.toString() }
        try {
            val api = dates.flatMap { fetchNhl(it) }
            Schedule(NhlApi.merge(games, api), now, true)
        } catch (e: Exception) {
            Schedule(games, now, false) // onhockey data still shown
        }
    }
}
