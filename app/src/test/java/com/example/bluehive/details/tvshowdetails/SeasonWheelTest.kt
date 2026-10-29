package com.example.bluehive.details.tvshowdetails

import org.junit.Assert.assertEquals
import org.junit.Test

/** The season wheel goes all the way round, and never shows a season twice. */
class SeasonWheelTest {

    @Test
    fun `past the last season comes Season 1, and before Season 1 the last`() {
        assertEquals(listOf(1, 2), listOf(1, 2).map { wheelSeason(29, it, 29) })
        assertEquals(listOf(29, 28), listOf(-1, -2).map { wheelSeason(1, it, 29) })
        assertEquals(1, wheelSeason(28, 2, 29))
    }

    @Test
    fun `in the middle it is plain arithmetic`() {
        assertEquals(listOf(1, 2, 4, 5), listOf(-2, -1, 1, 2).map { wheelSeason(3, it, 5) })
    }

    @Test
    fun `few seasons fill only as many slabs as there are other seasons`() {
        assertEquals(emptyList<Int>(), wheelOffsets(1))
        assertEquals(listOf(1), wheelOffsets(2))
        assertEquals(listOf(1, -1), wheelOffsets(3))
        assertEquals(listOf(1, -1, 2), wheelOffsets(4))
        assertEquals(listOf(1, -1, 2, -2), wheelOffsets(5))
        assertEquals(listOf(1, -1, 2, -2), wheelOffsets(29))
    }

    @Test
    fun `every season has one place, the short way round`() {
        assertEquals(listOf(-2, -1, 0, 1, 2), (1..5).map { wheelDistance(it, 3, 5) })
        assertEquals(listOf(1, 2), listOf(1, 2).map { wheelDistance(it, 29, 29) })
        assertEquals(listOf(-1, -2), listOf(29, 28).map { wheelDistance(it, 1, 29) })
        // An even wheel puts the far side below the card.
        assertEquals(2, wheelDistance(3, 1, 4))
    }

    @Test
    fun `the rolled pieces are the same seasons the slabs showed`() {
        for (total in 1..9) {
            for (shown in 1..total) {
                val rolled = (-2..2).filter { o -> wheelDistance(wheelSeason(shown, o, total), shown, total) == o && o != 0 }
                    .map { wheelSeason(shown, it, total) }.toSet()
                val slabs = wheelOffsets(total).map { wheelSeason(shown, it, total) }.toSet()
                assertEquals("total $total shown $shown", slabs, rolled)
            }
        }
    }

    @Test
    fun `no season is drawn twice, and never the card's own`() {
        for (total in 1..8) {
            for (shown in 1..total) {
                val seasons = wheelOffsets(total).map { wheelSeason(shown, it, total) }
                assertEquals("total $total shown $shown", seasons.size, seasons.toSet().size)
                assert(shown !in seasons) { "total $total shown $shown drew its own season" }
            }
        }
    }
}
