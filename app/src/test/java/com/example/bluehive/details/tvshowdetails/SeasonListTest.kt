package com.example.bluehive.details.tvshowdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The season list stops at both ends, and keeps the gold row in its window. */
class SeasonListTest {

    @Test
    fun `the list stops at Season 1 and at the last season`() {
        assertNull(seasonListStep(1, -1, 12))
        assertNull(seasonListStep(12, 1, 12))
        assertNull(seasonListStep(1, 1, 1))
        assertEquals(2, seasonListStep(1, 1, 12))
        assertEquals(11, seasonListStep(12, -1, 12))
    }

    @Test
    fun `a short list is centred in the window`() {
        assertEquals(-1.5f, seasonListScroll(1, 1), 0f)
        assertEquals(-0.5f, seasonListScroll(2, 3), 0f)
        assertEquals(0f, seasonListScroll(4, 4), 0f)
    }

    @Test
    fun `a long list keeps the gold row in the middle, never past either end`() {
        assertEquals(0f, seasonListScroll(1, 12), 0f)
        assertEquals(0f, seasonListScroll(2, 12), 0f)
        assertEquals(0.5f, seasonListScroll(3, 12), 0f)
        assertEquals(8f, seasonListScroll(12, 12), 0f)
        assertEquals(8f, seasonListScroll(11, 12), 0f)
    }

    @Test
    fun `the gold row is always fully inside the window`() {
        for (total in 1..15) for (shown in 1..total) {
            val row = shown - 1 - seasonListScroll(shown, total)
            assert(row >= 0f && row + 1 <= LIST_VISIBLE_ROWS) { "total $total shown $shown at row $row" }
        }
    }

    @Test
    fun `a move waits for the last one to land`() {
        assertEquals(150L, SEASON_TURN_GAP_MS)
        assertEquals(false, seasonTurnAllowed(now = 1_000 + 149, last = 1_000))
        assertEquals(true, seasonTurnAllowed(now = 1_000 + 150, last = 1_000))
        // The first press of all: the start value never blocks it, nor overflows.
        assertEquals(true, seasonTurnAllowed(now = 5, last = Long.MIN_VALUE / 2))
    }
}
