package com.example.bluehive.details.shared.layout

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A line-limited overview takes the largest size that fits; shrunk sizes may use more lines. */
class OverviewSizeTest {

    private val tv = OverviewLines(atFullSize = 5, whenShrunk = 6)

    /** Lines a fake overview wraps to at each font size: smaller text, fewer lines. */
    private fun lines(at18: Int) = { size: OverviewSize -> at18 - (18 - size.fontSize.value.toInt()) }

    @Test
    fun `a short overview keeps 18 and five lines`() {
        assertEquals(OverviewFit(OVERVIEW_SIZES[0], 5), pickOverviewSize(OVERVIEW_SIZES, tv, lines(at18 = 5)))
    }

    @Test
    fun `six lines at 18 is too many, but six at 17 fits`() {
        assertEquals(OverviewFit(OVERVIEW_SIZES[1], 6), pickOverviewSize(OVERVIEW_SIZES, tv, lines(at18 = 7)))
    }

    @Test
    fun `it steps down only as far as it must`() {
        assertEquals(16.sp, pickOverviewSize(OVERVIEW_SIZES, tv, lines(at18 = 8)).size.fontSize)
    }

    @Test
    fun `too long for any size ends at the floor with the shrunk limit`() {
        assertEquals(OverviewFit(OVERVIEW_SIZES.last(), 6), pickOverviewSize(OVERVIEW_SIZES, tv, lines(at18 = 20)))
    }

    @Test
    fun `it stops measuring at the first fit`() {
        var measured = 0
        pickOverviewSize(OVERVIEW_SIZES, tv) { measured++; 3 }
        assertEquals(1, measured)
    }

    @Test
    fun `sizes run largest first and never grow their line height`() {
        val fonts = OVERVIEW_SIZES.map { it.fontSize.value }
        val heights = OVERVIEW_SIZES.map { it.lineHeight.value }
        assertEquals(fonts.sortedDescending(), fonts)
        assertEquals(heights.sortedDescending(), heights)
    }

    @Test
    fun `six shrunk lines stay inside the overview box`() {
        // The box is 108.25 dp tall; sp == dp at the default font scale.
        for (size in OVERVIEW_SIZES.drop(1)) {
            assertTrue("$size x ${tv.whenShrunk} overflows the box", size.lineHeight.value * tv.whenShrunk <= 108.25f)
        }
    }
}
