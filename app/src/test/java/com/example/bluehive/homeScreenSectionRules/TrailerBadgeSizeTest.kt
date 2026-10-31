package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Latest Trailers card's type and count badges are 20% smaller than the
 * trending frame's (since v567), and the film strips tried in v567 are gone
 * (v568): the picture fills the TV's screen again.
 */
class TrailerBadgeSizeTest {

    private val card = MainSource.code("latestTrailersComponents/HomeScreenTrailerAdapterCompose.kt")
    private val section = MainSource.code("latestTrailersComponents/HomeScreenTrailerSectionCompose.kt")

    @Test
    fun `the type and count badges are 20 percent smaller`() {
        assertTrue(card.contains("private val BADGE_FONT_SIZE = 8.sp"))
        assertTrue(card.contains("private val BADGE_PAD_H = 7.2.dp"))
        assertTrue(card.contains("private val BADGE_PAD_V = 3.2.dp"))
        assertTrue(card.contains("private val BADGE_SHAPE = RoundedCornerShape(2.4.dp)"))
        assertFalse(card.contains("fontSize = 10.sp"))
        assertFalse(card.contains("padding(horizontal = 9.dp, vertical = 4.dp)"))
    }

    @Test
    fun `no film strips, and the picture fills the screen`() {
        assertFalse(section.contains("TrailerFilmStrips"))
        assertTrue(section.contains("val viewportH = 182.35919189453125.dp"))
        val main = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        assertFalse(File(main, "com/example/bluehive/latestTrailersComponents/TrailerFilmStrips.kt").exists())
    }
}
