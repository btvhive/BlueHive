package com.example.bluehive.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FavoritesData's start values, Undo, and when "Exit Remove Mode" shows. */
class FavoritesDataTest {

    private fun fav(id: Int) = FavoriteEntry(favoriteId = id, tmdbId = id, mediaType = "movie", addedAt = "d", title = "T$id")

    private fun FavoritesData.remove(id: Int) {
        entries.remove(fav(id))
        removed.add(fav(id))
    }

    @Test
    fun `it starts loading, with nothing removed`() {
        val data = FavoritesData()
        assertTrue(data.isLoading)
        assertFalse(data.isNavigating)
        assertFalse(data.removeMode)
        assertFalse(data.showDeleteConfirm)
        assertTrue(data.entries.isEmpty())
        assertTrue(data.removed.isEmpty())
    }

    @Test
    fun `undo puts the removed titles back at the end, in the order removed`() {
        val data = FavoritesData()
        data.entries.addAll(listOf(fav(1), fav(2), fav(3), fav(4)))
        data.remove(3)
        data.remove(1)
        data.undoRemovals()
        assertEquals(listOf(2, 4, 3, 1), data.entries.map { it.favoriteId })
        assertTrue(data.removed.isEmpty())
    }

    @Test
    fun `Exit Remove Mode shows when asked for, or when every card is removed`() {
        val data = FavoritesData()
        data.entries.addAll(listOf(fav(1), fav(2)))
        assertFalse(data.showExitRemoveOverlay)
        data.showDeleteConfirm = true
        assertTrue(data.showExitRemoveOverlay)
        data.showDeleteConfirm = false
        data.removeMode = true
        data.remove(1)
        assertFalse(data.showExitRemoveOverlay)
        data.remove(2)
        assertTrue(data.showExitRemoveOverlay)
        data.removeMode = false
        assertFalse(data.showExitRemoveOverlay)
    }
}
