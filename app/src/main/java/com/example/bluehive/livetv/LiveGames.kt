package com.example.bluehive.livetv

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * On now (2026-11-01, design C of live_tv_on_now_designs_abc): today's games
 * from zlive's list, each a stream of its own. The server sends them as the
 * first row, started games newest first, then the rest by start; each carries
 * its start (epoch seconds), league, sport and matchup picture. The TV puts
 * the row first, leaves its entries unnumbered, and lets the viewer narrow it
 * with sport pills (All, NFL, Soccer...) in the search bar's place. zlive marks
 * a stream live hours before kickoff, so LIVE here means the start has passed.
 * Pure, so the JVM tests call it.
 */
internal object LiveGames {

    const val ROW = "On now"

    /** A pill: its sport (null = All) and how many games it holds. */
    data class Pill(val sport: String?, val label: String, val count: Int)

    // Pill order; any other sport follows A-Z.
    private val ORDER = listOf("NFL", "Football", "Soccer", "Hockey", "Basketball", "Baseball", "Motor", "Fights")

    /** "All 45", then each sport that has a game, in the usual order. */
    fun pills(games: List<LiveTvData.Channel>): List<Pill> {
        val counts = games.groupingBy { it.sport.ifEmpty { "Other" } }.eachCount()
        val sports = counts.keys.sortedWith(compareBy({ ORDER.indexOf(it).let { i -> if (i < 0) ORDER.size else i } }, { it }))
        return listOf(Pill(null, "All", games.size)) + sports.map { Pill(it, it, counts.getValue(it)) }
    }

    /** The games a pill shows, in the row's own order. */
    fun shown(games: List<LiveTvData.Channel>, sport: String?): List<LiveTvData.Channel> =
        if (sport == null) games else games.filter { it.sport.ifEmpty { "Other" } == sport }

    /**
     * Where Up from the sport pills goes: the first channel category after On now
     * (Motorsport), past the profile's Recently watched. Leaving the games took
     * Right through every pill before (the user, 2026-11-05). Null when none follows.
     */
    fun firstCategoryAfter(rowNames: List<String>, from: Int): Int? =
        (from + 1..rowNames.lastIndex).firstOrNull { rowNames[it] != LiveTvData.RECENT_ROW }

    /** Started: what LIVE means here (zlive's own flag goes up hours early). */
    fun started(game: LiveTvData.Channel, nowS: Long): Boolean = game.start in 1..nowS

    /** A game still to start: its stream is not judged yet (a slate, or nothing, until then). False for a channel. */
    fun upcoming(game: LiveTvData.Channel, nowS: Long): Boolean = game.start > nowS

    private val CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val CLOCK_AMPM = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** The list's start column: "4:25". */
    fun clock(startS: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochSecond(startS).atZone(zone).format(CLOCK)

    /** The panel's line: "NFL · kicked off 1:00 PM", "F1 · starts 3:00 PM". */
    fun startLine(game: LiveTvData.Channel, nowS: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        startLine(game.start, game.sport, game.league, nowS, zone)

    /** The same from a game's parts, for the player, which keeps no [LiveTvData.Channel]. */
    fun startLine(start: Long, sport: String, league: String, nowS: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochSecond(start).atZone(zone).format(CLOCK_AMPM)
        val verb = if (start > nowS) "starts" else if (sport == "NFL" || sport == "Football") "kicked off" else "started"
        return listOf(league, "$verb $time").filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
