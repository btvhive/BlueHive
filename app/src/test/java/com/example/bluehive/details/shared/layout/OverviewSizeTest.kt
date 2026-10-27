package com.example.bluehive.details.shared.layout

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** A line-limited overview takes the largest size that fits its lines. */
class OverviewSizeTest {

    /** Lines a fake overview wraps to at each font size: smaller text, fewer lines. */
    private fun lines(at18: Int) = { size: OverviewSize -> at18 - (18 - size.fontSize.value.toInt()) }

    @Test
    fun `a short overview keeps 18`() {
        assertEquals(18.sp, pickOverviewSize(OVERVIEW_SIZES, 5, lines(at18 = 4)).fontSize)
    }

    @Test
    fun `it steps down only as far as it must`() {
        assertEquals(17.sp, pickOverviewSize(OVERVIEW_SIZES, 5, lines(at18 = 6)).fontSize)
        assertEquals(16.sp, pickOverviewSize(OVERVIEW_SIZES, 5, lines(at18 = 7)).fontSize)
    }

    @Test
    fun `too long for any size ends at the floor`() {
        assertEquals(OVERVIEW_SIZES.last(), pickOverviewSize(OVERVIEW_SIZES, 5, lines(at18 = 20)))
    }

    @Test
    fun `it stops measuring at the first fit`() {
        var measured = 0
        pickOverviewSize(OVERVIEW_SIZES, 5) { measured++; 3 }
        assertEquals(1, measured)
    }

    @Test
    fun `sizes run largest first and never grow their line height`() {
        val fonts = OVERVIEW_SIZES.map { it.fontSize.value }
        val lines = OVERVIEW_SIZES.map { it.lineHeight.value }
        assertEquals(fonts.sortedDescending(), fonts)
        assertEquals(lines.sortedDescending(), lines)
    }
}
