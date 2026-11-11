package com.eric.pucklauncher.data

import org.json.JSONObject
import java.time.Duration
import java.time.Instant

object NhlApi {
    fun scoreUrl(isoDate: String) = "https://api-web.nhle.com/v1/score/$isoDate"

    fun parseScore(json: String): List<NhlLive> {
        val arr = JSONObject(json).optJSONArray("games") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val g = arr.getJSONObject(i)
            val a = g.optJSONObject("awayTeam") ?: return@mapNotNull null
            val h = g.optJSONObject("homeTeam") ?: return@mapNotNull null
            val pd = g.optJSONObject("periodDescriptor")
            val clock = g.optJSONObject("clock")
            NhlLive(
                gameState = g.optString("gameState"),
                awayAbbrev = a.optString("abbrev"),
                homeAbbrev = h.optString("abbrev"),
                awayScore = if (a.has("score")) a.optInt("score") else null,
                homeScore = if (h.has("score")) h.optInt("score") else null,
                period = pd?.optInt("number", 0)?.takeIf { it > 0 },
                periodType = pd?.optString("periodType")?.ifEmpty { null },
                clock = clock?.optString("timeRemaining")?.ifEmpty { null },
                inIntermission = clock?.optBoolean("inIntermission", false) == true,
                startUtc = Instant.parse(g.getString("startTimeUTC")),
            )
        }
    }

    /** Attach NHL API info to onhockey games by team abbreviations; closest start within 30h wins. */
    fun merge(games: List<Game>, api: List<NhlLive>): List<Game> = games.map { g ->
        val a = g.away?.abbrev ?: return@map g
        val h = g.home?.abbrev ?: return@map g
        val m = api.filter { it.awayAbbrev == a && it.homeAbbrev == h }
            .minByOrNull { Duration.between(it.startUtc, g.startUtc).abs() }
            ?.takeIf { Duration.between(it.startUtc, g.startUtc).abs() < Duration.ofHours(30) }
        if (m == null) g else g.copy(live = m, startUtc = m.startUtc)
    }
}
