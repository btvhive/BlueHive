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
        val layer = pulse.substringAfter("placeWithLayer(")
        assertTrue(Regex("""^[^{}]*\{\s*scaleX = scale\.value; scaleY = scale\.value\s*\}""").containsMatchIn(layer))
        assertTrue(!pulse.contains("by rememberInfiniteTransition"))
    }

    @Test
    fun `the chip's bounds hold it at its biggest, so a fade never cuts it to pieces`() {
        // v535 shifted it with a layer translation, outside its own bounds; a
        // fading layer is drawn offscreen at the size of its bounds, so the
        // fade in and out cut off whatever lay outside.
        val pulse = code.substringAfter("private fun PulsingBrowseChip(").substringBefore("private fun HintChip(")
        assertTrue(!pulse.contains("translationX"))
        assertTrue(!pulse.contains("translationY"))
        assertTrue(pulse.contains("val w = ceil(chip.width * BROWSE_PULSE_SCALE).toInt()"))
        assertTrue(pulse.contains("val h = ceil(chip.height * BROWSE_PULSE_SCALE).toInt()"))
        assertTrue(pulse.contains("placeWithLayer((w - chip.width) / 2, (h - chip.height) / 2)"))
        // The clearance from the frame is room inside the bounds too.
        assertTrue(pulse.contains(".padding(top = BROWSE_PULSE_CLEARANCE, end = BROWSE_PULSE_CLEARANCE)"))
        assertTrue(pulse.indexOf(".padding(") < pulse.indexOf(".layout {"))
        // Centred in room of its biggest size, a chip at full pulse fits exactly.
        for (width in listOf(97, 110, 131)) {
            val room = kotlin.math.ceil(width * BROWSE_PULSE_SCALE).toInt()
            val left = (room - width) / 2
            assertTrue(left - width * (BROWSE_PULSE_SCALE - 1f) / 2f >= -0.5f)
            assertTrue(left + width / 2f + width * BROWSE_PULSE_SCALE / 2f <= room + 0.5f)
        }
    }
}
