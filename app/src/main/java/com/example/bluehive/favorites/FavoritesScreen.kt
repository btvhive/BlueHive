package com.example.bluehive.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
 * details launch in FavoritesLaunch. Since Stage F2 its state is in
 * FavoritesData, FavoritesFilter and FavoritesGridFocus, and its seven effects
 * are child composables, called where they were.
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
    var glSurfaceView by remember { mutableStateOf<VanishGLSurfaceView?>(null) }

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

        } else if (!data.isLoading) {

            // ── Header — switches to remove mode instruction when active ──────
            Text(
                text       = if (data.removeMode) "Click on any media you want to remove" else HEADER_TEXT,
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
                selectedOption           = filter.genre,
                onOptionSelected         = { filter.genre = it },
                buttonWidth              = 160.dp,
                dropdownMaxHeight        = 237.dp,
                externalFocusRequester   = filterDropdownFocus,
                suppressInitialFocusSound = true,
                onExpandedChanged        = { filter.isAnyDropdownExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX, y = dropdownOffsetY)
                    .zIndex(1f),
            )

            HistoryDropdown(
                label                  = "Sort",
                options                = sortOptions,
                selectedOption         = filter.sort,
                onOptionSelected       = { filter.sort = it },
                buttonWidth            = 140.dp,
                externalFocusRequester = sortDropdownFocus,
                onExpandedChanged      = { filter.isAnyDropdownExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX + dropdownSpacing, y = dropdownOffsetY)
                    .zIndex(1f),
            )

            // ── Remove Media button ───────────────────────────────────────────
            ModularButton(
                textConfig      = removeMediaText,
                isToggleable    = true,
                externalToggled = data.removeMode,
                isFocusable     = true,
                fontFamily      = AppTypography.interBold,
                focusRequester  = deleteButtonFocus,
                onClick = {
                    data.removeMode = !data.removeMode
                    if (data.removeMode) {
                        // Entering remove mode — jump focus to first card
                        coroutineScope.launch {
                            focus.focusedGridIndex = 0
                            focus.gridState.scrollToItem(0)
                            kotlinx.coroutines.android.awaitFrame()
                            gridFocusRequesters.getOrNull(0)?.requestFocus()
                        }
                    } else {
                        // Manually toggling off — treat same as Accept, commit to backend
                        coroutineScope.launch {
                            data.commitRemovals(profileId, "Delete failed")
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dropdownOffsetX + dropdownSpacing + 150.dp, y = filterButtonOffsetY)
                    .zIndex(1f)
                    .onFocusChanged {
                        if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
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
                externalToggled = filter.typeTab == "all",
                isFocusable     = filter.typeTab != "all",
                fontFamily      = AppTypography.interBold,
                focusRequester  = allMediaFocus,
                onClick = {
                    filter.typeTab = "all"
                    focus.suppressNextHover = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        moviesFocus.requestFocus()
                    }, 50)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = filterButtonOffsetX, y = filterButtonOffsetY)
                    .onFocusChanged {
                        if (it.isFocused) {
                            if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                            else focus.suppressNextHover = false
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
                externalToggled = filter.typeTab == "movies",
                isFocusable     = filter.typeTab != "movies",
                fontFamily      = AppTypography.interBold,
                focusRequester  = moviesFocus,
                onClick = {
                    filter.typeTab = "movies"
                    focus.suppressNextHover = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        tvSeriesFocus.requestFocus()
                    }, 50)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = filterButtonOffsetX + filterButtonSpacing, y = filterButtonOffsetY)
                    .onFocusChanged {
                        if (it.isFocused) {
                            if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                            else focus.suppressNextHover = false
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
                externalToggled = filter.typeTab == "tv",
                isFocusable     = filter.typeTab != "tv",
                fontFamily      = AppTypography.interBold,
                focusRequester  = tvSeriesFocus,
                onClick = {
                    filter.typeTab = "tv"
                    focus.suppressNextHover = true
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
                            if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                            else focus.suppressNextHover = false
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
                                    if (data.removeMode) {
                                        data.showDeleteConfirm = true
                                    } else {
                                        filterDropdownFocus.requestFocus()
                                    }
                                    true
                                } else false
                            },
                    ) {
                        LazyVerticalGrid(
                            columns           = GridCells.Fixed(CARDS_PER_ROW),
                            state             = focus.gridState,
                            userScrollEnabled = false,
                            modifier          = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (filter.isAnyDropdownExpanded) 0.08f else 1f }
                                .focusGroup()
                                .onFocusChanged { fs -> focus.hasGridFocus = fs.hasFocus },
                            contentPadding    = PaddingValues(bottom = 50.dp),
                            horizontalArrangement = Arrangement.spacedBy(cardHorizontalSpacing),
                            verticalArrangement   = Arrangement.spacedBy(rowSpacing),
                        ) {

                        itemsIndexed(
                            items = filter.filtered,
                            key   = { _, entry -> entry.favoriteId },
                        ) { index, entry ->

                            // ── Single source of truth for focus ──────────────────
                            // Mirrors TvWideLazyRow (267-268): derivedStateOf gates invalidation on
                            // the Boolean RESULT, so only the two tiles whose focus flips recompose
                            // per D-pad move — not every visible tile (the raw read was the grid lag).
                            val isItemFocused by remember(index) {
                                derivedStateOf { focus.hasGridFocus && focus.focusedGridIndex == index }
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
                                        if (data.removeMode) Modifier.vanishable(
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
                                                focus.focusedGridIndex = index
                                                if (focus.hoverArmed) BlueHiveApplication.playHoverSound()
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
                                                Key.DirectionLeft,
                                                Key.DirectionRight,
                                                Key.DirectionUp,
                                                Key.DirectionDown -> {
                                                    if (now - focus.lastNavTime < NAVIGATION_COOLDOWN_MS) return@onKeyEvent true
                                                    focus.lastNavTime = now
                                                    // Where the press goes: gridTarget, pinned by FavoritesGridFocusTest
                                                    val move = when (event.key) {
                                                        Key.DirectionLeft  -> GridKey.Left
                                                        Key.DirectionRight -> GridKey.Right
                                                        Key.DirectionUp    -> GridKey.Up
                                                        else               -> GridKey.Down
                                                    }
                                                    when (val target = gridTarget(focus.focusedGridIndex, move, filter.filtered.size, CARDS_PER_ROW)) {
                                                        is GridTarget.Card -> {
                                                            focus.focusGridItem(target.index)
                                                            // Down also plays the title-card sound
                                                            if (move == GridKey.Down) BlueHiveApplication.playTitleCardNavigation()
                                                        }
                                                        // First row → send focus back up to control bar
                                                        GridTarget.ControlBar -> {
                                                            filterDropdownFocus.requestFocus()
                                                            BlueHiveApplication.playHoverSound()
                                                        }
                                                        // An edge, or Down on the last row: nothing, no sound
                                                        GridTarget.Stay -> Unit
                                                    }
                                                    true
                                                }

                                                Key.DirectionCenter,
                                                Key.Enter,
                                                Key.NumPadEnter -> {
                                                    BlueHiveApplication.playClickSound()
                                                    if (data.removeMode) {
                                                        vanishController.vanish()
                                                        coroutineScope.launch {
                                                            delay(150)
                                                            // Pre-move focus to the neighboring card BEFORE
                                                            // removing the entry — prevents Compose from
                                                            // auto-searching up to the control bar.
                                                            val removeIdx = index
                                                            val nextIdx = when {
                                                                removeIdx < filter.filtered.lastIndex -> removeIdx + 1
                                                                removeIdx > 0 -> removeIdx - 1
                                                                else -> -1
                                                            }
                                                            if (nextIdx >= 0) {
                                                                gridFocusRequesters.getOrNull(nextIdx)?.requestFocus()
                                                                // After removal, items shift: if we moved forward
                                                                // the next item slides into removeIdx's slot
                                                                focus.focusedGridIndex = if (nextIdx > removeIdx) removeIdx else nextIdx
                                                            }
                                                            kotlinx.coroutines.android.awaitFrame()
                                                            data.entries.remove(entry)
                                                            data.removed.add(entry)
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
        if (data.showExitRemoveOverlay) {
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
                        text       = "${data.removed.size} item${if (data.removed.size == 1) "" else "s"} removed. Do you wish to keep these changes?",
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
                                    data.commitRemovals(profileId, "Remove commit failed")
                                }
                                data.removeMode = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.android.awaitFrame()
                                    kotlinx.coroutines.android.awaitFrame()
                                    if (filter.filtered.isNotEmpty()) {
                                        val target = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
                                        focus.focusedGridIndex = target
                                        focus.gridState.scrollToItem(target)
                                        kotlinx.coroutines.android.awaitFrame()
                                        gridFocusRequesters.getOrNull(target)?.requestFocus()
                                    }
                                    data.showDeleteConfirm = false
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
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
                                data.undoRemovals()
                                data.removeMode = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.android.awaitFrame()
                                    kotlinx.coroutines.android.awaitFrame()
                                    if (filter.filtered.isNotEmpty()) {
                                        val target = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
                                        focus.focusedGridIndex = target
                                        focus.gridState.scrollToItem(target)
                                        kotlinx.coroutines.android.awaitFrame()
                                        gridFocusRequesters.getOrNull(target)?.requestFocus()
                                    }
                                    data.showDeleteConfirm = false
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
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
