package com.example.bluehive.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.continueWatchingComponents.HomeScreenContinueWatchingCompose

/**
 * The Continue Watching panel beside the first shelf.
 *
 * Moved here from HomeScreen in Stage H3 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces.
 */
@Composable
internal fun BoxScope.HomeContinueWatchingPanel(
    entry: HomeEntryTransition,
    sidebar: HomeSidebarState,
    shelves: HomeShelfState,
    focusMap: HomeFocusMap,
    profileId: Int,
) {
    // ====================================================================================> Continue Watching Section (extracted)
    // ✅ Shows Continue Watching for ALL media types on first shelf
    if (entry.startContent && shelves.isOnFirstShelf) {
        Box(
            modifier = Modifier
                .offset(x = 690.dp, y = 290.dp)
                .focusRequester(focusMap.continueWatchingRequester)
                .onFocusChanged { if (it.hasFocus) sidebar.lastFocusedTarget = SidebarReturnTarget.CONTINUE_WATCHING }
                .focusProperties {
                    up   = focusMap.trendingFrameFocusRequester
                    left = focusMap.animeToggleButtonFocusRequester
                }
        ) {
            HomeScreenContinueWatchingCompose(
                profileId = profileId,
                focusRequester = focusMap.continueWatchingRequester,
                upNeighbor = focusMap.trendingFrameFocusRequester,
                // DOWN SKIPS THE FIRST ROW ON PURPOSE.
                //
                // This panel sits BESIDE row 0, not above it -- the row
                // runs underneath it and stops short of it. So "down"
                // onto row 0 would be a sideways move dressed as a
                // downward one, landing on a row that is already on
                // screen and already reachable with LEFT. Row 1 is the
                // first thing genuinely below the panel.
                //
                // Deliberately ONE WAY. Coming back UP from row 1 goes
                // to row 0, not here, because that is the stack's own
                // vertical order and the panel is not part of it.
                //
                // The sound is made here because nothing downstream
                // will: requestNavigation is called directly rather
                // than through the stack's key handler, which is where
                // its own DOWN sound lives.
                onNavigateDown = {
                    BlueHiveApplication.playHoverSound()
                    shelves.enterShelfAt = 1
                },
                onNavigateLeft = {
                    focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus()
                },
                // Composed even when the profile has no history, so it keeps
                // loading (and refreshing on every return here) -- it just
                // draws nothing, and this is how the screen finds out.
                onHistoryKnown = { hasHistory -> shelves.continueWatchingHasHistory = hasHistory },
                knownHasHistory = shelves.continueWatchingHasHistory,
            )
        }
    }
    // ====================================================================================>
}
