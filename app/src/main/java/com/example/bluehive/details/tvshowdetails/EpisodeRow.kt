package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.example.bluehive.utilities.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import android.os.SystemClock
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import com.example.bluehive.BlueHiveApplication
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope

// ⬅️➡️ Hold-to-repeat navigation speed for the episode LazyRow: the minimum time (ms)
// between focus moves while LEFT/RIGHT is HELD DOWN. HIGHER = SLOWER. (Continue Watching
// uses CW_NAV_REPEAT_MS = 70L; episode tiles are chunkier, so this starts a touch slower.)
private const val EPISODE_NAV_REPEAT_MS = 140L

/** The row's left edge: 11.25 dp inside the season cap, like the old mini cap's overlap. */
internal val EPISODE_ROW_X = SEASON_CAP_X + SEASON_CAP_WIDTH - 11.25.dp

/** The row's right edge: 17 dp under the right cap, as with the cap at 902.75 and the edge at 919.75. */
internal val EPISODE_ROW_END = RIGHT_CAP_X + 17.dp

/**
 * The episode row: "Loading episodes...", "No episodes found", or the
 * LazyRow of EpisodeCards for the season on show, keyed by tmdbEpisodeId.
 * The tile's D-pad rules live here: Back closes its source overlay or goes to
 * its season, OK opens the overlay (or arms an unaired episode first), Down
 * enters the recommended row, and Left / Right move with the hold-to-repeat pace.
 *
 * Moved here from the screen in Stage 8 of the details redesign; drawn into
 * the play area's Box at the same offsets as before.
 */
@Composable
internal fun BoxScope.EpisodeRow(
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    unairedGate: UnairedGate,
    listedSeasons: Int?,
    numberOfSeasons: Int,
    todayYmd: Int,
    backdropUrl: String?,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
    coroutineScope: CoroutineScope,
    /** Down on a tile enters the recommended row under the episodes through this. */
    rowEntry: RecommendedRowEntry,
) {
    // ================================================================> episode lazy row
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            // Starts 11.25 dp inside the season cap's right edge (as it did with
            // the mini cap) and ends 17 dp under the right cap (EPISODE_ROW_END).
            .offset(x = EPISODE_ROW_X, y = 299.5.dp)
            .width(EPISODE_ROW_END - EPISODE_ROW_X)
            .height(260.dp) //122.25
        //.background(Color.Red)
    ) {
        when {
            episodesState.isLoadingEpisodes -> {
                // Simple loading state (optional)
                Text(
                    text = "Loading episodes...",
                    color = Color(0xFFAAAAAA),
                    fontFamily = dongleRegular,
                    fontSize = 23.sp,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = 45.dp)
                )
            }

            episodesState.episodes.isEmpty() -> {
                // Optional empty state
                Text(
                    text = "No episodes found for Season ${episodesState.selectedSeason}",
                    color = Color(0xFFAAAAAA),
                    fontFamily = dongleRegular,
                    fontSize = 23.sp,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = 45.dp)
                )
            }

            else -> {
                val totalEpisodes = episodesState.episodes.size

                LazyRow(
                    state = episodesState.rowState,
                    userScrollEnabled = episodesState.selectedEpisodeOverlayIndex == -1,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(
                        start = 18.dp,   // same as before
                        end = 40.dp,     // ⬅ bump this up until the last card clears the decor nicely
                        top = 12.dp,
                        bottom = 12.dp
                    )
                ) {

                    itemsIndexed(
                        items = episodesState.episodes,
                        key = { _, ep -> ep.tmdbEpisodeId }
                    ) { index, episode ->

                        // Watched marker for THIS episode. watchedEpisodes changes only on
                        // load and when the user starts a stream, so reading it per item
                        // costs nothing during D-pad navigation.
                        val isWatched = (episodesState.selectedSeason to episode.episodeNumber) in episodesState.watchedEpisodes

                        // Still FOCUSABLE when unaired -- people want to read what is
                        // coming -- but not pressable. See the scrim in EpisodeCard and
                        // the Enter branch below.
                        val isUnaired = remember(episode.airDate, todayYmd) {
                            isEpisodeUnaired(episode.airDate, todayYmd)
                        }

                        EpisodeCard(
                            episode                = episode,
                            index                  = index,
                            totalEpisodes          = totalEpisodes,
                            isWatched              = isWatched,
                            isUnaired              = isUnaired,
                            isEpisodeSelectOverlay = episodesState.selectedEpisodeOverlayIndex == index,
                            backdropUrl            = backdropUrl,
                            rowState               = episodesState.rowState,
                            focusRequester         = episodesState.focusRequesters[index],
                            passionFont            = passionFont,
                            dongleRegular          = dongleRegular,
                            onFocused = {
                                episodesState.lastFocusedEpisodeIndex = index
                                episodesState.episodeFocusLanded = true  // confirms initial focus actually landed
                                // Arming belongs to ONE episode. Landing on a
                                // different one drops it, so a press here can
                                // never be the second half of a pair begun on a
                                // neighbour three cards away.
                                if (unairedGate.armedIndex != index) unairedGate.armedIndex = -1
                            },
                            onKeyEvent = { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.Back -> {
                                            when {
                                                episodesState.selectedEpisodeOverlayIndex == index -> {
                                                    episodesState.selectedEpisodeOverlayIndex = -1
                                                    true
                                                }
                                                (listedSeasons ?: numberOfSeasons) > 1 -> {
                                                    BlueHiveApplication.playHoverSound()
                                                    // Don't assume the accordion is attached: on the
                                                    // Continue Watching path the tile is focused under
                                                    // load, before the season check lets it compose --
                                                    // see focusSeasonPicker.
                                                    coroutineScope.launch {
                                                        focusMap.focusSeasonPicker()
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        }


                                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                            if (isUnaired && unairedGate.armedIndex != index) {
                                                // REFUSED, NOT IGNORED. Silence here is
                                                // indistinguishable from a dropped press,
                                                // and the answer to that is to press again
                                                // harder. The sound says the box heard it
                                                // -- and the notice says what a second
                                                // press will do.
                                                BlueHiveApplication.playErrorSound()
                                                unairedGate.armedIndex = index
                                            } else {
                                                BlueHiveApplication.playClickSound()
                                                unairedGate.armedIndex = -1
                                                episodesState.selectedEpisodeOverlayIndex = index
                                            }
                                            true
                                        }


                                        Key.DirectionDown -> {
                                            if (episodesState.selectedEpisodeOverlayIndex == index) {
                                                // move focus into the provider grid
                                                focusMap.providers.firstOrNull()?.requestFocus()
                                                true
                                            } else {
                                                // The recommended row peeks under the episodes
                                                // (DetailsPeekState). Consumed either way: before
                                                // its titles arrive the press is silent rather than
                                                // sending focus somewhere arbitrary.
                                                if (rowEntry.focus()) BlueHiveApplication.playHoverSound()
                                                true
                                            }
                                        }
                                        Key.DirectionLeft, Key.DirectionRight -> {
                                            if (episodesState.selectedEpisodeOverlayIndex == index) {
                                                true
                                            } else if (event.key == Key.DirectionRight && index == episodesState.episodes.lastIndex) {
                                                // Dead-end: RIGHT on the last episode has nowhere to go —
                                                // consume it silently (no scroll sound, no no-op traversal).
                                                true
                                            } else {
                                                // Hold-to-repeat throttle (see EPISODE_NAV_REPEAT_MS): swallow
                                                // repeats that land sooner than the interval so holding L/R
                                                // scrolls at a controlled pace, like the Continue Watching list.
                                                val now = SystemClock.uptimeMillis()
                                                if (now - episodesState.navGate[0] < EPISODE_NAV_REPEAT_MS) {
                                                    true   // too soon — consume, no move (and no sound)
                                                } else {
                                                    episodesState.navGate[0] = now
                                                    BlueHiveApplication.playTitleCardNavigation()
                                                    false  // allow the move
                                                }
                                            }
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            },
                        )
                    }

                }
            }
        }
    }
}
