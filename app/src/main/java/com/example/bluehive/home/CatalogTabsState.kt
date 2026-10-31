package com.example.bluehive.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * The Movies / TV Shows / Anime tabs: which is selected, the parking node's
 * flag, the button a tab switch hands focus to next, and the sound arming.
 *
 * Moved here from HomeScreen in Stage H2 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces, its names read through the holder.
 */
@Stable
internal class CatalogTabsState {
    var selectedTab by mutableStateOf(CatalogTab.MOVIES)
    var pendingFocusRequester by mutableStateOf<FocusRequester?>(null)
    var radioParkingEnabled by mutableStateOf(false)
    // ── Radio-button navigation sound ──────────────────────────────────────
    // The sound means "you moved onto this". The home screen OPENS with focus
    // already on a radio button, and announcing that is announcing a move that
    // never happened -- so the first arrival arms the flag instead of playing.
    //
    // ONE FLAG FOR ALL THREE, not one each: with a flag per button the first
    // hop from MOVIES to TV SHOWS would be silent too.
    var radioSoundArmed by mutableStateOf(false)

    // ── Silent hand-off after a click ──────────────────────────────────────
    // Clicking a tab plays the click, then hands focus to the next tab
    // (pendingFocusRequester). That arrival is not a move the viewer made, so
    // it must not play the navigation sound on top of the click (owner,
    // 2026-10-31): HomeFocusMap stamps this just before the hand-off, and the
    // next arrival inside the window is silent and clears it. Moving between
    // the tabs by hand still plays the navigation sound.
    var radioClickHandOffUntil = 0L
}

/** How long after a tab click the hand-off's arrival counts as part of the click. */
internal const val RADIO_CLICK_HANDOFF_MS = 500L
