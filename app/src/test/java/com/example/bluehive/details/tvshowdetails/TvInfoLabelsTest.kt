package com.example.bluehive.details.tvshowdetails

import androidx.compose.ui.unit.dp
import com.example.bluehive.details.shared.layout.detailsGenresTop
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TV page's metadata lines: missing ones drop out, the rest close up, genres follow. */
class TvInfoLabelsTest {

    @Test
    fun `all four lines keep their text and sizes, genres at 96 dp`() {
        val lines = tvInfoLines(3, "Ended", "2014-04-06", "Japanese")
        assertEquals(
            listOf("Seasons:          3", "Status:            Ended", "Release Date:     04/06/2014", "Language:        Japanese"),
            lines.map { it.text },
        )
        assertEquals(listOf(17, 17, 16, 17), lines.map { it.fontSizeSp })
        assertEquals(96.dp, detailsGenresTop(lines.size))
    }

    @Test
    fun `unknown values are left out, not printed as N A`() {
        val lines = tvInfoLines(0, "N/A", "", "English")
        assertEquals(listOf("Language:        English"), lines.map { it.text })
        assertEquals(34.dp + 21.dp, detailsGenresTop(lines.size))
    }

    @Test
    fun `a date not in yyyy-mm-dd keeps its label`() {
        assertEquals("Release Date:     2014", tvInfoLines(0, null, "2014", null).single().text)
    }

    @Test
    fun `the count the screen places the genres with always matches the lines`() {
        val cases = listOf(
            listOf<Any?>(3, "Ended", "2014-04-06", "Japanese"),
            listOf<Any?>(0, "N/A", "", "English"),
            listOf<Any?>(1, null, null, null),
            listOf<Any?>(0, null, null, null),
            listOf<Any?>(2, " ", "N/A", "N/A"),
        )
        for (c in cases) {
            val n = c[0] as Int; val s = c[1] as String?; val d = c[2] as String?; val l = c[3] as String?
            assertEquals(tvInfoLines(n, s, d, l).size, tvInfoLineCount(n, s, d, l))
        }
    }
}
