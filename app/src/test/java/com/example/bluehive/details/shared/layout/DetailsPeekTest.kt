package com.example.bluehive.details.shared.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The movie peek layout's geometry: extra play-button rows push the row and the slide down. */
class DetailsPeekTest {

    @Test
    fun `up to four buttons fit in the two rows the layout is drawn for`() {
        assertEquals(listOf(0, 0, 0, 0, 0), (0..4).map { peekExtraSourceRows(it) })
    }

    @Test
    fun `each further row of two counts once`() {
        assertEquals(listOf(1, 1, 2, 2), (5..8).map { peekExtraSourceRows(it) })
    }

    @Test
    fun `the play buttons sit just under the overview's last line, never below its full box`() {
        assertEquals(PEEK_PLAY_TOP, peekPlayTop(null))
        assertEquals(237.dp + PEEK_PLAY_GAP, peekPlayTop(237.dp))
        assertEquals(PEEK_PLAY_TOP, peekPlayTop(PEEK_PLAY_TOP - PEEK_PLAY_GAP))
        assertEquals(PEEK_PLAY_TOP, peekPlayTop(300.dp))
    }

    @Test
    fun `an extra row moves the row and the slide down by one button row`() {
        val peek = DetailsPeekState(startFocused = false)
        assertEquals(PEEK_ROW_TOP, peek.rowTop)
        assertEquals(PEEK_SLIDE, peek.slide)
        peek.extraSourceRows = 1
        assertEquals(PEEK_ROW_TOP + 55.5.dp, peek.rowTop)
        assertEquals(PEEK_SLIDE + 55.5.dp, peek.slide)
    }

    @Test
    fun `a page with its own row top and slide keeps the slid-up row where the movie page's ends`() {
        val tv = DetailsPeekState(startFocused = false, baseRowTop = PEEK_ROW_TOP + 25.dp, baseSlide = PEEK_SLIDE + 25.dp)
        val movie = DetailsPeekState(startFocused = false)
        assertEquals(PEEK_ROW_TOP + 25.dp, tv.rowTop)
        assertEquals(movie.rowTop - movie.slide, tv.rowTop - tv.slide)
    }

    @Test
    fun `giving up the start focus is for good`() {
        val peek = DetailsPeekState(startFocused = true)
        peek.giveUpStartFocus()
        assertEquals(false, peek.startFocused)
    }
}
