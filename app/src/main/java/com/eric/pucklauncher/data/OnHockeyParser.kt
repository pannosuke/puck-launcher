package com.eric.pucklauncher.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Parses https://www.onhockey.tv/schedule_table.php (HTML fragment, already decoded from windows-1251).
 * Times on the site are HH:MM in UTC with no date; see [resolveStarts].
 */
object OnHockeyParser {
    const val BASE = "https://www.onhockey.tv/"
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")

    fun parse(html: String, now: Instant, league: String = "NHL"): List<Game> {
        val doc = Jsoup.parse(html, BASE)
        val rows = mutableListOf<RawRow>()
        for (tbody in doc.select("tbody")) {
            val header = tbody.selectFirst("tr:not(.game) b") ?: continue
            if (header.text().trim() != league) continue
            tbody.select("tr.game").mapNotNullTo(rows) { parseRow(it) }
        }
        val starts = resolveStarts(rows.map { it.hour to it.minute }, now)
        return rows.mapIndexed { i, r ->
            val away = Teams.find(r.away)
            val home = Teams.find(r.home)
            Game(
                id = "%02d%02d-%s-%s".format(r.hour, r.minute, Teams.norm(r.away), Teams.norm(r.home)).replace(' ', '_'),
                awayRaw = r.away, homeRaw = r.home, away = away, home = home,
                startUtc = starts[i], groups = r.groups, note = r.note,
            )
        }
    }

    private class RawRow(val hour: Int, val minute: Int, val away: String, val home: String, val groups: List<FeedGroup>, val note: String?)

    private fun parseRow(row: Element): RawRow? {
        val tds = row.children().filter { it.tagName() == "td" }
        if (tds.size < 2) return null
        val m = TIME.find(tds[0].text()) ?: return null
        val teams = tds[1].ownText().trim()
        val parts = teams.split(Regex("""\s+-\s+"""), limit = 2)
        val (away, home) = if (parts.size == 2) parts[0].trim() to parts[1].trim() else teams to ""

        val groups = LinkedHashMap<String, MutableList<Stream>>()
        val notes = mutableListOf<String>()
        var label = "streams"
        tds[1].selectFirst("div.gamelinks")?.childNodes()?.forEach { node ->
            when (node) {
                is TextNode -> {
                    val t = node.text().trim()
                    if (t.isEmpty()) return@forEach
                    if (t.endsWith(":")) label = t.dropLast(1).trim() else notes += t
                }
                is Element -> if (node.tagName() == "a" && node.hasAttr("href")) {
                    val rawHref = node.attr("href")
                    val title = node.attr("title").trim().let { if (it.contains("opens in a new tab", true)) "" else it }
                    groups.getOrPut(label) { mutableListOf() } += Stream(
                        group = label,
                        host = node.text().trim(),
                        channel = title,
                        url = node.absUrl("href").ifEmpty { rawHref },
                        embedUrl = embedUrlFor(rawHref),
                    )
                }
            }
        }
        return RawRow(
            m.groupValues[1].toInt() % 24, m.groupValues[2].toInt(), away, home,
            groups.map { FeedGroup(it.key, it.value) },
            notes.joinToString(" ").ifBlank { null },
        )
    }

    /** Raw player URL behind onhockey's wrapper pages, or null for direct links. */
    fun embedUrlFor(rawHref: String): String? {
        val channel = rawHref.substringAfter("channel=", "").ifEmpty { return null }
        return when {
            rawHref.startsWith("np_vk.php") -> {
                val decoded = channel.replace("%26", "&")
                val oid = decoded.substringBefore("&id=")
                val id = decoded.substringAfter("&id=", "").ifEmpty { return null }
                "https://vkvideo.ru/video_ext.php?oid=$oid&id=$id&autoplay=1"
            }
            rawHref.startsWith("np_") -> when {
                channel.startsWith("//") -> "https:$channel"
                channel.startsWith("http") -> channel
                else -> null
            }
            else -> null
        }
    }

    /**
     * The site gives only UTC HH:MM, rows in chronological order. The first row takes the occurrence inside
     * (now - 8h, now + 16h]; each later row is the first occurrence not earlier than the previous row.
     * The NHL API merge later replaces these with authoritative startTimeUTC when it matches.
     */
    fun resolveStarts(times: List<Pair<Int, Int>>, now: Instant): List<Instant> {
        val out = ArrayList<Instant>(times.size)
        val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
        fun at(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).toInstant(ZoneOffset.UTC)
        for ((h, m) in times) {
            val prev = out.lastOrNull()
            val t = if (prev == null) {
                val lo = now.minus(Duration.ofHours(8))
                (-1L..1L).map { at(today.plusDays(it), h, m) }.firstOrNull { it.isAfter(lo) && !it.isAfter(now.plus(Duration.ofHours(16))) }
                    ?: (-1L..1L).map { at(today.plusDays(it), h, m) }.minBy { Duration.between(now, it).abs() }
            } else {
                var c = at(prev.atOffset(ZoneOffset.UTC).toLocalDate(), h, m)
                if (c.isBefore(prev)) c = c.plus(Duration.ofDays(1))
                c
            }
            out += t
        }
        return out
    }
}
