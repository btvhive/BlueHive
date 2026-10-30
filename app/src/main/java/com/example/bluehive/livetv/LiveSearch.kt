package com.example.bluehive.livetv

import com.example.bluehive.searchBarComponent.HiveDir
import java.text.Normalizer

/**
 * Live TV search: which channels a few typed (or spoken) letters mean, in the
 * order the search page lists them, the quick picks it offers before anything
 * is typed, and its lines of text. Pure, so the JVM tests call it.
 *
 * Every channel is already on the TV, so a search is a filter, run on every
 * letter. Order: live channels first and off-air ones last, as in the guide;
 * inside each, a name that starts with the query, then one whose words start
 * with the query's words, then one that merely contains them; then A-Z.
 */
internal object LiveSearch {

    /** A family the page offers in one click: [query] is the search it runs. */
    data class Pick(val label: String, val query: String, val color: Long, val count: Int, val live: Int)

    const val MAX_PICKS = 12
    const val PICK_COLUMNS = 3
    const val RESULT_COLUMNS = 2

    private data class Family(val label: String, val query: String, val home: String)

    // The first twelve that find anything are shown. The last three stand in
    // for the families a kids profile (Kids row only) has none of. A pick takes
    // the colour of its home row, as the mockup drew it.
    private val FAMILIES = listOf(
        Family("Fox", "fox", "US Sports"),
        Family("Sky Sports", "sky sports", "Sky Sports"),
        Family("ESPN", "espn", "US Sports"),
        Family("beIN Sports", "bein", "World Sports"),
        Family("DAZN", "dazn", "World Sports"),
        Family("TNT", "tnt", "World Sports"),
        Family("NBC", "nbc", "US Networks"),
        Family("BBC", "bbc", "Entertainment"),
        Family("HBO", "hbo", "Movies & Premium"),
        Family("Showtime", "showtime", "Movies & Premium"),
        Family("Disney", "disney", "Kids"),
        Family("Nickelodeon", "nick", "Kids"),
        Family("Cartoon Network", "cartoon", "Kids"),
        Family("PBS Kids", "pbs", "Kids"),
        Family("Boomerang", "boomerang", "Kids"),
    )

    // "Try fox, espn, sky or disney" while the guide's search bar has the focus.
    private val EXAMPLES = listOf("fox", "espn", "sky", "disney", "nick", "cartoon", "pbs")

    // Speech comes back with words ("fox sports one"); names mostly use digits.
    // Both sides are folded the same way, so "BBC One" still finds "bbc one".
    private val NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
    )
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Every channel once -- the Recently watched row repeats some -- in guide order. */
    fun channels(rows: List<LiveTvData.Row>): List<LiveTvData.Channel> =
        rows.filter { it.name != LiveTvData.RECENT_ROW }.flatMap { it.channels }.distinctBy { it.id }

    /** What [query] finds among [channels], in the page's order; nothing for a blank query. */
    fun find(channels: List<LiveTvData.Channel>, query: String): List<LiveTvData.Channel> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ')
        return channels
            .mapNotNull { c -> tier(fold(c.name), q, words)?.let { it to c } }
            .sortedWith(
                compareBy<Pair<Int, LiveTvData.Channel>>(
                    { it.second.off }, { it.first }, { it.second.name.lowercase() }, { it.second.number },
                )
            )
            .map { it.second }
    }

    /** Lower case, accents and symbols gone, number words as digits: "LA 1 Español" -> "la 1 espanol". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
            .split(NOT_WORD).filter { it.isNotEmpty() }
            .joinToString(" ") { NUMBER_WORDS[it] ?: it }

    private fun tier(name: String, query: String, words: List<String>): Int? {
        val nameWords = name.split(' ')
        return when {
            name.startsWith(query) -> 0
            words.all { w -> nameWords.any { it.startsWith(w) } } -> 1
            words.all { it in name } -> 2
            else -> null
        }
    }

    /** The quick picks that find something here, at most [MAX_PICKS]. */
    fun picks(channels: List<LiveTvData.Channel>): List<Pick> = FAMILIES.mapNotNull { family ->
        val found = find(channels, family.query)
        if (found.isEmpty()) return@mapNotNull null
        val color = (found.firstOrNull { it.row == family.home } ?: found.first()).rowColor
        Pick(family.label, family.query, color, found.size, found.count { !it.off })
    }.take(MAX_PICKS)

    /** Where a D-pad press moves the focus in a grid of [count] tiles, [columns] wide; null at its edge. */
    fun step(tile: Int, count: Int, columns: Int, dir: HiveDir): Int? = when (dir) {
        HiveDir.UP -> (tile - columns).takeIf { it >= 0 }
        HiveDir.DOWN -> if (tile / columns < (count - 1) / columns) minOf(tile + columns, count - 1) else null
        HiveDir.LEFT -> (tile - 1).takeIf { tile % columns > 0 }
        HiveDir.RIGHT -> (tile + 1).takeIf { tile % columns < columns - 1 && it < count }
    }

    // ── Lines of text ─────────────────────────────────────────────────────────

    fun count(n: Int): String = if (n == 1) "1 channel" else "$n channels"

    /** The guide's search bar. */
    fun barHint(total: Int): String = "Search ${count(total)}"

    /** Under "Search channels" while the bar has the focus. */
    fun scope(rows: List<LiveTvData.Row>, total: Int): String {
        val categories = rows.filter { it.name != LiveTvData.RECENT_ROW }
        return if (categories.size == 1) "${categories[0].name} · ${count(total)}"
        else "All ${categories.size} categories · ${count(total)}"
    }

    /** "Try fox, espn, sky or disney": up to four that find something here. */
    fun tryLine(channels: List<LiveTvData.Channel>): String? {
        val names = EXAMPLES.filter { find(channels, it).isNotEmpty() }.take(4)
        if (names.isEmpty()) return null
        val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " or " + names.last()
        return "Try $list"
    }

    /** Under the search page's title. */
    fun pageNote(channels: List<LiveTvData.Channel>): String =
        "${count(channels.size)} · ${channels.count { !it.off }} live now"

    /** The keyboard's line under its box. */
    fun keyboardStatus(found: List<LiveTvData.Channel>): String =
        if (found.isEmpty()) "No channels match" else "${count(found.size)} · ${found.count { !it.off }} live"

    fun resultsTitle(found: List<LiveTvData.Channel>): String =
        if (found.isEmpty()) "No channels match" else count(found.size)

    fun resultsNote(found: List<LiveTvData.Channel>): String {
        val live = found.count { !it.off }
        val off = found.size - live
        return when {
            found.isEmpty() -> "Try fewer letters, or another name"
            live == 0 -> "None of them live right now"
            off == 0 -> "$live live now"
            off == 1 -> "$live live now, the off-air one last"
            else -> "$live live now, the off-air ones last"
        }
    }
}
