package com.example.bluehive.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MEASURED 2026-11-23: Lyra's real and decoy answers for Teen Titans S3E8. */
class LyraResolverTest {
    // q= payloads as Lyra sends them (base64url JSON, "d" = the CDN folder).
    private val real = "https://stream.hls.lol/mbox/mpd?q=" +
        "eyJkIjoiaHR0cHM6Ly9zYWNkbi5oYWt1bmF5bWF0YXRhLmNvbS9kYXNoLzg5NDI4ODY2MDEyODIxNzU4MjRfM184XzEwODBfaDI2NV8xMzMifQ"
    private val decoy = "https://stream.hls.lol/mbox/mpd?q=" +
        "eyJkIjoiaHR0cHM6Ly9zY2hvb2xpbmcuY2ZkL2VkZ2UtN2EzZTkxZDAvaG0tZGVjb3kvZGFzaC83OWI1NDc4ZDVmZTRlZjgwLXQifQ"

    @Test fun realHostIsRead() {
        assertEquals("https://sacdn.hakunaymatata.com/dash/8942886601282175824_3_8_1080_h265_133",
                     LyraResolver.realHost(real))
    }

    @Test fun realStreamIsNotADecoy() = assertFalse(LyraResolver.isDecoy(real))

    @Test fun decoyIsRefused() = assertTrue(LyraResolver.isDecoy(decoy))

    @Test fun unreadableUrlIsRefused() {
        assertTrue(LyraResolver.isDecoy("https://stream.hls.lol/mbox/mpd?q=%%%"))
        assertTrue(LyraResolver.isDecoy("https://stream.hls.lol/mbox/mpd"))
    }

    @Test fun originalTrackBeatsDubs() {
        val json = """{"tracks":[
            {"kind":"dash","mode":"dub","lang":"French","url":"https://x/french"},
            {"kind":"dash","mode":"original","lang":"Original","url":"https://x/original"}]}"""
        assertEquals("https://x/original", LyraResolver.pickTrack(json))
    }

    @Test fun firstDashTrackWhenNoOriginal() {
        val json = """{"tracks":[{"kind":"dash","mode":"dub","url":"https://x/a"},{"kind":"dash","mode":"dub","url":"https://x/b"}]}"""
        assertEquals("https://x/a", LyraResolver.pickTrack(json))
    }

    // ── remembered links (Lyra blinked "no_source" for 8-9 minutes, 2026-11-23) ──
    @Test fun rememberedLinkComesBackBeforeItsTime() {
        val raw = LyraResolver.encodeLink("https://x/mpd?q=1", untilMs = 2_000)
        assertEquals("https://x/mpd?q=1", LyraResolver.decodeLink(raw, nowMs = 1_000))
    }

    @Test fun expiredLinkIsNotUsed() {
        val raw = LyraResolver.encodeLink("https://x/mpd", untilMs = 1_000)
        assertNull(LyraResolver.decodeLink(raw, nowMs = 1_000))
        assertNull(LyraResolver.decodeLink(raw, nowMs = 5_000))
    }

    @Test fun malformedStoredLinkIsIgnored() {
        assertNull(LyraResolver.decodeLink("garbage", 0))
        assertNull(LyraResolver.decodeLink("123|not-a-url", 0))
        assertNull(LyraResolver.decodeUntil("x|https://y"))
    }

    @Test fun lyraExpiryIsParsedAsUtc() {
        // 2026-11-30T23:12:28Z, as Lyra sent it for Teen Titans S3E8
        assertEquals(1_796_080_348_000L, LyraResolver.expiryOf("""{"expiresAt":"2026-11-30T23:12:28Z","tracks":[]}"""))
        assertNull(LyraResolver.expiryOf("""{"tracks":[]}"""))
        assertNull(LyraResolver.expiryOf("""{"expiresAt":"soon"}"""))
    }

    @Test fun noTracksNoUrl() {
        assertNull(LyraResolver.pickTrack("""{"tracks":[]}"""))
        assertNull(LyraResolver.pickTrack("""{"error":"nope"}"""))
        assertNull(LyraResolver.pickTrack("not json"))
    }
}
