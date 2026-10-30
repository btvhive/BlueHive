package com.example.bluehive.utilities

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 4 (v515): every focus move in the recommended row recomposed the whole
 * section, because the scroll-nudge effect was keyed on the focused index;
 * and a card passed over while scrolling laid out a title it never showed.
 * The nudge follows the index in a flow now, and the title is composed only
 * once it is due.
 */
class RecommendedRowFocusTest {

    /** The nudge as it was written before v515, to compare against. */
    private fun oldNudge(focused: Int, first: Int, last: Int): Int? {
        if (focused in (first + 1)..(last - 1)) return null
        return when {
            focused <= first -> maxOf(focused - 1, 0)
            focused >= last -> maxOf(focused - 1, 0)
            else -> focused
        }
    }

    @Test
    fun `the nudge decides exactly as before`() {
        for (first in 0..10) for (last in first..first + 8) for (focused in 0..20) {
            assertEquals("f=$focused [$first, $last]", oldNudge(focused, first, last), recommendedRowNudge(focused, first, last))
        }
    }

    @Test
    fun `inside the window nothing moves, at an edge it steps one card in`() {
        assertNull(recommendedRowNudge(focused = 3, firstVisible = 1, lastVisible = 5))
        assertEquals(4, recommendedRowNudge(focused = 5, firstVisible = 1, lastVisible = 5))
        assertEquals(0, recommendedRowNudge(focused = 0, firstVisible = 0, lastVisible = 5))
    }

    private val row = MainSource.code("utilities/RecommendedMediaSection.kt")

    @Test
    fun `the focused index is followed in a flow, not used as an effect key`() {
        assertFalse(Regex("""LaunchedEffect\(\s*focusedIndex\s*\)""").containsMatchIn(row))
        assertTrue(row.contains("snapshotFlow { focusedIndex }"))
    }

    @Test
    fun `the title waits until it is due`() {
        assertTrue(row.contains("if (isItemFocused && titleVisible) {"))
    }
}
