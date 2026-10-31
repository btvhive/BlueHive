package com.example.bluehive.home

import androidx.compose.animation.core.Spring
import com.example.bluehive.singleShelfComponents.SHELF_NAV_COOLDOWN_MS
import com.example.bluehive.singleShelfComponents.SHELF_SLIDE_STIFFNESS
import com.example.bluehive.singleShelfComponents.SHELF_TITLE_BRUSH
import com.example.bluehive.singleShelfComponents.SHELF_TITLE_GLOW_ALPHA
import com.example.bluehive.singleShelfComponents.SHELF_TITLE_SIZE
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    fun `the row title is white through its top fifth, then down to steel grey`() {
        // The owner's pick "A1" (v552).
        val expected = Brush.verticalGradient(
            0f to Color.White,
            0.2f to Color.White,
            1f to Color(0xFF7C8190),
        )
        assertEquals(expected, SHELF_TITLE_BRUSH)
        assertTrue(code.contains("brush      = SHELF_TITLE_BRUSH,"))
        assertTrue(code.contains("style    = SHELF_TITLE_STYLE,"))
    }

    @Test
    fun `a faint white glow shows from the top half of the title only`() {
        // The owner's pick "G1" (v553).
        assertEquals(0.35f, SHELF_TITLE_GLOW_ALPHA)
        val title = code.substringAfter("private fun ShelfTitle(").substringBefore("\n}\n")
        // The glow copy is drawn first, so the real title sits on top of it.
        assertTrue(title.indexOf("style    = glowStyle") in 0 until title.indexOf("style    = SHELF_TITLE_STYLE"))
        assertTrue(title.contains("color      = Color.White.copy(alpha = SHELF_TITLE_GLOW_ALPHA)"))
        assertTrue(title.contains("offset     = Offset.Zero"))
        // Masked to the top half of the line, on its own layer.
        assertTrue(title.contains("compositingStrategy = CompositingStrategy.Offscreen"))
        assertTrue(title.contains("val keep = (room + line * 0.5f) / size.height"))
        assertTrue(title.contains("drawRect(mask, blendMode = BlendMode.DstIn)"))
        // Both copies inside the room, and the room inside the title's offset,
        // so the fade-in cannot cut the glow off and the text has not moved.
        assertEquals(2, Regex("""padding\(SHELF_TITLE_GLOW_ROOM\)""").findAll(title).count())
        assertTrue(code.contains("Modifier.offset(x = (-29).dp - SHELF_TITLE_GLOW_ROOM, y = (-69).dp - SHELF_TITLE_GLOW_ROOM)"))
        // No blur modifier: it only works from Android 12.
        assertTrue(!title.contains(".blur("))
    }

    @Test
    fun `the row title still lands inside the gap between two moves`() {
        val delay = Regex("""HEADER_REAPPEAR_DELAY_MS\s*=\s*(\d+)L""").find(code)!!.groupValues[1].toLong()
        val fade = Regex("""HEADER_FADE_IN_DURATION_MS\s*=\s*(\d+)""").find(code)!!.groupValues[1].toLong()
        assertTrue("$delay + $fade must fit in $SHELF_NAV_COOLDOWN_MS", delay + fade < SHELF_NAV_COOLDOWN_MS)
    }
}
