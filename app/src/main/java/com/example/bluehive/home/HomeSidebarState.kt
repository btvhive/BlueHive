package com.example.bluehive.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.bluehive.BlueHiveApplication

/**
 * The sidebar: whether it has focus or may take it, the open and return
 * requests, where focus goes back to, the parking node's flag, and the last
 * place focus was on the screen.
 *
 * Moved here from HomeScreen in Stage H2 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces, its names read through the holder.
 */
@Stable
internal class HomeSidebarState {
    var isSidebarFocused by mutableStateOf(false)
    var sidebarCanFocus by mutableStateOf(false)
    var requestSidebarFocus by mutableStateOf(false)
    var requestSidebarReturnFocus by mutableStateOf(false)
    var sidebarReturnTarget by mutableStateOf<SidebarReturnTarget?>(null)
    // Tracks which home-screen area had focus when back was pressed,
    // so the sidebar can restore focus to exactly that spot on close.
    var lastFocusedTarget by mutableStateOf(SidebarReturnTarget.SEARCH_BAR)
    var sidebarParkingEnabled by mutableStateOf(false)

    fun openSidebar(from: SidebarReturnTarget) {
        sidebarReturnTarget = from
        sidebarCanFocus = true
        requestSidebarFocus = true
    }
}

/** Effect 11 of the home screen's 13 (H-C5): focus back from the sidebar, through the parking node. */
@Composable
internal fun SidebarReturnEffect(sidebar: HomeSidebarState, focusMap: HomeFocusMap, shelves: HomeShelfState, entry: HomeEntryTransition) {
    LaunchedEffect(sidebar.requestSidebarReturnFocus, sidebar.sidebarCanFocus) {
        if (sidebar.requestSidebarReturnFocus && !sidebar.sidebarCanFocus) {
            // ✅ Parking is ALREADY enabled from onExitToRight, and so is the
            // navigation-tick mute; finally ends it once focus has landed.
            try {
                // Wait 1 frame for composition
                kotlinx.coroutines.android.awaitFrame()

                // Move focus to parking spot
                focusMap.sidebarParkingRequester.requestFocus()
                kotlinx.coroutines.android.awaitFrame()

                // Now restore to actual target
                when (sidebar.sidebarReturnTarget) {
                    SidebarReturnTarget.SEARCH_BAR -> focusMap.trailerFrameFocusRequester.requestFocus()
                    SidebarReturnTarget.TRAILER -> focusMap.trailerFrameFocusRequester.requestFocus()
                    SidebarReturnTarget.TRENDING -> focusMap.trendingFrameFocusRequester.requestFocus()
                    SidebarReturnTarget.CONTINUE_WATCHING ->
                        if (shelves.continueWatchingShown(entry)) focusMap.continueWatchingRequester.requestFocus()
                        else focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus()
                    SidebarReturnTarget.MOVIES -> focusMap.moviesToggleButtonFocusRequester.requestFocus()
                    SidebarReturnTarget.TV_SHOWS -> focusMap.tvShowsToggleButtonFocusRequester.requestFocus()
                    SidebarReturnTarget.ANIME -> focusMap.animeToggleButtonFocusRequester.requestFocus()
                    SidebarReturnTarget.MOVIES_SHELF -> focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus()
                    SidebarReturnTarget.TV_SHOWS_SHELF -> focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus()
                    SidebarReturnTarget.ANIME_SHELF -> focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus()
                    null -> focusMap.trailerFrameFocusRequester.requestFocus()
                }

                // Wait for focus to settle
                kotlinx.coroutines.android.awaitFrame()

                // Cleanup
                sidebar.sidebarReturnTarget = null
                sidebar.requestSidebarReturnFocus = false
                sidebar.sidebarParkingEnabled = false

                Log.d("SIDEBAR_PARKING", "✅ Focus restored and parking disabled")
            } finally {
                // Focus has landed (or the restore was cancelled): from here
                // on every tick is a move the viewer made.
                BlueHiveApplication.unmuteHoverSounds()
            }
        }
    }
}
