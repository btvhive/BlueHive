package com.example.bluehive.details.shared.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins TrailerState to what both details screens did before Stage 4 of the
 * details redesign moved the trailer flags into it: the Play/Pause button,
 * stop(), and the reports from DetailsTrailerPlayer. Except for the two
 * "Loading trailer..." fixes made after the redesign: a press after stop()
 * with the player made, and a failed extraction.
 */
class TrailerStateTest {

    private val url = "https://www.youtube.com/watch?v=abc123"

    @Test
    fun firstPress_startsTheExtractionAndPlays() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        assertTrue(trailer.shouldExtract)
        assertTrue(trailer.isExtracting)
        assertTrue(trailer.hasPlayed)
        assertTrue(trailer.isPlaying)
    }

    @Test
    fun secondPress_pausesAndKeepsThePlayerView() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.onButtonPressed()
        assertFalse(trailer.isPlaying)
        assertTrue(trailer.hasPlayed)
        assertTrue(trailer.shouldExtract)
    }

    @Test
    fun noTrailer_aPressDoesNothing() {
        for (none in listOf(null, "", "   ")) {
            val trailer = TrailerState(none)
            trailer.onButtonPressed()
            assertFalse(trailer.isAvailable)
            assertFalse(trailer.isPlaying)
            assertFalse(trailer.hasPlayed)
            assertFalse(trailer.shouldExtract)
            assertFalse(trailer.isExtracting)
        }
    }

    @Test
    fun playerReady_endsTheExtraction() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.onPlayerReady(null)
        assertFalse(trailer.isExtracting)
        assertTrue(trailer.isPlaying)
    }

    @Test
    fun stop_clearsOnlyPlayingAndPlayed() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.onPlayerReady(null)
        trailer.stop()
        assertFalse(trailer.isPlaying)
        assertFalse(trailer.hasPlayed)
        // Neither screen's reset ever touched these.
        assertTrue(trailer.shouldExtract)
        assertFalse(trailer.isExtracting)
    }

    /**
     * The fix for case 1. DetailsTrailerPlayer reports a player only when it
     * builds one, so after stop() -- the player still there and ready -- a press
     * that showed "Loading trailer..." left it up for good while the trailer
     * played. Now it plays without it. (The tests report a null player: a JVM
     * test cannot make an ExoPlayer, and only the report matters here.)
     */
    @Test
    fun pressAfterStop_withThePlayerMade_playsWithoutLoading() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.onPlayerReady(null)
        trailer.stop()
        trailer.onButtonPressed()
        assertFalse(trailer.isExtracting)
        assertTrue(trailer.isPlaying)
        assertTrue(trailer.hasPlayed)
    }

    /** Stopped while the stream is still being extracted: the loading line stays until the player is made. */
    @Test
    fun pressAfterStop_beforeThePlayerIsMade_keepsLoading() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.stop()
        trailer.onButtonPressed()
        assertTrue(trailer.isExtracting)
        assertTrue(trailer.isPlaying)
        trailer.onPlayerReady(null)
        assertFalse(trailer.isExtracting)
    }

    /**
     * The fix for case 2. A failed extraction never made a player, so nothing
     * cleared "Loading trailer...", and shouldExtract stayed true, so no press
     * could ask for the extraction again. Now the button is back to before the
     * first press, and the next press asks again.
     */
    @Test
    fun failedExtraction_clearsLoadingAndTheNextPressRetries() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.onExtractionFailed()
        assertFalse(trailer.isExtracting)
        assertFalse(trailer.shouldExtract)
        assertFalse(trailer.hasPlayed)
        assertFalse(trailer.isPlaying)

        trailer.onButtonPressed()
        assertTrue(trailer.shouldExtract)
        assertTrue(trailer.isExtracting)
        assertTrue(trailer.hasPlayed)
        assertTrue(trailer.isPlaying)
    }

    @Test
    fun seekButtons_doNothingWithoutAPlayer() {
        val trailer = TrailerState(url)
        trailer.onButtonPressed()
        trailer.rewind()
        trailer.fastForward()
        assertTrue(trailer.isPlaying)
    }

    @Test
    fun playbackReport_setsPlaying() {
        val trailer = TrailerState(url)
        trailer.onPlaybackStateChanged(true)
        assertTrue(trailer.isPlaying)
        trailer.onPlaybackStateChanged(false)
        assertFalse(trailer.isPlaying)
    }
}
