package com.example.bluehive.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester

/**
 * Every focus requester on the home screen: the two frames, the three tab
 * buttons and their parking node, Continue Watching, the sidebar and its
 * parking node, and the shelf stack.
 *
 * Moved here from HomeScreen in Stage H2 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces, its names read through the holder.
 */
@Stable
internal class HomeFocusMap {
    val trailerFrameFocusRequester = FocusRequester()
    val trendingFrameFocusRequester = FocusRequester()
    val moviesToggleButtonFocusRequester = FocusRequester()
    val tvShowsToggleButtonFocusRequester = FocusRequester()
    val animeToggleButtonFocusRequester = FocusRequester()
    val radioParkingRequester = FocusRequester()
    val continueWatchingRequester = FocusRequester()
    val sidebarFocusRequester = FocusRequester()
    val tvLazyRowsSingleShelfFocusRequester = FocusRequester()
    val sidebarParkingRequester = FocusRequester()

    fun requesterFor(tab: CatalogTab): FocusRequester = when (tab) {
        CatalogTab.MOVIES -> moviesToggleButtonFocusRequester
        CatalogTab.TV_SHOWS -> tvShowsToggleButtonFocusRequester
        CatalogTab.ANIME -> animeToggleButtonFocusRequester
    }
}

// moved focus FIRST, then update selectedTab
//Don’t wait a frame. Don’t use pendingFocusTab at all.
internal fun neighborOf(tab: CatalogTab): CatalogTab = when (tab) {
    CatalogTab.MOVIES -> CatalogTab.TV_SHOWS
    CatalogTab.TV_SHOWS -> CatalogTab.ANIME
    CatalogTab.ANIME -> CatalogTab.MOVIES
}

/** Effects 2 to 6 of the home screen's 13 (H-C5), in their order. */
@Composable
internal fun HomeFocusEffects(focusMap: HomeFocusMap, sidebar: HomeSidebarState, shelves: HomeShelfState, tabs: CatalogTabsState) {
    LaunchedEffect(Unit) {
        Log.d("FOCUS_DEBUG", "🎯 continueWatchingRequester created: ${focusMap.continueWatchingRequester.hashCode()}")
    }

    // If the panel goes while it holds focus -- history cleared on the
    // server, noticed by the refresh on returning to this screen -- hand
    // focus to the shelf instead of letting it drop to the top of the page.
    LaunchedEffect(shelves.continueWatchingHasHistory) {
        if (shelves.continueWatchingHasHistory == false &&
            sidebar.lastFocusedTarget == SidebarReturnTarget.CONTINUE_WATCHING &&
            !sidebar.isSidebarFocused
        ) {
            kotlinx.coroutines.android.awaitFrame()
            runCatching { focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus() }
        }
    }

    // SECOND CHANCE, not the main event. Dismissing the choice overlay asks
    // for the shelf synchronously (see onRestoreRowFocus, further down),
    // while the overlay is still composed, so focus never has a gap for
    // Compose to fill with the trailer section -- the same failure the
    // radio-tab parking node exists to prevent. This runs two frames later
    // and only if that did not stick, which is what isShelfFocused answers.
    LaunchedEffect(shelves.restoreShelfFocusPending) {
        if (!shelves.restoreShelfFocusPending) return@LaunchedEffect
        kotlinx.coroutines.android.awaitFrame()
        kotlinx.coroutines.android.awaitFrame()
        if (!shelves.isShelfFocused) {
            Log.d("FOCUS_DEBUG", "choice overlay dismissed: shelf lost focus, re-asking")
            // runCatching: the shelf can be gone by now -- a tab switch, or
            // the session ending underneath us -- and a detached requester
            // throws rather than returning false.
            runCatching { focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus() }
        }
        shelves.restoreShelfFocusPending = false
    }

    LaunchedEffect(tabs.pendingFocusRequester) {
        tabs.pendingFocusRequester?.let { requester ->
            kotlinx.coroutines.android.awaitFrame()
            requester.requestFocus()
            tabs.pendingFocusRequester = null
            // Target button now owns focus — release the parking node.
            tabs.radioParkingEnabled = false
        }
    }

    LaunchedEffect(sidebar.requestSidebarFocus, sidebar.sidebarCanFocus) {
        if (sidebar.requestSidebarFocus && sidebar.sidebarCanFocus) {
            kotlinx.coroutines.android.awaitFrame()
            focusMap.sidebarFocusRequester.requestFocus()
            sidebar.requestSidebarFocus = false
        }
    }
}
