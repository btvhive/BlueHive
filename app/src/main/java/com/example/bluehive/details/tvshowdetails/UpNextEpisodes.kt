package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.details.shared.playback.UpNextEpisode
import com.example.bluehive.models.Episode

/**
 * The episode after [episode] for the player's Up Next card (v580), from the
 * season on show: none when the grid holds another season or is still loading,
 * at the season's last episode, or when the next one has not aired.
 */
internal fun nextEpisodeIn(
    episodes: List<Episode>,
    shownSeason: Int,
    loading: Boolean,
    season: Int,
    episode: Int,
    todayYmd: Int,
): UpNextEpisode? {
    if (loading || shownSeason != season) return null
    val next = episodes.firstOrNull { it.episodeNumber == episode + 1 } ?: return null
    if (isEpisodeUnaired(next.airDate, todayYmd)) return null
    return UpNextEpisode(next.episodeNumber, next.title, next.stillImageUrl)
}

/** [nextEpisodeIn] for the episode a press recorded. */
internal fun EpisodesState.nextAfterPending(todayYmd: Int): UpNextEpisode? =
    nextEpisodeIn(episodes, selectedSeason, isLoadingEpisodes, pendingSeason, pendingEpisode, todayYmd)

/**
 * Up Next opens (season, episode): record it as a press on it would, and put
 * the row's focus memory on its tile, so leaving the player lands there.
 */
internal fun EpisodesState.recordUpNext(season: Int, episode: Int, title: String?) {
    pendingSeason      = season
    pendingEpisode     = episode
    pendingEpisodeName = title
    if (selectedSeason == season) {
        episodes.indexOfFirst { it.episodeNumber == episode }
            .takeIf { it >= 0 }
            ?.let { lastFocusedEpisodeIndex = it }
    }
}
