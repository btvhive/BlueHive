package com.example.bluehive.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.MediaCard
import com.example.bluehive.utilities.TitleCardPress
import com.example.bluehive.utilities.runTitleCardPress
import com.exjunk.lib.thanos.vanish.VanishEffect.vanishable
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import com.exjunk.lib.thanos.vanish.rememberVanishController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One Favorites card: its focus, its press dip, its own vanish controller, and
 * its keys -- the D-pad through gridTarget, and OK to open the title or, in
 * remove mode, to remove it.
 *
 * Moved here from FavoritesScreen in Stage F3 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); the code is the code
 * it replaces.
 */
@Composable
internal fun FavoriteCard(
    index: Int,
    entry: FavoriteEntry,
    data: FavoritesData,
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    coroutineScope: CoroutineScope,
    gridFocusRequesters: List<FocusRequester>,
    glSurfaceView: State<VanishGLSurfaceView?>,
    backgroundPainter: Painter,
    focusedPainter: Painter,
    shadowPainter: Painter,
    filterDropdownFocus: FocusRequester,
    navigateToDetails: (FavoriteEntry) -> Unit,
) {
    // ── Single source of truth for focus ──────────────────
    // Mirrors TvWideLazyRow (267-268): derivedStateOf gates invalidation on
    // the Boolean RESULT, so only the two tiles whose focus flips recompose
    // per D-pad move — not every visible tile (the raw read was the grid lag).
    val isItemFocused by remember(index) {
        derivedStateOf { focus.hasGridFocus && focus.focusedGridIndex == index }
    }

    // Each card gets its own VanishController so only the tapped
    // card plays the disintegration effect, not the whole grid.
    val vanishController = rememberVanishController(glSurfaceView.value)

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
                                // A second OK while this card is leaving does nothing.
                                if (!data.startRemoval(entry)) return@onKeyEvent true
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
                                    data.completeRemoval(entry)
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
