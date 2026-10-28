package com.example.bluehive.details.shared.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** How much of the recommended row peeks, per page, and where that puts it. */
class PeekRuleTest {

    @Test
    fun `the shares sit where they were measured`() {
        assertEquals(485.25.dp, peekRowTop(40))   // ~40%, the default since v476
        assertEquals(469.25.dp, peekRowTop(50))
        assertEquals(453.25.dp, peekRowTop(60))   // the movie page's old ~60%
    }

    @Test
    fun `the TV page is 35 percent whatever its overview`() {
        assertEquals(35, PeekRule.Standard.percent(2))
        assertEquals(35, PeekRule.Standard.percent(null))
        assertEquals(493.25.dp, peekRowTop(35))
    }

    @Test
    fun `a movie lifts to 50 percent under three lines or fewer`() {
        val rule = PeekRule.LiftUnderShortOverview
        assertEquals(50, rule.percent(0))   // no overview
        assertEquals(50, rule.percent(3))
        assertEquals(40, rule.percent(4))
        assertEquals("not laid out yet", 40, rule.percent(null))
    }

    @Test
    fun `an anime film is 60 percent across the board`() {
        assertEquals(60, PeekRule.Anime.percent(1))
        assertEquals(60, PeekRule.Anime.percent(7))
        assertEquals(60, PeekRule.Anime.percent(null))
    }

    @Test
    fun `the slid-up row lands in the same place at every share`() {
        for (rule in PeekRule.entries) {
            for (lines in listOf(null, 0, 3, 6)) {
                val peek = DetailsPeekState(startFocused = false, rule = rule).apply { overviewLines = lines }
                assertEquals(PEEK_SLID_ROW_TOP, peek.rowTop - peek.slide)
            }
        }
    }

    @Test
    fun `a short overview moves the movie row up as soon as it is laid out`() {
        val peek = DetailsPeekState(startFocused = false, rule = PeekRule.LiftUnderShortOverview)
        assertEquals(PEEK_ROW_TOP, peek.rowTop)   // 40% until the overview is laid out
        peek.overviewLines = 2
        assertEquals(peekRowTop(50), peek.rowTop)
        assertEquals(peekRowTop(50) - PEEK_SLID_ROW_TOP, peek.slide)
    }
}
