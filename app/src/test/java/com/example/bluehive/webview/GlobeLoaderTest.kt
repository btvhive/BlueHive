package com.example.bluehive.webview

import androidx.compose.ui.unit.dp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extraction loader's globe (v575, design 5 from the owner's mock-ups):
 * a wireframe globe turning, the bee riding on it, beams out to servers, in
 * the old spinning hive's blue and purple.
 */
class GlobeLoaderTest {

    private val globe = MainSource.code("webview/GlobeLoader.kt")
    private val overlay = MainSource.code("webview/ExtractionLoadingOverlay.kt")

    @Test
    fun `the overlay shows the globe instead of the spinning hive`() {
        assertTrue(overlay.contains("GlobeLoader(beeBitmap)"))
        assertFalse(overlay.contains("OrbitDot"))
        assertFalse(overlay.contains("outerRing"))
    }

    @Test
    fun `a big globe and a big bee, turning once every six seconds`() {
        assertEquals(100.dp, GLOBE_RADIUS)
        assertEquals(64.dp, GLOBE_BEE_SIZE)
        assertEquals(6000, GLOBE_TURN_MS)
        assertEquals(1000, GLOBE_BEAM_MS)
    }

    @Test
    fun `the old loader's colours`() {
        for (c in listOf("0xFF2244FF", "0xFF4466FF", "0xFF9922FF", "0xFF6622CC", "0xFF88AAFF", "0xFF55AAFF", "0xFF33DDFF")) {
            assertTrue(c, globe.contains("Color($c)"))
        }
    }

    @Test
    fun `the grid is meridians and parallels every 30 degrees`() {
        val lines = gridLines()
        assertEquals(12 + 5, lines.size)
        // Each line is (lat, lon) pairs, sampled every 4 degrees.
        assertEquals(46 * 2, lines.first().size)
        assertEquals(91 * 2, lines.last().size)
    }

    @Test
    fun `it turns in the draw phase, never recomposing`() {
        val draw = globe.substringAfter("onDrawBehind {")
        assertTrue(draw.contains("val rot = spin.value"))
        assertTrue(draw.contains("val phase0 = beam.value"))
        assertFalse(globe.substringBefore("onDrawBehind {").contains("spin.value"))
    }
}
