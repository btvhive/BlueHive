package com.example.bluehive.webview.hls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The website player's quality rules. Ladder = Beta's 4K ladder as the TV saw
 * it on 2026-11-19 (2160p 16 Mbps, 1080p 5.5, 720p 2.8, 480p 1.2).
 */
class QualityRulesTest {

    private val beta = listOf(Rung(2160, 16_000_000), Rung(1080, 5_500_000), Rung(720, 2_800_000), Rung(480, 1_200_000))
    private val mbps = 1_000_000L

    // ── ceiling ────────────────────────────────────────────────────────────
    @Test fun `a 4K display mode allows 4K`() = assertEquals(2160, QualityRules.ceiling(2160))
    @Test fun `a 1080p display stays at 1080p`() = assertEquals(1080, QualityRules.ceiling(1080))
    @Test fun `a 720p display still caps at 1080p, not lower`() = assertEquals(1080, QualityRules.ceiling(720))

    // ── ladder ─────────────────────────────────────────────────────────────
    @Test fun `ladder is highest first and drops duplicate heights and unknown ones`() {
        val l = QualityRules.ladder(listOf(Rung(720, 2), Rung(1080, 5), Rung(1080, 9), Rung(0, 7), Rung(2160, 16)))
        assertEquals(listOf(Rung(2160, 16), Rung(1080, 9), Rung(720, 2)), l)
    }

    // ── start ──────────────────────────────────────────────────────────────
    @Test fun `no speed measured yet starts on the best the TV can show`() {
        assertEquals(2160, QualityRules.startHeight(beta, 2160, null))
        assertEquals(1080, QualityRules.startHeight(beta, 1080, null))
    }

    @Test fun `a fast last speed starts on 4K`() = assertEquals(2160, QualityRules.startHeight(beta, 2160, 40 * mbps))

    @Test fun `a last speed too slow for 4K starts on 1080p`() =
        assertEquals(1080, QualityRules.startHeight(beta, 2160, 12 * mbps))

    @Test fun `4K needs 30 percent headroom to start on it`() {
        assertEquals(1080, QualityRules.startHeight(beta, 2160, 20 * mbps))   // 16 x 1.3 = 20.8
        assertEquals(2160, QualityRules.startHeight(beta, 2160, 21 * mbps))
    }

    @Test fun `a very slow last speed starts on the lowest rung`() =
        assertEquals(480, QualityRules.startHeight(beta, 2160, 500_000))

    @Test fun `a stream without the ceiling's rung starts on its own top`() =
        assertEquals(1080, QualityRules.startHeight(listOf(Rung(1080, 0), Rung(720, 0)), 2160, null))

    // ── neighbours ─────────────────────────────────────────────────────────
    @Test fun `lower and higher walk the ladder within the ceiling`() {
        assertEquals(1080, QualityRules.lower(beta, 2160))
        assertNull(QualityRules.lower(beta, 480))
        assertEquals(1080, QualityRules.higher(beta, 720, 2160))
        assertNull(QualityRules.higher(beta, 1080, 1080))
    }

    @Test fun `a rung without a declared bitrate uses the typical one`() =
        assertEquals(5_500_000, QualityRules.bitrateOf(listOf(Rung(1080, 0)), 1080))

    @Test fun `a cropped 4K frame belongs to the 2160p rung`() {
        // Gladiator: Beta's 2160p rung decodes at 3840x1600.
        assertEquals(2160, QualityRules.rungFor(beta, 1600, 16_000_000))
        assertEquals(2160, QualityRules.rungFor(beta, 1600, 0))            // no bitrate: lowest rung >= 1600
        assertEquals(1080, QualityRules.rungFor(beta, 800, 5_500_000))
        assertEquals(1080, QualityRules.rungFor(beta, 800, 0))
        assertEquals(720, QualityRules.rungFor(beta, 720, 2_900_000))      // nearest declared bitrate
        assertEquals(2160, QualityRules.rungFor(beta, 3000, 0))            // taller than every rung
    }

    @Test fun `the pill names the level on screen in everyday words`() {
        assertEquals("Quality: 4K", QualityRules.pillText(2160))
        assertEquals("Quality: HD", QualityRules.pillText(1080))
        assertEquals("Quality: 720p", QualityRules.pillText(720))
        assertEquals("Quality: SD", QualityRules.pillText(480))
        assertEquals("Quality", QualityRules.pillText(0))
    }

    @Test fun `labels say 4K for 2160p`() {
        assertEquals("2160p (4K)", QualityRules.label(2160))
        assertEquals("1080p", QualityRules.label(1080))
    }

    // ── Auto: stalls ───────────────────────────────────────────────────────
    private fun auto(start: Int = 2160, ceiling: Int = 2160) = AutoQuality(beta, ceiling, start, now = 0L)

    @Test fun `one short stall does not change quality`() {
        val a = auto()
        assertNull(a.onStallStart(30_000, 2160))
        assertNull(a.onStallTick(32_000, 2_000, 2160))
        assertEquals(2160, a.cap)
    }

    @Test fun `two stalls within a minute drop 4K to 1080p`() {
        val a = auto()
        assertNull(a.onStallStart(30_000, 2160))
        assertEquals(1080, a.onStallStart(70_000, 2160))
        assertEquals(1080, a.cap)
    }

    @Test fun `two stalls more than a minute apart do not`() {
        val a = auto()
        assertNull(a.onStallStart(30_000, 2160))
        assertNull(a.onStallStart(95_000, 2160))
    }

    @Test fun `one stall of 4 seconds drops on its own, once`() {
        val a = auto()
        a.onStallStart(30_000, 2160)
        assertNull(a.onStallTick(33_000, 3_000, 2160))
        assertEquals(1080, a.onStallTick(34_000, 4_000, 2160))
        assertNull(a.onStallTick(40_000, 10_000, 1080))   // same stall: counted already
    }

    @Test fun `buffering right after start or a seek is not counted`() {
        val a = auto()
        assertNull(a.onStallStart(2_000, 2160))                 // within the start settle
        assertNull(a.onStallTick(8_000, 6_000, 2160))
        a.onSeek(60_000)
        assertNull(a.onStallStart(61_000, 2160))
        assertNull(a.onStallTick(66_000, 5_000, 2160))
        assertEquals(2160, a.cap)
    }

    @Test fun `still choppy at 1080p steps on down to 720p`() {
        val a = auto()
        a.onStallStart(30_000, 2160); a.onStallStart(40_000, 2160)   // -> 1080
        assertNull(a.onStallStart(42_000, 1080))                     // inside the settle after the drop
        a.onStallStart(60_000, 1080)
        assertEquals(720, a.onStallStart(70_000, 1080))
    }

    @Test fun `at the bottom rung there is nowhere lower to go`() {
        val a = auto(start = 480)
        a.onStallStart(30_000, 480)
        assertNull(a.onStallStart(40_000, 480))
        assertEquals(480, a.cap)
    }

    @Test fun `a drop starts from what is actually playing`() {
        // Capped at 2160 but the stream is playing 1080 (no 4K rung picked):
        // the next step is 720, not 1080.
        val a = auto()
        a.onStallStart(30_000, 1080)
        assertEquals(720, a.onStallStart(40_000, 1080))
    }

    // ── Auto: measured speed ───────────────────────────────────────────────
    @Test fun `three slow samples with a short buffer drop a rung`() {
        val a = auto()
        assertNull(a.onSample(30_000, 9 * mbps, 2160, 8_000))
        assertNull(a.onSample(35_000, 9 * mbps, 2160, 6_000))
        assertEquals(1080, a.onSample(40_000, 9 * mbps, 2160, 4_000))
    }

    @Test fun `after a drop, speed is judged against the new cap, not the 4K still buffered`() {
        // Measured on the Fire Stick at 8 Mbps: 4K -> 1080p, then 7.3 Mbps samples
        // while buffered 4K was still playing. 7.3 carries 1080p (5.5 x 1.1).
        val a = auto()
        a.onSample(30_000, 7_500_000, 2160, 15_000); a.onSample(35_000, 7_500_000, 2160, 15_000)
        assertEquals(1080, a.onSample(40_000, 7_500_000, 2160, 14_000))
        repeat(6) { i -> assertNull(a.onSample(50_000L + i * 5_000, 7_300_000, 2160, 16_000)) }
        assertEquals(1080, a.cap)
    }

    @Test fun `slow samples with a full buffer ride the dip out`() {
        val a = auto()
        repeat(6) { i -> assertNull(a.onSample(30_000L + i * 5_000, 9 * mbps, 2160, 45_000)) }
        assertEquals(2160, a.cap)
    }

    @Test fun `a fast sample in between resets the slow count`() {
        val a = auto()
        a.onSample(30_000, 9 * mbps, 2160, 5_000)
        a.onSample(35_000, 9 * mbps, 2160, 5_000)
        assertNull(a.onSample(40_000, 30 * mbps, 2160, 5_000))
        assertNull(a.onSample(45_000, 9 * mbps, 2160, 5_000))
        assertEquals(2160, a.cap)
    }

    // ── Auto: stepping back up ─────────────────────────────────────────────
    @Test fun `steps back up only after two smooth minutes and three fast samples`() {
        val a = auto()
        a.onStallStart(30_000, 2160); a.onStallStart(40_000, 2160)   // -> 1080 at 40 s
        // 30 Mbps carries 4K (16 x 1.6 = 25.6), but too soon:
        assertNull(a.onSample(100_000, 30 * mbps, 1080, 60_000))
        assertNull(a.onSample(150_000, 30 * mbps, 1080, 60_000))
        assertNull(a.onSample(161_000, 30 * mbps, 1080, 60_000))
        assertNull(a.onSample(166_000, 30 * mbps, 1080, 60_000))
        assertEquals(2160, a.onSample(171_000, 30 * mbps, 1080, 60_000))
        assertEquals("speed 30000 kbps carries 2160p", a.lastReason)
    }

    @Test fun `not enough room for the rung above means no step up`() {
        val a = auto(start = 1080)
        repeat(10) { i -> assertNull(a.onSample(200_000L + i * 5_000, 20 * mbps, 1080, 60_000)) }
        assertEquals(1080, a.cap)
    }

    @Test fun `never steps above the ceiling`() {
        val a = auto(start = 1080, ceiling = 1080)
        repeat(10) { i -> assertNull(a.onSample(200_000L + i * 5_000, 100 * mbps, 1080, 60_000)) }
        assertEquals(1080, a.cap)
    }

    // ── Auto: a slow start (Man of Steel, 36.8 s to the first 4K picture) ─
    @Test fun `no 4K picture after 6 seconds starts on 1080p, once`() {
        val a = auto()
        assertNull(a.onStartupWait(5_000, 5_000))
        assertEquals(1080, a.onStartupWait(6_000, 6_000))
        assertEquals("no picture after 6s at 2160p", a.lastReason)
        assertNull(a.onStartupWait(12_000, 12_000))          // fires once
        assertEquals(1080, a.cap)
    }

    @Test fun `a slow start picks the rung the measured speed carries`() {
        // Fire Stick at 5 Mbps (measured 4.6): HD restarted, stalled 6 s, fell to 720p.
        assertEquals(720, auto().onStartupWait(6_000, 6_000, 4_600_000))
        // The early estimate reads low (5 Mbps read < 3.7 at 6 s): no headroom on top.
        assertEquals(720, auto().onStartupWait(6_000, 6_000, 3_400_000))
        assertEquals("no picture after 6s at 2160p, speed 3400 kbps", auto().apply { onStartupWait(6_000, 6_000, 3_400_000) }.lastReason)
        assertEquals(1080, auto().onStartupWait(6_000, 6_000, 6_000_000))   // 8 Mbps: HD fits
        assertEquals(480, auto().onStartupWait(6_000, 6_000, 2_000_000))
        assertEquals(1080, auto().onStartupWait(6_000, 6_000, 0))           // no measurement: HD
    }

    @Test fun `a fast first picture ends the startup rule`() {
        val a = auto()
        a.onFirstFrame()
        assertNull(a.onStartupWait(20_000, 20_000))
        assertEquals(2160, a.cap)
    }

    @Test fun `the startup rule leaves an HD start alone`() {
        val a = auto(start = 1080)
        assertNull(a.onStartupWait(30_000, 30_000))
        assertEquals(1080, a.cap)
    }

    @Test fun `after a slow start, 4K comes back after 30 smooth seconds, not 2 minutes`() {
        val a = auto()
        a.onStartupWait(6_000, 6_000)                         // -> 1080 at 6 s
        assertNull(a.onSample(20_000, 60 * mbps, 1080, 30_000))   // too soon (< 30 s)
        assertNull(a.onSample(36_000, 60 * mbps, 1080, 30_000))
        assertNull(a.onSample(41_000, 60 * mbps, 1080, 30_000))
        assertEquals(2160, a.onSample(46_000, 60 * mbps, 1080, 30_000))
    }

    @Test fun `a stall after a slow start still holds 4K back the full 2 minutes`() {
        val a = auto()
        a.onStartupWait(6_000, 6_000)
        a.onStallStart(20_000, 1080)                          // trouble again at 20 s
        repeat(4) { i -> assertNull(a.onSample(40_000L + i * 5_000, 60 * mbps, 1080, 30_000)) }
        assertEquals(1080, a.cap)
    }

    // ── the selection's cap ────────────────────────────────────────────────
    @Test fun `the selection allows rungs at or under the cap and unknown heights`() {
        assertEquals(true, CappedAdaptiveSelection.capAllows(1080, 1080))
        assertEquals(true, CappedAdaptiveSelection.capAllows(720, 1080))
        assertEquals(false, CappedAdaptiveSelection.capAllows(2160, 1080))
        assertEquals(true, CappedAdaptiveSelection.capAllows(-1, 720))
    }

    @Test fun `the real ladder can restart Auto on a lower rung`() {
        val a = auto()
        a.startAt(1080, 10_000)
        assertEquals(1080, a.cap)
        assertNull(a.onStallStart(12_000, 1080))   // settle after a start
    }
}
