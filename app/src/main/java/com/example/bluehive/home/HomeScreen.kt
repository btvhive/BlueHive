package com.example.bluehive.home

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.catalog.openMediaCatalogScreen
import com.example.bluehive.continueWatchingComponents.HomeScreenContinueWatchingCompose
import com.example.bluehive.favorites.openFavoritesScreen
import com.example.bluehive.latestTrailersComponents.HomeScreenTrailerSectionCompose
import com.example.bluehive.latestTrailersComponents.TrailerFrameState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.searchBarComponent.openSearchScreen
import com.example.bluehive.sidebarComponents.HomeScreenSidebarCompose
import com.example.bluehive.sidebarComponents.openHistoryScreen
import com.example.bluehive.sidebarComponents.openLiveTvScreen
import com.example.bluehive.sidebarComponents.openProfileScreen
import com.example.bluehive.singleShelfComponents.ShelfStack
import com.example.bluehive.trendingComponents.HomeScreenTrendingCompose
import com.example.bluehive.trendingComponents.TrendingFrameState
import com.example.bluehive.utilities.CloseApplicationHandler
import com.example.bluehive.utilities.NetworkMonitor
import com.example.bluehive.utilities.RetryOverlay
import com.example.bluehive.utilities.overlayScrimColor
import kotlinx.coroutines.launch


internal enum class CatalogTab { MOVIES, TV_SHOWS, ANIME }

/**
 * Longest the navigation tick stays muted while focus returns from the
 * sidebar. The restore ends the mute itself the moment focus lands (~100 ms,
 * measured); this cap only matters if it never does.
 */
private const val SIDEBAR_RETURN_MUTE_CAP_MS = 1_000L


internal enum class SidebarReturnTarget {
    SEARCH_BAR,
    TRAILER,
    MOVIES,
    TV_SHOWS,
    ANIME,
    CONTINUE_WATCHING,
    TRENDING,
    // Separate from MOVIES/TV_SHOWS (which return to the radio buttons).
    // These return directly into the ShelfStack so the card-focus tracker
    // can restore the exact title card the user was on.
    MOVIES_SHELF,
    TV_SHOWS_SHELF,
    // ANIME_SHELF exists for the same reason, and its absence was a quiet bug:
    // the Anime shelf used to report plain ANIME on focus, the same value its
    // radio button reports, so leaving the shelf for the sidebar and coming
    // back landed on the radio button instead of the card. It also made the
    // two states indistinguishable to anything asking "is focus on a card?".
    ANIME_SHELF,
}


/**
 * The home screen: the trailer and trending carousels, the Movies / TV Shows /
 * Anime tabs and their shelves, Continue Watching, the sidebar, the offline
 * banner and the entry overlay. HomeScreenCompose shows it; nothing else may use
 * home/ (checkDetailsBoundaries).
 *
 * Moved here from HomeScreenCompose.kt in Stage H1 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md), where it was
 * HomeScreenComposeContent. Since Stage H2 its state is in five holders and
 * effects 2 to 11 are child composables, called where they were.
 */
@Composable
internal fun HomeScreen(
    onMediaClick:     (MediaItem) -> Unit,
    profileAvatarRes: Int = R.drawable.avatar1,
    profileId:        Int = -1,
    profileName:      String = "",
) {
    val context = LocalContext.current

    // ── State: the entry overlay, the sidebar, the shelves, the tabs, focus ───
    // Five holders (HomeEntryTransition, HomeSidebarState, HomeShelfState,
    // CatalogTabsState, HomeFocusMap). Their properties keep the names these
    // had as local variables, so every read here is the old read.
    val entry    = remember { HomeEntryTransition() }
    val sidebar  = remember { HomeSidebarState() }
    val shelves  = remember { HomeShelfState() }
    val tabs     = remember { CatalogTabsState() }
    val focusMap = remember { HomeFocusMap() }



    // Track whether the home activity is currently in the foreground.
    // When it's not (user navigated to catalog/search/details/etc.), we pause
    // all background work: trailer playback, any polling, etc.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isHomeInForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var trailerSectionState by remember { mutableStateOf<TrailerFrameState>(TrailerFrameState.UnFocused) }
    var trendingState by remember { mutableStateOf<TrendingFrameState>(TrendingFrameState.UnFocused) }






    // Item 6 — offline retry banner state.
    var showRetryBanner by remember { mutableStateOf(false) }
    val retryScope = rememberCoroutineScope()



    val radioNavSound: () -> Unit = {
        if (tabs.radioSoundArmed) BlueHiveApplication.playNavClickSound()
        else tabs.radioSoundArmed = true
    }





    // Single shared clock that drives both the Trailer and Trending carousels
    // in perfect sync. Both sections react to the same increment rather than
    // running independent timers. The screen only holds it: the tick is read
    // by CarouselTickProvider, around the two carousels (P1, CarouselClock.kt).
    val carouselClock = remember { CarouselClock() }

    val carouselClockPaused = carouselPaused(sidebar.isSidebarFocused, isHomeInForeground)

    // Effect 1 of 13: the clock's tick (CarouselClock.kt).
    CarouselClockTicker(carouselClock, paused = carouselClockPaused)


    Box(
        modifier = Modifier
            .fillMaxSize()
            // While the entry transition is up, swallow every key so the user
            // can't interact with the half-loaded screen underneath.
            .onPreviewKeyEvent { entry.showEntryTransition }
    ) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .graphicsLayer(alpha = 0f)
                .focusRequester(focusMap.sidebarParkingRequester)
                .focusProperties { canFocus = sidebar.sidebarParkingEnabled }
                .onPreviewKeyEvent { event ->
                    if (sidebar.sidebarParkingEnabled && event.type == KeyEventType.KeyDown) {
                        Log.d(
                            "SIDEBAR_PARKING",
                            "🚫 Blocking ${event.key} during sidebar transition"
                        )
                        true // Consume all keys
                    } else {
                        false
                    }
                }
                .focusable()
        )







        // ── Effects 2 to 6 of 13: focus (HomeFocusMap.kt) ──
        HomeFocusEffects(focusMap, sidebar, shelves, tabs)


        // IS THE CONTINUE WATCHING PANEL ON SCREEN? (HomeShelfState.continueWatchingShown)
        fun continueWatchingShown(): Boolean = shelves.continueWatchingShown(entry)









        // ── Effects 7 to 10 of 13: the entry overlay (HomeEntryTransition.kt) ──
        HomeEntryEffects(entry, sidebar, focusMap)






        // ── Effect 11 of 13: focus back from the sidebar (HomeSidebarState.kt) ──
        SidebarReturnEffect(sidebar, focusMap, shelves, entry)










        // =================================================================================
        // ✅ MAIN CONTENT BOX - Gets blurred when sidebar opens
        // =================================================================================

        //        Radius | GPU Cost | Frame Time | Visual Quality
        //        -------|----------|------------|---------------
        //        5px    | 0.5ms    | Negligible | Subtle
        //        10px   | 1-2ms    | Negligible | Noticeable ✅ YOU ARE HERE
        //        15px   | 2-3ms    | Minor      | Strong
        //        25px   | 4-6ms    | Moderate   | Very strong
        //        50px   | 10-15ms  | Noticeable | Extreme

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sidebar.isSidebarFocused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // API 31+ (onn box): real GPU blur — looks great, leave it.
                        Modifier.graphicsLayer {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(
                                    10f, 10f,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                .asComposeRenderEffect()
                        }
                    } else {
                        // API 25–30 (Fire TV): RenderEffect blur doesn't exist here.
                        // The old fallback dropped the whole screen to alpha 0.4 and
                        // scaled it 0.97 — that's the washed-out "haze" + shrink. Leave
                        // the content untouched; we darken it with a scrim below instead.
                        Modifier
                    }
                )
        ) {
            // Background image
            Image(
                painter = painterResource(id = R.drawable.home_screen),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )


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
                            down = if (continueWatchingShown()) focusMap.continueWatchingRequester
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







            // ====================================================================================> Radio Buttons

            // Radio-tab parking node — invisible, 1dp. Clicking a radio button
            // makes that button unfocusable (selected → isFocusable=false),
            // leaving a one-frame gap before pendingFocusRequester re-homes
            // focus. Without a holder, Compose fills that gap by flashing focus
            // onto the trailer section. We park focus here synchronously the
            // instant a tab is clicked, so focus never escapes the radio area.
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .graphicsLayer { alpha = 0f }
                    .focusRequester(focusMap.radioParkingRequester)
                    .focusProperties { canFocus = tabs.radioParkingEnabled }
                    .onPreviewKeyEvent { event ->
                        // While parked, swallow keys so a stray press during the
                        // swap frame can't move focus before the target lands.
                        tabs.radioParkingEnabled && event.type == KeyEventType.KeyDown
                    }
                    .focusable()
            )

            MoviesRadioButton(
                fontFamily = passionFont,
                focusRequester = focusMap.moviesToggleButtonFocusRequester,
                selected = (tabs.selectedTab == CatalogTab.MOVIES),
                downNeighbor = focusMap.tvLazyRowsSingleShelfFocusRequester,
                upNeighbor = focusMap.trailerFrameFocusRequester,
                onOpenSidebar = { sidebar.openSidebar(SidebarReturnTarget.MOVIES) },
                onFocused = { sidebar.lastFocusedTarget = SidebarReturnTarget.MOVIES },
                onNavSound = radioNavSound,
                rightNeighbor = if (tabs.selectedTab == CatalogTab.TV_SHOWS) {
                    focusMap.animeToggleButtonFocusRequester
                } else {
                    focusMap.tvShowsToggleButtonFocusRequester
                },
                onSelect = {
                    tabs.radioParkingEnabled = true
                    focusMap.radioParkingRequester.requestFocus()
                    tabs.selectedTab = CatalogTab.MOVIES
                    Log.d("FOCUS_DEBUG", "🔄 Tab switched to: ${tabs.selectedTab}")
                    tabs.pendingFocusRequester = focusMap.tvShowsToggleButtonFocusRequester
                }
            )

            TvShowsRadioButton(
                fontFamily = passionFont,
                focusRequester = focusMap.tvShowsToggleButtonFocusRequester,
                selected = (tabs.selectedTab == CatalogTab.TV_SHOWS),
                upNeighbor = focusMap.trailerFrameFocusRequester,
                downNeighbor = focusMap.tvLazyRowsSingleShelfFocusRequester,
                shouldOpenSidebar = (tabs.selectedTab == CatalogTab.MOVIES),
                onOpenSidebar = {
                    sidebar.openSidebar(SidebarReturnTarget.TV_SHOWS)
                },
                onFocused = { sidebar.lastFocusedTarget = SidebarReturnTarget.TV_SHOWS },
                onNavSound = radioNavSound,
                leftNeighbor = focusMap.moviesToggleButtonFocusRequester,
                // When ANIME is the selected tab its radio button is toggled and
                // unfocusable, so RIGHT from TV SHOWS must skip it and land on
                // Continue Watching instead.
                rightNeighbor = if (tabs.selectedTab == CatalogTab.ANIME) {
                    // With no panel there is nothing to the right; stay put.
                    if (continueWatchingShown()) focusMap.continueWatchingRequester else FocusRequester.Cancel
                } else {
                    focusMap.animeToggleButtonFocusRequester
                },
                onSelect = {
                    tabs.radioParkingEnabled = true
                    focusMap.radioParkingRequester.requestFocus()
                    tabs.selectedTab = CatalogTab.TV_SHOWS
                    Log.d("FOCUS_DEBUG", "🔄 Tab switched to: ${tabs.selectedTab}")
                    tabs.pendingFocusRequester = focusMap.animeToggleButtonFocusRequester
                }
            )

            AnimeRadioButton(
                fontFamily = passionFont,
                focusRequester = focusMap.animeToggleButtonFocusRequester,
                selected = (tabs.selectedTab == CatalogTab.ANIME),
                upNeighbor = focusMap.trailerFrameFocusRequester,
                downNeighbor = focusMap.tvLazyRowsSingleShelfFocusRequester,
                onFocused = { sidebar.lastFocusedTarget = SidebarReturnTarget.ANIME },
                onNavSound = radioNavSound,
                leftNeighbor = if (tabs.selectedTab == CatalogTab.TV_SHOWS) {
                    focusMap.moviesToggleButtonFocusRequester
                } else {
                    focusMap.tvShowsToggleButtonFocusRequester
                },
                // With no panel there is nothing to the right; stay put.
                rightNeighbor = if (continueWatchingShown()) focusMap.continueWatchingRequester
                                else FocusRequester.Cancel,
                onSelect = {
                    tabs.radioParkingEnabled = true
                    focusMap.radioParkingRequester.requestFocus()
                    tabs.selectedTab = CatalogTab.ANIME
                    tabs.pendingFocusRequester = focusMap.moviesToggleButtonFocusRequester
                }
            )


            // ====================================================================================>


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


        // ── Sidebar dim (Fire TV / API < 31 only) ───────────────────────────────
        // The onn box blurs the home content via RenderEffect; pre-31 devices can't,
        // so we drop the same dark scrim the close-app overlay uses over the home
        // screen while the sidebar is open. Sits above the content, below the sidebar.
        if (sidebar.isSidebarFocused && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayScrimColor())
            )
        }


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

        // ── Item 6: offline / retry banner ──────────────────────────────────────
        // Toggling startContent off→on remounts every content child, which
        // re-runs their fetch effects. That's our "reload" with zero child
        // changes. The connectivity check is the failure signal because the
        // repositories swallow errors and return empty lists.
        val reloadContent: () -> Unit = {
            retryScope.launch {
                entry.startContent = false
                kotlinx.coroutines.android.awaitFrame()
                kotlinx.coroutines.android.awaitFrame()
                entry.startContent = true
                kotlinx.coroutines.android.awaitFrame()
                focusMap.trailerFrameFocusRequester.requestFocus()
            }
            Unit
        }

        // Initial check: if we mounted offline, surface the banner immediately.
        LaunchedEffect(Unit) {
            if (!NetworkMonitor.isOnline(context)) showRetryBanner = true
        }

        // Auto-recovery: when the network returns on its own, hide the banner
        // and reload. The callback fires on a binder thread, so hop to the
        // composition's main-dispatcher scope before touching state.
        DisposableEffect(Unit) {
            val cb = NetworkMonitor.registerOnlineCallback(context) {
                retryScope.launch {
                    if (showRetryBanner) {
                        showRetryBanner = false
                        reloadContent()
                    }
                }
            }
            onDispose { NetworkMonitor.unregister(context, cb) }
        }

        if (showRetryBanner) {
            RetryOverlay(
                onRetry = {
                    if (NetworkMonitor.isOnline(context)) {
                        showRetryBanner = false
                        reloadContent()
                    }
                    // Still offline → banner stays up, user can press again.
                }
            )
        }

        // ── Profile-entry transition overlay ────────────────────────────────────
        // Topmost layer. Fades out once content is ready + min time elapsed.
        AnimatedVisibility(
            visible = entry.showEntryTransition,
            enter   = androidx.compose.animation.EnterTransition.None,
            exit    = fadeOut(animationSpec = tween(durationMillis = 400)),
        ) {
            // Shared with ProfileScreenActivity, which raises the identical
            // overlay the instant its press animation ends — see
            // ProfileEntryOverlay. The two must stay identical, or the
            // handoff between the activities becomes a visible flicker.
            com.example.bluehive.utilities.ProfileEntryOverlay(profileName)
        }

        // ── Exit confirmation overlay / back-button routing ─────────────────────
        // On the home screen (sidebar NOT open): back → open sidebar.
        // On the sidebar (sidebar IS open):      back → show exit overlay.
        // All close-app logic lives in CloseApplication.kt — nothing to maintain
        // here except passing the two context values below.
        CloseApplicationHandler(
            isSidebarOpen = sidebar.isSidebarFocused,
            onOpenSidebar = { sidebar.openSidebar(sidebar.lastFocusedTarget) },
            // From a title card, back asks instead of assuming. The guard is the
            // container-focus flag, so it covers every shelf of every tab and
            // nothing else on the screen.
            isOnLazyRow = shelves.isShelfFocused,
            // "Top of the page" is the radio button for the NEXT tab along,
            // because the selected tab's own button is toggled and unfocusable.
            // Same destination UP from the first shelf already goes to, via
            // onNavigateBackToTabs.
            //
            // And the rows go back to the first shelf with it, so the page
            // really is at its top: What's Popular under the tabs and Continue
            // Watching beside it, instead of whichever row back was pressed on.
            // Focus moves first; the stack then changes row without taking it.
            onGoToTop = {
                focusMap.requesterFor(neighborOf(tabs.selectedTab)).requestFocus()
                shelves.scrollShelfTo = 0
            },
            // Synchronous on purpose: this runs before the recomposition that
            // takes the overlay away, so focus steps straight from its button
            // to the title card with no unowned frames in between. Asking for
            // the STACK rather than a card is what makes its own tracker
            // restore the saved card of the shelf the viewer was on, so this
            // screen never has to know which row or which card that was.
            onRestoreRowFocus = {
                runCatching { focusMap.tvLazyRowsSingleShelfFocusRequester.requestFocus() }
                shelves.restoreShelfFocusPending = true
            },
        )

    }
}
