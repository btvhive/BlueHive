package com.example.bluehive.favorites

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The grid's focus: which card has it, the D-pad's repeat clock, the scroll
 * state, the cards' focus requesters, and the two flags that keep the hover
 * sound quiet when focus moves on its own.
 *
 * Moved here from FavoritesScreen in Stage F2 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); each method is the
 * code it replaces.
 */
@Stable
internal class FavoritesGridFocus(
    val gridState: LazyGridState,
    private val filter: FavoritesFilter,
    private val scope: CoroutineScope,
) {
    // ── Grid focus state ──────────────────────────────────────────────────────
    // Mirrors TvWideLazyRow exactly:
    //   hasGridFocus  = whether ANY card in the grid currently has focus
    //   focusedGridIndex = which index is focused (-1 = none yet)
    // isItemFocused = hasGridFocus && focusedGridIndex == index
    // This is the ONLY way MediaCard.isFocused gets set — never individual per-card vars.
    var hasGridFocus     by mutableStateOf(false)
    var focusedGridIndex by mutableIntStateOf(-1)
    var lastNavTime      by mutableLongStateOf(0L)

    // ── Hover-sound arming ───────────────────────────────────────────────────
    // The page auto-focuses its first card once loading finishes (see the
    // "On page enter" effect below). That focus event is indistinguishable from
    // a real one at the card, so opening Favourites always announced itself with
    // a navigation sound for a move the viewer never made.
    //
    // Armed one frame AFTER that initial requestFocus lands, which is what
    // guarantees the event has already been delivered. Re-disarmed whenever the
    // page reloads, since the same auto-focus runs again.
    var hoverArmed        by mutableStateOf(false)

    /** Skips the hover sound once: set by a type-tab press, whose focus hand-off would sound. */
    var suppressNextHover by mutableStateOf(false)

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
    // awaitFrame()s after a size change (see FavoritesGridEffects), so the
    // new requesters are attached before anything requests focus through them.
    private var requesterCount = -1
    private var requesterPool: List<FocusRequester> = emptyList()

    /**
     * The pool for [count] cards, made again whenever the count changes, as the
     * remember keyed to the list size it replaces was. The screen calls it while
     * composing and hands the pool on, so an effect or a click keeps the pool it
     * was made with; focusGridItem uses the latest one.
     */
    fun requesters(count: Int): List<FocusRequester> {
        if (count != requesterCount) {
            requesterPool = List(count) { FocusRequester() }
            requesterCount = count
        }
        return requesterPool
    }

    /**
     * Focus card [index] through the latest pool, without scrolling. For a
     * restore after the list has grown: the pool a click captured belongs to
     * the old size and is attached to nothing (§9 #7).
     */
    fun requestCardFocus(index: Int) {
        requesterPool.getOrNull(index)?.requestFocus()
    }

    // ── focusGridItem — mirrors TvWideLazyRow.focusAndScrollTo ───────────────
    fun focusGridItem(index: Int, scroll: Boolean = true) {
        if (index !in filter.filtered.indices) return
        focusedGridIndex = index
        // getOrNull even though the line above bounds-checks: that check is against
        // filteredEntries, which is a DIFFERENT list from this pool. Validating one
        // and indexing the other is precisely how the 200-item crash happened.
        requesterPool.getOrNull(index)?.requestFocus()
        if (scroll) {
            scope.launch {
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
}

@Composable
internal fun rememberFavoritesGridFocus(filter: FavoritesFilter, scope: CoroutineScope): FavoritesGridFocus {
    // ── Grid scroll state ─────────────────────────────────────────────────────
    val gridState = rememberLazyGridState()
    return remember(gridState, filter, scope) { FavoritesGridFocus(gridState, filter, scope) }
}

/** A D-pad press on a card. */
internal enum class GridKey { Left, Right, Up, Down }

/** Where a D-pad press on a card goes. */
internal sealed interface GridTarget {
    /** Another card: focusGridItem([index]). */
    data class Card(val index: Int) : GridTarget

    /** Up from the first row: the Filter dropdown. */
    data object ControlBar : GridTarget

    /** Nowhere, and no sound. */
    data object Stay : GridTarget
}

/**
 * Where [key] from card [index] goes, in a grid of [count] cards with [perRow]
 * to a row. Left and Right step through the cards in order, across rows. Up
 * from the first row goes to the control bar. Down past the last full row lands
 * on the last card, unless the press is already on the last row. [index] is -1
 * while no card has had focus. Pure, so FavoritesGridFocusTest pins it.
 */
internal fun gridTarget(index: Int, key: GridKey, count: Int, perRow: Int): GridTarget {
    val lastIndex = count - 1
    return when (key) {
        GridKey.Left -> if (index > 0) GridTarget.Card(index - 1) else GridTarget.Stay
        GridKey.Right -> if (index < lastIndex) GridTarget.Card(index + 1) else GridTarget.Stay
        GridKey.Up -> {
            val target = index - perRow
            if (target >= 0) GridTarget.Card(target) else GridTarget.ControlBar
        }
        GridKey.Down -> {
            val target = index + perRow
            if (target <= lastIndex) {
                GridTarget.Card(target)
            } else {
                // Target overshoots — check if there are items on the last row below current index
                val lastRowStart = (lastIndex / perRow) * perRow
                if (index < lastRowStart) GridTarget.Card(lastIndex) else GridTarget.Stay
            }
        }
    }
}

/**
 * Effects 3 to 6 of the screen's seven (F-C5), in their order: the filter
 * reset, the auto-land, the page-enter focus and the restore after a removal.
 * [gridFocusRequesters] is the pool of the composition that runs them, so an
 * effect keeps the pool it started with, as it did inline.
 */
@Composable
internal fun FavoritesGridEffects(
    data: FavoritesData,
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    gridFocusRequesters: List<FocusRequester>,
) {
    // ── Reset focus + scroll when filter/sort changes ─────────────────────────
    LaunchedEffect(filter.typeTab, filter.genre, filter.sort) {
        focus.focusedGridIndex = -1
        focus.gridState.scrollToItem(0)
    }

    // ── Auto-land on first item when grid receives focus with nothing selected ─
    // Mirrors TvWideLazyRow: LaunchedEffect(items.size, hasRowFocus)
    LaunchedEffect(filter.filtered.size, focus.hasGridFocus) {
        if (focus.hasGridFocus && filter.filtered.isNotEmpty() && focus.focusedGridIndex == -1) {
            focus.focusedGridIndex = 0
            gridFocusRequesters.getOrNull(0)?.requestFocus()
        }
    }

    // ── On page enter: auto-focus first card as soon as data is ready ─────────
    // Fires once when isLoading flips to false and there is at least one item.
    // awaitFrame gives the grid one frame to compose its items before we
    // request focus — without it the FocusRequester may not be attached yet.
    LaunchedEffect(data.isLoading) {
        // Disarmed for the whole of this effect: a reload re-runs the auto-focus
        // below, and that landing must stay as silent as the first one.
        focus.hoverArmed = false
        if (!data.isLoading && filter.filtered.isNotEmpty()) {
            kotlinx.coroutines.android.awaitFrame()
            val targetIndex = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
            focus.focusedGridIndex = targetIndex
            focus.gridState.scrollToItem(targetIndex)
            kotlinx.coroutines.android.awaitFrame()
            gridFocusRequesters.getOrNull(targetIndex)?.requestFocus()
            // One more frame, so the focus event above has been delivered and
            // cannot be mistaken for the viewer navigating.
            kotlinx.coroutines.android.awaitFrame()
        }
        focus.hoverArmed = true
    }


    // ── Restore focus to next card after removal in Remove Mode ───────────────
    LaunchedEffect(filter.filtered.size) {
        if (data.removeMode && filter.filtered.isNotEmpty()) {
            kotlinx.coroutines.android.awaitFrame()
            kotlinx.coroutines.android.awaitFrame()
            val nextIndex = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
            focus.focusedGridIndex = nextIndex
            gridFocusRequesters.getOrNull(nextIndex)?.requestFocus()
        }
    }
}
