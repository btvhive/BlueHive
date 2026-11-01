package com.example.bluehive.livetv

import com.example.bluehive.api.LiveChannelDto
import com.example.bluehive.api.LiveRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNewsTest {

    private fun station(id: String, name: String, states: List<String>, status: String = "ok") = LiveChannelDto(
        id = id, name = name, row = LocalNews.ROW, flag = states.firstOrNull() ?: "US", quality = "FHD",
        backup = null, status = status, states = states,
    )

    // In the server's order: home state, then name; the one for everyone last.
    private val local = LiveRowDto(
        LocalNews.ROW, "#7CB342", listOf(
            station("loc-wcpo", "WCPO 9", listOf("OH", "KY", "IN")),
            station("loc-woio", "Cleveland 19 News", listOf("OH")),
            station("loc-wkyt", "WKYT Lexington", listOf("KY")),
            station("loc-wews", "News 5", listOf("OH"), status = "off"),
            station("loc-wave", "WAVE Now Louisville", listOf("KY", "IN")),
            station("loc-now", "Local Now", emptyList()),
        ),
    )
    private val news = LiveRowDto("News", "#D9A21B", listOf(LiveChannelDto("cnn", "CNN USA", "News", "US", "FHD", null, "ok")))
    private val server = listOf(local, news)

    private fun localRow(state: String?, recent: List<String> = emptyList()) =
        LiveTvData.buildRows(server, kids = false, recentIds = recent, localState = state)

    @Test
    fun noStatePicked_theRowStaysEmpty_soItsPillCanBeReached() {
        val rows = localRow(null)
        assertEquals(listOf(LocalNews.ROW, "News"), rows.map { it.name })
        assertTrue(rows[0].channels.isEmpty())
    }

    @Test
    fun ohio_workingFirst_ownStationsThenEveryones_offAirLast() {
        val row = localRow("OH").first { it.name == LocalNews.ROW }
        assertEquals(listOf("WCPO 9", "Cleveland 19 News", "Local Now", "News 5"), row.channels.map { it.name })
        assertEquals(listOf(101, 102, 103, 104), row.channels.map { it.number })
        assertTrue(row.channels.last().off)
    }

    @Test
    fun kentucky_aNeighboursStationServingItComesAfterItsOwn() {
        val row = localRow("KY").first { it.name == LocalNews.ROW }
        assertEquals(
            listOf("WKYT Lexington", "WAVE Now Louisville", "WCPO 9", "Local Now"),
            row.channels.map { it.name },
        )
    }

    @Test
    fun thePillNamesTheState_itsCodeWhenTheNameIsLong() {
        assertEquals("New York", LocalNews.pillLabel("NY"))
        assertEquals("Louisiana", LocalNews.pillLabel("LA"))
        assertEquals("NC", LocalNews.pillLabel("NC"))
        assertEquals("DC", LocalNews.pillLabel("DC"))
        assertEquals("Pick state", LocalNews.pillLabel(null))
    }

    @Test
    fun theStateListOffersEveryStateAStationServes_aToZ() {
        val choices = LocalNews.choices(local)
        assertEquals(listOf("Indiana", "Kentucky", "Ohio"), choices.map { it.name })
        assertEquals(listOf(2, 3, 3), choices.map { it.stations })
        assertEquals(listOf("WCPO 9", "Cleveland 19 News", "News 5"), choices.last().examples)
        assertEquals("WCPO 9, Cleveland 19 News, News 5", LocalNews.examples(choices.last()))
        assertEquals("1 station", LocalNews.stationsLabel(1))
        assertEquals("Washington, D.C.", LocalNews.stateName("DC"))
        assertEquals("ZZ", LocalNews.stateName("ZZ"))
    }

    @Test
    fun recentlyWatchedAndSearch_findTheStateStations_notOtherStates() {
        val rows = localRow("OH", recent = listOf("loc-woio", "loc-wkyt", "cnn"))
        assertEquals(LiveTvData.RECENT_ROW, rows[0].name)
        assertEquals(listOf("loc-woio", "cnn"), rows[0].channels.map { it.id })
        val all = LiveSearch.channels(rows)
        assertEquals(listOf("Cleveland 19 News"), LiveSearch.find(all, "cleveland").map { it.name })
        assertTrue(LiveSearch.find(all, "lexington").isEmpty())
    }

    @Test
    fun aKidsProfile_hasNoLocalNews() {
        val kids = LiveRowDto(LiveTvData.KIDS_ROW, "#E0559A", listOf(LiveChannelDto("nick", "Nickelodeon", "Kids", "", "FHD", null, "ok")))
        val rows = LiveTvData.buildRows(server + kids, kids = true, recentIds = emptyList(), localState = "OH")
        assertEquals(listOf(LiveTvData.KIDS_ROW), rows.map { it.name })
    }
}
