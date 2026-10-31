package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trailer and trending panels' big LEFT/RIGHT arrows bounce on each turn
 * of the carousel: down, a little past their size, and back (owner,
 * 2026-10-31).
 */
class ArrowBounceTest {

    private val hints = MainSource.code("homeScreenSectionRules/PanelHints.kt")

    @Test
    fun `each turn counts on its own side`() {
        val presses = ArrowPresses()
        presses.press(-1); presses.press(-1); presses.press(1)
        assertEquals(2, presses.left)
        assertEquals(1, presses.right)
    }

    @Test
    fun `the bounce dips, overshoots very slightly, and ends at exactly its size`() {
        assertTrue(ARROW_BOUNCE_DOWN in 0.75f..0.84f)
        assertTrue(ARROW_BOUNCE_OVER in 1.02f..1.08f)
        val disc = hints.substringAfter("private fun BrowseDisc(").substringBefore("private fun DrawScope.chevron(")
        val down = disc.indexOf("animateTo(ARROW_BOUNCE_DOWN")
        val over = disc.indexOf("animateTo(ARROW_BOUNCE_OVER")
        val rest = disc.indexOf("animateTo(1f")
        assertTrue(down in 0 until over && over < rest)
        // A press mid-bounce restarts it; the first value is skipped, so a disc
        // fading in does not bounce for earlier presses.
        assertTrue(disc.contains(".collectLatest { count ->"))
        assertTrue(disc.contains("if (count == seen) return@collectLatest"))
        // Scaled in the layer, inside room for the overshoot.
        assertTrue(disc.contains(".padding(ARROW_BOUNCE_ROOM)"))
        assertTrue(disc.contains(".graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }"))
    }

    @Test
    fun `both carousels report their turns and both panels pass them to the arrows`() {
        val wiring = mapOf(
            "latestTrailersComponents/HomeScreenTrailerNavigationCompose.kt" to "trailers.size",
            "trendingComponents/HomeScreenTrendingCompose.kt" to "items.size",
        )
        for ((path, size) in wiring) {
            val code = MainSource.code(path)
            assertTrue(path, code.contains("% $size\n                            BlueHiveApplication.playHoverSound()\n                            onTurn(-1)"))
            assertTrue(path, code.contains("% $size\n                            BlueHiveApplication.playHoverSound()\n                            onTurn(1)"))
        }
        for (path in listOf("latestTrailersComponents/HomeScreenTrailerSectionCompose.kt", "trendingComponents/HomeScreenTrendingCompose.kt")) {
            val code = MainSource.code(path)
            assertTrue(path, code.contains("val arrowPresses = remember { ArrowPresses() }"))
            assertTrue(path, Regex("""onTurn\s*=\s*arrowPresses::press""").containsMatchIn(code))
            assertTrue(path, code.contains("arrowPresses = arrowPresses"))
        }
        // The two arrows read their own side.
        assertTrue(hints.contains("BrowseDisc(left = true, scale = arrowScale, presses = { arrowPresses?.left ?: 0 })"))
        assertTrue(hints.contains("BrowseDisc(left = false, scale = arrowScale, presses = { arrowPresses?.right ?: 0 })"))
    }
}
