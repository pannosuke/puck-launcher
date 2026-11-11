package com.eric.pucklauncher.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset
import java.time.Instant

class NhlApiTest {
    private val api = NhlApi.parseScore(javaClass.getResourceAsStream("/nhl_score_2026-11-10.json")!!.readBytes().toString(Charsets.UTF_8))
    private val now = Instant.parse("2026-11-11T00:51:00Z")
    private val games = OnHockeyParser.parse(
        javaClass.getResourceAsStream("/schedule_table_cp1251.html")!!.readBytes().toString(Charset.forName("windows-1251")), now,
    )

    @Test fun parsesScoreFeed() {
        assertEquals(7, api.size)
        val tor = api.first { it.homeAbbrev == "TOR" }
        assertEquals("COL", tor.awayAbbrev)
        assertEquals("LIVE", tor.gameState)
        assertEquals(1, tor.homeScore)
        assertEquals(1, tor.period)
        assertTrue(tor.inIntermission)
    }

    @Test fun mergesAllSevenGames() {
        val merged = NhlApi.merge(games, api)
        assertEquals(7, merged.count { it.live != null })
        val sorted = sortGames(merged, now)
        assertEquals(Phase.LIVE, sorted.first().phase(now))
        assertEquals("NSH", sorted.last().away?.abbrev) // future game last
        assertEquals("LIVE · 1st INT", TimeFmt.statusLabel(merged[0], now))
    }

    @Test fun mergeWithoutApiKeepsOnhockeyData() {
        val merged = NhlApi.merge(games, emptyList())
        assertEquals(games, merged)
    }
}
