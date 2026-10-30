package com.example.bluehive.livetv

import com.example.bluehive.api.LiveChannelDto
import com.example.bluehive.api.LiveRowDto
import com.example.bluehive.searchBarComponent.HiveDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSearchTest {

    private val off = setOf("FOX SPORTS NEWS")

    private fun row(name: String, color: String, vararg names: String) = LiveRowDto(
        name, color,
        names.map { n ->
            LiveChannelDto(
                id = n.lowercase(), name = n, row = name, flag = "", quality = "FHD", backup = null,
                status = if (n in off) "off" else "ok",
            )
        },
    )

    // Tonight's real names for the families the tests search, in guide order.
    private val server = listOf(
        row("Football", "#2E9E5B", "Fox Sports 504 (Footy)"),
        row(
            "US Sports", "#2F6FE0", "ESPN", "ESPN Deportes", "ESPN2", "ESPNEWS", "Fox Deportes",
            "Fox Sports 1", "Fox Sports 2", "Fox Sports 501 (Cricket)", "FOX SPORTS NEWS",
        ),
        row("World Sports", "#1E9AA6", "Fox Sports 502 AU", "Fox Sports 503", "Fox Sports 505", "Fox Sports 506", "Fox Sports 507"),
        row("US Networks", "#C7962E", "FOX 8 HD [CLEVELAND]", "LA 1 Español", "Fox", "NBC"),
        row("News", "#D9A21B", "Fox News"),
        row("Kids", "#E0559A", "Disney Channel", "Nick Jr."),
    )

    private val rows = LiveTvData.buildRows(server, kids = false, recentIds = listOf("fox news", "espn"))
    private val all = LiveSearch.channels(rows)

    private fun names(query: String) = LiveSearch.find(all, query).map { it.name }

    @Test
    fun fox_findsTheWholeFamily_liveAtoZ_offAirLast() {
        assertEquals(
            listOf(
                "Fox", "FOX 8 HD [CLEVELAND]", "Fox Deportes", "Fox News", "Fox Sports 1", "Fox Sports 2",
                "Fox Sports 501 (Cricket)", "Fox Sports 502 AU", "Fox Sports 503", "Fox Sports 504 (Footy)",
                "Fox Sports 505", "Fox Sports 506", "Fox Sports 507", "FOX SPORTS NEWS",
            ),
            names("fox"),
        )
    }

    @Test
    fun aNameThatStartsWithIt_beforeAWordThatDoes() {
        assertEquals(listOf("ESPN", "ESPN Deportes", "ESPN2", "ESPNEWS", "LA 1 Español"), names("esp"))
    }

    @Test
    fun liveChannels_beforeABetterMatchThatIsOffAir() {
        // Fox News and FOX SPORTS NEWS both have a word "news"; ESPNEWS only contains it.
        assertEquals(listOf("Fox News", "ESPNEWS", "FOX SPORTS NEWS"), names("news"))
    }

    @Test
    fun caseAccentsAndSymbolsIgnored_spokenNumbersRead() {
        assertEquals("la 1 espanol", LiveSearch.fold("LA 1 Español"))
        assertEquals("canal sport 5 pl", LiveSearch.fold("CANAL+ Sport 5 PL"))
        assertEquals("fox sports 1", LiveSearch.fold("Fox Sports One"))
        assertEquals(listOf("LA 1 Español"), names("espanol"))
        assertEquals("Fox Sports 1", names("fox sports one").first())
        assertEquals(listOf("FOX 8 HD [CLEVELAND]"), names("cleveland"))
        assertTrue(names("   ").isEmpty())
        assertTrue(names("+").isEmpty())
        assertTrue(names("zzz").isEmpty())
    }

    @Test
    fun theRecentlyWatchedRow_isNotSearchedTwice() {
        assertEquals(LiveTvData.RECENT_ROW, rows[0].name)
        assertEquals(22, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(listOf("Fox News", "FOX SPORTS NEWS"), names("fox news"))
    }

    @Test
    fun picks_countTheFamily_takeTheHomeRowColour_skipFamiliesNotHere() {
        val picks = LiveSearch.picks(all)
        assertEquals(listOf("Fox", "ESPN", "NBC", "Disney", "Nickelodeon"), picks.map { it.label })
        val fox = picks[0]
        assertEquals("fox", fox.query)
        assertEquals(14, fox.count)
        assertEquals(13, fox.live)
        assertEquals(0xFF2F6FE0, fox.color)
        assertEquals(0xFFC7962E, picks[2].color)
        assertEquals("nick", picks[4].query)
    }

    @Test
    fun aKidsProfile_getsKidsPicks_andKidsWords() {
        val kids = LiveSearch.channels(LiveTvData.buildRows(server, kids = true, recentIds = emptyList()))
        assertEquals(listOf("Disney", "Nickelodeon"), LiveSearch.picks(kids).map { it.label })
        assertEquals("Try disney or nick", LiveSearch.tryLine(kids))
        assertTrue(LiveSearch.find(kids, "fox").isEmpty())
    }

    @Test
    fun atMostTwelvePicks_inTheirOrder() {
        val families = listOf(
            "Fox", "Sky Sports Mix", "ESPN", "beIN Sports 1", "DAZN 1", "TNT Sports 1", "NBC", "BBC One",
            "HBO", "Showtime", "Disney XD", "Nickelodeon", "Cartoon Network", "PBS Kids", "Boomerang",
        )
        val picks = LiveSearch.picks(LiveSearch.channels(LiveTvData.buildRows(listOf(row("All", "#000000", *families.toTypedArray())), false, emptyList())))
        assertEquals(LiveSearch.MAX_PICKS, picks.size)
        assertEquals("Fox", picks.first().label)
        assertEquals("Nickelodeon", picks.last().label)
    }

    @Test
    fun gridSteps_stopAtTheEdges() {
        // 13 results, two across: the last row holds one tile.
        assertEquals(12, LiveSearch.step(11, 13, 2, HiveDir.DOWN))
        assertEquals(12, LiveSearch.step(10, 13, 2, HiveDir.DOWN))
        assertNull(LiveSearch.step(12, 13, 2, HiveDir.DOWN))
        assertEquals(11, LiveSearch.step(10, 13, 2, HiveDir.RIGHT))
        assertNull(LiveSearch.step(11, 13, 2, HiveDir.RIGHT))
        assertNull(LiveSearch.step(12, 13, 2, HiveDir.RIGHT))
        assertNull(LiveSearch.step(4, 13, 2, HiveDir.LEFT))     // the left column: on to the keyboard
        assertEquals(1, LiveSearch.step(3, 13, 2, HiveDir.UP))
        assertNull(LiveSearch.step(1, 13, 2, HiveDir.UP))
        // Twelve picks, three across.
        assertEquals(11, LiveSearch.step(8, 12, 3, HiveDir.DOWN))
        assertNull(LiveSearch.step(11, 12, 3, HiveDir.DOWN))
        assertEquals(2, LiveSearch.step(1, 12, 3, HiveDir.RIGHT))
        assertNull(LiveSearch.step(2, 12, 3, HiveDir.RIGHT))
    }

    @Test
    fun linesOfText() {
        assertEquals("Search 22 channels", LiveSearch.barHint(all.size))
        assertEquals("All 6 categories · 22 channels", LiveSearch.scope(rows, all.size))
        val kidsRows = LiveTvData.buildRows(server, kids = true, recentIds = emptyList())
        assertEquals("Kids · 2 channels", LiveSearch.scope(kidsRows, 2))
        assertEquals("Try fox, espn, disney or nick", LiveSearch.tryLine(all))
        assertNull(LiveSearch.tryLine(emptyList()))
        assertEquals("22 channels · 21 live now", LiveSearch.pageNote(all))

        val fox = LiveSearch.find(all, "fox")
        assertEquals("14 channels · 13 live", LiveSearch.keyboardStatus(fox))
        assertEquals("14 channels", LiveSearch.resultsTitle(fox))
        assertEquals("13 live now, the off-air one last", LiveSearch.resultsNote(fox))
        val espn = LiveSearch.find(all, "espn")
        assertEquals("4 live now", LiveSearch.resultsNote(espn))
        assertEquals("1 channel · 1 live", LiveSearch.keyboardStatus(LiveSearch.find(all, "nbc")))
        assertEquals("No channels match", LiveSearch.resultsTitle(emptyList()))
        assertEquals("No channels match", LiveSearch.keyboardStatus(emptyList()))
        assertEquals("Try fewer letters, or another name", LiveSearch.resultsNote(emptyList()))
        assertEquals("None of them live right now", LiveSearch.resultsNote(LiveSearch.find(all, "fox sports news")))
    }
}
