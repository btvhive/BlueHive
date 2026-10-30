package com.example.bluehive.details.shared.layout

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 8 (v515): opening or closing the search keyboard recomposed the whole
 * movie screen (and the TV screen, also on each episode-overlay change),
 * because the screen read whether to blur while composing; each recomposition
 * while blurred also made a new blur. detailsBackdropBlur reads it in the
 * layer and makes the blur once.
 */
class DetailsBackdropBlurTest {

    @Test
    fun `the blur is for API 31 and up`() {
        assertFalse(blurSupported(25))
        assertFalse(blurSupported(30))
        assertTrue(blurSupported(31))
        assertTrue(blurSupported(34))
    }

    @Test
    fun `both screens hand the blur a lambda`() {
        val movie = MainSource.code("details/moviesdetails/MovieDetailsScreen.kt")
        val tv = MainSource.code("details/tvshowdetails/TvShowDetailsScreen.kt")
        assertTrue(movie.contains("detailsBackdropBlur { search.isOpen }"))
        assertTrue(tv.contains("detailsBackdropBlur { search.isOpen || episodeOverlayOpen(episodesState) }"))
    }

    @Test
    fun `no details source uses the composition-time blur`() {
        val users = MainSource.all().filter { (path, code) -> path.startsWith("details/") && code.contains("hiveBackdropBlur") }
        assertEquals(emptySet<String>(), users.keys)
    }

    @Test
    fun `the blur is read in the layer and made once`() {
        val code = MainSource.code("details/shared/layout/DetailsBackdropBlur.kt")
        assertTrue(code.contains("graphicsLayer { renderEffect = if (active()) PageBlur else null }"))
        assertTrue(code.contains("private val PageBlur: RenderEffect by lazy"))
    }
}
