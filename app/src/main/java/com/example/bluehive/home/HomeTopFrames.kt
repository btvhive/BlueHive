package com.example.bluehive.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.latestTrailersComponents.HomeScreenTrailerSectionCompose
import com.example.bluehive.latestTrailersComponents.TrailerFrameState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.trendingComponents.HomeScreenTrendingCompose
import com.example.bluehive.trendingComponents.TrendingFrameState

/**
 * The trailer and trending frames, their focus and their Left keys, around the
 * two carousels and inside the tick provider (CarouselClock.kt).
 *
 * Moved here from HomeScreen in Stage H3 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces.
 */
@Composable
internal fun BoxScope.HomeTopFrames(
    entry: HomeEntryTransition,
    sidebar: HomeSidebarState,
    shelves: HomeShelfState,
    tabs: CatalogTabsState,
    focusMap: HomeFocusMap,
    carouselClock: CarouselClock,
    isHomeInForeground: Boolean,
    onMediaClick: (MediaItem) -> Unit,
) {
    var trailerSectionState by remember { mutableStateOf<TrailerFrameState>(TrailerFrameState.UnFocused) }
    var trendingState by remember { mutableStateOf<TrendingFrameState>(TrendingFrameState.UnFocused) }

    // ====================================================================================> Trailer Section (extracted)
    CarouselTickProvider(carouselClock) {
        if (entry.startContent) Box(
            modifier = Modifier
                .offset(y = (-18).dp)
                .focusRequester(focusMap.trailerFrameFocusRequester)
                .onFocusChanged { if (it.hasFocus) sidebar.lastFocusedTarget = SidebarReturnTarget.TRAILER }
                .focusProperties {
                    right = focusMap.trendingFrameFocusRequester
                    down = focusMap.requesterFor(neighborOf(tabs.selectedTab))
                    left = focusMap.sidebarFocusRequester
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (trailerSectionState !is TrailerFrameState.Selected) {
                                    BlueHiveApplication.playHoverSound()
                                    sidebar.openSidebar(SidebarReturnTarget.TRAILER)
                                    true
                                } else {
                                    false  // Let carousel handle it
                                }
                            }

                            else -> false
                        }
                    } else false
                }
        ) {
            HomeScreenTrailerSectionCompose(
                // Pause if sidebar is focused OR if the home activity is not the
                // foreground activity (user is in catalog, search, details, etc.)
                isPaused = sidebar.isSidebarFocused || !isHomeInForeground,
                onStateChanged = { newState ->
                    trailerSectionState = newState
                }
            )
        }

        // ====================================================================================>

        // ====================================================================================> trending Section (extracted)
        if (entry.startContent) Box(
            modifier = Modifier
                // 458 dp before the panel narrowed by 35 dp to 16:9
                // (2026-10-19); moved right by the same 35 so its right
                // edge still lines up with Continue Watching below.
                .offset(x = 493.dp, y = (-3.6).dp)
                .focusRequester(focusMap.trendingFrameFocusRequester)
                .onFocusChanged { if (it.hasFocus) sidebar.lastFocusedTarget = SidebarReturnTarget.TRENDING }
                .focusProperties {
                    left = focusMap.trailerFrameFocusRequester
                    // Straight down to the shelf when there is no panel --
                    // the first shelf then runs underneath this frame.
                    down = if (shelves.continueWatchingShown(entry)) focusMap.continueWatchingRequester
                           else focusMap.tvLazyRowsSingleShelfFocusRequester
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (trendingState !is TrendingFrameState.Selected) {
                                    BlueHiveApplication.playHoverSound()
                                    focusMap.trailerFrameFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    } else false
                }
        ) {
            HomeScreenTrendingCompose(
                trendType = "day",
                isPaused = sidebar.isSidebarFocused || !isHomeInForeground,
                onItemClick = onMediaClick,
                onStateChanged = { newState -> trendingState = newState }
            )
        }
    }
    // ====================================================================================>
}
