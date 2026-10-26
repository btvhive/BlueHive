package com.example.bluehive.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The profile-entry overlay and when the screen's content starts: the overlay
 * stays up until the first shelf has cards and a minimum time has passed.
 *
 * Moved here from HomeScreen in Stage H2 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces, its names read through the holder.
 */
@Stable
internal class HomeEntryTransition {
    // ── Profile-entry transition overlay ────────────────────────────────────
    // Black screen shown the moment the home screen mounts, hiding the cold
    // load (posters popping in, empty frames) behind a clean message + spinner.
    // Dismisses once BOTH conditions hold: content is ready (startContent) AND
    // a minimum display time has elapsed so it never flashes. While visible it
    // blocks all remote input.
    var showEntryTransition by mutableStateOf(true)
    var minTransitionElapsed by mutableStateOf(false)
    // Set to true when the Popular shelf reports its first page is ready.
    // The entry overlay waits for this before fading out so the home screen
    // is never revealed with empty rows.
    var firstShelfDataReady by mutableStateOf(false)
    var startContent by mutableStateOf(false)
}

/** Effects 7 to 10 of the home screen's 13 (H-C5): startup, the minimum time, the safety net, the dismiss. */
@Composable
internal fun HomeEntryEffects(entry: HomeEntryTransition, sidebar: HomeSidebarState, focusMap: HomeFocusMap) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.android.awaitFrame()
        kotlinx.coroutines.android.awaitFrame() // let UI settle first

        sidebar.sidebarCanFocus = false
        focusMap.trailerFrameFocusRequester.requestFocus()

        // Stagger heavy content — let focus and background settle before
        // triggering all the data fetches and image loads
        delay(300)
        entry.startContent = true
    }
    // Minimum on-screen time for the entry transition so it doesn't flash.
    LaunchedEffect(Unit) {
        delay(2_000L)
        entry.minTransitionElapsed = true
    }
    // Safety net: if the shelf never signals ready (offline, server error),
    // force-dismiss after 6s so the RetryOverlay underneath becomes visible.
    LaunchedEffect(entry.startContent) {
        if (entry.startContent) {
            delay(6_000L)
            entry.firstShelfDataReady = true
        }
    }
    // Dismiss only when BOTH the shelf has real cards AND the minimum
    // display time has elapsed. Cards are rendered behind the overlay
    // before it ever fades — the home screen is never revealed empty.
    LaunchedEffect(entry.firstShelfDataReady, entry.minTransitionElapsed) {
        if (entry.firstShelfDataReady && entry.minTransitionElapsed) {
            entry.showEntryTransition = false
        }
    }
}
