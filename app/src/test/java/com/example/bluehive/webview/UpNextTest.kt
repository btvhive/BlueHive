package com.example.bluehive.webview

import com.example.bluehive.api.ExtractResponse
import com.example.bluehive.details.shared.playback.PlaybackRequest
import com.example.bluehive.details.shared.playback.PlayerExtra
import com.example.bluehive.details.shared.playback.PlayerIntents
import com.example.bluehive.details.shared.playback.UpNextEpisode
import com.example.bluehive.details.tvshowdetails.nextEpisodeIn
import com.example.bluehive.models.Episode
import com.example.bluehive.testutil.MainSource
import com.example.bluehive.webview.hls.HlsPlayerActivity
import com.example.bluehive.webview.hls.NextEpisode
import com.example.bluehive.webview.hls.STILL_WATCHING_LEAD_MS
import com.example.bluehive.webview.hls.UNATTENDED_LIMIT
import com.example.bluehive.webview.hls.UP_NEXT_WINDOW_MS
import com.example.bluehive.webview.hls.UpNextPhase
import com.example.bluehive.webview.hls.shouldAskStillWatching
import com.example.bluehive.webview.hls.unattendedAfter
import com.example.bluehive.webview.hls.upNextHeading
import com.example.bluehive.webview.hls.upNextPhaseFor
import com.example.bluehive.webview.hls.upNextSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v580: the last 10 s of an episode show the next one, and it opens when the
 * stream ends; every third untouched episode asks "Still watching?" 30 s out.
 */
class UpNextTest {

    private val dur = 45L * 60_000L

    private fun phase(remaining: Long, cancelled: Boolean = false, ask: Boolean = false, dismissed: Boolean = false, hasNext: Boolean = true) =
        upNextPhaseFor(remaining, dur, hasNext, cancelled, ask, dismissed)

    @Test
    fun `the card shows for the last ten seconds of the stream`() {
        assertEquals(10_000L, UP_NEXT_WINDOW_MS)
        assertEquals(UpNextPhase.Hidden, phase(10_001))
        assertEquals(UpNextPhase.Card, phase(10_000))
        assertEquals(UpNextPhase.Card, phase(0))
        assertEquals(UpNextPhase.Hidden, phase(5_000, cancelled = true))
    }

    @Test
    fun `no next episode, or no real duration, shows nothing`() {
        assertEquals(UpNextPhase.Hidden, phase(5_000, hasNext = false))
        assertEquals(UpNextPhase.Hidden, upNextPhaseFor(5_000, 90_000, true, false, false, false))
    }

    @Test
    fun `the prompt shows 30 s out, then the card 20 s later`() {
        assertEquals(30_000L, STILL_WATCHING_LEAD_MS)
        assertEquals(UpNextPhase.Hidden, phase(30_001, ask = true))
        assertEquals(UpNextPhase.StillWatching, phase(30_000, ask = true))
        assertEquals(UpNextPhase.StillWatching, phase(10_001, ask = true))
        assertEquals(UpNextPhase.Card, phase(10_000, ask = true))
        assertEquals(UpNextPhase.Hidden, phase(20_000, ask = true, dismissed = true))
    }

    @Test
    fun `the third episode in a row without a press asks`() {
        assertEquals(3, UNATTENDED_LIMIT)
        assertFalse(shouldAskStillWatching(0, false))
        assertFalse(shouldAskStillWatching(1, false))
        assertTrue(shouldAskStillWatching(2, false))
        assertFalse(shouldAskStillWatching(2, true))
        assertEquals(1, unattendedAfter(0, false))
        assertEquals(3, unattendedAfter(2, false))
        assertEquals(0, unattendedAfter(2, true))
    }

    @Test
    fun `the countdown runs 10 to 1 on the stream's clock`() {
        assertEquals(10, upNextSeconds(10_000))
        assertEquals(10, upNextSeconds(9_001))
        assertEquals(7, upNextSeconds(6_500))
        assertEquals(1, upNextSeconds(1))
        assertEquals(1, upNextSeconds(0))
    }

    @Test
    fun `the heading names the season, episode and title`() {
        assertEquals("S1 E2  ·  Compulsion", upNextHeading(NextEpisode(1, 2, "Compulsion", null)))
        assertEquals("S3 E4", upNextHeading(NextEpisode(3, 4, " ", null)))
    }

    private fun ep(n: Int, airDate: String? = "2020-01-01", still: String? = "https://image.tmdb.org/t/p/w300/s$n.jpg") = Episode(
        episodeId = n, tmdbEpisodeId = n, episodeNumber = n, title = "Episode $n", overview = null,
        airDate = airDate, runtime = null, stillImageUrl = still, rating = null,
    )

    @Test
    fun `the next episode comes from the season on show, never past its end`() {
        val season = listOf(ep(1), ep(2), ep(3, airDate = "2099-01-01"))
        val today = 20261031
        assertEquals(UpNextEpisode(2, "Episode 2", "https://image.tmdb.org/t/p/w300/s2.jpg"),
            nextEpisodeIn(season, 1, false, 1, 1, today))
        assertNull(nextEpisodeIn(season, 1, false, 1, 2, today))   // next one unaired
        assertNull(nextEpisodeIn(season, 1, false, 1, 3, today))   // season's end
        assertNull(nextEpisodeIn(season, 2, false, 1, 1, today))   // grid shows another season
        assertNull(nextEpisodeIn(season, 1, true, 1, 1, today))    // still loading
    }

    private val body = ExtractResponse(
        isHls = null, m3u8 = "https://cdn.example/x.m3u8", referer = "https://embed.example/",
        user_agent = "Mozilla/5.0 test", subtitle_url = null, page_url = "https://embed.example/tv/1438/1/1",
        proxy_url = null,
    )

    private fun hls(next: UpNextEpisode?, unattended: Int = 0) = PlayerIntents.hlsPlayerExtras(
        request = PlaybackRequest.Episode(1438, 1, 1, "Extreme Aggressor"), body = body,
        embedUrl = "https://vidnest.fun/tv/1438/1/1", sourceName = "VidNest", profileId = 7,
        mediaType = "tv", mediaTitle = "Criminal Minds", startAtMs = 0, next = next, unattended = unattended,
    )

    @Test
    fun `the player is told the next episode, and nothing without one`() {
        val extras = hls(UpNextEpisode(2, "Compulsion", "https://image.tmdb.org/t/p/w300/s2.jpg"), unattended = 2)
        assertTrue(PlayerExtra.Whole(HlsPlayerActivity.EXTRA_NEXT_EPISODE, 2) in extras)
        assertTrue(PlayerExtra.Text(HlsPlayerActivity.EXTRA_NEXT_TITLE, "Compulsion") in extras)
        assertTrue(PlayerExtra.Text(HlsPlayerActivity.EXTRA_NEXT_STILL, "https://image.tmdb.org/t/p/w300/s2.jpg") in extras)
        assertTrue(PlayerExtra.Whole(HlsPlayerActivity.EXTRA_UNATTENDED, 2) in extras)
        val none = hls(null).map { it.key }
        assertFalse(HlsPlayerActivity.EXTRA_NEXT_EPISODE in none)
        assertFalse(HlsPlayerActivity.EXTRA_UNATTENDED in none)
    }

    @Test
    fun `the still stays TMDB's small one, and the overlay is drawn last`() {
        val upNext = MainSource.code("webview/hls/UpNext.kt")
        assertTrue(upNext.contains(".size(300, 169)"))
        assertFalse(upNext.contains("fullSizeStill"))
        val player = MainSource.code("webview/hls/HlsPlayerActivity.kt")
        val subs = player.indexOf("SubtitleLayer()\n")
        val card = player.indexOf("UpNextCard(next, upNextRemainingMs")
        assertTrue(subs in 0 until card)
    }

    @Test
    fun `an ended episode opens the next through the details screen`() {
        val player = MainSource.code("webview/hls/HlsPlayerActivity.kt")
        assertTrue(player.contains("autoplayAtEnd()) {\n                            playNextEpisode(\"episode ended\")"))
        val controller = MainSource.code("details/shared/playback/SourcePlaybackController.kt")
        assertTrue(controller.contains("HlsPlayerActivity.RESULT_PLAY_NEXT"))
        assertTrue(controller.contains("LaunchedEffect(controller.playNextHop)"))
        assertTrue(controller.contains("sources.firstOrNull { it.name.equals(hop.source, ignoreCase = true) }"))
    }
}
