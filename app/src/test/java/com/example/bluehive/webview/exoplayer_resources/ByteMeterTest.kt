package com.example.bluehive.webview.exoplayer_resources

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteMeterTest {
    @Test fun `bits per second over the window`() {
        // 1.875 MB in 3 s = 5 Mbps (the Fire Stick test line).
        assertEquals(5_000_000L, ByteMeter.bps(0, 3_000, 1_875_000, 6_000))
    }

    @Test fun `nothing to go on reads zero`() {
        assertEquals(0L, ByteMeter.bps(500, 6_000, 500, 6_000))     // one reading
        assertEquals(0L, ByteMeter.bps(500, 3_000, 500, 6_000))     // nothing arrived
    }
}
