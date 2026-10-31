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
 * v580/v581: the last minute of an episode shows the next one, counting down
 * from 60, and it opens when the stream ends; every third untouched episode
 * asks "Still watching?" 20 s before the card. TV shows and anime alike.
 */
class UpNextTest {

    private val dur = 45L * 60_000L

    private fun phase(remaining: Long, cancelled: Boolean = false, ask: Boolean = false, dismissed: Boolean = false, hasNext: Boolean = true) =
        upNextPhaseFor(remaining, dur, hasNext, cancelled, ask, dismissed)

    @Test
    fun `the card shows for the last minute of the stream`() {
        assertEquals(60_000L, UP_NEXT_WINDOW_MS)
        assertEquals(UpNextPhase.Hidden, phase(60_001))
        assertEquals(UpNextPhase.Card, phase(60_000))
        assertEquals(UpNextPhase.Card, phase(0))
        assertEquals(UpNextPhase.Hidden, phase(5_000, cancelled = true))
    }

    @Test
    fun `no next episode, or no real duration, shows nothing`() {
        assertEquals(UpNextPhase.Hidden, phase(5_000, hasNext = false))
        assertEquals(UpNextPhase.Hidden, upNextPhaseFor(5_000, 90_000, true, false, false, false))
    }

    @Test
    fun `the prompt shows 80 s out, then the card 20 s later`() {
        assertEquals(80_000L, STILL_WATCHING_LEAD_MS)
        assertEquals(UpNextPhase.Hidden, phase(80_001, ask = true))
        assertEquals(UpNextPhase.StillWatching, phase(80_000, ask = true))
        assertEquals(UpNextPhase.StillWatching, phase(60_001, ask = true))
        assertEquals(UpNextPhase.Card, phase(60_000, ask = true))
        assertEquals(UpNextPhase.Hidden, phase(70_000, ask = true, dismissed = true))
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
    fun `the countdown runs 60 to 1 on the stream's clock`() {
        assertEquals(60, upNextSeconds(60_000))
        assertEquals(60, upNextSeconds(59_001))
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
    fun `the anime player is told the next episode too`() {
        val extras = PlayerIntents.miruroBackendExtras(
            request = PlaybackRequest.Episode(37854, 1, 1, "Romance Dawn"), body = body, sourceName = "Miruro (Sub)",
            profileId = 7, mediaType = "tv", mediaTitle = "One Piece", playingDub = false, server = null,
            next = UpNextEpisode(2, "Enter the Great Swordsman", null), unattended = 1,
        )
        assertTrue(PlayerExtra.Whole(HlsPlayerActivity.EXTRA_NEXT_EPISODE, 2) in extras)
        assertTrue(PlayerExtra.Whole(HlsPlayerActivity.EXTRA_UNATTENDED, 1) in extras)
    }

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
    fun `the still stays TMDB's small one, at half the v580 size`() {
        val upNext = MainSource.code("webview/hls/UpNext.kt")
        assertTrue(upNext.contains(".size(300, 169)"))
        assertFalse(upNext.contains("fullSizeStill"))
        assertTrue(upNext.contains("UP_NEXT_STILL_W = 110.dp"))
        assertTrue(upNext.contains("UP_NEXT_STILL_H = 62.dp"))
        assertTrue(upNext.contains(".height(22.dp)"))
    }

    @Test
    fun `both players draw it last and open the next episode when one ends`() {
        for (path in listOf("webview/hls/HlsPlayerActivity.kt", "webview/miruro/MiruroPlayerActivity.kt")) {
            val player = MainSource.code(path)
            val subs = player.indexOf("SubtitleLayer()\n")
            val card = player.indexOf("upNext?.Overlay(this)")
            assertTrue(path, subs in 0 until card)
            assertTrue(path, player.contains("upNext?.autoplayAtEnd(player) == true"))
            assertTrue(path, player.contains("playNextEpisode(\"episode ended\")"))
            assertTrue(path, player.contains("RESULT_PLAY_NEXT, data)"))
            assertTrue(path, player.contains("upNext?.onKeyDown()"))
        }
    }

    @Test
    fun `the details screen opens the next episode for shows and anime`() {
        val source = MainSource.code("details/shared/playback/SourcePlaybackController.kt")
        assertTrue(source.contains("LaunchedEffect(controller.playNextHop)"))
        assertTrue(source.contains("sources.firstOrNull { it.name.equals(hop.source, ignoreCase = true) }"))
        val anime = MainSource.code("details/shared/playback/AnimePlaybackController.kt")
        assertTrue(anime.contains("LaunchedEffect(controller.playNextHop)"))
        assertTrue(anime.contains("controller.playerLauncher = rememberLauncherForActivityResult("))
        assertFalse("anime players launch for their result now", anime.contains("context.startActivity("))
        assertTrue(anime.contains("launchExtract(url, pendingDub, null)"))
        val screen = MainSource.code("details/tvshowdetails/TvShowDetailsScreen.kt")
        assertEquals(2, Regex("""onNextEpisode = \{ season, episode, title -> episodesState.recordUpNext""").findAll(screen).count())
    }

    @Test
    fun `the source overlay is closed before the page shows again`() {
        val episodes = MainSource.code("details/tvshowdetails/UpNextEpisodes.kt")
        assertTrue(episodes.contains("selectedEpisodeOverlayIndex = -1"))
        // Straight from the result, not a frame later from the effect.
        for (path in listOf("details/shared/playback/SourcePlaybackController.kt", "details/shared/playback/AnimePlaybackController.kt")) {
            val code = MainSource.code(path)
            val result = code.indexOf("val hop = playNextHopFrom(result.data) ?: return")
            val record = code.indexOf("onNextEpisode(hop.season, hop.episode, hop.title)")
            assertTrue(path, result in 0 until record)
            assertEquals(path, 1, Regex("""onNextEpisode\(hop\.season""").findAll(code).count())
        }
    }
}
