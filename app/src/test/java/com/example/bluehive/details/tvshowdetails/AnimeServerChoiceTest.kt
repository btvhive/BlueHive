package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.testutil.MainSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v585: the anime episode card asks for the server itself. English or
 * Japanese snaps the row away and Default server / Choose a server fade in
 * where it was -- the same buttons and glow -- and Choose a server lists the
 * servers in the same card. No second overlay on the TV page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnimeServerChoiceTest {

    private fun step(
        anime: Boolean = true,
        picker: Boolean = false,
        servers: Boolean = true,
        prompt: Boolean = false,
        checking: Boolean = false,
    ) = animeCardStep(anime, picker, servers, prompt, checking)

    @Test
    fun `the card's step follows the anime flow`() {
        assertEquals(AnimeCardStep.Language, step())
        assertEquals(AnimeCardStep.Pair, step(prompt = true))
        assertEquals(AnimeCardStep.Pair, step(checking = true))      // no language row behind the loader
        assertEquals(AnimeCardStep.List, step(picker = true))
        assertEquals(AnimeCardStep.Language, step(picker = true, servers = false))
        assertEquals(AnimeCardStep.Language, step(anime = false, prompt = true))
    }

    private fun TestScope.row(dust: Boolean) =
        RowSnapState(scope = this, rendererOf = { null }, warn = { _, _ -> }, startDust = { dust })

    @Test
    fun `the row turns to dust first, and the choice comes only after it`() = runTest {
        val r = row(dust = true)
        var switched = false
        r.snapThen { switched = true }
        runCurrent()
        assertTrue(r.busy)
        advanceTimeBy(SNAP_DURATION_MS - 1)
        assertFalse("the pair waits for the dust", switched)
        advanceTimeBy(2)
        assertTrue(switched)
        assertTrue("the row's place is the pair's, fading in", r.rowVisible)
        assertTrue("keys stay swallowed while the pair fades in", r.busy)
        advanceUntilIdle()
        assertFalse(r.busy)
        assertFalse(r.rendererWanted)
    }

    @Test
    fun `a second press while the row snaps does nothing`() = runTest {
        val r = row(dust = true)
        var presses = 0
        r.snapThen { presses++ }
        r.snapThen { presses++ }
        advanceUntilIdle()
        assertEquals(1, presses)
    }

    @Test
    fun `no renderer, no dust -- the choice still comes`() = runTest {
        val r = row(dust = false)
        var switched = false
        r.snapThen { switched = true }
        runCurrent()
        assertTrue(switched)
        advanceUntilIdle()
        assertFalse(r.busy)
    }

    @Test
    fun `the TV page draws no second overlay, the movie page keeps its own`() {
        val overlays = MainSource.code("details/shared/playback/PlaybackOverlays.kt")
        assertTrue(overlays.contains("anime.showDefaultPrompt && !serverChoiceInCard"))
        assertTrue(overlays.contains("anime.serverOptions.isNotEmpty() && !serverChoiceInCard"))
        val tv = MainSource.code("details/tvshowdetails/TvShowDetailsScreen.kt")
        assertTrue(tv.contains("AnimeServerOverlays(anime, dongleRegular, serverChoiceInCard = true)"))
        val movie = MainSource.code("details/moviesdetails/MovieDetailsScreen.kt")
        assertTrue(movie.contains("AnimeServerOverlays(anime, dongleRegular)"))
    }

    @Test
    fun `English and Japanese snap the row, then ask for the server`() {
        val card = MainSource.code("details/tvshowdetails/EpisodeSourceOverlay.kt")
        assertEquals(2, Regex("""snap\.row\.snapThen \{ anime\.showDefaultPrompt = true \}""").findAll(card).count())
        assertFalse(card.contains("anime.showDefaultPrompt  = true"))
        assertTrue(card.contains(".rowSnap(snap.row)"))
        assertTrue(card.contains("AnimeInlineServerList("))
    }

    @Test
    fun `the pair is the card's own buttons, glow and size unchanged`() {
        val buttons = MainSource.code("details/tvshowdetails/EpisodeAnimeButtons.kt")
        val pair = buttons.substringAfter("internal fun AnimeServerChoiceButtons(").substringBefore("\n}\n")
        assertEquals(2, Regex("""OverlayWatchButton\(""").findAll(pair).count())
        assertTrue(pair.contains("tween(SERVER_PAIR_FADE_MS)"))
    }
}
