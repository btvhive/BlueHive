package com.example.bluehive.details.moviesdetails

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The movie page's metadata lines: missing ones drop out, the rest close up, genres follow. */
class MovieInfoLabelsTest {

    @Test
    fun `all four lines keep the slots they always had, genres at 96 dp`() {
        val rows = movieInfoRows(1_000, 2_000, "1985-12-21", "Japanese")
        assertEquals(listOf("Revenue", "Budget", "Release Date", "Language"), rows.map { it.text.substringBefore(':') })
        assertEquals(listOf(34.dp, 48.dp, 63.dp, 75.dp), rows.indices.map { movieInfoRowTop(it) })
        assertEquals(96.dp, movieGenresTop(rows.size))
    }

    @Test
    fun `without revenue and budget the date and language move to the top slots`() {
        val rows = movieInfoRows(0, 0, "1985-12-21", "Japanese")
        assertEquals("Release Date:      12/21/1985", rows[0].text)
        assertEquals("Language:         Japanese", rows[1].text)
        assertEquals(2, rows.size)
        assertEquals(48.dp + 21.dp, movieGenresTop(rows.size))
    }

    @Test
    fun `an unknown date or language is left out, not printed as N A`() {
        val rows = movieInfoRows(5, 0, "N/A", "")
        assertEquals(listOf("Revenue"), rows.map { it.text.substringBefore(':') })
        assertEquals(34.dp + 21.dp, movieGenresTop(rows.size))
    }

    @Test
    fun `with nothing at all the genres take the first slot`() {
        assertEquals(emptyList<MovieInfoRow>(), movieInfoRows(0, 0, null, null))
        assertEquals(34.dp, movieGenresTop(0))
    }

    @Test
    fun `a date not in yyyy-mm-dd is shown as given`() {
        assertEquals("1985", movieInfoRows(0, 0, "1985", null).single().text)
    }

    @Test
    fun `money keeps its grouping and the release date its 16 sp`() {
        val rows = movieInfoRows(75_634_409, 0, "1993-10-09", null)
        assertEquals("Revenue:          $75,634,409.00", rows[0].text)
        assertEquals(listOf(17, 16), rows.map { it.fontSizeSp })
    }
}
