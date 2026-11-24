package com.example.bluehive.webview.exoplayer_resources

import org.junit.Assert.assertEquals
import org.junit.Test

/** The test-build speed limiter's pacing (ThrottledDataSource). */
class ThrottleMathTest {

    @Test fun `off means no wait`() {
        assertEquals(0L to 123L, ThrottleMath.reserve(1_000, 50_000, 0, 123))
    }

    @Test fun `1 MB at 8 Mbps takes one second`() {
        val (wait, next) = ThrottleMath.reserve(nowNs = 0, bytes = 1_000_000, bitsPerSecond = 8_000_000, nextFreeNs = 0)
        assertEquals(1_000_000_000L, wait)
        assertEquals(1_000_000_000L, next)
    }

    @Test fun `two readers share one line`() {
        // The second read queues behind the first: total rate = the cap.
        val (_, afterFirst) = ThrottleMath.reserve(0, 500_000, 8_000_000, 0)
        val (wait2, afterSecond) = ThrottleMath.reserve(0, 500_000, 8_000_000, afterFirst)
        assertEquals(1_000_000_000L, wait2)
        assertEquals(1_000_000_000L, afterSecond)
    }

    @Test fun `an idle line does not bank credit`() {
        // Free since long ago: the read still takes its own time, not zero.
        val (wait, _) = ThrottleMath.reserve(nowNs = 10_000_000_000, bytes = 125_000, bitsPerSecond = 1_000_000, nextFreeNs = 0)
        assertEquals(1_000_000_000L, wait)
    }

    @Test fun `slices are about a twentieth of a second, at least 1 KB`() {
        assertEquals(25_000, ThrottleMath.slice(4_000_000, 1_000_000))   // 4 Mbps -> 500 KB/s -> 25 KB
        assertEquals(1024, ThrottleMath.slice(8_000, 1_000_000))
        assertEquals(500, ThrottleMath.slice(4_000_000, 500))
        assertEquals(64_000, ThrottleMath.slice(0, 64_000))
    }
}
