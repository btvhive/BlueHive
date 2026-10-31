package com.example.bluehive.homeScreenSectionRules

import androidx.compose.ui.unit.dp
import com.example.bluehive.latestTrailersComponents.FILM_STRIP_HEIGHT
import com.example.bluehive.latestTrailersComponents.TRAILER_CARD_HEIGHT
import com.example.bluehive.latestTrailersComponents.TRAILER_CARD_WIDTH
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v567: film strips along the top and bottom of the Latest Trailers TV's
 * screen, running all the time, with the picture between them; and the type
 * and count badges 20% smaller.
 */
class TrailerFilmStripsTest {

    private val section = MainSource.code("latestTrailersComponents/HomeScreenTrailerSectionCompose.kt")
    private val strips = MainSource.code("latestTrailersComponents/TrailerFilmStrips.kt")
    private val nav = MainSource.code("latestTrailersComponents/HomeScreenTrailerNavigationCompose.kt")
    private val card = MainSource.code("latestTrailersComponents/HomeScreenTrailerAdapterCompose.kt")

    @Test
    fun `the picture is the screen's width and a strip shorter top and bottom`() {
        assertEquals(12.dp, FILM_STRIP_HEIGHT)
        assertEquals(325.19415283203125.dp, TRAILER_CARD_WIDTH)
        assertEquals(182.35919189453125.dp - 24.dp, TRAILER_CARD_HEIGHT)
        assertTrue(section.contains("val viewportW = TRAILER_CARD_WIDTH"))
        assertTrue(section.contains("val viewportH = TRAILER_CARD_HEIGHT"))
        assertTrue(section.contains("val viewportX = screenX"))
        assertTrue(section.contains("val viewportY = screenY + FILM_STRIP_HEIGHT"))
        // The carousel is that size too, and slides by that width.
        assertTrue(nav.contains(".size(TRAILER_CARD_WIDTH, TRAILER_CARD_HEIGHT)"))
        assertTrue(nav.contains("val cardWidthPx = with(density) { TRAILER_CARD_WIDTH.toPx() }"))
    }

    @Test
    fun `the strips cover the screen's top and bottom and run without stopping`() {
        assertTrue(section.contains("TrailerFilmStrips(x = screenX, y = screenY, width = screenW, height = screenH)"))
        assertTrue(strips.contains("infiniteRepeatable(tween(FILM_STRIP_LOOP_MS, easing = LinearEasing), RepeatMode.Restart)"))
        assertTrue(strips.contains("for (top in floatArrayOf(0f, size.height - stripH))"))
        // Read while drawing, so the endless animation never recomposes.
        assertTrue(strips.substringAfter("Canvas(").contains("val shift = travel.value * pitch"))
    }

    @Test
    fun `the type and count badges are 20 percent smaller`() {
        assertTrue(card.contains("private val BADGE_FONT_SIZE = 8.sp"))
        assertTrue(card.contains("private val BADGE_PAD_H = 7.2.dp"))
        assertTrue(card.contains("private val BADGE_PAD_V = 3.2.dp"))
        assertTrue(card.contains("private val BADGE_SHAPE = RoundedCornerShape(2.4.dp)"))
        assertTrue(!card.contains("fontSize = 10.sp"))
        assertTrue(!card.contains("padding(horizontal = 9.dp, vertical = 4.dp)"))
    }
}
