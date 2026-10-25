package com.example.bluehive.details.shared.playback

import com.example.bluehive.utilities.ServerOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins AnimeServerList to the movie screen's server failover from before
 * Stage 6 of the details redesign moved it there: which server the automatic
 * failover tries next, and how the lists are keyed.
 */
class AnimeServerListTest {

    private val url = "https://www.miruro.tv/watch?id=21"

    @Test
    fun key_keepsTheTwoTracksApart() {
        val servers = AnimeServerList()
        assertEquals("$url|dub", servers.key(url, true))
        assertEquals("$url|sub", servers.key(url, false))
    }

    @Test
    fun pickNext_prefersAServerTaggedForTheTrack() {
        val servers = AnimeServerList()
        servers.cache[servers.key(url, true)] = listOf(
            ServerOption("Alpha", listOf("sub")),
            ServerOption("Bravo", listOf("DUB")),
            ServerOption("Charlie"),
        ) to 0
        assertEquals("Bravo", servers.pickNext(url, true, emptyList()))

        // Bravo tried: nothing else advertises dub, so the first untried one.
        servers.tried += "Bravo"
        assertEquals("Alpha", servers.pickNext(url, true, emptyList()))
    }

    @Test
    fun pickNext_usesTheCachedListBeforeThePickersList() {
        val servers = AnimeServerList()
        servers.cache[servers.key(url, false)] = listOf(ServerOption("Cached")) to 0
        assertEquals("Cached", servers.pickNext(url, false, listOf(ServerOption("Shown"))))
    }

    @Test
    fun pickNext_fallsBackToThePickersList() {
        val servers = AnimeServerList()
        // A list cached for the OTHER track does not count.
        servers.cache[servers.key(url, true)] = listOf(ServerOption("Dubbed")) to 0
        assertEquals("Shown", servers.pickNext(url, false, listOf(ServerOption("Shown"))))
    }

    @Test
    fun pickNext_returnsNullOnceEveryServerWasTried() {
        val servers = AnimeServerList()
        assertNull(servers.pickNext(url, false, emptyList()))

        servers.cache[servers.key(url, false)] =
            listOf(ServerOption("Alpha"), ServerOption("Bravo")) to 1
        servers.tried += listOf("Alpha", "Bravo")
        assertNull(servers.pickNext(url, false, emptyList()))
    }
}
