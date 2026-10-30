package com.example.bluehive.details.shared.layout

import androidx.compose.ui.unit.sp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 9 (v515): the movie page laid out two or three times as it opened. Its
 * overview size was measured in an effect after the first frame (a third
 * pass, and a visible font jump when it shrank), and the Watch buttons and
 * the row read where the overview ended while composing (a second pass of
 * every button and the row). The size is measured while composing now, and
 * the buttons and row read their tops while placing.
 */
class OverviewFirstFrameTest {

    /** The movie rule as the effect applied it before v515. */
    private fun oldSize(lineCount: Int, didOverflowHeight: Boolean): Pair<Float, Float> =
        if (lineCount > 5 || didOverflowHeight) 16f to 13.7f else 18f to 14f

    @Test
    fun `the movie overview keeps its two sizes, chosen by the same rule`() {
        for (lines in 0..12) for (overflow in listOf(false, true)) {
            val size = movieOverviewSize(lines, overflow)
            assertEquals("lines $lines overflow $overflow", oldSize(lines, overflow), size.fontSize.value to size.lineHeight.value)
        }
    }

    @Test
    fun `five lines keep 18, six or an overflow drop to 16`() {
        assertEquals(18.sp, movieOverviewSize(5, didOverflowHeight = false).fontSize)
        assertEquals(16.sp, movieOverviewSize(6, didOverflowHeight = false).fontSize)
        assertEquals(16.sp, movieOverviewSize(3, didOverflowHeight = true).fontSize)
    }

    @Test
    fun `the size is measured while composing, not in an effect`() {
        val code = MainSource.code("details/shared/layout/DetailsOverview.kt")
        assertFalse(code.contains("LaunchedEffect"))
        assertTrue(code.contains("remember(text, dongleRegular)"))
    }

    @Test
    fun `the Watch buttons and the row read their tops while placing`() {
        val grid = MainSource.code("details/moviesdetails/MovieSourceGrid.kt")
        assertTrue(grid.contains("playTop: () -> Dp"))
        assertEquals(3, Regex("""\.offsetWithY\(offsetX, offsetY\)""").findAll(grid).count())
        assertFalse(Regex("""\.offset\(x = offsetX, y = offsetY\)""").containsMatchIn(grid))

        val screen = MainSource.code("details/moviesdetails/MovieDetailsScreen.kt")
        assertTrue(screen.contains("playTop                = { peek.playTop }"))

        val row = MainSource.code("details/shared/layout/DetailsRecommendations.kt")
        assertTrue(row.contains("Modifier.offsetWithY(0.dp) { peek.rowTop }"))
        assertFalse(row.contains("topOffset = peek.rowTop"))
    }
}
