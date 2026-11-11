package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.details.shared.playback.UpNextEpisode
import com.example.bluehive.models.Episode

/**
 * The episode after [episode] for the player's Up Next card (v580), from the
 * season on show: none when the grid holds another season or is still loading,
 * or when the next one has not aired. At the season's last episode it is the
 * next season's first, when [nextSeasonFirst] holds it (v678).
 */
internal fun nextEpisodeIn(
    episodes: List<Episode>,
    shownSeason: Int,
    loading: Boolean,
    season: Int,
    episode: Int,
    todayYmd: Int,
    /** (season, its first episode): the season after the one on show, if there is one. */
    nextSeasonFirst: Pair<Int, Episode>? = null,
): UpNextEpisode? {
    if (loading || shownSeason != season) return null
    val next = episodes.firstOrNull { it.episodeNumber == episode + 1 }
    if (next != null) {
        if (isEpisodeUnaired(next.airDate, todayYmd)) return null
        return UpNextEpisode(next.episodeNumber, next.title, next.stillImageUrl, season)
    }
    // The season's end: only past its LAST episode, never over a gap in the list.
    if (episodes.none { it.episodeNumber > episode } && episodes.any { it.episodeNumber == episode }) {
        val (s, first) = nextSeasonFirst ?: return null
        if (s != season + 1 || isEpisodeUnaired(first.airDate, todayYmd)) return null
        return UpNextEpisode(first.episodeNumber, first.title, first.stillImageUrl, s)
    }
    return null
}

/** [nextEpisodeIn] for the episode a press recorded. */
internal fun EpisodesState.nextAfterPending(todayYmd: Int): UpNextEpisode? =
    nextEpisodeIn(episodes, selectedSeason, isLoadingEpisodes, pendingSeason, pendingEpisode, todayYmd, nextSeasonFirst)

/**
 * Up Next opens (season, episode): record it as a press on it would, close
 * the episode's source overlay -- the loading screen comes up over the page,
 * not over the last episode's buttons (v581) -- and put the row's focus
 * memory on its tile, so leaving the player lands there. Into the next
 * season (v678), the grid turns to it as the season list would.
 */
internal fun EpisodesState.recordUpNext(season: Int, episode: Int, title: String?) {
    selectedEpisodeOverlayIndex = -1
    pendingSeason      = season
    pendingEpisode     = episode
    pendingEpisodeName = title
    if (selectedSeason != season && season > 0) {
        selectedSeason = season
        lastFocusedEpisodeIndex = 0
        loadSeasonEpisodes(season)
        return
    }
    episodes.indexOfFirst { it.episodeNumber == episode }
        .takeIf { it >= 0 }
        ?.let { lastFocusedEpisodeIndex = it }
}
