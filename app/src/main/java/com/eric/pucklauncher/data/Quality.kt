package com.eric.pucklauncher.data

/** Expected stream quality. [rank] is the in-group sort order (likely 1080p first, SD last). */
enum class Quality(val label: String, val rank: Int) {
    HD1080("1080p", 0),
    HD720("720p", 1),
    UNKNOWN("?", 2),
    SD("SD", 3),
}

/**
 * Host reputation table built from sampled live NHL streams (2026-11-11, see research/quality).
 * Estimates only: hosts switch sources, so treat badges as "likely".
 */
object QualityTable {
    val byHost: Map<String, Quality> = mapOf(
        "ppvst" to Quality.HD1080,   // 3/3 1080p ~13 Mbps
        "strmfr" to Quality.HD1080,  // 1080p59.94 ~8 Mbps
        "hlsst" to Quality.HD1080,   // 1080p50-60
        "xstream" to Quality.HD720,  // 3/3 720p30
        "alieztv" to Quality.HD720,  // 2/2 720p30
        "vertex" to Quality.SD,      // 1152x648 ~1.5 Mbps
        "nexast" to Quality.SD,      // same source as vertex
        "korast" to Quality.SD,      // same ?id= scheme as vertex/nexast (inferred)
        "freestr" to Quality.SD,     // 960x540
        // Mixed / per channel: mtchor, lovecdn, asiria (see overrides). Not measurable: plytvme, streamc,
        // embedv, ddlive, vklive, vidcdn.
    )

    /** mtchor channel ids measured individually. */
    private val mtchorChannelIds = mapOf(
        "904213" to Quality.HD1080,  // TSN 4, 1080p60
        "177347" to Quality.HD720,   // TSN 4, 720p60
        "1949830" to Quality.HD720,  // ESPN+, 720p30
    )

    /** (host, channel-title prefix) overrides, matched case-insensitively on the onhockey link title. */
    private val channelOverrides: Map<String, List<Pair<String, Quality>>> = mapOf(
        "lovecdn" to listOf("TSN" to Quality.HD1080, "TVA" to Quality.HD720, "Viasat" to Quality.HD720),
        "asiria" to listOf("TSN" to Quality.HD1080),
        "mtchor" to listOf("ESPN" to Quality.HD720),
    )

    fun of(host: String, channel: String, embedUrl: String?): Quality {
        val h = host.lowercase()
        if (h == "mtchor") {
            val id = embedUrl?.substringAfterLast("/channel/", "")?.takeWhile { it.isDigit() }
            mtchorChannelIds[id]?.let { return it }
        }
        channelOverrides[h]?.firstOrNull { channel.trim().startsWith(it.first, ignoreCase = true) }?.let { return it.second }
        byHost[h]?.let { return it }
        // URL hint, e.g. streamfree-style slugs ending in "1080p".
        if (embedUrl?.contains("1080p", ignoreCase = true) == true) return Quality.HD1080
        return Quality.UNKNOWN
    }

    fun of(s: Stream): Quality = of(s.host, s.channel, s.embedUrl)
}

val Stream.quality: Quality get() = QualityTable.of(this)

/** Stable sort: likely 1080p, then 720p, then unknown, then SD. */
fun List<Stream>.sortedByQuality(): List<Stream> = sortedBy { it.quality.rank }

val Game.likely1080Count: Int get() = groups.sumOf { g -> g.streams.count { it.quality == Quality.HD1080 } }
