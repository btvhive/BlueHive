package com.example.bluehive.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.bluehive.utilities.TitleCardPress
import com.example.bluehive.utilities.runTitleCardPress
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.api.ApiClient
import com.example.bluehive.models.MediaItem
import com.example.bluehive.sidebarComponents.HistoryDropdown
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.MediaCard
import com.example.bluehive.utilities.ModularButton
import com.exjunk.lib.thanos.vanish.VanishContainer
import com.exjunk.lib.thanos.vanish.VanishEffect.vanishable
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import com.exjunk.lib.thanos.vanish.rememberVanishController
import kotlinx.coroutines.delay
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
 * details launch in FavoritesLaunch.
 */
@Composable
fun FavoritesScreen(profileId: Int = -1, refreshTrigger: Int = 0) {

    // ── Data state ────────────────────────────────────────────────────────────
    val favEntries   = remember { mutableStateListOf<FavoriteEntry>() }
    var isLoading    by remember { mutableStateOf(true) }
    var isNavigating by remember { mutableStateOf(false) }

    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()



    // ── Filter / sort state ───────────────────────────────────────────────────
    var selectedGenre         by remember { mutableStateOf("All Content") }
    var selectedSort          by remember { mutableStateOf("Newest") }
    var isAnyDropdownExpanded by remember { mutableStateOf(false) }
    var selectedFilter        by remember { mutableStateOf("all") }
    var suppressNextHover     by remember { mutableStateOf(false) }

    // ── Hover-sound arming ───────────────────────────────────────────────────
    // The page auto-focuses its first card once loading finishes (see the
    // "On page enter" effect below). That focus event is indistinguishable from
    // a real one at the card, so opening Favourites always announced itself with
    // a navigation sound for a move the viewer never made.
    //
    // Armed one frame AFTER that initial requestFocus lands, which is what
    // guarantees the event has already been delivered. Re-disarmed whenever the
    // page reloads, since the same auto-focus runs again.
    var hoverArmed            by remember { mutableStateOf(false) }
    var removeMode            by remember { mutableStateOf(false) }
    var showDeleteConfirm     by remember { mutableStateOf(false) }
    val deletedEntries        = remember { mutableStateListOf<FavoriteEntry>() }

    // ── Focus requesters — control bar ────────────────────────────────────────
    val filterDropdownFocus = remember { FocusRequester() }
    val sortDropdownFocus   = remember { FocusRequester() }
    val allMediaFocus       = remember { FocusRequester() }
    val moviesFocus         = remember { FocusRequester() }
    val tvSeriesFocus       = remember { FocusRequester() }
    val deleteButtonFocus   = remember { FocusRequester() }
    val acceptChangesFocus  = remember { FocusRequester() }
    val undoChangesFocus    = remember { FocusRequester() }

    // ── Grid focus state ──────────────────────────────────────────────────────
    // Mirrors TvWideLazyRow exactly:
    //   hasGridFocus  = whether ANY card in the grid currently has focus
    //   focusedGridIndex = which index is focused (-1 = none yet)
    // isItemFocused = hasGridFocus && focusedGridIndex == index
    // This is the ONLY way MediaCard.isFocused gets set — never individual per-card vars.
    var hasGridFocus     by remember { mutableStateOf(false) }
    var focusedGridIndex by remember { mutableIntStateOf(-1) }
    var lastNavTime      by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            favEntries.clear()
            deletedEntries.clear()

            isLoading = true
            isNavigating = false

            selectedGenre = "All Content"
            selectedSort = "Newest"
            selectedFilter = "all"
            isAnyDropdownExpanded = false
            suppressNextHover = false
            hoverArmed = false
            removeMode = false
            showDeleteConfirm = false

            hasGridFocus = false
            focusedGridIndex = -1
            lastNavTime = 0L
        }
    }



    // ── MediaCard painters — loaded once, reused for every card ──────────────
    val backgroundPainter = painterResource(id = R.drawable.titlecard_base)
    val focusedPainter    = painterResource(id = R.drawable.titlecard_focused)
    val shadowPainter     = painterResource(id = R.drawable.titlecard_shadow)
    var glSurfaceView by remember { mutableStateOf<VanishGLSurfaceView?>(null) }

    // ── Data fetch ────────────────────────────────────────────────────────────
    LaunchedEffect(profileId, refreshTrigger) {
        if (profileId == -1) { isLoading = false; return@LaunchedEffect }
        // First load shows the loading state. Subsequent resumes (refreshTrigger > 1)
        // re-fetch silently in the background so existing cards stay visible — no flash, no jerk.
        val silent = favEntries.isNotEmpty()
        if (!silent) isLoading = true
        try {
            val rawList = ApiClient.bluehiveApi.getFavorites(profileId)

            // ONE REQUEST FOR EVERY FAVOURITE, not one per favourite.
            //
            // This used to fire a getMediaDetails per entry and awaitAll() them.
            // MEASURED 2026-09-04 on a profile with 131 favourites: the server
            // answered each in ~3.5ms, but OkHttp allows only 5 concurrent
            // requests per host by default, so the screen waited on ~27
            // sequential round trips. The same details now arrive in one call.
            //
            // CHUNKED because the endpoint caps a call at 300 pairs. At 131 this
            // is a single request; it only matters for someone who hoards.
            val details = rawList
                .map { "${it.media_tmdb_id}:${it.media_type}" }
                .chunked(200)
                .flatMap { chunk ->
                    try {
                        ApiClient.trailerApi.getMediaBatch(chunk.joinToString(","))
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "FavoritesScreen", "batch details failed: ${e.message}"
                        )
                        emptyList()
                    }
                }
                .associateBy { it.tmdb_id to it.media_type }

            // Built from rawList, so the server's ordering is preserved and every
            // favourite gets a card. A title the batch had nothing for falls back
            // to the same stub the per-item 404 produced before.
            val detailed = rawList.map { fav ->
                details[fav.media_tmdb_id to fav.media_type]
                    ?.toFavoriteEntry(favoriteId = fav.id, addedAt = fav.added_at)
                    ?: FavoriteEntry(
                        favoriteId = fav.id,
                        tmdbId     = fav.media_tmdb_id,
                        mediaType  = fav.media_type,
                        addedAt    = fav.added_at,
                        title      = fav.media_title ?: "Unknown",
                    )
            }
            favEntries.clear()
            favEntries.addAll(detailed)
        } catch (e: Exception) {
            android.util.Log.e("FavoritesScreen", "Failed to load favorites: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // ── Filtered + sorted list ────────────────────────────────────────────────
    val filteredEntries by remember(selectedFilter, selectedGenre, selectedSort) {
        derivedStateOf {
            var items: List<FavoriteEntry> = when (selectedFilter) {
                "movies" -> favEntries.filter { it.mediaType == "movie" }
                "tv"     -> favEntries.filter { it.mediaType == "tv" }
                else     -> favEntries.toList()
            }
            if (selectedGenre != "All Content") {
                items = items.filter { entry ->
                    entry.genresList.any { it.equals(selectedGenre, ignoreCase = true) }
                }
            }
            items = when (selectedSort) {
                "Oldest"          -> items.sortedBy { it.favoriteId }
                "A–Z"             -> items.sortedBy { it.title.lowercase() }
                "Z–A"             -> items.sortedByDescending { it.title.lowercase() }
                "By Release Date" -> items.sortedByDescending { it.releaseDate ?: "" }
                else              -> items.sortedByDescending { it.favoriteId }  // Newest
            }
            items
        }
    }

    val showFilteredEmptyState by remember(isLoading, favEntries.size, filteredEntries.size) {
        derivedStateOf {
            !isLoading && favEntries.isNotEmpty() && filteredEntries.isEmpty()
        }
    }

    // ── Per-item FocusRequesters, ACTUALLY keyed to list size ────────────────
    //
    // This was `remember { List(200) { ... } }` while the comment claimed it was
    // keyed to the list — it was a fixed pool, and the grid indexes it with the
    // position from itemsIndexed(filteredEntries). A profile with 200+ favorites
    // therefore threw IndexOutOfBoundsException from inside composition the moment
    // card 201 scrolled into view, taking the Favorites screen down and leaving it
    // unreachable. Neither side caps: getFavorites takes no limit/offset and the
    // backend returns every row.
    //
    // Re-creating on size change is safe here: every focus-restore path already
    // awaitFrame()s after a size change (see the LaunchedEffects below), so the
    // new requesters are attached before anything requests focus through them.
    val gridFocusRequesters = remember(filteredEntries.size) {
        List(filteredEntries.size) { FocusRequester() }
    }

    // ── Grid scroll state ─────────────────────────────────────────────────────
    val gridState = rememberLazyGridState()

    // ── Reset focus + scroll when filter/sort changes ─────────────────────────
    LaunchedEffect(selectedFilter, selectedGenre, selectedSort) {
        focusedGridIndex = -1
        gridState.scrollToItem(0)
    }

    // ── Auto-land on first item when grid receives focus with nothing selected ─
    // Mirrors TvWideLazyRow: LaunchedEffect(items.size, hasRowFocus)
    LaunchedEffect(filteredEntries.size, hasGridFocus) {
        if (hasGridFocus && filteredEntries.isNotEmpty() && focusedGridIndex == -1) {
            focusedGridIndex = 0
            gridFocusRequesters.getOrNull(0)?.requestFocus()
        }
    }

    // ── On page enter: auto-focus first card as soon as data is ready ─────────
    // Fires once when isLoading flips to false and there is at least one item.
    // awaitFrame gives the grid one frame to compose its items before we
    // request focus — without it the FocusRequester may not be attached yet.
    LaunchedEffect(isLoading) {
        // Disarmed for the whole of this effect: a reload re-runs the auto-focus
        // below, and that landing must stay as silent as the first one.
        hoverArmed = false
        if (!isLoading && filteredEntries.isNotEmpty()) {
            kotlinx.coroutines.android.awaitFrame()
            val targetIndex = focusedGridIndex.coerceIn(0, filteredEntries.lastIndex)
            focusedGridIndex = targetIndex
            gridState.scrollToItem(targetIndex)
            kotlinx.coroutines.android.awaitFrame()
            gridFocusRequesters.getOrNull(targetIndex)?.requestFocus()
            // One more frame, so the focus event above has been delivered and
            // cannot be mistaken for the viewer navigating.
            kotlinx.coroutines.android.awaitFrame()
        }
        hoverArmed = true
    }


    // ── Restore focus to next card after removal in Remove Mode ───────────────
    LaunchedEffect(filteredEntries.size) {
        if (removeMode && filteredEntries.isNotEmpty()) {
            kotlinx.coroutines.android.awaitFrame()
            kotlinx.coroutines.android.awaitFrame()
            val nextIndex = focusedGridIndex.coerceIn(0, filteredEntries.lastIndex)
            focusedGridIndex = nextIndex
            gridFocusRequesters.getOrNull(nextIndex)?.requestFocus()
        }
    }


    // ── focusGridItem — mirrors TvWideLazyRow.focusAndScrollTo ───────────────
    fun focusGridItem(index: Int, scroll: Boolean = true) {
        if (index !in filteredEntries.indices) return
        focusedGridIndex = index
        // getOrNull even though the line above bounds-checks: that check is against
        // filteredEntries, which is a DIFFERENT list from this pool. Validating one
        // and indexing the other is precisely how the 200-item crash happened.
        gridFocusRequesters.getOrNull(index)?.requestFocus()
        if (scroll) {
            coroutineScope.launch {
                val layoutInfo = gridState.layoutInfo
                val viewportTop = layoutInfo.viewportStartOffset
                val viewportBottom = layoutInfo.viewportEndOffset
                val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

                if (targetItem != null) {
                    // Item is composed but might be partially clipped
                    val scrollMargin = 60  // px — breathing room so cards don't hug the edge
                    val itemTop = targetItem.offset.y
                    val itemBottom = itemTop + targetItem.size.height

                    when {
                        // Card is clipped at the bottom — nudge down just enough
                        itemBottom + scrollMargin > viewportBottom -> {
                            val scrollBy = (itemBottom + scrollMargin - viewportBottom).toFloat()
                            gridState.animateScrollBy(scrollBy)
                        }
                        // Card is clipped at the top — nudge up just enough
                        itemTop - scrollMargin < viewportTop -> {
                            val scrollBy = (itemTop - scrollMargin - viewportTop).toFloat()
                            gridState.animateScrollBy(scrollBy)
                        }
                        // Fully visible — don't scroll at all
                    }
                } else {
                    // Item isn't composed yet (far away) — fall back to item-based scroll
                    // but target one row above so it doesn't slam to the top edge
                    val rowAbove = (index - CARDS_PER_ROW).coerceAtLeast(0)
                    gridState.animateScrollToItem(rowAbove)
                }
            }
        }
    }

    // ── Navigate to details — press-and-prefetch pattern ─────────────────────
    fun navigateToDetails(entry: FavoriteEntry) {
        if (isNavigating) return
        isNavigating = true
        coroutineScope.launch {
            try {
                val intent = prefetchDetailsLaunch(context, entry, profileId)

                isNavigating = false
                context.startActivity(intent)

            } catch (e: Exception) {
                android.util.Log.e("FavoritesScreen", "Navigation failed: ${e.message}")
                isNavigating = false
            }
        }
    }

    // ── Auto-focus confirm overlay when it appears ────────────────────────────
    val showExitRemoveOverlay by remember(
        showDeleteConfirm,
        removeMode,
        deletedEntries.size,
        favEntries.size
    ) {
        derivedStateOf {
            showDeleteConfirm || (removeMode && deletedEntries.isNotEmpty() && favEntries.isEmpty())
        }
    }

    LaunchedEffect(showExitRemoveOverlay) {
        if (showExitRemoveOverlay) {
            kotlinx.coroutines.android.awaitFrame()
            acceptChangesFocus.requestFocus()
        }
    }

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
                    if (removeMode) Color.Red.copy(alpha = 0.15f)
                    else Color.Black.copy(alpha = 0.45f)
                )
        )

        // ── Empty state ───────────────────────────────────────────────────────
        if (!isLoading && favEntries.isEmpty() && !showExitRemoveOverlay) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                        .padding(horizontal = 40.dp, vertical = 32.dp)
                        .height(130.dp)
                        .width(480.dp),
                ) {
                    Text(
                        text       = "No favorite content added yet",
                        color      = Color.White,
                        fontFamily = AppTypography.interBold,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    Text(
                        text       = "Open any movie or TV show and press the favorite button to see it here.",
                        color      = Color(0xFFAAAAAA),
                        fontFamily = AppTypography.interSemiBold,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 13.sp,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .offset(y = 8.dp),
                    )
                    Text(
                        text       = "To leave just press the back button",
                        color      = Color(0xFFA01D1D),
                        fontFamily = AppTypography.interBold,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 15.sp,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .offset(y = 36.dp, x = 2.dp),
                    )
                }
            }

        } else if (!isLoading) {

            // ── Header — switches to remove mode instruction when active ──────
            Text(
                text       = if (removeMode) "Click on any media you want to remove" else HEADER_TEXT,
                fontSize   = HEADER_FONT_SIZE.sp,
                fontFamily = AppTypography.lalezarRegular,
                color      = Color.White.copy(alpha = HEADER_OPACITY),
                modifier   = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = headerOffsetX, y = headerOffsetY),
            )

            // ── Dropdowns ─────────────────────────────────────────────────────
            HistoryDropdown(
                label                    = "Filter",
                options                  = genreOptions,
                selectedOption           = selectedGenre,
                onOptionSelected         = { selectedGenre = it },
                buttonWidth              = 160.dp,
                dropdownMaxHeight        = 237.dp,
                externalFocusRequester   = filterDropdownFocus,
                suppressInitialFocusSound = true,
                onExpandedChanged        = { isAnyDropdownExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX, y = dropdownOffsetY)
                    .zIndex(1f),
            )

            HistoryDropdown(
                label                  = "Sort",
                options                = sortOptions,
                selectedOption         = selectedSort,
                onOptionSelected       = { selectedSort = it },
                buttonWidth            = 140.dp,
                externalFocusRequester = sortDropdownFocus,
                onExpandedChanged      = { isAnyDropdownExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX + dropdownSpacing, y = dropdownOffsetY)
                    .zIndex(1f),
            )

            // ── Remove Media button ───────────────────────────────────────────
            ModularButton(
                textConfig      = removeMediaText,
                isToggleable    = true,
                externalToggled = removeMode,
                isFocusable     = true,
                fontFamily      = AppTypography.interBold,
                focusRequester  = deleteButtonFocus,
                onClick = {
                    removeMode = !removeMode
                    if (removeMode) {
                        // Entering remove mode — jump focus to first card
                        coroutineScope.launch {
                            focusedGridIndex = 0
                            gridState.scrollToItem(0)
                            kotlinx.coroutines.android.awaitFrame()
                            gridFocusRequesters.getOrNull(0)?.requestFocus()
                        }
                    } else {
                        // Manually toggling off — treat same as Accept, commit to backend
                        coroutineScope.launch {
                            deletedEntries.forEach { entry ->
                                try {
                                    ApiClient.bluehiveApi.toggleFavorite(
                                        com.example.bluehive.api.FavoriteRequest(
                                            profile_id    = profileId,
                                            media_tmdb_id = entry.tmdbId,
                                            media_type    = entry.mediaType,
                                            media_title   = entry.title,
                                        )
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("FavoritesScreen", "Delete failed: ${e.message}")
                                }
                            }
                            deletedEntries.clear()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX + dropdownSpacing + 150.dp, y = filterButtonOffsetY)
                    .zIndex(1f)
                    .onFocusChanged {
                        if (it.isFocused && hoverArmed) BlueHiveApplication.playHoverSound()
                    },
                dimensions = removeMediaDimensions,
                colors = removeMediaColors,
                glowConfig = buttonGlow,
                animationConfig = buttonAnimation,
            )

            // ── All Media button ──────────────────────────────────────────────
            ModularButton(
                textConfig      = allMediaText,
                isToggleable    = true,
                externalToggled = selectedFilter == "all",
                isFocusable     = selectedFilter != "all",
                fontFamily      = AppTypography.interBold,
                focusRequester  = allMediaFocus,
                onClick = {
                    selectedFilter = "all"
                    suppressNextHover = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        moviesFocus.requestFocus()
                    }, 50)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = filterButtonOffsetX, y = filterButtonOffsetY)
                    .onFocusChanged {
                        if (it.isFocused) {
                            if (!suppressNextHover) BlueHiveApplication.playHoverSound()
                            else suppressNextHover = false
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            BlueHiveApplication.playHoverSound()
                        }
                        false
                    },
                dimensions      = filterTabDimensions,
                colors          = filterTabColors,
                glowConfig      = buttonGlow,
                animationConfig = buttonAnimation,
            )

            // ── Movies button ─────────────────────────────────────────────────
            ModularButton(
                textConfig      = moviesText,
                isToggleable    = true,
                externalToggled = selectedFilter == "movies",
                isFocusable     = selectedFilter != "movies",
                fontFamily      = AppTypography.interBold,
                focusRequester  = moviesFocus,
                onClick = {
                    selectedFilter = "movies"
                    suppressNextHover = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        tvSeriesFocus.requestFocus()
                    }, 50)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = filterButtonOffsetX + filterButtonSpacing, y = filterButtonOffsetY)
                    .onFocusChanged {
                        if (it.isFocused) {
                            if (!suppressNextHover) BlueHiveApplication.playHoverSound()
                            else suppressNextHover = false
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            BlueHiveApplication.playHoverSound()
                        }
                        false
                    },
                dimensions      = filterTabDimensions,
                colors          = filterTabColors,
                glowConfig      = buttonGlow,
                animationConfig = buttonAnimation,
            )

            // ── TV Shows button ───────────────────────────────────────────────
            ModularButton(
                textConfig      = tvShowsText,
                isToggleable    = true,
                externalToggled = selectedFilter == "tv",
                isFocusable     = selectedFilter != "tv",
                fontFamily      = AppTypography.interBold,
                focusRequester  = tvSeriesFocus,
                onClick = {
                    selectedFilter = "tv"
                    suppressNextHover = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        moviesFocus.requestFocus()
                    }, 50)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = filterButtonOffsetX + (filterButtonSpacing * 2),
                        y = filterButtonOffsetY
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            if (!suppressNextHover) BlueHiveApplication.playHoverSound()
                            else suppressNextHover = false
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            BlueHiveApplication.playHoverSound()
                        }
                        false
                    },
                dimensions      = filterTabDimensions,
                colors          = filterTabColors,
                glowConfig      = buttonGlow,
                animationConfig = buttonAnimation,
            )

            // ── Media card grid ───────────────────────────────────────────────
            // VanishContainer provides the OpenGL surface layer that the
            // particle disintegration effect renders onto when a card is removed.
                VanishContainer(
                    modifier = Modifier.fillMaxSize(),
                    onGLSurfaceViewCreated = {
                        glSurfaceView = it
                        it.setAnimationConfig(
                            duration = 2500f,
                            particleSize = 2f
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = gridOffsetY)
                            .width(860.dp)
                            .height(gridHeight)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                                    if (removeMode) {
                                        showDeleteConfirm = true
                                    } else {
                                        filterDropdownFocus.requestFocus()
                                    }
                                    true
                                } else false
                            },
                    ) {
                        LazyVerticalGrid(
                            columns           = GridCells.Fixed(CARDS_PER_ROW),
                            state             = gridState,
                            userScrollEnabled = false,
                            modifier          = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (isAnyDropdownExpanded) 0.08f else 1f }
                                .focusGroup()
                                .onFocusChanged { fs -> hasGridFocus = fs.hasFocus },
                            contentPadding    = PaddingValues(bottom = 50.dp),
                            horizontalArrangement = Arrangement.spacedBy(cardHorizontalSpacing),
                            verticalArrangement   = Arrangement.spacedBy(rowSpacing),
                        ) {

                        itemsIndexed(
                            items = filteredEntries,
                            key   = { _, entry -> entry.favoriteId },
                        ) { index, entry ->

                            // ── Single source of truth for focus ──────────────────
                            // Mirrors TvWideLazyRow (267-268): derivedStateOf gates invalidation on
                            // the Boolean RESULT, so only the two tiles whose focus flips recompose
                            // per D-pad move — not every visible tile (the raw read was the grid lag).
                            val isItemFocused by remember(index) {
                                derivedStateOf { hasGridFocus && focusedGridIndex == index }
                            }

                            // Each card gets its own VanishController so only the tapped
                            // card plays the disintegration effect, not the whole grid.
                            val vanishController = rememberVanishController(glSurfaceView)

                            // MutableState, not a delegated var: runTitleCardPress flips
                            // these from a coroutine after this composition.
                            val isPressed = remember { mutableStateOf(false) }
                            val pressBusy = remember { mutableStateOf(false) }
                            val cardScale by animateFloatAsState(
                                targetValue   = if (isPressed.value) TitleCardPress.SCALE else 1f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    if (isPressed.value) TitleCardPress.SHRINK_MS else TitleCardPress.REGROW_MS
                                ),
                                label         = "cardScale",
                            )

                            val mediaItem = remember(entry) {
                                MediaItem(
                                    tmdbId           = entry.tmdbId,
                                    mediaId          = entry.tmdbId,
                                    title            = entry.title,
                                    mediaType        = entry.mediaType,
                                    posterUrl        = entry.posterPath,
                                    backdropUrl      = entry.backdropPath,
                                    logoUrl          = entry.logoUrl,
                                    overview         = entry.overview,
                                    releaseDate      = entry.releaseDate,
                                    status           = entry.status,
                                    voteAverage      = entry.voteAverage,
                                    voteCount        = null,
                                    popularity       = null,
                                    popularityRank   = null,
                                    originalLanguage = entry.originalLanguage,
                                    numberOfSeasons  = entry.numberOfSeasons,
                                    numberOfEpisodes = null,
                                    contentRating    = entry.contentRating,
                                    runtime          = null,
                                    budget           = null,
                                    revenue          = null,
                                    trailerUrl       = entry.trailerUrl,
                                    genres           = entry.genresList.ifEmpty { null },
                                    similarItems     = null,
                                    whereToWatch     = null,
                                )
                            }

                            val releaseDateText = remember(entry.releaseDate) {
                                entry.releaseDate
                                    ?.takeIf { it.isNotBlank() && it != "N/A" }
                                    ?.let { raw ->
                                        val parts = raw.split("-")
                                        if (parts.size == 3) "${parts[1]}/${parts[2]}/${parts[0]}" else raw
                                    }
                            }

                            // TV: season count. Movies: runtime. Shared with every
                            // other row via TitleCardFormat.
                            val runTimeText = remember(entry.runtime, entry.numberOfSeasons, entry.mediaType) {
                                com.example.bluehive.utilities.TitleCardFormat
                                    .meta(entry.mediaType, entry.runtime, entry.numberOfSeasons)
                            }



                            // ── Cell structure — mirrors TvWideLazyRow's Column { MediaCard } ──
                            // .vanishable() registers this composable with the VanishController
                            // so the library can capture a bitmap of it when vanish() is called.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(112.dp)
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                                    .then(
                                        if (removeMode) Modifier.vanishable(
                                            vanishController,
                                            backgroundColor = Color.Transparent
                                        )
                                        else Modifier
                                    ),
                            ) {
                                MediaCard(
                                    mediaItem         = mediaItem,
                                    isFocused         = isItemFocused,
                                    backgroundPainter = backgroundPainter,
                                    focusedPainter    = focusedPainter,
                                    shadowPainter     = shadowPainter,
                                    releaseDateText   = releaseDateText,
                                    runTimeText       = runTimeText,
                                    rating            = entry.voteAverage?.takeIf { it > 0.0 },
                                    allowHardwareBitmaps = false,
                                    modifier          = Modifier
                                        .focusable()
                                        // unbounded = titlecard_focused halo (137dp) can overflow
                                        // the 112dp column without clipping — same as TvWideLazyRow
                                        .wrapContentSize(unbounded = true)
                                        // Guarded: the pool is re-created on size
                                        // change, so a recomposition mid-change can
                                        // briefly index past it. A throwaway requester
                                        // is inert — nothing holds a reference to it.
                                        .focusRequester(
                                            gridFocusRequesters.getOrNull(index)
                                                ?: remember { FocusRequester() }
                                        )
                                        .onFocusChanged { fs ->
                                            if (fs.isFocused) {
                                                focusedGridIndex = index
                                                if (hoverArmed) BlueHiveApplication.playHoverSound()
                                            }
                                        }
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp) {
                                                // Release is no longer what ends the dip —
                                                // runTitleCardPress owns the whole sequence, so
                                                // a quick tap animates identically to a long hold.
                                                return@onKeyEvent false
                                            }
                                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                            val now = System.currentTimeMillis()

                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    if (now - lastNavTime < NAVIGATION_COOLDOWN_MS) return@onKeyEvent true
                                                    lastNavTime = now
                                                    if (focusedGridIndex > 0) {
                                                        focusGridItem(focusedGridIndex - 1)  // removed scroll = false
                                                    }
                                                    true
                                                }

                                                Key.DirectionRight -> {
                                                    if (now - lastNavTime < NAVIGATION_COOLDOWN_MS) return@onKeyEvent true
                                                    lastNavTime = now
                                                    if (focusedGridIndex < filteredEntries.lastIndex) {
                                                        focusGridItem(focusedGridIndex + 1)  // removed scroll = false
                                                    }
                                                    true
                                                }


                                                Key.DirectionUp -> {
                                                    if (now - lastNavTime < NAVIGATION_COOLDOWN_MS) return@onKeyEvent true
                                                    lastNavTime = now
                                                    val target = focusedGridIndex - CARDS_PER_ROW
                                                    if (target >= 0) {
                                                        focusGridItem(target)
                                                    } else {
                                                        // First row → send focus back up to control bar
                                                        filterDropdownFocus.requestFocus()
                                                        BlueHiveApplication.playHoverSound()
                                                    }
                                                    true
                                                }

                                                Key.DirectionDown -> {
                                                    if (now - lastNavTime < NAVIGATION_COOLDOWN_MS) return@onKeyEvent true
                                                    lastNavTime = now
                                                    val target = focusedGridIndex + CARDS_PER_ROW
                                                    if (target <= filteredEntries.lastIndex) {
                                                        focusGridItem(target)
                                                        BlueHiveApplication.playTitleCardNavigation()
                                                    } else {
                                                        // Target overshoots — check if there are items on the last row below current index
                                                        val lastRowStart =
                                                            (filteredEntries.lastIndex / CARDS_PER_ROW) * CARDS_PER_ROW
                                                        if (focusedGridIndex < lastRowStart) {
                                                            // We're not yet on the last row — land on the last item
                                                            focusGridItem(filteredEntries.lastIndex)
                                                            BlueHiveApplication.playTitleCardNavigation()
                                                        }
                                                        // Already on the last row — do nothing, no sound
                                                    }
                                                    true
                                                }

                                                Key.DirectionCenter,
                                                Key.Enter,
                                                Key.NumPadEnter -> {
                                                    BlueHiveApplication.playClickSound()
                                                    if (removeMode) {
                                                        vanishController.vanish()
                                                        coroutineScope.launch {
                                                            delay(150)
                                                            // Pre-move focus to the neighboring card BEFORE
                                                            // removing the entry — prevents Compose from
                                                            // auto-searching up to the control bar.
                                                            val removeIdx = index
                                                            val nextIdx = when {
                                                                removeIdx < filteredEntries.lastIndex -> removeIdx + 1
                                                                removeIdx > 0 -> removeIdx - 1
                                                                else -> -1
                                                            }
                                                            if (nextIdx >= 0) {
                                                                gridFocusRequesters.getOrNull(nextIdx)?.requestFocus()
                                                                // After removal, items shift: if we moved forward
                                                                // the next item slides into removeIdx's slot
                                                                focusedGridIndex = if (nextIdx > removeIdx) removeIdx else nextIdx
                                                            }
                                                            kotlinx.coroutines.android.awaitFrame()
                                                            favEntries.remove(entry)
                                                            deletedEntries.add(entry)
                                                        }
                                                    } else {
                                                        // Animate, then open — see TitleCardPress.
                                                        coroutineScope.runTitleCardPress(isPressed, pressBusy) {
                                                            navigateToDetails(entry)
                                                        }
                                                    }
                                                    true
                                                }

                                                else -> false
                                            }
                                        },
                                )
                            }
                        }
                    }
                    }   // closes inner Box
                }       // closes VanishContainer

            if (showFilteredEmptyState) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = gridOffsetY)
                        .width(860.dp)
                        .height(gridHeight)
                ) {

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                                .padding(horizontal = 40.dp, vertical = 32.dp)
                                .height(130.dp)
                                .width(520.dp),
                        ) {
                            Text(
                                text       = "No content found for this filter",
                                color      = Color.White,
                                fontFamily = AppTypography.interBold,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                            )

                            Text(
                                text       = "There are no favorites matching the current genre and media type selection.",
                                color      = Color(0xFFAAAAAA),
                                fontFamily = AppTypography.interSemiBold,
                                fontWeight = FontWeight.Normal,
                                fontSize   = 13.sp,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 8.dp),
                            )

                            Text(
                                text       = "Try changing the filter or switching between Movies and TV Shows",
                                color      = Color(0xFFA01D1D),
                                fontFamily = AppTypography.interBold,
                                fontWeight = FontWeight.Normal,
                                fontSize   = 15.sp,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 36.dp, x = 2.dp),
                            )
                        }
                    }
                }
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
        if (showExitRemoveOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .zIndex(2f)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .onPreviewKeyEvent { event ->
                        // Trap all back presses while overlay is open
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            true
                        } else false
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                        .padding(horizontal = 40.dp, vertical = 28.dp)
                        .width(520.dp),
                ) {
                    Text(
                        text       = "Exit Remove Mode",
                        color      = Color.White,
                        fontFamily = AppTypography.interBold,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    )
                    Text(
                        text       = "${deletedEntries.size} item${if (deletedEntries.size == 1) "" else "s"} removed. Do you wish to keep these changes?",
                        color      = Color(0xFFAAAAAA),
                        fontFamily = AppTypography.interSemiBold,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 13.sp,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )

                    // ── Buttons row ───────────────────────────────────────
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp, Alignment.CenterHorizontally
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Accept Changes — blue 3D button
                        ModularButton(
                            textConfig      = acceptChangesText,
                            isToggleable    = false,
                            isFocusable     = true,
                            fontFamily      = AppTypography.interBold,
                            focusRequester  = acceptChangesFocus,
                            onClick = {
                                BlueHiveApplication.playClickSound()
                                coroutineScope.launch {
                                    deletedEntries.forEach { entry ->
                                        try {
                                            ApiClient.bluehiveApi.toggleFavorite(
                                                com.example.bluehive.api.FavoriteRequest(
                                                    profile_id    = profileId,
                                                    media_tmdb_id = entry.tmdbId,
                                                    media_type    = entry.mediaType,
                                                    media_title   = entry.title,
                                                )
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("FavoritesScreen", "Remove commit failed: ${e.message}")
                                        }
                                    }
                                    deletedEntries.clear()
                                }
                                removeMode = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.android.awaitFrame()
                                    kotlinx.coroutines.android.awaitFrame()
                                    if (filteredEntries.isNotEmpty()) {
                                        val target = focusedGridIndex.coerceIn(0, filteredEntries.lastIndex)
                                        focusedGridIndex = target
                                        gridState.scrollToItem(target)
                                        kotlinx.coroutines.android.awaitFrame()
                                        gridFocusRequesters.getOrNull(target)?.requestFocus()
                                    }
                                    showDeleteConfirm = false
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.isFocused && hoverArmed) BlueHiveApplication.playHoverSound()
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionRight -> {
                                                undoChangesFocus.requestFocus()
                                                true
                                            }
                                            Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                            else -> false
                                        }
                                    } else false
                                },
                            dimensions = overlayButtonDimensions,
                            colors = acceptChangesColors,
                            glowConfig      = buttonGlow,
                            animationConfig = buttonAnimation,
                        )

                        // Undo Changes — red 3D button
                        ModularButton(
                            textConfig      = undoChangesText,
                            isToggleable    = false,
                            isFocusable     = true,
                            fontFamily      = AppTypography.interBold,
                            focusRequester  = undoChangesFocus,
                            onClick = {
                                BlueHiveApplication.playClickSound()
                                favEntries.addAll(deletedEntries)
                                deletedEntries.clear()
                                removeMode = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.android.awaitFrame()
                                    kotlinx.coroutines.android.awaitFrame()
                                    if (filteredEntries.isNotEmpty()) {
                                        val target = focusedGridIndex.coerceIn(0, filteredEntries.lastIndex)
                                        focusedGridIndex = target
                                        gridState.scrollToItem(target)
                                        kotlinx.coroutines.android.awaitFrame()
                                        gridFocusRequesters.getOrNull(target)?.requestFocus()
                                    }
                                    showDeleteConfirm = false
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.isFocused && hoverArmed) BlueHiveApplication.playHoverSound()
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                acceptChangesFocus.requestFocus()
                                                true
                                            }
                                            Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                            else -> false
                                        }
                                    } else false
                                },
                            dimensions = overlayButtonDimensions,
                            colors = undoChangesColors,
                            glowConfig      = buttonGlow,
                            animationConfig = buttonAnimation,
                        )
                    }
                }
            }
        }

    }   // closes main Box fillMaxSize

}   // closes FavoritesScreen


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
