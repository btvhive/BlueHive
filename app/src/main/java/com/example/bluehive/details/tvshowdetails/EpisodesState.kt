package com.example.bluehive.details.tvshowdetails

import android.util.Log
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
import com.example.bluehive.api.ApiClient
import com.example.bluehive.models.Episode
import com.example.bluehive.repository.EpisodesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TV screen's seasons and episodes: the season on show and its episodes,
 * the episode row's scroll and focus, the tile whose source overlay is open,
 * and the watched marks.
 *
 * Moved here from the screen in Stage 8 of the details redesign: the same
 * variables with the same snapshot state, so every write lands in the same
 * frame as before. The screen's effects that drive them -- the first season
 * load, the first focus, the watched refresh on resume -- stay where they
 * were in the screen, in the same order (architecture C5).
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
