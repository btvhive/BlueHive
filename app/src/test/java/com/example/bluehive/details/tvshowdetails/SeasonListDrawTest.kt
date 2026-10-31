package com.example.bluehive.details.tvshowdetails

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 3 (v515): the season list drew straight into the page's display list,
 * so each frame of a move or fade redrew the whole page, and every frame made
 * new gradients, paths and strokes. It has its own layer now, makes what never
 * changes once per size, and draws only the seasons in view.
 */
class SeasonListDrawTest {

    private val brushes = SeasonListBrushes(rowHeight = 40f, shadeDepth = 6f, top = 10f, bottom = 190f, fadeDepth = 20f)

    @Test
    fun `the three resting tiles reuse one face each, frame after frame`() {
        for (tile in listOf(DULL_TILE, GREY_TILE, BRIGHT_TILE)) {
            assertSame(brushes.face(tile), brushes.face(tile))
            assertEquals(tileFace(tile, 40f), brushes.face(tile))
        }
    }

    @Test
    fun `a tile mid-fade gets the right face, made on the spot and not kept`() {
        val halfway = lerpTile(DULL_TILE, GREY_TILE, 0.5f)
        assertEquals(tileFace(halfway, 40f), brushes.face(halfway))
        assertNotSame(brushes.face(halfway), brushes.face(halfway))
    }

    @Test
    fun `the cached gradients are the ones drawn before, moved to the row's top`() {
        // A slot's gradient ran from its own top to its bottom; it is now made
        // from 0 and drawn translated to the row, which is the same paint.
        assertEquals(Brush.verticalGradient(listOf(Color(0xFF1E1E25), Color(0xFF25252D)), startY = 0f, endY = 40f), brushes.slot)
        assertEquals(
            Brush.verticalGradient(listOf(Color(0xFF070707), Color.Transparent), startY = 10f, endY = 30f),
            brushes.fadeAbove,
        )
        assertEquals(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF070707)), startY = 170f, endY = 190f),
            brushes.fadeBelow,
        )
    }

    @Test
    fun `every season with any part in the window is drawn`() {
        // In rows: the window is LIST_VISIBLE_ROWS tall, a row 0.9 of a step.
        val rowHeight = 0.9f
        for (total in 1..40) {
            var scroll = -2f
            while (scroll <= total.toFloat()) {
                val inView = seasonsInView(scroll, total)
                for (season in 1..total) {
                    val y = season - 1 - scroll
                    val visible = y < LIST_VISIBLE_ROWS && y + rowHeight > 0f
                    if (visible) assertTrue("total $total scroll $scroll season $season", season in inView)
                }
                scroll += 0.05f
            }
        }
    }

    @Test
    fun `a long show draws a handful of rows, not all of them`() {
        assertEquals(1..4, seasonsInView(0f, 40))
        assertEquals(11..15, seasonsInView(10.4f, 40))
        assertEquals(37..40, seasonsInView(36f, 40))
        // A short list, centred (negative scroll), draws all it has.
        assertEquals(1..2, seasonsInView(-1f, 2))
    }

    @Test
    fun `the tiles sit 4 dp apart`() {
        // The user's pick from mock-ups of 2-6 dp (v540; 3 dp before).
        val code = MainSource.code("details/tvshowdetails/SeasonAccordion.kt")
        assertTrue(Regex("""private val ROW_GAP = 4\.dp\b""").containsMatchIn(code))
    }

    @Test
    fun `the list has its own layer, under the cached drawing`() {
        val code = MainSource.code("details/tvshowdetails/SeasonAccordion.kt")
        assertTrue(Regex("""\.graphicsLayer\(\)\s*\.drawWithCache\s*\{""").containsMatchIn(code))
        assertTrue(!code.contains(".drawBehind"))
        // Nothing is made per frame in the drawing helpers.
        val drawing = code.substringAfter("private fun DrawScope.drawSlot(")
        assertTrue(!drawing.contains("Path()"))
        assertTrue(!drawing.contains("Stroke("))
        assertTrue(!drawing.contains("Brush.verticalGradient"))
    }
}
