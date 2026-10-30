package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v526: the season picker's focus is the buttons' underglow behind the whole
 * cap (the white line is gone), and the episode card the cap half-covers
 * tucks under the cap while it shows.
 */
class SeasonCapGlowTest {

    // In px, as on the onn box: the cap covers 22.5 px (11.25 dp) of the row.
    private val cover = 22.5f

    @Test
    fun `a card is under the cap only while the cap covers its left end and its right end shows`() {
        assertTrue(cardUnderCap(itemOffset = -100, itemSize = 348, viewportStart = -36, capCover = cover))
        assertFalse("starts clear of the cap", cardUnderCap(itemOffset = 0, itemSize = 348, viewportStart = -36, capCover = cover))
        assertFalse("wholly under the cap", cardUnderCap(itemOffset = -400, itemSize = 348, viewportStart = -36, capCover = cover))
    }

    @Test
    fun `it tucks until its right end meets the row's left edge`() {
        assertEquals(284f, tuckDistance(itemOffset = -100, itemSize = 348, viewportStart = -36), 0f)
        assertEquals(0f, tuckDistance(itemOffset = -500, itemSize = 348, viewportStart = -36), 0f)
    }

    @Test
    fun `the glow art is the cap plus 11 dp each side at 2 px per dp`() {
        // A lossless WebP (VP8L): width-1 and height-1 are 14-bit fields after the signature byte.
        val b = File("src/main/res/drawable-xhdpi/season_cap_focus_glow.webp").readBytes()
        assertEquals("VP8L", String(b, 12, 4))
        val bits = (b[21].toInt() and 0xFF) or ((b[22].toInt() and 0xFF) shl 8) or
            ((b[23].toInt() and 0xFF) shl 16) or ((b[24].toInt() and 0xFF) shl 24)
        val width = (bits and 0x3FFF) + 1
        val height = ((bits shr 14) and 0x3FFF) + 1
        // (134.25 + 2 x 11) x (122.25 + 2 x 11) dp, at 2 px per dp, rounded down.
        assertEquals(312, width)
        assertEquals(288, height)
    }

    @Test
    fun `the glow is drawn behind the cap, and the white line is gone`() {
        val area = MainSource.code("details/tvshowdetails/TvPlayArea.kt")
        // First of all: behind the strip below the cap, the episode row and the cap.
        val glow = area.indexOf("SeasonCapGlow(focusMap)")
        val strip = area.indexOf("heldPainter(R.drawable.extended_mini_left_lower_body)")
        val row = area.indexOf("EpisodeRow(")
        val cap = area.indexOf("heldPainter(R.drawable.extended_mini_left_upper_body)")
        assertTrue(glow >= 0 && glow < strip && glow < row && glow < cap)
        val picker = MainSource.code("details/tvshowdetails/SeasonAccordion.kt")
        assertFalse(picker.contains("drawFocusLine"))
        assertFalse(picker.contains("FOCUS_LINE"))
        assertTrue(picker.contains("val glowOn = focused && !choosing"))
    }

    @Test
    fun `cards tuck from the glow's own flag`() {
        val row = MainSource.code("details/tvshowdetails/EpisodeRow.kt")
        assertTrue(row.contains("capGlowOn              = { focusMap.seasonCapGlowOn },"))
        val card = MainSource.code("details/tvshowdetails/EpisodeCard.kt")
        assertTrue(card.contains("targetValue = if (isUnderCap && capGlowOn()) 1f else 0f"))
    }
}
