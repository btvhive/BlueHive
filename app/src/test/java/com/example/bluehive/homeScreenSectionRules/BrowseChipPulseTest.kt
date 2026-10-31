package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Click to browse" chip pulses big and back for as long as a home panel
 * (trailers, trending) has focus, so the cue is hard to miss (2026-10-30).
 */
class BrowseChipPulseTest {

    private val code = MainSource.code("homeScreenSectionRules/PanelHints.kt")

    @Test
    fun `the pulse is moderate and about a second and a half a beat`() {
        assertTrue(BROWSE_PULSE_SCALE in 1.2f..1.3f)
        assertTrue(BROWSE_PULSE_MS * 2 in 1000..1800)
    }

    @Test
    fun `the chip shown while a panel has focus is the pulsing one`() {
        val layer = code.substringAfter("fun PanelHintLayer(").substringBefore("private fun PulsingBrowseChip(")
        val chip = layer.substringAfter("visible  = compact != null").substringBefore("AnimatedVisibility(")
        assertTrue(chip.contains("PulsingBrowseChip(lastCompact[0])"))
    }

    @Test
    fun `it repeats back and forth, read in the layer so it never recomposes`() {
        val pulse = code.substringAfter("private fun PulsingBrowseChip(").substringBefore("private fun HintChip(")
        assertTrue(pulse.contains("rememberInfiniteTransition"))
        assertTrue(pulse.contains("RepeatMode.Reverse"))
        assertTrue(pulse.contains("scaleX = scale.value; scaleY = scale.value"))
        assertTrue(pulse.contains("Box(Modifier.graphicsLayer {"))
        assertTrue(!pulse.contains("by rememberInfiniteTransition"))
    }

    @Test
    fun `at its biggest the chip stays inside where it used to rest, clear of the frame`() {
        // Grown from its centre, the chip passes its own right and top edges by
        // half of what it grows; the fixed shift is that, plus the clearance.
        val pulse = code.substringAfter("private fun PulsingBrowseChip(").substringBefore("private fun HintChip(")
        assertTrue(pulse.contains("val grow = (BROWSE_PULSE_SCALE - 1f) / 2f"))
        assertTrue(pulse.contains("translationX = -(size.width * grow + clear)"))
        assertTrue(pulse.contains("translationY = size.height * grow + clear"))
        val width = 110f
        val grownRight = width / 2 + width * BROWSE_PULSE_SCALE / 2 - (width * (BROWSE_PULSE_SCALE - 1f) / 2f + 4f)
        assertTrue(grownRight <= width - 4f + 0.001f)
    }
}
