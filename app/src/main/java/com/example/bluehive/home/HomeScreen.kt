package com.example.bluehive.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.bluehive.R
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.CloseApplicationHandler
import com.example.bluehive.utilities.overlayScrimColor


internal enum class CatalogTab { MOVIES, TV_SHOWS, ANIME }


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
 * effects 2 to 11 are child composables, called where they were. Since Stage
 * H3 its sections are too: HomeTopFrames, HomeContinueWatchingPanel,
 * HomeCatalogTabs, HomeShelves, HomeSidebar and OfflineRetry.
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


            // ── Trailer and trending frames (HomeTopFrames.kt) ──
            HomeTopFrames(
                entry              = entry,
                sidebar            = sidebar,
                shelves            = shelves,
                tabs               = tabs,
                focusMap           = focusMap,
                carouselClock      = carouselClock,
                isHomeInForeground = isHomeInForeground,
                onMediaClick       = onMediaClick,
            )

            // ── Continue Watching (HomeContinueWatchingPanel.kt) ──
            HomeContinueWatchingPanel(
                entry     = entry,
                sidebar   = sidebar,
                shelves   = shelves,
                focusMap  = focusMap,
                profileId = profileId,
            )

            // ── Movies, TV Shows and Anime tabs (CatalogTabs.kt) ──
            HomeCatalogTabs(
                entry    = entry,
                sidebar  = sidebar,
                shelves  = shelves,
                tabs     = tabs,
                focusMap = focusMap,
            )

            // ── The shelves (HomeShelves.kt) ──
            HomeShelves(
                entry        = entry,
                sidebar      = sidebar,
                shelves      = shelves,
                tabs         = tabs,
                focusMap     = focusMap,
                onMediaClick = onMediaClick,
                profileId    = profileId,
            )

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


        // ── Sidebar (HomeSidebar.kt) ──
        HomeSidebar(
            sidebar          = sidebar,
            focusMap         = focusMap,
            context          = context,
            profileId        = profileId,
            profileAvatarRes = profileAvatarRes,
        )

        // ── Offline / retry banner (effects 12 and 13) (OfflineRetry.kt) ──
        OfflineRetry(
            entry    = entry,
            focusMap = focusMap,
            context  = context,
        )

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
