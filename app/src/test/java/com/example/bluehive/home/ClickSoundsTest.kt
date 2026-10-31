package com.example.bluehive.home

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Click sounds on the home screen (owner, 2026-10-31):
 *  - clicking into Continue Watching plays the click;
 *  - clicking a tab plays only the click, not the navigation sound of the tab
 *    focus is handed to afterwards; moving between tabs by hand still does.
 */
class ClickSoundsTest {

    @Test
    fun `clicking into Continue Watching plays the click`() {
        val code = MainSource.code("continueWatchingComponents/ContinueWatchingFrame.kt")
        val click = code.substringAfter(".clickable(").substringBefore(".focusable(interactionSource = frameInteraction)")
        val sound = click.indexOf("BlueHiveApplication.playClickSound()")
        val select = click.indexOf("frameState = ContinueWatchingFrameState.Selected")
        assertTrue(sound in 0 until select)
    }

    @Test
    fun `the tab a click hands focus to arrives silently`() {
        val focusMap = MainSource.code("home/HomeFocusMap.kt")
        val handOff = focusMap.substringAfter("LaunchedEffect(tabs.pendingFocusRequester)").substringBefore("LaunchedEffect(")
        val stamp = handOff.indexOf("tabs.radioClickHandOffUntil = android.os.SystemClock.uptimeMillis() + RADIO_CLICK_HANDOFF_MS")
        val request = handOff.indexOf("requester.requestFocus()")
        assertTrue("stamped just before the hand-off", stamp in 0 until request)

        val tabs = MainSource.code("home/CatalogTabs.kt")
        val sound = tabs.substringAfter("val radioNavSound: () -> Unit = {").substringBefore("\n    }\n")
        val window = sound.indexOf("if (android.os.SystemClock.uptimeMillis() < tabs.radioClickHandOffUntil)")
        val consume = sound.indexOf("tabs.radioClickHandOffUntil = 0L")
        val play = sound.indexOf("BlueHiveApplication.playNavClickSound()")
        // Checked first, used up by the one arrival, and the sound is only in
        // the other branch -- so a move by hand afterwards still speaks.
        assertTrue(window in 0 until consume && consume < play)
        assertEquals(1, Regex("""playNavClickSound\(\)""").findAll(sound).count())
        assertEquals(500L, RADIO_CLICK_HANDOFF_MS)
    }
}
