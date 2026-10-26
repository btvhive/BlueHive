package com.example.bluehive.home

import com.example.bluehive.singleShelfComponents.MediaType
import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.example.bluehive.models.MediaItem
import com.example.bluehive.singleShelfComponents.ShelfStack


/**
 * What the home screen's one ShelfStack call does differently on each tab: the
 * media type, where focus returns to from the sidebar, whether the stack
 * reports its first page (the entry overlay waits on the Movies one), and the
 * log lines each of the three calls it replaced wrote, word for word.
 *
 * Since Stage H1 of the Favorites and Home redesign; read off the old calls by
 * script, and pinned by HomeShelvesTest.
 */
internal class HomeShelfTab private constructor(
    val mediaType: MediaType,
    val returnTarget: SidebarReturnTarget,
    val reportsFirstPage: Boolean,
    /** Logged each time the stack is composed, as tag to message; null logs nothing. */
    val renderLog: Pair<String, String>?,
    /** Starts the FOCUS_DEBUG line a focus change logs; null logs nothing. */
    val focusLog: String?,
    val shelfChangedLog: String,
    val firstShelfLog: String,
) {
    companion object {
        private val movies = HomeShelfTab(
            mediaType        = MediaType.MOVIES,
            returnTarget     = SidebarReturnTarget.MOVIES_SHELF,
            reportsFirstPage = true,
            renderLog        = null,
            focusLog         = null,
            shelfChangedLog  = "📺 Movies shelf changed to:",
            firstShelfLog    = "📺 First shelf status:",
        )
        private val tvShows = HomeShelfTab(
            mediaType        = MediaType.TV_SHOWS,
            returnTarget     = SidebarReturnTarget.TV_SHOWS_SHELF,
            reportsFirstPage = false,
            renderLog        = "FOCUS_DEBUG" to "🎬 Rendering TV Shows shelf",
            focusLog         = "TV Shows ShelfStack focus changed:",
            shelfChangedLog  = "📺 TV shelf changed to:",
            firstShelfLog    = "📺 TV first shelf status:",
        )
        private val anime = HomeShelfTab(
            mediaType        = MediaType.ANIME,
            returnTarget     = SidebarReturnTarget.ANIME_SHELF,
            reportsFirstPage = false,
            renderLog        = "ANIME_DEBUG" to "🎌 Shelf rendering, shelves count check",
            focusLog         = "Anime ShelfStack focus changed:",
            shelfChangedLog  = "🎌 Anime shelf changed to:",
            firstShelfLog    = "🎌 Anime first shelf status:",
        )

        fun of(tab: CatalogTab): HomeShelfTab = when (tab) {
            CatalogTab.MOVIES -> movies
            CatalogTab.TV_SHOWS -> tvShows
            CatalogTab.ANIME -> anime
        }
    }
}

/**
 * The shelves: ONE ShelfStack, keyed by tab (HomeShelfTab holds what differs).
 *
 * Moved here from HomeScreen in Stage H3 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces.
 */
@Composable
internal fun BoxScope.HomeShelves(
    entry: HomeEntryTransition,
    sidebar: HomeSidebarState,
    shelves: HomeShelfState,
    tabs: CatalogTabsState,
    focusMap: HomeFocusMap,
    onMediaClick: (MediaItem) -> Unit,
    profileId: Int,
) {
    // ====================================================================================> Tv Lazy Column implementation (TV is class type not media type)

    // The shelves: ONE ShelfStack, inside key(selectedTab), which disposes
    // and creates it on a tab switch exactly as the three if blocks it
    // replaced did. What differs by tab is in HomeShelfTab (HomeShelves.kt).
    if (entry.startContent) key(tabs.selectedTab) {
        val shelf = HomeShelfTab.of(tabs.selectedTab)
        shelf.renderLog?.let { (tag, message) -> Log.d(tag, message) }
        val backToTabsRequester = focusMap.requesterFor(neighborOf(tabs.selectedTab))

        ShelfStack(
            mediaType = shelf.mediaType,
            // LEFT on the first card of any shelf opens the sidebar,
            // identical to BACK. lastFocusedTarget is what lets RIGHT
            // on the sidebar come back to this row and card.
            onExitLeft = { sidebar.openSidebar(sidebar.lastFocusedTarget) },
            modifier = Modifier
                .focusRequester(focusMap.tvLazyRowsSingleShelfFocusRequester)
                .offset(x = 90.dp, y = SHELF_STACK_Y)
                .width(880.dp)
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) sidebar.lastFocusedTarget = shelf.returnTarget
                    shelves.isShelfFocused = focusState.hasFocus
                    shelf.focusLog?.let { Log.d("FOCUS_DEBUG", "$it ${focusState.hasFocus}") }
                },
            onMediaClick = onMediaClick,
            onNavigateBackToTabs = { backToTabsRequester.requestFocus() },
            onShelfChanged = { newShelf ->
                Log.d("HOME", "${shelf.shelfChangedLog} $newShelf")
            },
            showContinueWatching = shelves.isOnFirstShelf,
            onFirstShelfChanged = { isFirst ->
                shelves.isOnFirstShelf = isFirst
                Log.d("HOME", "${shelf.firstShelfLog} $isFirst")
            },
            profileId = profileId,
            // Only the Movies stack reports it; the others did not pass this.
            onFirstPageReady = { if (shelf.reportsFirstPage) entry.firstShelfDataReady = true },
            reserveContinueWatching = shelves.continueWatchingHasHistory != false,
            enterAtShelf = shelves.enterShelfAt,
            onEnterAtShelfHandled = { shelves.enterShelfAt = null },
            scrollToShelf = shelves.scrollShelfTo,
            onScrollToShelfHandled = { shelves.scrollShelfTo = null },
        )
    }
    // ====================================================================================>
}
