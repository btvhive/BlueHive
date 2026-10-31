package com.example.bluehive.home

import androidx.compose.animation.core.Spring
import com.example.bluehive.singleShelfComponents.SHELF_NAV_COOLDOWN_MS
import com.example.bluehive.singleShelfComponents.SHELF_SLIDE_STIFFNESS
import com.example.bluehive.singleShelfComponents.SHELF_TITLE_BRUSH
import com.example.bluehive.singleShelfComponents.SHELF_TITLE_SIZE
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving between the home screen's rows is a bit faster since v550 (owner,
 * 2026-10-31): a shorter gap between moves and a stiffer slide, with the row
 * title still landing between two presses.
 */
class ShelfNavSpeedTest {

    private val code = MainSource.code("singleShelfComponents/ShelfStack.kt")

    @Test
    fun `a move can follow the last sooner, and the slide is quicker`() {
        assertEquals(380L, SHELF_NAV_COOLDOWN_MS)
        assertTrue(SHELF_SLIDE_STIFFNESS > Spring.StiffnessMediumLow && SHELF_SLIDE_STIFFNESS < Spring.StiffnessMedium)
        assertTrue(code.contains("if (now - lastShelfNavTime < SHELF_NAV_COOLDOWN_MS) return false"))
        assertTrue(code.contains("stiffness = SHELF_SLIDE_STIFFNESS,"))
    }

    @Test
    fun `the row title is 21 sp, 2 sp under its old 23`() {
        assertEquals(21.sp, SHELF_TITLE_SIZE)
        assertTrue(code.contains("fontSize   = SHELF_TITLE_SIZE,"))
        assertTrue(!code.contains("fontSize = 23.sp"))
    }

    @Test
    fun `the row title is pure white through its top quarter, then down to steel grey`() {
        // The owner's pick "A1" (v552), white held to 25% since v555 (20% before).
        val expected = Brush.verticalGradient(
            0f to Color.White,
            0.25f to Color.White,
            1f to Color(0xFF7C8190),
        )
        assertEquals(expected, SHELF_TITLE_BRUSH)
        assertEquals(0xFFFFFFFF.toInt(), Color.White.toArgb())
        assertTrue(code.contains("brush      = SHELF_TITLE_BRUSH,"))
        assertTrue(code.contains("style = SHELF_TITLE_STYLE,"))
    }

    @Test
    fun `the row title still lands inside the gap between two moves`() {
        val delay = Regex("""HEADER_REAPPEAR_DELAY_MS\s*=\s*(\d+)L""").find(code)!!.groupValues[1].toLong()
        val fade = Regex("""HEADER_FADE_IN_DURATION_MS\s*=\s*(\d+)""").find(code)!!.groupValues[1].toLong()
        assertTrue("$delay + $fade must fit in $SHELF_NAV_COOLDOWN_MS", delay + fade < SHELF_NAV_COOLDOWN_MS)
    }
}
