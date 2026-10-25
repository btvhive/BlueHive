package com.example.bluehive.details.shared.playback

import com.example.bluehive.BuildConfig
import com.example.bluehive.api.ExtractServerOption
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

/**
 * PINS THE BEHAVIOUR of the pure helpers the two details screens share
 * (docs/details-redesign-architecture.md). Written in Stage 0 while they still
 * lived in TVShowsDetailsScreenCompose.kt; at Stage 1 they moved, word for
 * word, to ExtractionMessages.kt here, and this file moved with them unchanged.
 *
 * Every expected string is copied from the source, character for character,
 * including the em dashes. A test that fails here means a viewer would read a
 * different message — change the test only together with a deliberate wording
 * change.
 */
class DetailsExtractionMessagesTest {

    // ── extractionErrorMessage ──────────────────────────────────────────────

    @Test
    fun `several sources tried names every one and claims only that none played`() {
        assertEquals(
            "Tried VidNest, VidFast and VidLove.\n\nNone of them could play this episode.",
            extractionErrorMessage(502, "no_stream", "VidLove", "episode",
                listOf("VidNest", "VidFast", "VidLove")),
        )
    }

    @Test
    fun `two sources tried are joined with and`() {
        assertEquals(
            "Tried VidNest and MoviesAPI.\n\nNone of them could play this movie.",
            extractionErrorMessage(null, null, "MoviesAPI", "movie", listOf("VidNest", "MoviesAPI")),
        )
    }

    @Test
    fun `the several-sources message wins over a 503 and over the reason`() {
        assertEquals(
            "Tried VidNest and VidFast.\n\nNone of them could play this episode.",
            extractionErrorMessage(503, "source_blocked", "VidFast", "episode", listOf("VidNest", "VidFast")),
        )
    }

    @Test
    fun `a single source tried reads like no chain at all`() {
        assertEquals(
            extractionErrorMessage(502, "timeout", "VidNest", "episode"),
            extractionErrorMessage(502, "timeout", "VidNest", "episode", listOf("VidNest")),
        )
    }

    @Test
    fun `503 is our extractor starting up, whatever the reason says`() {
        assertEquals(
            "VidNest is starting up.\n\nGive it a moment and try again.",
            extractionErrorMessage(503, "no_stream", "VidNest", "episode"),
        )
    }

    @Test
    fun `source_blocked names the source and points at the others`() {
        assertEquals(
            "Your network is blocking VidFast.\n\nPick a different source below — the others usually work.",
            extractionErrorMessage(502, "source_blocked", "VidFast", "movie"),
        )
    }

    @Test
    fun `no_stream says the source lacks the title`() {
        assertEquals(
            "VidLove doesn't have this episode.\n\nTry a different source.",
            extractionErrorMessage(502, "no_stream", "VidLove", "episode"),
        )
    }

    @Test
    fun `timeout says the source was slow`() {
        assertEquals(
            "VidNest took too long to respond.\n\nTry again, or pick another source.",
            extractionErrorMessage(502, "timeout", "VidNest", "movie"),
        )
    }

    @Test
    fun `dead_segments says the copy is broken, not the title`() {
        assertEquals(
            "MoviesAPI has a broken copy of this movie.\n\nTry another source — the movie itself is fine.",
            extractionErrorMessage(502, "dead_segments", "MoviesAPI", "movie"),
        )
    }

    @Test
    fun `anything else is a plain could-not-load`() {
        assertEquals(
            "Couldn't load this episode from VidNest.\n\nTry another source.",
            extractionErrorMessage(502, "unknown", "VidNest", "episode"),
        )
        assertEquals(
            "Couldn't load this movie from VidFast.\n\nTry another source.",
            extractionErrorMessage(null, null, "VidFast", "movie"),
        )
    }

    // ── parseExtractReason ──────────────────────────────────────────────────

    private fun errorResponse(code: Int, body: String) =
        retrofit2.Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `the reason code is read out of the 502 body`() {
        val resp = errorResponse(502, """{"detail":{"reason":"source_blocked","detail":"vidfast.vc refused"}}""")
        assertEquals("source_blocked", parseExtractReason(resp))
    }

    @Test
    fun `a blank reason is no reason`() {
        assertNull(parseExtractReason(errorResponse(502, """{"detail":{"reason":"","detail":"x"}}""")))
    }

    @Test
    fun `a body without the detail object has no reason`() {
        assertNull(parseExtractReason(errorResponse(502, """{"error":"boom"}""")))
    }

    @Test
    fun `a body that is not JSON has no reason`() {
        assertNull(parseExtractReason(errorResponse(500, "<html>Bad gateway</html>")))
    }

    @Test
    fun `no response and a successful response have no reason`() {
        assertNull(parseExtractReason(null))
        assertNull(parseExtractReason(retrofit2.Response.success("ok")))
    }

    // ── miruroSourceLabel ───────────────────────────────────────────────────

    @Test
    fun `the server the backend saw in use wins`() {
        val servers = listOf(ExtractServerOption("Zoro", false), ExtractServerOption("Kiwi", true))
        assertEquals("Miruro Kiwi", miruroSourceLabel(servers, requested = "Zoro"))
    }

    @Test
    fun `with no server in use the requested one is named`() {
        assertEquals("Miruro Zoro", miruroSourceLabel(null, requested = "Zoro"))
        assertEquals("Miruro Zoro", miruroSourceLabel(listOf(ExtractServerOption("Kiwi", false)), "Zoro"))
        assertEquals("Miruro Zoro", miruroSourceLabel(listOf(ExtractServerOption("  ", true)), "Zoro"))
    }

    @Test
    fun `with nothing known it is plain Miruro, never a dangling space`() {
        assertEquals("Miruro", miruroSourceLabel(null, null))
        assertEquals("Miruro", miruroSourceLabel(emptyList(), ""))
    }

    // ── animeRelayUrl ───────────────────────────────────────────────────────

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    @Test
    fun `an episode relay url carries every tag in a fixed order`() {
        val m3u8 = "https://cdn.example.test/a b/master.m3u8?token=x&y=1"
        val url = animeRelayUrl(
            m3u8 = m3u8, tmdbId = 1399, mediaType = "anime_tv", title = "Frieren: Beyond",
            profileId = 7, sourceName = "Miruro Kiwi", season = 2, episode = 5,
        )
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val expected = Regex(
            "^" + Regex.escape("$base/api/hls/proxy?url=${enc(m3u8)}&sid=") + "[0-9a-f]{32}" +
                Regex.escape("&tmdb=1399&mtype=anime_tv&season=2&episode=5" +
                    "&src=${enc("Miruro Kiwi")}&title=${enc("Frieren: Beyond")}&profile=7") +
                "(&device=[^&]+)?$"
        )
        assertTrue(url, expected.matches(url))
    }

    @Test
    fun `a movie relay url leaves out season and episode`() {
        val url = animeRelayUrl(
            m3u8 = "https://cdn.example.test/m.m3u8", tmdbId = 129, mediaType = "anime_movie",
            title = "Spirited Away", profileId = 3, sourceName = "Miruro",
        )
        assertFalse(url, url.contains("&season="))
        assertFalse(url, url.contains("&episode="))
        assertTrue(url, url.contains("&tmdb=129&mtype=anime_movie&src=Miruro&title=Spirited+Away&profile=3"))
    }

    @Test
    fun `no profile is sent for an unknown profile`() {
        for (profile in listOf(-1, 0)) {
            val url = animeRelayUrl("https://cdn.example.test/m.m3u8", 1, "anime_tv", "T", profile, "Miruro", 1, 1)
            assertFalse(url, url.contains("&profile="))
        }
    }

    @Test
    fun `every call gets its own session id`() {
        fun sid(u: String) = Regex("&sid=([0-9a-f]{32})").find(u)!!.groupValues[1]
        val a = animeRelayUrl("https://cdn.example.test/m.m3u8", 1, "anime_tv", "T", 1, "Miruro", 1, 1)
        val b = animeRelayUrl("https://cdn.example.test/m.m3u8", 1, "anime_tv", "T", 1, "Miruro", 1, 1)
        assertTrue(sid(a) != sid(b))
    }
}
