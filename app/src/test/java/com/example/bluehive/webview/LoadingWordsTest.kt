package com.example.bluehive.webview

import androidx.compose.ui.unit.dp
import com.example.bluehive.details.shared.playback.SLOW_LINES
import com.example.bluehive.details.shared.playback.slowLineFor
import com.example.bluehive.details.shared.playback.stageWords
import com.example.bluehive.details.shared.playback.stepWords
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v576: the loading screen talks like the browser it is -- the stage timer's
 * words, or the server browser's real step when it sends one -- names the
 * source being tried, and keeps one centred layout on every TV.
 */
class LoadingWordsTest {

    @Test
    fun `the timer walks the browser's steps in order`() {
        assertEquals("Opening the site in a secure browser…", stageWords(1))
        assertEquals("Loading the page…", stageWords(2))
        assertEquals("Starting the site’s video player…", stageWords(3))
        assertEquals("Catching the stream from the player…", stageWords(4))
    }

    @Test
    fun `the server's real steps have words, and an unknown one falls back`() {
        assertEquals("Opening the site in a secure browser…", stepWords("opening"))
        assertEquals("Page loaded, starting the site’s video player…", stepWords("page_loaded"))
        assertEquals("Stream found, checking it plays…", stepWords("checking"))
        assertNull(stepWords("something_new"))
    }

    @Test
    fun `a long step alternates with reassurance, then comes back`() {
        val base = "Catching the stream from the player…"
        assertEquals(base, slowLineFor(base, 0))
        assertEquals(SLOW_LINES[0], slowLineFor(base, 1))
        assertEquals(base, slowLineFor(base, 2))
        assertEquals(SLOW_LINES[1], slowLineFor(base, 3))
        assertEquals(SLOW_LINES[2], slowLineFor(base, 5))
        assertEquals(SLOW_LINES[0], slowLineFor(base, 7))
    }

    @Test
    fun `the headline names the source`() {
        assertEquals("Browsing source 2 of 3: Royal Jelly", chainHeadline(2, 3, "Royal Jelly"))
        assertEquals("Browsing Royal Jelly", chainHeadline(1, 1, "Royal Jelly"))
        assertEquals("Browsing source 1 of 3", chainHeadline(1, 3, null))
        assertEquals("Browsing for your stream", chainHeadline(1, 1, ""))
    }

    @Test
    fun `fixed slots keep the block one height, the title 8 px higher`() {
        assertEquals((-4).dp, TITLE_RAISE)
        assertEquals(28.dp, HEADLINE_SLOT)
        assertEquals(44.dp, STATUS_SLOT)
        assertEquals(36.dp, NOTE_SLOT)
        val overlay = MainSource.code("webview/ExtractionLoadingOverlay.kt")
        assertTrue(overlay.contains("Box(Modifier.height(HEADLINE_SLOT)"))
        assertTrue(overlay.contains("Box(Modifier.height(STATUS_SLOT)"))
        assertTrue(overlay.contains("Box(Modifier.height(NOTE_SLOT)"))
        assertTrue(overlay.contains("modifier = Modifier.offset(y = TITLE_RAISE)"))
    }

    @Test
    fun `the job poll's step reaches the loading screen`() {
        assertTrue(MainSource.code("api/ApiService.kt").contains("val step:   String? = null,"))
        assertTrue(MainSource.code("api/ExtractAwait.kt").contains("poll.step?.let { onStep?.invoke(it) }"))
        assertTrue(MainSource.code("details/shared/playback/SourcePlaybackController.kt").contains("onStep  = { session.extractStep = it },"))
        assertTrue(MainSource.code("details/shared/playback/PlaybackOverlays.kt").contains("session.extractStep?.let(::stepWords) ?: stageWords(session.extractStage)"))
    }
}
