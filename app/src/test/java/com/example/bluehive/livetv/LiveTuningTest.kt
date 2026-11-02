package com.example.bluehive.livetv

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTuningTest {

    private val ny = ZoneId.of("America/New_York")

    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, 1, hour, minute, 0, 0, ny).toEpochSecond()

    @Test
    fun lead_isTheNumber_orAGamesLeague() {
        assertEquals("304", LiveTuning.lead(304, ""))
        assertEquals("NFL", LiveTuning.lead(0, "NFL"))
        assertEquals("", LiveTuning.lead(0, ""))
    }

    @Test
    fun meta_channelsSayLive_gamesSayTheirStart() {
        assertEquals("Live · FHD · US Sports", LiveTuning.meta("FHD", "US Sports", 0, "", "", at(12), ny))
        assertEquals("Live · Kids", LiveTuning.meta("", "Kids", 0, "", "", at(12), ny))
        assertEquals("NFL · kicked off 1:00 PM", LiveTuning.meta("HD", "On now", at(13), "NFL", "NFL", at(14), ny))
        assertEquals("Serie A · starts 4:25 PM", LiveTuning.meta("HD", "On now", at(16, 25), "Soccer", "Serie A", at(14), ny))
    }

    @Test
    fun liveDot_forChannels_andStartedGamesOnly() {
        assertTrue(LiveTuning.liveDot(0, at(12)))
        assertTrue(LiveTuning.liveDot(at(13), at(13)))
        assertFalse(LiveTuning.liveDot(at(13), at(13) - 1))
    }

    @Test
    fun stageLine_findsTheStream_thenStartsThePicture() {
        assertEquals("Finding the live stream…", LiveTuning.stageLine(1))
        assertEquals("Starting the picture…", LiveTuning.stageLine(2))
    }

    @Test
    fun ink_whiteOnTheRowColour_darkOnThePaleOnes() {
        val white = 0xFFFFFFFF
        val dark = 0xFF121213
        assertEquals(white, LiveTuning.ink(0xFF2F6FE0))   // US Sports
        assertEquals(white, LiveTuning.ink(0xFFC62828))   // On now
        assertEquals(white, LiveTuning.ink(0xFFD06A2A))   // Entertainment
        assertEquals(dark, LiveTuning.ink(0xFFD9A21B))    // News
        assertEquals(dark, LiveTuning.ink(0xFF7CB342))    // Local News
    }
}
