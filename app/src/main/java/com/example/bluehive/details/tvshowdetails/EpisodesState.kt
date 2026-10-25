package com.example.bluehive.details.tvshowdetails

import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bluehive.api.ApiClient
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.models.Episode
import com.example.bluehive.repository.EpisodesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withContext

/**
 * The TV screen's seasons and episodes: the season on show and its episodes,
 * the episode row's scroll and focus, the tile whose source overlay is open,
 * and the watched marks.
 *
 * Moved here from the screen in Stage 8 of the details redesign: the same
 * variables with the same snapshot state, so every write lands in the same
 * frame as before. The screen's effects that drive them -- the first season
 * load, the first focus, the watched refresh on resume -- are the composables
 * at the end of this file since Stage 10. The screen calls each where its
 * effects were, so they run in the same order (architecture C5).
 */
@Stable
class EpisodesState internal constructor(
    private val scope: CoroutineScope,
    private val repository: EpisodesRepository,
    private val tmdbId: Int,
    /** ✅ Give LazyRow a state so we can scroll before restoring focus. */
    val rowState: LazyListState,
) {
    var selectedSeason by mutableIntStateOf(1)  // default to Season 1
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var isLoadingEpisodes by mutableStateOf(false)

    /** 🔹 Focus requesters for episode tiles (rebuilds safely when episodes change). */
    var focusRequesters: List<FocusRequester> = emptyList()
        internal set

    /** ✅ Remember the last focused episode so RIGHT from seasons always restores correctly. */
    var lastFocusedEpisodeIndex by mutableIntStateOf(0)

    // Hold-to-repeat throttle timestamp for the episode row (see EPISODE_NAV_REPEAT_MS).
    // Plain 1-slot array (NOT observable state) so ticking it never triggers recomposition.
    val navGate = longArrayOf(0L)

    var selectedEpisodeOverlayIndex by mutableIntStateOf(-1)

    /** ✅ Position of the selected episode still in root coordinates. */
    var overlayAnchor by mutableStateOf<Offset?>(null)

    // Set true by the focused episode tile's onFocusChanged (see Edit 3).
    // Lets the initial-focus effect confirm focus actually landed
    // instead of assuming a fire-and-forget requestFocus() succeeded.
    var episodeFocusLanded by mutableStateOf(false)

    // The episode a press recorded: what the player is asked for. Set by the
    // source overlay's buttons; both playback controllers' requests read it.
    var pendingSeason      by mutableIntStateOf(1)
    var pendingEpisode     by mutableIntStateOf(1)
    var pendingEpisodeName by mutableStateOf<String?>(null)

    // ── Watched-episode markers ──────────────────────────────────────────────
    // (season, episode) pairs this profile has started, for THIS show. Drives the
    // corner fold on each episode card. Fetched once per show; failures leave the
    // set empty so the grid simply shows no markers (never blocks the screen).
    var watchedEpisodes by mutableStateOf<Set<Pair<Int, Int>>>(emptySet())

    // ── Season loads are SERIALISED, and stale answers are discarded ─────────
    //
    // This used to fire a bare coroutineScope.launch per call with no job held
    // and no staleness check, while nothing debounced the season buttons or
    // disabled them during a load. Two presses in quick succession therefore left
    // two requests in flight, and the FIRST to return cleared the spinner and
    // painted its episodes — under the newly highlighted season heading.
    //
    // That is worse than a cosmetic mismatch. Opening an episode from that grid
    // calls onEpisodeSelected(selectedSeason, episodeNumber, ...) and sets
    // pendingSeason = selectedSeason, so the source chain and the player extras
    // get the season the user is LOOKING at paired with an episode number from a
    // different season — the wrong episode plays, and that pair is what lands in
    // watch history and drives the watched-corner markers.
    //
    // Guarded by a sequence number rather than by comparing seasonNumber, because
    // that comparison depends on selectedSeason being assigned before the call
    // (it is today, at the button) and would silently rot if a future caller
    // ordered it differently. The sequence answers the only question that
    // matters: is this still the newest request?
    private var seasonLoadSeq by mutableIntStateOf(0)
    private var seasonLoadJob by mutableStateOf<Job?>(null)

    /** 🔹 Load the episodes for a given season -- serialised, see above. */
    fun loadSeasonEpisodes(seasonNumber: Int) {
        seasonLoadJob?.cancel()
        val mySeq = ++seasonLoadSeq
        seasonLoadJob = scope.launch {
            isLoadingEpisodes = true
            try {
                val response = repository.getSeasonEpisodes(
                    tmdbId = tmdbId,
                    seasonNumber = seasonNumber
                )
                if (mySeq != seasonLoadSeq) {
                    Log.d("TVEpisodes", "S$seasonNumber arrived late — discarding")
                    return@launch
                }
                episodes = response.episodes
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Superseded by a newer season press. Not a failure, and it must
                // not reach the catch below or it would blank the grid the newer
                // request is about to fill.
                throw e
            } catch (e: Exception) {
                Log.e("TVEpisodes", "Error loading episodes for S$seasonNumber", e)
                if (mySeq == seasonLoadSeq) episodes = emptyList()
            } finally {
                // Only the newest request owns the spinner. Clearing it from a
                // superseded one is what let the previous season's episodes read
                // as final while the real request was still out.
                if (mySeq == seasonLoadSeq) isLoadingEpisodes = false
            }
        }
    }

    /** Re-read this profile's watched marks for the show; a failure leaves them as they were. */
    suspend fun refreshWatchedEpisodes(profileId: Int) {
        if (profileId == -1) return
        try {
            val marks = withContext(Dispatchers.IO) {
                ApiClient.bluehiveApi.getWatchedEpisodes(profileId, tmdbId)
            }
            watchedEpisodes = marks.map { it.season_number to it.episode_number }.toSet()
        } catch (e: Exception) {
            Log.w("TVShowsDetails", "watched-episodes fetch failed (non-fatal): ${e.message}")
        }
    }
}

/**
 * The screen's EpisodesState. [scope] is the screen's own, so leaving the page
 * cancels a season load in flight, as before.
 */
@Composable
fun rememberEpisodesState(
    scope: CoroutineScope,
    repository: EpisodesRepository,
    tmdbId: Int,
): EpisodesState {
    val rowState = rememberLazyListState()
    val state = remember(scope, repository, tmdbId) { EpisodesState(scope, repository, tmdbId, rowState) }
    // 🔹 Focus requesters for episode tiles (rebuilds safely when episodes change)
    state.focusRequesters = remember(state.episodes) {
        List(state.episodes.size) { FocusRequester() }
    }
    return state
}

/**
 * The first focus when the page opens: the episode a resume asked for, or the
 * start of the row. [onHeaderReleased] lifts the screen's header focus block.
 *
 * Moved here from the screen in Stage 10 of the details redesign; the screen
 * calls it where the effect was (architecture C5).
 */
@Composable
internal fun FirstEpisodeFocus(
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    resumeToEpisode: Boolean,
    lastEpisode: Int,
    onHeaderReleased: () -> Unit,
) {
    var isInitialEpisodeLoad by remember { mutableStateOf(resumeToEpisode) }

    // ✅ Episode row scroll + initial focus. The fallback needs the header
    //    requesters, so this takes the focus map.
    //
    //    Why this is a retry loop instead of delay(150) + requestFocus():
    //    the target tile lives in a LazyRow, so its FocusRequester is only
    //    attached once the tile is composed AND placed. On entry from
    //    Continue Watching the main thread is busy (heavy frames + Coil cache
    //    thrashing), so a fixed delay elapses before layout finishes and
    //    requestFocus() no-ops ("FocusRequester is not initialized"). Because
    //    the header is focus-blocked in this mode, that leaves the whole
    //    screen with nothing focused = stuck. So: scroll, wait until the tile
    //    is actually laid out (presence in visibleItemsInfo => node attached),
    //    request focus, confirm it landed, and only then release the gates.
    LaunchedEffect(episodesState.episodes) {
        if (episodesState.episodes.isEmpty()) return@LaunchedEffect

        if (isInitialEpisodeLoad) {
            val targetIndex = (lastEpisode - 1).coerceIn(0, episodesState.episodes.lastIndex)
            episodesState.lastFocusedEpisodeIndex = targetIndex
            episodesState.rowState.scrollToItem(targetIndex)

            episodesState.episodeFocusLanded = false
            var landed = false
            var attempts = 0
            while (attempts < 40 && !landed) {
                awaitFrame()
                val laidOut = episodesState.rowState.layoutInfo
                    .visibleItemsInfo.any { it.index == targetIndex }
                if (laidOut) {
                    episodesState.focusRequesters.getOrNull(targetIndex)?.requestFocus()
                    awaitFrame()
                    if (episodesState.episodeFocusLanded) landed = true
                }
                attempts++
            }

            // Never strand the user with no focus: if the episode tile truly
            // never took focus, un-block the header and focus Favorites.
            if (!landed) {
                Log.w("TVDetails", "⚠️ Episode focus never landed — falling back to header")
                onHeaderReleased()
                awaitFrame()
                focusMap.favorite.requestFocus()
            }

            onHeaderReleased()
            isInitialEpisodeLoad = false
        } else {
            episodesState.lastFocusedEpisodeIndex = 0
            episodesState.rowState.scrollToItem(0)
        }
    }
}

/**
 * The watched marks: read when the page opens, and re-read on every resume.
 *
 * Moved here from the screen in Stage 10 of the details redesign; the screen
 * calls it where the effects were (architecture C5).
 */
@Composable
internal fun WatchedEpisodesEffects(
    episodesState: EpisodesState,
    tmdbId: Int,
    profileId: Int,
    coroutineScope: CoroutineScope,
) {
    LaunchedEffect(tmdbId, profileId) { episodesState.refreshWatchedEpisodes(profileId) }

    // Re-read on every resume. Nothing is marked optimistically any more — the
    // marker follows the backend row, and that row is only written once the player
    // confirms video is actually playing. Coming back from the player is therefore
    // the moment a genuinely-watched episode should light up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tmdbId, profileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { episodesState.refreshWatchedEpisodes(profileId) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * The season loads the screen starts itself: the first one when the page
 * opens (from the prefetch when there is one), and a move to the newest
 * listed season when the season check leaves the selected one off.
 *
 * Moved here from the screen in Stage 10 of the details redesign; the screen
 * calls it where the effects were (architecture C5).
 */
@Composable
internal fun SeasonLoadEffects(
    episodesState: EpisodesState,
    tmdbId: Int,
    numberOfSeasons: Int,
    lastSeason: Int,
    prefetch: BlueHiveApplication.MediaPrefetchData?,
    listedSeasons: Int?,
) {
    // 🔹 On first entry, auto-load Season 1 if we have seasons
    LaunchedEffect(tmdbId, numberOfSeasons) {
        if (numberOfSeasons > 0) {
            episodesState.selectedSeason = lastSeason.coerceIn(1, numberOfSeasons)
            val prefetchedEpisodes = prefetch?.episodes
            if (prefetchedEpisodes != null && lastSeason == 1) {
                episodesState.episodes = prefetchedEpisodes
                episodesState.isLoadingEpisodes = false
                Log.d("Prefetch", "✅ Episodes loaded from prefetch cache (${prefetchedEpisodes.size} eps)")
            } else {
                episodesState.loadSeasonEpisodes(episodesState.selectedSeason)
            }
        }
    }

    // The season check can leave off the selected season -- the screen was
    // opened on a season with nothing aired. Move to the newest listed one, so
    // the grid never shows a season the column does not.
    LaunchedEffect(listedSeasons) {
        val listed = listedSeasons ?: return@LaunchedEffect
        if (episodesState.selectedSeason > listed) {
            episodesState.selectedSeason = listed
            episodesState.loadSeasonEpisodes(listed)
        }
    }
}
