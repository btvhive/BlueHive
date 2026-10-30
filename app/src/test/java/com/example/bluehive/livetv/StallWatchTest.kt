package com.example.bluehive.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StallWatchTest {

    /** One sample a second from [from]; the first second that said stuck, or null. */
    private fun StallWatch.firstStuck(seconds: Int, from: Int = 0, position: (Int) -> Long, frames: (Int) -> Int?): Int? {
        for (s in from until from + seconds) if (stuck(s * 1000L, position(s), frames(s))) return s
        return null
    }

    @Test
    fun steadyPlayback_neverStuck() {
        assertNull(StallWatch().firstStuck(60, position = { it * 1000L }, frames = { it * 30 }))
    }

    @Test
    fun frozen_stuckOnceAWholeWindowHasPassed() {
        assertEquals(8, StallWatch().firstStuck(20, position = { 5_000L }, frames = { 150 }))
    }

    @Test
    fun stallLoopCrawl_stuck() {
        // Comedy Central 2026-10-29: playing/buffering flips, the position barely moving.
        assertEquals(8, StallWatch().firstStuck(20, position = { it * 200L }, frames = { it * 12 }))
    }

    @Test
    fun soundButFrozenPicture_stuck() {
        assertEquals(8, StallWatch().firstStuck(20, position = { it * 1000L }, frames = { 300 }))
    }

    @Test
    fun threeSecondRebuffer_notStuck() {
        val pos = { s: Int -> if (s < 10) s * 1000L else if (s < 13) 10_000L else (s - 3) * 1000L }
        val frames = { s: Int -> if (s < 10) s * 30 else if (s < 13) 300 else (s - 3) * 30 }
        assertNull(StallWatch().firstStuck(40, position = pos, frames = frames))
    }

    @Test
    fun fiveSecondRebuffer_stuck() {
        val pos = { s: Int -> if (s < 10) s * 1000L else if (s < 15) 10_000L else (s - 5) * 1000L }
        assertEquals(15, StallWatch().firstStuck(40, position = pos, frames = { it * 30 }))
    }

    @Test
    fun resetStartsAFreshWindow() {
        val watch = StallWatch()
        assertNull(watch.firstStuck(7, position = { 5_000L }, frames = { 150 }))
        watch.reset()
        assertEquals(15, watch.firstStuck(20, from = 7, position = { 5_000L }, frames = { 150 }))
    }

    @Test
    fun noVideoDecoder_judgedByPositionAlone() {
        assertNull(StallWatch().firstStuck(30, position = { it * 1000L }, frames = { null }))
        assertEquals(8, StallWatch().firstStuck(20, position = { 1_000L }, frames = { null }))
    }

    @Test
    fun frameCounterRestarting_isNotAFreeze() {
        // A re-enabled decoder starts its count again from zero.
        val frames = { s: Int -> if (s < 5) 100 + s * 30 else (s - 5) * 30 }
        assertNull(StallWatch().firstStuck(30, position = { it * 1000L }, frames = frames))
    }
}
