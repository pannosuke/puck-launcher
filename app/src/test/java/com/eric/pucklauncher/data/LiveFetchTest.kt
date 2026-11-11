package com.eric.pucklauncher.data

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits the real sites. Run with LIVE_CHECK=1 ./gradlew testDebugUnitTest --tests '*LiveFetchTest*' */
class LiveFetchTest {
    @Test fun liveFetchAndParse() {
        assumeTrue(System.getenv("LIVE_CHECK") == "1")
        val repo = ScheduleRepository()
        val html = repo.fetchOnHockeyHtml()
        assertTrue("schedule html", html.contains("gametable"))
        val s = runBlocking { repo.load() }
        println("LIVE: fetchedAt=${s.fetchedAt} nhlOk=${s.nhlOk} games=${s.games.size}")
        for (g in sortGames(s.games, s.fetchedAt)) {
            println(
                "LIVE: ${TimeFmt.tokyoDay(g.startUtc, s.fetchedAt)} ${TimeFmt.tokyoTime(g.startUtc)} JST  " +
                    "${g.awayCity}(${g.away?.abbrev}) @ ${g.homeCity}(${g.home?.abbrev})  [${TimeFmt.statusLabel(g, s.fetchedAt)}]  " +
                    "score=${g.live?.awayScore}-${g.live?.homeScore} streams=${g.streamCount} groups=${g.groups.map { it.label }}",
            )
        }
    }
}
