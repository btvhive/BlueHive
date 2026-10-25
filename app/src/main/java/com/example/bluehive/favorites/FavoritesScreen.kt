package com.example.bluehive.favorites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bluehive.R
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import kotlinx.coroutines.launch


// ─────────────────────────────────────────────────────────────────────────────
//  Main composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Favorites screen: the profile's favourites as a grid of title cards, with
 * Filter, Sort, the type tabs and remove mode. FavoritesScreenActivity shows it.
 *
 * Moved here from sidebarComponents/SidebarFavorites.kt in Stage F1 of the
 * Favorites and Home redesign (docs/favorites-home-redesign-architecture.md),
 * with its constants in FavoritesStyle, the model in FavoriteEntry and the
 * details launch in FavoritesLaunch. Since Stage F2 its state is in
 * FavoritesData, FavoritesFilter and FavoritesGridFocus, and its seven effects
 * are child composables, called where they were. Since Stage F3 its sections
 * are too: FavoritesControlBar, FavoritesTypeTabs, FavoritesGrid (with
 * FavoriteCard), the two empty panels and RemoveConfirmOverlay.
 */
@Composable
fun FavoritesScreen(profileId: Int = -1, refreshTrigger: Int = 0) {

    // ── State: the favourites, the filter and sort, the grid's focus ──────────
    val data   = remember { FavoritesData() }
    val filter = remember { FavoritesFilter(data.entries) }

    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val focus = rememberFavoritesGridFocus(filter, coroutineScope)

    // ── Focus requesters — control bar ────────────────────────────────────────
    val filterDropdownFocus = remember { FocusRequester() }
    val sortDropdownFocus   = remember { FocusRequester() }
    val allMediaFocus       = remember { FocusRequester() }
    val moviesFocus         = remember { FocusRequester() }
    val tvSeriesFocus       = remember { FocusRequester() }
    val deleteButtonFocus   = remember { FocusRequester() }
    val acceptChangesFocus  = remember { FocusRequester() }
    val undoChangesFocus    = remember { FocusRequester() }

    // ── Every piece of state is reset when the screen leaves (effect 1 of 7) ─
    FavoritesResetOnDispose(data, filter, focus)

    // ── MediaCard painters — loaded once, reused for every card ──────────────
    val backgroundPainter = painterResource(id = R.drawable.titlecard_base)
    val focusedPainter    = painterResource(id = R.drawable.titlecard_focused)
    val shadowPainter     = painterResource(id = R.drawable.titlecard_shadow)
    val glSurfaceView = remember { mutableStateOf<VanishGLSurfaceView?>(null) }

    // ── Data fetch (effect 2 of 7) ────────────────────────────────────────────
    FavoritesFetch(data, profileId, refreshTrigger)

    val showFilteredEmptyState by remember(data.isLoading, data.entries.size, filter.filtered.size) {
        derivedStateOf {
            !data.isLoading && data.entries.isNotEmpty() && filter.filtered.isEmpty()
        }
    }

    // ── Per-item FocusRequesters, keyed to the list size (FavoritesGridFocus) ─
    val gridFocusRequesters = focus.requesters(filter.filtered.size)

    // ── Grid focus effects (3 to 6 of 7) ──────────────────────────────────────
    FavoritesGridEffects(data, filter, focus, gridFocusRequesters)

    // ── Navigate to details — press-and-prefetch pattern ─────────────────────
    fun navigateToDetails(entry: FavoriteEntry) {
        if (data.isNavigating) return
        data.isNavigating = true
        coroutineScope.launch {
            try {
                val intent = prefetchDetailsLaunch(context, entry, profileId)

                data.isNavigating = false
                context.startActivity(intent)

            } catch (e: Exception) {
                android.util.Log.e("FavoritesScreen", "Navigation failed: ${e.message}")
                data.isNavigating = false
            }
        }
    }

    // ── Auto-focus confirm overlay when it appears (effect 7 of 7) ────────────
    ExitRemoveOverlayFocus(data, acceptChangesFocus)

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize()) {

        // Background
        Image(
            painter            = painterResource(id = R.drawable.home_screen),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

        // Dark scrim — turns red at low opacity when Remove Mode is active
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (data.removeMode) Color.Red.copy(alpha = 0.15f)
                    else Color.Black.copy(alpha = 0.45f)
                )
        )

        // ── Empty state ───────────────────────────────────────────────────────
        if (!data.isLoading && data.entries.isEmpty() && !data.showExitRemoveOverlay) {

            NoFavoritesPanel()

        } else if (!data.isLoading) {

            // ── Header, Filter and Sort, Remove Media ─────────────────────────
            FavoritesControlBar(
                data                = data,
                filter              = filter,
                focus               = focus,
                profileId           = profileId,
                coroutineScope      = coroutineScope,
                gridFocusRequesters = gridFocusRequesters,
                filterDropdownFocus = filterDropdownFocus,
                sortDropdownFocus   = sortDropdownFocus,
                deleteButtonFocus   = deleteButtonFocus,
            )

            // ── All Media, Movies and TV Shows ────────────────────────────────
            FavoritesTypeTabs(
                filter        = filter,
                focus         = focus,
                allMediaFocus = allMediaFocus,
                moviesFocus   = moviesFocus,
                tvSeriesFocus = tvSeriesFocus,
            )

            // ── Media card grid ───────────────────────────────────────────────
            FavoritesGrid(
                data                = data,
                filter              = filter,
                focus               = focus,
                glSurfaceView       = glSurfaceView,
                filterDropdownFocus = filterDropdownFocus,
                coroutineScope      = coroutineScope,
                gridFocusRequesters = gridFocusRequesters,
                backgroundPainter   = backgroundPainter,
                focusedPainter      = focusedPainter,
                shadowPainter       = shadowPainter,
                navigateToDetails   = ::navigateToDetails,
            )

            if (showFilteredEmptyState) {
                NoMatchesPanel()
            }

            // ── Bottom fade scrim — signals more content below ────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(65.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                        )
                    )
            )

        }   // closes else if (!isLoading)

        // ── Remove mode confirmation overlay ──────────────────────────────────
        if (data.showExitRemoveOverlay) {
            RemoveConfirmOverlay(
                data                = data,
                filter              = filter,
                focus               = focus,
                profileId           = profileId,
                coroutineScope      = coroutineScope,
                gridFocusRequesters = gridFocusRequesters,
                acceptChangesFocus  = acceptChangesFocus,
                undoChangesFocus    = undoChangesFocus,
            )
        }

    }   // closes main Box fillMaxSize

}   // closes FavoritesScreen


/**
 * Effect 1 of the screen's seven (F-C5): every piece of state is reset when the
 * screen leaves. That state is thrown away anyway, so this does nothing; it
 * stays until removing it is approved (docs/favorites-home-redesign-architecture.md
 * section 9, #2).
 */
@Composable
private fun FavoritesResetOnDispose(data: FavoritesData, filter: FavoritesFilter, focus: FavoritesGridFocus) {
    DisposableEffect(Unit) {
        onDispose {
            data.entries.clear()
            data.removed.clear()

            data.isLoading = true
            data.isNavigating = false

            filter.genre = "All Content"
            filter.sort = "Newest"
            filter.typeTab = "all"
            filter.isAnyDropdownExpanded = false
            focus.suppressNextHover = false
            focus.hoverArmed = false
            data.removeMode = false
            data.showDeleteConfirm = false

            focus.hasGridFocus = false
            focus.focusedGridIndex = -1
            focus.lastNavTime = 0L
        }
    }
}

/** Effect 7 of the screen's seven (F-C5): "Exit Remove Mode" takes focus on Accept when it appears. */
@Composable
private fun ExitRemoveOverlayFocus(data: FavoritesData, acceptChangesFocus: FocusRequester) {
    LaunchedEffect(data.showExitRemoveOverlay) {
        if (data.showExitRemoveOverlay) {
            kotlinx.coroutines.android.awaitFrame()
            acceptChangesFocus.requestFocus()
        }
    }
}


// =========================================================== DO NOT DELETE ===============================================================
@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 960,
    heightDp = 540,
)
@Composable
fun FavoritesScreenPreview() {
    FavoritesScreen()
}
// =========================================================== DO NOT DELETE ===============================================================
