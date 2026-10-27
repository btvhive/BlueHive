package com.example.bluehive.details.moviesdetails

import androidx.compose.ui.unit.dp
import com.example.bluehive.details.shared.layout.DetailsInfoLine
import com.example.bluehive.details.shared.layout.detailsGenresTop
import com.example.bluehive.details.shared.layout.detailsInfoLineTop
import org.junit.Assert.assertEquals
import org.junit.Test

/** The movie page's metadata lines: missing ones drop out, the rest close up, genres follow. */
class MovieInfoLabelsTest {

    @Test
    fun `all four lines keep the slots they always had, genres at 96 dp`() {
        val rows = movieInfoRows(1_000, 2_000, "1985-12-21", "Japanese")
        assertEquals(listOf("Revenue", "Budget", "Release Date", "Language"), rows.map { it.text.substringBefore(':') })
        assertEquals(listOf(34.dp, 48.dp, 63.dp, 75.dp), rows.indices.map { detailsInfoLineTop(it) })
        assertEquals(96.dp, detailsGenresTop(rows.size))
    }

    @Test
    fun `without revenue and budget the date and language move to the top slots`() {
        val rows = movieInfoRows(0, 0, "1985-12-21", "Japanese")
        assertEquals("Release Date:      12/21/1985", rows[0].text)
        assertEquals("Language:         Japanese", rows[1].text)
        assertEquals(2, rows.size)
        assertEquals(48.dp + 21.dp, detailsGenresTop(rows.size))
    }

    @Test
    fun `an unknown date or language is left out, not printed as N A`() {
        val rows = movieInfoRows(5, 0, "N/A", "")
        assertEquals(listOf("Revenue"), rows.map { it.text.substringBefore(':') })
        assertEquals(34.dp + 21.dp, detailsGenresTop(rows.size))
    }

    @Test
    fun `with nothing at all the genres take the first slot`() {
        assertEquals(emptyList<DetailsInfoLine>(), movieInfoRows(0, 0, null, null))
        assertEquals(34.dp, detailsGenresTop(0))
    }

    @Test
    fun `a date not in yyyy-mm-dd is shown as given`() {
        assertEquals("1985", movieInfoRows(0, 0, "1985", null).single().text)
    }

    @Test
    fun `the count the screen places the genres with always matches the lines`() {
        val cases = listOf(
            listOf<Any?>(1_000L, 2_000L, "1985-12-21", "Japanese"),
            listOf<Any?>(0L, 0L, "1985-12-21", "Japanese"),
            listOf<Any?>(5L, 0L, "N/A", ""),
            listOf<Any?>(0L, 9L, null, "English"),
            listOf<Any?>(0L, 0L, null, null),
        )
        for (c in cases) {
            val rev = c[0] as Long; val bud = c[1] as Long; val date = c[2] as String?; val lang = c[3] as String?
            assertEquals(movieInfoRows(rev, bud, date, lang).size, movieInfoRowCount(rev, bud, date, lang))
        }
    }

    @Test
    fun `money keeps its grouping and the release date its 16 sp`() {
        val rows = movieInfoRows(75_634_409, 0, "1993-10-09", null)
        assertEquals("Revenue:          $75,634,409.00", rows[0].text)
        assertEquals(listOf(17, 16), rows.map { it.fontSizeSp })
    }
}
