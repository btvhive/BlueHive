package com.example.bluehive.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepInViewTest {

    @Test
    fun backAtTheTop_aHalfHiddenFirstRowScrollsBackIntoView() {
        // Ten channels under nine rows' room: the list's end stops mid-row, so
        // row 0 is still the first visible row, 30 px scrolled off (v509's stuck row).
        assertEquals(0, keepInView(focused = 0, first = 0, firstOffset = 30, visible = 9))
        assertNull(keepInView(focused = 0, first = 0, firstOffset = 0, visible = 9))
        assertNull(keepInView(focused = 1, first = 0, firstOffset = 30, visible = 9))
    }

    @Test
    fun aboveOrBelowTheWindow_scrollsJustEnough() {
        assertEquals(3, keepInView(focused = 3, first = 5, firstOffset = 0, visible = 9))
        assertEquals(1, keepInView(focused = 9, first = 0, firstOffset = 0, visible = 9))
        assertNull(keepInView(focused = 8, first = 0, firstOffset = 0, visible = 9))
        assertEquals(2, keepInView(focused = 8, first = 0, firstOffset = 0, visible = 7))
    }
}
