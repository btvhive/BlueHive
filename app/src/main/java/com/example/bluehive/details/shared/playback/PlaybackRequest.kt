package com.example.bluehive.details.shared.playback

/**
 * What a details screen plays: a film, or one episode of a show. The launches
 * and extractions that differ between the two read it instead of each screen
 * writing its own copy (docs/details-redesign-architecture.md §3).
 */
sealed interface PlaybackRequest {
    val tmdbId: Int

    /** A film. */
    data class Movie(override val tmdbId: Int) : PlaybackRequest

    /** One episode. [episodeName] only travels to the player, for its title and history. */
    data class Episode(
        override val tmdbId: Int,
        val season: Int,
        val episode: Int,
        val episodeName: String?,
    ) : PlaybackRequest
}

/** The type the backend and SourceCatalog use: "movie" or "tv". */
val PlaybackRequest.apiType: String
    get() = when (this) {
        is PlaybackRequest.Movie -> "movie"
        is PlaybackRequest.Episode -> "tv"
    }

/** SourceCatalog's season: 0 for a film. */
val PlaybackRequest.catalogSeason: Int get() = (this as? PlaybackRequest.Episode)?.season ?: 0

/** SourceCatalog's episode: 0 for a film. */
val PlaybackRequest.catalogEpisode: Int get() = (this as? PlaybackRequest.Episode)?.episode ?: 0

/** The backend's season: none for a film, the same as leaving it out. */
val PlaybackRequest.apiSeason: Int? get() = (this as? PlaybackRequest.Episode)?.season

/** The backend's episode: none for a film, the same as leaving it out. */
val PlaybackRequest.apiEpisode: Int? get() = (this as? PlaybackRequest.Episode)?.episode
