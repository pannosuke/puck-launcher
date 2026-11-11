package com.eric.pucklauncher.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFmt {
    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun tokyoTime(i: Instant): String = hm.format(i.atZone(TOKYO))

    fun tokyoDay(i: Instant, now: Instant): String {
        val d = i.atZone(TOKYO).toLocalDate()
        val today = now.atZone(TOKYO).toLocalDate()
        return when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(i.atZone(TOKYO))
        }
    }

    fun statusLabel(g: Game, now: Instant): String {
        val l = g.live
        return when (g.phase(now)) {
            Phase.LIVE -> {
                val p = periodName(l?.period, l?.periodType)
                when {
                    l == null -> "LIVE"
                    l.inIntermission -> "LIVE · $p INT"
                    l.clock != null -> "LIVE · $p ${l.clock}"
                    else -> "LIVE · $p"
                }
            }
            Phase.FINAL -> if (l == null) "Ended?" else when (l.periodType) {
                "OT" -> "FINAL/OT"; "SO" -> "FINAL/SO"; else -> "FINAL"
            }
            Phase.UPCOMING -> {
                val mins = Duration.between(now, g.startUtc).toMinutes()
                val pre = if (l?.gameState == "PRE") "Pregame · " else ""
                pre + when {
                    mins < 1 -> "Starting"
                    mins < 60 -> "in $mins min"
                    else -> "in ${mins / 60}h ${mins % 60}m"
                }
            }
            Phase.STARTED -> "Started"
        }
    }

    private fun periodName(n: Int?, type: String?): String = when {
        type == "OT" -> "OT"
        type == "SO" -> "SO"
        n == 1 -> "1st"; n == 2 -> "2nd"; n == 3 -> "3rd"
        n != null -> "P$n"
        else -> ""
    }
}
