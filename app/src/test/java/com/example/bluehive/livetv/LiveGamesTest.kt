package com.example.bluehive.livetv

import com.example.bluehive.api.LiveChannelDto
import com.example.bluehive.api.LiveRowDto
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGamesTest {

    private val ny = ZoneId.of("America/New_York")

    /** Sunday 2026-11-01 (the day the clocks went back) at [hour]:[minute] Eastern. */
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, 1, hour, minute, 0, 0, ny).toEpochSecond()

    private fun game(id: String, sport: String, league: String = sport, start: Long = at(13), off: Boolean = false) =
        LiveTvData.Channel(id, id.uppercase(), LiveGames.ROW, 0xFFC62828, "", "HD", false, off, 0, start, league, sport, "")

    private fun channel(id: String, row: String, number: Int, off: Boolean = false) =
        LiveTvData.Channel(id, id.uppercase(), row, 0xFF2644A6, "UK", "HD", false, off, number)

    @Test
    fun pills_allFirst_thenTheUsualOrder_unknownSportsAtoZ_blankIsOther() {
        val games = listOf(
            game("a", "Soccer"), game("b", "NFL"), game("c", "Cricket"), game("d", "Soccer"),
            game("e", "Motor"), game("f", ""), game("g", "NFL"), game("h", "Soccer"),
        )
        val pills = LiveGames.pills(games)
        assertEquals(listOf("All", "NFL", "Soccer", "Motor", "Cricket", "Other"), pills.map { it.label })
        assertEquals(listOf(8, 2, 3, 1, 1, 1), pills.map { it.count })
        assertEquals(null, pills[0].sport)
        assertEquals(listOf("f"), LiveGames.shown(games, "Other").map { it.id })
    }

    @Test
    fun shown_keepsTheRowsOrder_andAllIsEverything() {
        val games = listOf(game("a", "Soccer"), game("b", "NFL"), game("c", "Soccer"))
        assertEquals(listOf("a", "c"), LiveGames.shown(games, "Soccer").map { it.id })
        assertEquals(listOf("a", "b", "c"), LiveGames.shown(games, null).map { it.id })
        assertTrue(LiveGames.shown(games, "Hockey").isEmpty())
    }

    @Test
    fun startedAtItsStart_upcomingBefore_aChannelIsNeither() {
        val g = game("a", "NFL", start = at(13))
        assertFalse(LiveGames.started(g, at(13) - 1))
        assertTrue(LiveGames.upcoming(g, at(13) - 1))
        assertTrue(LiveGames.started(g, at(13)))
        assertFalse(LiveGames.upcoming(g, at(13)))
        val c = channel("cnn", "News", 201)
        assertFalse(LiveGames.started(c, at(13)))
        assertFalse(LiveGames.upcoming(c, at(13)))
    }

    @Test
    fun clockAndStartLine_inTheTvsZone() {
        assertEquals("4:25", LiveGames.clock(at(16, 25), ny))
        val nfl = game("a", "NFL", start = at(13))
        assertEquals("NFL · kicked off 1:00 PM", LiveGames.startLine(nfl, at(14), ny))
        assertEquals("NFL · starts 1:00 PM", LiveGames.startLine(nfl, at(12), ny))
        val serieA = game("b", "Soccer", league = "Serie A", start = at(9, 30))
        assertEquals("Serie A · started 9:30 AM", LiveGames.startLine(serieA, at(10), ny))
        val noLeague = game("c", "Motor", league = "", start = at(15))
        assertEquals("starts 3:00 PM", LiveGames.startLine(noLeague, at(10), ny))
    }

    @Test
    fun onNowComesFirst_unnumbered_otherRowsKeepTheirNumbers() {
        fun dto(id: String, row: String, start: Long? = null, sport: String? = null) =
            LiveChannelDto(id, id.uppercase(), row, "", "HD", null, "ok", start = start, league = sport, sport = sport,
                thumb = "https://example.test/$id.png")
        val server = listOf(
            LiveRowDto("Motorsport", "#D64545", listOf(dto("f1", "Motorsport"))),
            LiveRowDto(LiveGames.ROW, "#C62828", listOf(dto("g1", LiveGames.ROW, at(13), "NFL"), dto("g2", LiveGames.ROW, at(15), "Soccer"))),
            LiveRowDto("News", "#D9A21B", listOf(dto("cnn", "News"))),
        )
        val rows = LiveTvData.buildRows(server, kids = false, recentIds = listOf("g1", "f1"))
        assertEquals(listOf(LiveGames.ROW, LiveTvData.RECENT_ROW, "Motorsport", "News"), rows.map { it.name })
        assertEquals(listOf(0, 0), rows[0].channels.map { it.number })
        assertEquals(listOf(at(13), at(15)), rows[0].channels.map { it.start })
        assertEquals(listOf("NFL", "Soccer"), rows[0].channels.map { it.sport })
        assertEquals("https://example.test/g1.png", rows[0].channels[0].thumb)
        assertEquals(listOf("g1", "f1"), rows[1].channels.map { it.id })
        assertEquals(listOf(101), rows[2].channels.map { it.number })
        assertEquals(listOf(201), rows[3].channels.map { it.number })
        assertEquals(listOf("Kids"), LiveTvData.buildRows(server + LiveRowDto("Kids", "#E0559A", listOf(dto("nick", "Kids"))),
            kids = true, recentIds = emptyList()).map { it.name })
    }

    @Test
    fun searchTileMeta_channelsByNumber_gamesByLeagueAndStart() {
        assertEquals("101 · Motorsport", LiveSearch.tileMeta(channel("f1", "Motorsport", 101), at(12), ny))
        assertEquals("201 · News · off air", LiveSearch.tileMeta(channel("cnn", "News", 201, off = true), at(12), ny))
        // Before kickoff its stream isn't judged: the start, never "off air".
        assertEquals("On now · NFL · starts 4:25",
            LiveSearch.tileMeta(game("a", "NFL", start = at(16, 25), off = true), at(12), ny))
        assertEquals("On now · NFL · off air", LiveSearch.tileMeta(game("a", "NFL", start = at(13), off = true), at(14), ny))
        assertEquals("On now", LiveSearch.tileMeta(game("b", "Motor", league = "", start = at(13)), at(14), ny))
    }
}
