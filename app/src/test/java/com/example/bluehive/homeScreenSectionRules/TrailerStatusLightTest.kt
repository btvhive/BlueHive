package com.example.bluehive.homeScreenSectionRules

import androidx.compose.ui.graphics.Color
import com.example.bluehive.latestTrailersComponents.STATUS_LIGHT_GREEN
import com.example.bluehive.latestTrailersComponents.STATUS_LIGHT_RED
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Latest Trailers TV's status light (v566): red while the panel is idle or
 * focused, neon green in select mode, sitting in the socket the frame art has
 * at the chin's right end, after four baked-in buttons.
 */
class TrailerStatusLightTest {

    private val section = MainSource.code("latestTrailersComponents/HomeScreenTrailerSectionCompose.kt")
    private val light = MainSource.code("latestTrailersComponents/TrailerStatusLight.kt")

    private fun spec(name: String): Double =
        Regex("""val $name = ([0-9.]+)\.dp""").find(section)!!.groupValues[1].toDouble()

    @Test
    fun `red at rest, neon green (not mint) in select mode`() {
        assertEquals(Color(0xFFFF1010), STATUS_LIGHT_RED)
        assertEquals(Color(0xFF39FF14), STATUS_LIGHT_GREEN)
        assertTrue(light.contains("if (selected) STATUS_LIGHT_GREEN else STATUS_LIGHT_RED"))
        assertTrue(section.contains("selected = trailerState is TrailerFrameState.Selected,"))
    }

    @Test
    fun `the light sits on the frame art's socket, centred in the chin`() {
        // In from the frame's right edge: 11 dp rim, 6 dp panel corner, 3 dp.
        assertEquals(spec("frameX") + spec("frameW") - 20, spec("statusLightX"), 0.01)
        // Halfway down the chin: 4 dp of panel + 14 dp under the picture.
        assertEquals(spec("viewportY") + spec("viewportH") + 9, spec("statusLightY"), 0.01)
        // Drawn after the frame, so it sits on it.
        assertTrue(section.indexOf("R.drawable.trailer_frame") < section.indexOf("TrailerStatusLight("))
    }

    @Test
    fun `colour and glow are read while drawing, not composing`() {
        val canvas = light.substringAfter("Canvas(")
        assertTrue(canvas.contains("val c = color"))
        assertTrue(canvas.contains("val g = glow.toPx()"))
    }

    @Test
    fun `the generator bakes in the buttons and the socket, and centres the label`() {
        val root = listOf(File("."), File("..")).first { File(it, "tools/home-art").isDirectory }
        val script = File(root, "tools/home-art/make_trailer_frame.py").readText()
        assertTrue(script.contains("chin_cy = pic[3] + (px(PANEL_PAD_DP) + px(CHIN_DP)) / 2"))
        assertTrue(script.contains("spaced_text(img, w / 2, chin_cy, LABEL"))
        assertTrue(script.contains("for i in range(4):"))
        assertTrue(script.contains("r = px(LIGHT_SOCKET_DP)"))
    }
}
