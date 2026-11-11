package com.eric.pucklauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.Instant

class QualityTableTest {
    private fun q(host: String, channel: String = "", embed: String? = null) = QualityTable.of(host, channel, embed)

    @Test fun hostDefaults() {
        assertEquals(Quality.HD1080, q("ppvst"))
        assertEquals(Quality.HD1080, q("strmfr"))
        assertEquals(Quality.HD1080, q("hlsst"))
        assertEquals(Quality.HD720, q("xstream"))
        assertEquals(Quality.HD720, q("alieztv"))
        assertEquals(Quality.SD, q("vertex"))
        assertEquals(Quality.SD, q("nexast"))
        assertEquals(Quality.SD, q("freestr"))
        assertEquals(Quality.UNKNOWN, q("plytvme", "ESPN+"))
        assertEquals(Quality.UNKNOWN, q("ddlive", "TSN 4"))
        assertEquals(Quality.UNKNOWN, q("brandnewhost"))
    }

    @Test fun channelOverrides() {
        assertEquals(Quality.HD1080, q("lovecdn", "TSN 4"))
        assertEquals(Quality.HD720, q("lovecdn", "TVA Sports"))
        assertEquals(Quality.HD720, q("lovecdn", "Viasat Sport"))
        assertEquals(Quality.UNKNOWN, q("lovecdn", "Sportsnet One"))
        assertEquals(Quality.HD1080, q("asiria", "TSN 3"))
        assertEquals(Quality.UNKNOWN, q("asiria", "Premier Sports 2"))
        assertEquals(Quality.HD720, q("mtchor", "ESPN+", "https://example.invalid/embed/channel/555"))
        assertEquals(Quality.UNKNOWN, q("mtchor", "TSN 4", "https://example.invalid/embed/channel/555"))
    }

    @Test fun mtchorChannelIdBeatsTitle() {
        assertEquals(Quality.HD1080, q("mtchor", "TSN 4", "https://example.invalid/embed/channel/904213"))
        assertEquals(Quality.HD720, q("mtchor", "TSN 4", "https://example.invalid/embed/channel/177347"))
    }

    @Test fun urlHintForUnknownHosts() {
        assertEquals(Quality.HD1080, q("newhost", "", "https://x.example/embed/a-vs-b1080p"))
        assertEquals(Quality.SD, q("vertex", "", "https://example.invalid/1080p")) // table wins over hint
    }

    @Test fun sortOrderIs1080Then720ThenUnknownThenSd() {
        fun st(host: String, ch: String = "") = Stream("home feed", host, ch, "https://u/$host$ch", null)
        val sorted = listOf(st("vertex"), st("plytvme"), st("xstream"), st("ppvst"), st("freestr"), st("lovecdn", "TSN 4"))
            .sortedByQuality()
        assertEquals(listOf("ppvst", "lovecdn", "xstream", "plytvme", "vertex", "freestr"), sorted.map { it.host })
    }

    @Test fun parserSortsGroupsAndCountsHd() {
        val html = javaClass.getResourceAsStream("/schedule_table_cp1251.html")!!.readBytes().toString(Charset.forName("windows-1251"))
        val games = OnHockeyParser.parse(html, Instant.parse("2026-11-11T00:51:00Z"))
        for (g in games) for (grp in g.groups) {
            val ranks = grp.streams.map { it.quality.rank }
            assertEquals("${g.homeRaw}/${grp.label} sorted", ranks.sorted(), ranks)
        }
        val col = games[0]
        assertEquals(Quality.HD1080, col.groups.first { it.label == "home feed" }.streams.first().quality)
        assertTrue(col.likely1080Count > 0)
        println("1080p counts per game: " + games.map { "${it.homeRaw}=${it.likely1080Count}" })
    }
}
