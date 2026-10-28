package com.example.bluehive.livetv

import com.example.bluehive.livetv.StillWatching.State
import org.junit.Assert.assertEquals
import org.junit.Test

class StillWatchingTest {

    private val hour = 60L * 60 * 1000

    @Test
    fun watchingUntilFourHoursWithoutAPress() {
        assertEquals(State.Watching, StillWatching.state(now = 0, lastInput = 0))
        assertEquals(State.Watching, StillWatching.state(now = 4 * hour - 1, lastInput = 0))
    }

    @Test
    fun asksForTwoMinutes_countingDown() {
        assertEquals(State.Asking(120), StillWatching.state(now = 4 * hour, lastInput = 0))
        assertEquals(State.Asking(108), StillWatching.state(now = 4 * hour + 12_000, lastInput = 0))
        assertEquals(State.Asking(1), StillWatching.state(now = 4 * hour + 119_500, lastInput = 0))
    }

    @Test
    fun stopsWhenUnanswered_andAPressStartsAgain() {
        assertEquals(State.Stop, StillWatching.state(now = 4 * hour + 120_000, lastInput = 0))
        assertEquals(State.Watching, StillWatching.state(now = 4 * hour + 120_000, lastInput = 4 * hour + 60_000))
    }

    @Test
    fun clockText() {
        assertEquals("1:48", StillWatching.clock(108))
        assertEquals("0:05", StillWatching.clock(5))
        assertEquals("2:00", StillWatching.clock(120))
    }
}
