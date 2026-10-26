package com.example.bluehive.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.catalog.openMediaCatalogScreen
import com.example.bluehive.favorites.openFavoritesScreen
import com.example.bluehive.searchBarComponent.openSearchScreen
import com.example.bluehive.sidebarComponents.HomeScreenSidebarCompose
import com.example.bluehive.sidebarComponents.openHistoryScreen
import com.example.bluehive.sidebarComponents.openLiveTvScreen
import com.example.bluehive.sidebarComponents.openProfileScreen

/**
 * Longest the navigation tick stays muted while focus returns from the
 * sidebar. The restore ends the mute itself the moment focus lands (~100 ms,
 * measured); this cap only matters if it never does.
 */
private const val SIDEBAR_RETURN_MUTE_CAP_MS = 1_000L

/**
 * The sidebar, and where each of its items goes.
 *
 * Moved here from HomeScreen in Stage H3 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces.
 */
@Composable
internal fun BoxScope.HomeSidebar(
    sidebar: HomeSidebarState,
    focusMap: HomeFocusMap,
    context: Context,
    profileId: Int,
    profileAvatarRes: Int,
) {
    // ====================================================================================> SIDEBAR HERE
    HomeScreenSidebarCompose(
        focusRequester   = focusMap.sidebarFocusRequester,
        canFocus         = sidebar.sidebarCanFocus,
        profileAvatarRes = profileAvatarRes,
        onFocusChanged = { focused ->
            sidebar.isSidebarFocused = focused
        },
        onExitToRight = {
            BlueHiveApplication.coilImageLoader.memoryCache?.clear()
            Log.d("CACHE", "🧹 Cleared Coil memory + disk caches on sidebar exit")

            // ONE PRESS, ONE TICK. The sidebar has just played the tick for
            // this RIGHT. Everything from here to the end of the restore
            // below moves focus on its own, and a radio button announces
            // every arrival -- so the press used to tick twice, ~100 ms
            // apart (measured 2026-10-16). Muted until the restore lands;
            // the cap only matters if that never happens.
            BlueHiveApplication.muteHoverSounds(SIDEBAR_RETURN_MUTE_CAP_MS)

            sidebar.sidebarParkingEnabled = true
            sidebar.sidebarCanFocus = false
            sidebar.requestSidebarReturnFocus = true
        },
        onProfileClick = {
            openProfileScreen(context)
        },
        onSearchSubmit = { query ->
            openSearchScreen(context, profileId, query)
        },
        onMediaCatalogClick = {
            openMediaCatalogScreen(context, profileId)
        },
        onSettingsClick = {
            Log.d("SIDEBAR", "Settings clicked")
            context.startActivity(
                Intent(context, com.example.bluehive.settings.SettingsActivity::class.java)
            )
        },
        onFavoritesClick = { openFavoritesScreen(context, profileId) },
        onLiveTvClick = {
            Log.d("SIDEBAR", "Live TV clicked")
            openLiveTvScreen(context, profileId)
        },
        onChangelogClick = {
            Log.d("SIDEBAR", "Changelog clicked")
        },
        onHistoryClick = { openHistoryScreen(context, profileId) },
    )
    // ====================================================================================>
}
