package com.eric.pucklauncher.data

import java.time.Duration
import java.time.Instant

/** One clickable stream link. [url] is the absolute onhockey page (or direct link); [embedUrl] is the raw player URL if known. */
data class Stream(
    val group: String,
    val host: String,
    val channel: String,
    val url: String,
    val embedUrl: String?,
)

data class FeedGroup(val label: String, val streams: List<Stream>)

/** Live/final info from the NHL API. */
data class NhlLive(
    val gameState: String,
    val awayAbbrev: String,
    val homeAbbrev: String,
    val awayScore: Int?,
    val homeScore: Int?,
    val period: Int?,
    val periodType: String?,
    val clock: String?,
    val inIntermission: Boolean,
    val startUtc: Instant,
)

data class Game(
    val id: String,
    val awayRaw: String,
    val homeRaw: String,
    val away: Teams.Team?,
    val home: Teams.Team?,
    val startUtc: Instant,
    val groups: List<FeedGroup>,
    val note: String?,
    val live: NhlLive? = null,
) {
    val streamCount: Int get() = groups.sumOf { it.streams.size }
    val awayCity: String get() = away?.city ?: awayRaw
    val homeCity: String get() = home?.city ?: homeRaw
}

enum class Phase { LIVE, STARTED, UPCOMING, FINAL }

fun Game.phase(now: Instant): Phase {
    live?.let {
        return when (it.gameState) {
            "LIVE", "CRIT" -> Phase.LIVE
            "FINAL", "OFF" -> Phase.FINAL
            else -> if (now.isBefore(startUtc)) Phase.UPCOMING else Phase.STARTED
        }
    }
    return when {
        now.isBefore(startUtc) -> Phase.UPCOMING
        now.isBefore(startUtc.plus(Duration.ofHours(3))) -> Phase.STARTED
        else -> Phase.FINAL
    }
}

/** Live games first, then started, upcoming by time, finals last. */
fun sortGames(games: List<Game>, now: Instant): List<Game> =
    games.sortedWith(compareBy<Game>({ it.phase(now).ordinal }, { it.startUtc }, { it.homeCity }))
