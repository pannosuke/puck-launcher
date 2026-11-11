package com.eric.pucklauncher.data

import java.text.Normalizer

object Teams {
    data class Team(val abbrev: String, val city: String, val nickname: String, val extra: List<String> = emptyList())

    val all = listOf(
        Team("ANA", "Anaheim", "Ducks"), Team("BOS", "Boston", "Bruins"), Team("BUF", "Buffalo", "Sabres"),
        Team("CGY", "Calgary", "Flames"), Team("CAR", "Carolina", "Hurricanes"), Team("CHI", "Chicago", "Blackhawks"),
        Team("COL", "Colorado", "Avalanche"), Team("CBJ", "Columbus", "Blue Jackets"), Team("DAL", "Dallas", "Stars"),
        Team("DET", "Detroit", "Red Wings"), Team("EDM", "Edmonton", "Oilers"), Team("FLA", "Florida", "Panthers"),
        Team("LAK", "Los Angeles", "Kings", listOf("la kings")), Team("MIN", "Minnesota", "Wild"),
        Team("MTL", "Montréal", "Canadiens"), Team("NSH", "Nashville", "Predators"), Team("NJD", "New Jersey", "Devils"),
        Team("NYI", "New York", "Islanders", listOf("ny islanders", "nyi")),
        Team("NYR", "New York", "Rangers", listOf("ny rangers", "nyr")),
        Team("OTT", "Ottawa", "Senators"), Team("PHI", "Philadelphia", "Flyers"), Team("PIT", "Pittsburgh", "Penguins"),
        Team("SJS", "San Jose", "Sharks"), Team("SEA", "Seattle", "Kraken"), Team("STL", "St. Louis", "Blues"),
        Team("TBL", "Tampa Bay", "Lightning", listOf("tampa")), Team("TOR", "Toronto", "Maple Leafs"),
        Team("UTA", "Utah", "Mammoth", listOf("utah hockey club", "utah hc")), Team("VAN", "Vancouver", "Canucks"),
        Team("VGK", "Vegas", "Golden Knights", listOf("las vegas")), Team("WSH", "Washington", "Capitals"),
        Team("WPG", "Winnipeg", "Jets"),
    )

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun containsWord(hay: String, needle: String) = " $hay ".contains(" $needle ")

    fun find(name: String): Team? {
        val n = norm(name)
        if (n.isEmpty()) return null
        all.firstOrNull { containsWord(n, norm(it.nickname)) }?.let { return it }
        all.firstOrNull { t -> t.extra.any { containsWord(n, norm(it)) } }?.let { return it }
        // City match only when unambiguous (New York is shared).
        val byCity = all.filter { containsWord(n, norm(it.city)) }
        return byCity.singleOrNull()
    }

    fun byAbbrev(abbrev: String): Team? = all.firstOrNull { it.abbrev == abbrev }
}
