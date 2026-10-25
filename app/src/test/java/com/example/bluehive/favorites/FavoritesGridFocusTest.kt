package com.example.bluehive.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * gridTarget, where a D-pad press on a Favorites card goes: the edges, the
 * last-row rule, and the same answer as the four arrow branches it replaced,
 * for every grid up to 30 cards.
 */
class FavoritesGridFocusTest {

    @Test
    fun `Left and Right step through the cards, across rows`() {
        assertEquals(GridTarget.Card(2), gridTarget(3, GridKey.Left, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Card(6), gridTarget(7, GridKey.Left, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Card(7), gridTarget(6, GridKey.Right, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Stay, gridTarget(0, GridKey.Left, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Stay, gridTarget(19, GridKey.Right, 20, CARDS_PER_ROW))
    }

    @Test
    fun `Up from the first row goes to the control bar`() {
        for (index in 0 until CARDS_PER_ROW) {
            assertEquals(GridTarget.ControlBar, gridTarget(index, GridKey.Up, 20, CARDS_PER_ROW))
        }
        assertEquals(GridTarget.Card(3), gridTarget(10, GridKey.Up, 20, CARDS_PER_ROW))
    }

    @Test
    fun `Down goes to the card below, or to the last card from above a short last row`() {
        // 20 cards: 0-6, 7-13, 14-19.
        assertEquals(GridTarget.Card(10), gridTarget(3, GridKey.Down, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Card(19), gridTarget(13, GridKey.Down, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Stay, gridTarget(15, GridKey.Down, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Stay, gridTarget(19, GridKey.Down, 20, CARDS_PER_ROW))
    }

    @Test
    fun `before any card has had focus`() {
        assertEquals(GridTarget.Card(0), gridTarget(-1, GridKey.Right, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Stay, gridTarget(-1, GridKey.Left, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.ControlBar, gridTarget(-1, GridKey.Up, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Card(6), gridTarget(-1, GridKey.Down, 20, CARDS_PER_ROW))
        assertEquals(GridTarget.Card(4), gridTarget(-1, GridKey.Down, 5, CARDS_PER_ROW))
    }

    @Test
    fun `the same answer as the arrow branches before F2, for every grid up to 30 cards`() {
        for (perRow in 1..8) {
            for (count in 0..30) {
                for (index in -1..count) {
                    for (key in GridKey.entries) {
                        assertEquals(
                            "card $index, $key, $count cards, $perRow to a row",
                            beforeF2(index, key, count, perRow),
                            gridTarget(index, key, count, perRow),
                        )
                    }
                }
            }
        }
    }

    /**
     * FavoritesScreen's four arrow branches at v406 (980efb4), transcribed:
     * the card focusGridItem was called with, the Filter dropdown, or nothing.
     * The cooldown and the sounds stay in the screen.
     */
    private fun beforeF2(focusedGridIndex: Int, key: GridKey, count: Int, cardsPerRow: Int): GridTarget {
        val lastIndex = count - 1 // filteredEntries.lastIndex
        when (key) {
            GridKey.Left -> if (focusedGridIndex > 0) return GridTarget.Card(focusedGridIndex - 1)
            GridKey.Right -> if (focusedGridIndex < lastIndex) return GridTarget.Card(focusedGridIndex + 1)
            GridKey.Up -> {
                val target = focusedGridIndex - cardsPerRow
                return if (target >= 0) GridTarget.Card(target) else GridTarget.ControlBar
            }
            GridKey.Down -> {
                val target = focusedGridIndex + cardsPerRow
                if (target <= lastIndex) return GridTarget.Card(target)
                val lastRowStart = (lastIndex / cardsPerRow) * cardsPerRow
                if (focusedGridIndex < lastRowStart) return GridTarget.Card(lastIndex)
            }
        }
        return GridTarget.Stay
    }
}
