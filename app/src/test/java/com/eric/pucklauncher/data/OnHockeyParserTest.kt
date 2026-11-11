package com.eric.pucklauncher.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset
import java.time.Instant

class OnHockeyParserTest {
    private val html = javaClass.getResourceAsStream("/schedule_table_cp1251.html")!!.readBytes()
        .toString(Charset.forName("windows-1251"))
    private val now = Instant.parse("2026-11-11T00:51:00Z") // capture time
    private val games = OnHockeyParser.parse(html, now)

    @Test fun findsOnlyNhlGames() {
        assertEquals(7, games.size)
        assertEquals(listOf("Colorado", "Minnesota Wild", "Washington", "New Jersey", "Florida", "Detroit", "Nashville"), games.map { it.awayRaw })
        assertEquals(listOf("Toronto", "Montréal", "Ottawa", "Winnipeg", "Columbus", "St. Louis Blues", "Los Angeles"), games.map { it.homeRaw })
    }

    @Test fun mapsTeamsToAbbrevs() {
        assertEquals(listOf("COL", "MIN", "WSH", "NJD", "FLA", "DET", "NSH"), games.map { it.away?.abbrev })
        assertEquals(listOf("TOR", "MTL", "OTT", "WPG", "CBJ", "STL", "LAK"), games.map { it.home?.abbrev })
    }

    @Test fun timesAreUtcAndConvertToTokyo() {
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), games[0].startUtc)
        assertEquals(Instant.parse("2026-11-11T00:30:00Z"), games[4].startUtc)
        assertEquals(Instant.parse("2026-11-11T03:00:00Z"), games[6].startUtc)
        assertEquals("09:00", TimeFmt.tokyoTime(games[0].startUtc))
        assertEquals("12:00", TimeFmt.tokyoTime(games[6].startUtc))
    }

    @Test fun groupsAndCounts() {
        assertEquals(193, games.sumOf { it.streamCount })
        assertEquals(listOf(39, 57, 23, 32, 29, 13, 0), games.map { it.streamCount })
        val col = games[0]
        assertEquals(listOf("home feed", "away feed", "french", "russian", "czech"), col.groups.map { it.label })
        assertEquals(listOf(25, 7, 3, 3, 1), col.groups.map { it.streams.size })
        assertEquals(listOf("english", "russian"), games[4].groups.map { it.label })
    }

    @Test fun streamFields() {
        val s = games[0].groups[0].streams.first { it.host == "plytvme" }
        assertEquals("plytvme", s.host)
        assertEquals("ESPN+", s.channel)
        // Fixture links are scrubbed placeholders: np_stream400.php?channel=//example.invalid/stream/N
        assertTrue(s.url, Regex("""https://www\.onhockey\.tv/np_stream400\.php\?channel=//example\.invalid/stream/\d+""").matches(s.url))
        assertEquals("https:" + s.url.substringAfter("channel="), s.embedUrl)
        val vk = games[0].groups.first { it.label == "russian" }.streams.first { it.host == "vklive" }
        assertTrue(vk.url, Regex("""https://www\.onhockey\.tv/np_vk\.php\?channel=-1000%26id=\d+""").matches(vk.url))
        val id = vk.url.substringAfter("%26id=")
        assertEquals("https://vkvideo.ru/video_ext.php?oid=-1000&id=$id&autoplay=1", vk.embedUrl)
    }

    @Test fun emptyGameHasNote() {
        val g = games[6]
        assertTrue(g.groups.isEmpty())
        assertEquals("live stream will be available closer to the game time", g.note)
    }

    @Test fun otherLeagueSelectable() {
        val ahl = OnHockeyParser.parse(html, now, league = "AHL")
        assertTrue(ahl.isNotEmpty())
        val s = ahl.flatMap { g -> g.groups.flatMap { it.streams } }.first { it.host == "sportpl" }
        assertTrue(s.url.startsWith("https://example.invalid/direct/"))
        assertNull(s.embedUrl)
        assertEquals("", s.channel)
    }

    @Test fun ids_are_unique() = assertEquals(games.size, games.map { it.id }.toSet().size)

    @Test fun emptyOrForeignHtml() {
        assertTrue(OnHockeyParser.parse("You are not allowed to access this page directly.", now).isEmpty())
        assertTrue(OnHockeyParser.parse("", now).isEmpty())
    }
}

class DateRolloverTest {
    private fun t(s: String) = Instant.parse(s)

    @Test fun beforeMidnightUtcGamesAfterMidnightAreTomorrow() {
        val r = OnHockeyParser.resolveStarts(listOf(0 to 0, 3 to 0), t("2026-11-10T22:00:00Z"))
        assertEquals(listOf(t("2026-11-11T00:00:00Z"), t("2026-11-11T03:00:00Z")), r)
    }

    @Test fun recentGameStaysInThePast() {
        val r = OnHockeyParser.resolveStarts(listOf(23 to 0, 1 to 0), t("2026-11-11T01:30:00Z"))
        assertEquals(listOf(t("2026-11-10T23:00:00Z"), t("2026-11-11T01:00:00Z")), r)
    }

    @Test fun nextEveningSlateIsMonotonic() {
        // At 06:00 UTC, the next slate (17:00 .. 03:00 UTC) must not put the 03:00 game in the past.
        val r = OnHockeyParser.resolveStarts(listOf(17 to 0, 23 to 0, 0 to 0, 3 to 0), t("2026-11-11T06:00:00Z"))
        assertEquals(
            listOf(t("2026-11-11T17:00:00Z"), t("2026-11-11T23:00:00Z"), t("2026-11-12T00:00:00Z"), t("2026-11-12T03:00:00Z")), r,
        )
    }

    @Test fun tokyoDayLabel() {
        val now = t("2026-11-10T14:00:00Z") // 23:00 JST Nov 10
        assertEquals("Tomorrow", TimeFmt.tokyoDay(t("2026-11-11T00:00:00Z"), now))
        assertEquals("Today", TimeFmt.tokyoDay(t("2026-11-10T14:30:00Z"), now))
    }
}

class TeamsTest {
    @Test fun aliases() {
        assertEquals("NYR", Teams.find("NY Rangers")?.abbrev)
        assertEquals("NYI", Teams.find("New York Islanders")?.abbrev)
        assertNull(Teams.find("New York"))
        assertEquals("MTL", Teams.find("Montreal")?.abbrev)
        assertEquals("STL", Teams.find("St.Louis")?.abbrev ?: Teams.find("St. Louis")?.abbrev)
        assertEquals("VGK", Teams.find("Vegas Golden Knights")?.abbrev)
        assertEquals("UTA", Teams.find("Utah")?.abbrev)
        assertEquals("TBL", Teams.find("Tampa Bay")?.abbrev)
        assertEquals("LAK", Teams.find("LA Kings")?.abbrev)
    }
}
