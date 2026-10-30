package com.example.bluehive.details.tvshowdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Every focus requester on the TV details screen that more than one section
 * uses (docs/details-redesign-architecture.md §6, rule 5): the three header
 * buttons, the season accordion, the source overlay's buttons, and whichever
 * stream button was pressed last. Sections are handed these; none creates a
 * requester another one asks for. The episode tiles' requesters change with
 * the season on show, so they live with the episodes, in EpisodesState.
 *
 * Moved here from the screen in Stage 8 of the details redesign.
 */
@Stable
class TvFocusMap internal constructor(
    providerCount: Int,
) {
    val favorite = FocusRequester()
    val extras = FocusRequester()
    val trailer = FocusRequester()

    /** The season accordion (SeasonAccordion): one focus target for every season. */
    val seasonPicker = FocusRequester()

    /** The picker has focus: the white line rings its panel, and focusSeasonPicker() knows it landed. */
    var seasonPickerFocused by mutableStateOf(false)

    /**
     * ✅ Focus requesters for overlay buttons.
     * Pool sized to the largest provider set we might show — the tv source list
     * (the anime overlay shows fewer). Deriving the size from getStreamingSources()
     * means adding a source there also grows this pool, so the inline grid can
     * never index past it. coerceAtLeast(2) keeps the anime DuB/SuB pair safe.
     */
    val providers: List<FocusRequester> = List(providerCount) { FocusRequester() }

    /** Whichever stream button the viewer pressed last -- restored on the way back from the player (THE RULE). */
    var lastStream by mutableStateOf<FocusRequester?>(null)
}

/**
 * The screen's TvFocusMap. [providerCount] is read once, when the map is
 * made, as the pool it sizes always was.
 */
@Composable
fun rememberTvFocusMap(providerCount: () -> Int): TvFocusMap =
    remember { TvFocusMap(providerCount()) }

/**
 * Up from the recommended row: the episode Down came from (the last one
 * focused, 0 on a season's first draw) while it is on screen, else the first
 * episode on screen; with no episodes, the season accordion, then Play
 * Trailer. Each is tried in turn, so a target that is not attached yet never
 * leaves focus stranded in the row's slid-up page.
 */
internal fun TvFocusMap.focusAboveRow(episodesState: EpisodesState) {
    val visible = episodesState.rowState.layoutInfo.visibleItemsInfo
    val last = episodesState.lastFocusedEpisodeIndex
    val index = if (visible.any { it.index == last }) last else visible.firstOrNull()?.index
    val targets = listOfNotNull(
        index?.let { episodesState.focusRequesters.getOrNull(it) },
        seasonPicker,
        trailer,
    )
    for (target in targets) {
        try {
            target.requestFocus()
            return
        } catch (_: IllegalStateException) {
            // Not attached: try the next.
        }
    }
}
