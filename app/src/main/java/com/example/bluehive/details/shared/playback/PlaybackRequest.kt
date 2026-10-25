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
