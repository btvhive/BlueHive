package com.example.bluehive.details.shared.playback

import com.example.bluehive.api.ExtractResponse
import com.example.bluehive.webview.hls.HlsExtract
import com.example.bluehive.webview.hls.HlsPlayerActivity
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PlayerIntents against the record of every extra the details screens put on
 * their launches before the redesign (DetailsLaunchExtrasGolden, v376): the
 * same keys in the same order, each value from the source the record names,
 * each with the type its putExtra overload needs.
 */
class PlayerIntentsTest {

    private val movie = PlaybackRequest.Movie(tmdbId = 27205)
    private val episode = PlaybackRequest.Episode(tmdbId = 1396, season = 2, episode = 5, episodeName = "Breakage")
    private val unnamedEpisode = episode.copy(episodeName = null)

    private val body = ExtractResponse(
        isHls = null,
        m3u8 = "https://cdn.example/a/master.m3u8",
        referer = "https://embed.example/",
        user_agent = "Mozilla/5.0 test",
        subtitle_url = "https://subs.example/en.vtt",
        page_url = "https://embed.example/movie/27205",
        proxy_url = "https://relay.example/r/abc",
    )

    private fun List<PlayerExtra>.keys() = map { it.key }
    private fun List<PlayerExtra>.of(key: String) = single { it.key == key }
    private fun golden(name: String) = DetailsLaunchExtrasGolden.all.getValue(name).map { it.key }

    // ── HlsPlayerActivity ───────────────────────────────────────────────────

    private fun hls(request: PlaybackRequest, startAtMs: Long = 0L, b: ExtractResponse = body) =
        PlayerIntents.hlsPlayerExtras(
            request = request, body = b, embedUrl = "https://vidnest.fun/movie/27205",
            sourceName = "VidNest", profileId = 7, mediaType = "movie", mediaTitle = "Inception",
            startAtMs = startAtMs,
        )

    @Test
    fun `movie HLS player carries the recorded keys in order`() {
        assertEquals(golden("movieHlsPlayer"), hls(movie).keys())
    }

    @Test
    fun `episode HLS player carries the recorded keys in order`() {
        assertEquals(golden("tvHlsPlayer"), hls(episode).keys())
    }

    @Test
    fun `HLS player values and types`() {
        val e = hls(episode, startAtMs = 90_000L)
        assertEquals(PlayerExtra.Text("M3U8_URL", body.m3u8), e.of("M3U8_URL"))
        assertEquals(PlayerExtra.Text("M3U8_FALLBACK_URL", body.proxy_url), e.of("M3U8_FALLBACK_URL"))
        assertEquals(PlayerExtra.Text("REFERER", body.referer), e.of("REFERER"))
        assertEquals(PlayerExtra.Flag("IS_HLS", true), e.of("IS_HLS"))
        assertEquals(PlayerExtra.Text("UA", body.user_agent), e.of("UA"))
        assertEquals(PlayerExtra.Whole("TMDB_ID", 1396), e.of("TMDB_ID"))
        assertEquals(PlayerExtra.Text(HlsExtract.EXTRA_EXTRACTOR_CLASS, ""), e.of(HlsExtract.EXTRA_EXTRACTOR_CLASS))
        assertEquals(PlayerExtra.Text("EMBED_URL", "https://vidnest.fun/movie/27205"), e.of("EMBED_URL"))
        assertEquals(PlayerExtra.Text("EXTRACT_SOURCE", "vidnest"), e.of("EXTRACT_SOURCE"))
        assertEquals(PlayerExtra.Whole("PROFILE_ID", 7), e.of("PROFILE_ID"))
        assertEquals(PlayerExtra.Text("MEDIA_TYPE", "movie"), e.of("MEDIA_TYPE"))
        assertEquals(PlayerExtra.Text("MEDIA_TITLE", "Inception"), e.of("MEDIA_TITLE"))
        assertEquals(PlayerExtra.Whole("SEASON_NUMBER", 2), e.of("SEASON_NUMBER"))
        assertEquals(PlayerExtra.Whole("EPISODE_NUMBER", 5), e.of("EPISODE_NUMBER"))
        assertEquals(PlayerExtra.Text("EPISODE_NAME", "Breakage"), e.of("EPISODE_NAME"))
        assertEquals(PlayerExtra.Text("SOURCE_NAME", "VidNest"), e.of("SOURCE_NAME"))
        assertEquals(PlayerExtra.Text(HlsPlayerActivity.EXTRA_SERVER_NAME, null), e.of(HlsPlayerActivity.EXTRA_SERVER_NAME))
        assertEquals(PlayerExtra.Text("SUBTITLE_URL", body.subtitle_url), e.of("SUBTITLE_URL"))
        assertEquals(PlayerExtra.Millis(HlsPlayerActivity.EXTRA_START_AT_MS, 90_000L), e.of(HlsPlayerActivity.EXTRA_START_AT_MS))
    }

    @Test
    fun `SERVER_NAME is the server the backend marked as serving`() {
        val servers = listOf(
            com.example.bluehive.api.ExtractServerOption("Zeta", false),
            com.example.bluehive.api.ExtractServerOption("Beta", true),
        )
        assertEquals(PlayerExtra.Text(HlsPlayerActivity.EXTRA_SERVER_NAME, "Beta"),
            hls(movie, b = body.copy(servers = servers)).of(HlsPlayerActivity.EXTRA_SERVER_NAME))
    }

    @Test
    fun `IS_HLS follows the backend and defaults to true`() {
        assertEquals(PlayerExtra.Flag("IS_HLS", false), hls(movie, b = body.copy(isHls = false)).of("IS_HLS"))
        assertEquals(PlayerExtra.Flag("IS_HLS", true), hls(movie, b = body.copy(isHls = null)).of("IS_HLS"))
    }

    @Test
    fun `an unnamed episode still carries EPISODE_NAME, as null`() {
        assertEquals(PlayerExtra.Text("EPISODE_NAME", null), hls(unnamedEpisode).of("EPISODE_NAME"))
    }

    // ── MiruroPlayerActivity after an on-device extract ─────────────────────

    private fun onDevice(request: PlaybackRequest, dub: Boolean = true, server: String? = "Kiwi") =
        PlayerIntents.miruroOnDeviceExtras(
            request = request, m3u8 = "https://sun.example/x.m3u8", referer = "https://strm.cx/",
            userAgent = "UA", sourceName = "Miruro (Dub)", profileId = 7, mediaType = "tv",
            mediaTitle = "Frieren", dub = dub, server = server,
        )

    @Test
    fun `on-device Miruro player carries the recorded keys in order`() {
        assertEquals(golden("movieMiruroOnDevice"), onDevice(movie).keys())
        assertEquals(golden("tvMiruroOnDevice"), onDevice(episode).keys())
    }

    /** animeRelayUrl mints a fresh sid per call, one per playback; everything else must match. */
    private fun withoutSid(url: String?): String {
        val sid = Regex("&sid=([0-9a-f]+)&").find(url!!)!!.groupValues[1]
        assertEquals("sid should be a 32-hex id", 32, sid.length)
        return url.replace("&sid=$sid&", "&sid=*&")
    }

    @Test
    fun `on-device relay url is tagged anime_movie for a film and anime_tv with season and episode for an episode`() {
        assertEquals(
            withoutSid(animeRelayUrl(
                m3u8 = "https://sun.example/x.m3u8", tmdbId = 27205, mediaType = "anime_movie",
                title = "Frieren", profileId = 7, sourceName = "Miruro (Dub)",
            )),
            withoutSid((onDevice(movie).of("M3U8_FALLBACK_URL") as PlayerExtra.Text).value),
        )
        assertEquals(
            withoutSid(animeRelayUrl(
                m3u8 = "https://sun.example/x.m3u8", tmdbId = 1396, mediaType = "anime_tv",
                title = "Frieren", profileId = 7, sourceName = "Miruro (Dub)",
                season = 2, episode = 5,
            )),
            withoutSid((onDevice(episode).of("M3U8_FALLBACK_URL") as PlayerExtra.Text).value),
        )
    }

    @Test
    fun `each on-device launch mints its own relay sid`() {
        val a = (onDevice(movie).of("M3U8_FALLBACK_URL") as PlayerExtra.Text).value
        val b = (onDevice(movie).of("M3U8_FALLBACK_URL") as PlayerExtra.Text).value
        assert(a != b) { "two launches must not share a relay session id" }
    }

    @Test
    fun `on-device Miruro player values and types`() {
        val e = onDevice(episode)
        assertEquals(PlayerExtra.Text("M3U8_URL", "https://sun.example/x.m3u8"), e.of("M3U8_URL"))
        assertEquals(PlayerExtra.Text("REFERER", "https://strm.cx/"), e.of("REFERER"))
        assertEquals(PlayerExtra.Text("UA", "UA"), e.of("UA"))
        assertEquals(PlayerExtra.Whole("PROFILE_ID", 7), e.of("PROFILE_ID"))
        assertEquals(PlayerExtra.Text("MEDIA_ID", "1396"), e.of("MEDIA_ID"))
        assertEquals(PlayerExtra.Text("MEDIA_TYPE", "tv"), e.of("MEDIA_TYPE"))
        assertEquals(PlayerExtra.Text("MEDIA_TITLE", "Frieren"), e.of("MEDIA_TITLE"))
        assertEquals(PlayerExtra.Whole("SEASON_NUMBER", 2), e.of("SEASON_NUMBER"))
        assertEquals(PlayerExtra.Whole("EPISODE_NUMBER", 5), e.of("EPISODE_NUMBER"))
        assertEquals(PlayerExtra.Text("EPISODE_NAME", "Breakage"), e.of("EPISODE_NAME"))
        assertEquals(PlayerExtra.Text("SOURCE_NAME", "Miruro (Dub)"), e.of("SOURCE_NAME"))
        assertEquals(PlayerExtra.Text("AUDIO", "dub"), e.of("AUDIO"))
        assertEquals(PlayerExtra.Text("SERVER", "Kiwi"), e.of("SERVER"))
        assertEquals(PlayerExtra.Text("AUDIO", "sub"), onDevice(episode, dub = false).of("AUDIO"))
        assertEquals(PlayerExtra.Text("SERVER", null), onDevice(episode, server = null).of("SERVER"))
    }

    // ── MiruroPlayerActivity after a backend extract ────────────────────────

    private fun backend(request: PlaybackRequest, playingDub: Boolean = false, server: String? = null) =
        PlayerIntents.miruroBackendExtras(
            request = request, body = body, sourceName = "Miruro (Sub)", profileId = 7,
            mediaType = "movie", mediaTitle = "Suzume", playingDub = playingDub, server = server,
        )

    @Test
    fun `backend Miruro player carries the recorded keys in order`() {
        assertEquals(golden("movieMiruroBackend"), backend(movie).keys())
        assertEquals(golden("tvMiruroBackend"), backend(episode).keys())
    }

    @Test
    fun `backend Miruro player values and types`() {
        val e = backend(movie, playingDub = true, server = "Arc")
        assertEquals(PlayerExtra.Text("M3U8_URL", body.m3u8), e.of("M3U8_URL"))
        assertEquals(PlayerExtra.Text("M3U8_FALLBACK_URL", body.proxy_url), e.of("M3U8_FALLBACK_URL"))
        assertEquals(PlayerExtra.Text("REFERER", body.referer), e.of("REFERER"))
        assertEquals(PlayerExtra.Flag("IS_HLS", true), e.of("IS_HLS"))
        assertEquals(PlayerExtra.Text("UA", body.user_agent), e.of("UA"))
        assertEquals(PlayerExtra.Whole("PROFILE_ID", 7), e.of("PROFILE_ID"))
        assertEquals(PlayerExtra.Text("MEDIA_ID", "27205"), e.of("MEDIA_ID"))
        assertEquals(PlayerExtra.Text("MEDIA_TYPE", "movie"), e.of("MEDIA_TYPE"))
        assertEquals(PlayerExtra.Text("MEDIA_TITLE", "Suzume"), e.of("MEDIA_TITLE"))
        assertEquals(PlayerExtra.Text("SOURCE_NAME", "Miruro (Sub)"), e.of("SOURCE_NAME"))
        assertEquals(PlayerExtra.Text("AUDIO", "dub"), e.of("AUDIO"))
        assertEquals(PlayerExtra.Text("SERVER", "Arc"), e.of("SERVER"))
        assertEquals(PlayerExtra.Text("AUDIO", "sub"), backend(movie, playingDub = false).of("AUDIO"))
        assertEquals(PlayerExtra.Text("SERVER", null), backend(movie, server = null).of("SERVER"))
    }

    // ── MiruroExtractorActivity ─────────────────────────────────────────────

    @Test
    fun `extract names the server when one was picked, and never asks to capture then`() {
        val e = PlayerIntents.extractorExtractExtras("https://miruro.tv/watch/1", dub = true, server = "Kiwi", listNotCached = true)
        assertEquals(
            listOf(
                PlayerExtra.Text(MiruroExtractorActivity.EXTRA_URL, "https://miruro.tv/watch/1"),
                PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_DUB, true),
                PlayerExtra.Text(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_EXTRACT),
                PlayerExtra.Text(MiruroExtractorActivity.EXTRA_SERVER_NAME, "Kiwi"),
            ),
            e,
        )
    }

    @Test
    fun `extract with the default server captures the list only when it is not cached`() {
        val notCached = PlayerIntents.extractorExtractExtras("u", dub = false, server = null, listNotCached = true)
        assertEquals(PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_CAPTURE_SERVERS, true), notCached.last())
        assertEquals(4, notCached.size)
        val cached = PlayerIntents.extractorExtractExtras("u", dub = false, server = "  ", listNotCached = false)
        assertEquals(
            listOf(MiruroExtractorActivity.EXTRA_URL, MiruroExtractorActivity.EXTRA_DUB, MiruroExtractorActivity.EXTRA_MODE),
            cached.keys(),
        )
    }

    @Test
    fun `extract keys are the recorded ones, in order`() {
        val named = PlayerIntents.extractorExtractExtras("u", true, "Kiwi", listNotCached = false).keys()
        val capture = PlayerIntents.extractorExtractExtras("u", true, null, listNotCached = true).keys()
        val all = golden("extractorExtract")
        assertEquals(all.filter { it != MiruroExtractorActivity.EXTRA_CAPTURE_SERVERS }, named)
        assertEquals(all.filter { it != MiruroExtractorActivity.EXTRA_SERVER_NAME }, capture)
    }

    @Test
    fun `enumerate carries the recorded keys and the enumerate mode`() {
        val e = PlayerIntents.extractorEnumerateExtras("https://miruro.tv/watch/1", dub = true)
        assertEquals(golden("extractorEnumerate"), e.keys())
        assertEquals(PlayerExtra.Text(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_ENUMERATE), e.last())
        assertEquals(PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_DUB, true), e[1])
    }
}
