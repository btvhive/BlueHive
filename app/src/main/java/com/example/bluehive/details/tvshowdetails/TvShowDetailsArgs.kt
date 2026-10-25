package com.example.bluehive.details.tvshowdetails

import android.content.Intent
import androidx.compose.runtime.Immutable
import com.example.bluehive.models.MediaItem

/**
 * Everything the TV details page is opened with, read once from its Intent.
 *
 * Moved here from TVShowsDetailsScreenCompose's fields in Stage 9 of the
 * details redesign, with the same keys, defaults and getters, so a title opens
 * exactly as it did. The keys are the ones DetailsIntents and the screens
 * outside details/ send.
 */
@Immutable
class TvShowDetailsArgs(
    val mediaType: String,
    val contentRating: String?,
    val originalLanguage: String?,
    val mediaId: Int,
    val voteAverage: Double,
    val mediaTitle: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val trailerUrl: String?,
    val overview: String?,
    val logoUrl: String?,
    val releaseDate: String?,
    val genres: String?,
    val numberOfSeasons: Int,
    val status: String?,
    val profileId: Int,
    val sourceScreen: String?,
    val searchQuery: String?,
    val startInExtras: Boolean,
    val lastSeason: Int,
    val lastEpisode: Int,
    /**
     * Resume entries (Continue Watching) explicitly attach last_season /
     * last_episode even for S1E1; fresh opens (search/trending/recommendations)
     * never do. Presence — not "episode > 1" — is what should drive episode focus.
     */
    val resumeToEpisode: Boolean,
) {
    /** This page's title as the history stack keeps it, for Back from the title opened next. */
    fun toHistoryItem(): MediaItem = MediaItem(
        tmdbId = mediaId,
        mediaId = mediaId,
        title = mediaTitle,
        mediaType = mediaType,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        logoUrl = logoUrl,
        overview = overview,
        releaseDate = releaseDate,
        status = status,
        voteAverage = voteAverage,
        voteCount = null,
        popularity = null,
        popularityRank = null,
        originalLanguage = originalLanguage,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = null,
        contentRating = contentRating,
        runtime = null,
        budget = null,
        revenue = null,
        trailerUrl = trailerUrl,
        genres = genres?.split(", ")?.map { it.trim() },
        similarItems = null,
        whereToWatch = null
    )

    companion object {
        fun from(intent: Intent): TvShowDetailsArgs {
            return TvShowDetailsArgs(
                mediaType = intent.getStringExtra("media_type") ?: "tv",
                contentRating = intent.getStringExtra("contentRating"),
                originalLanguage = intent.getStringExtra("original_language"),
                mediaId = intent.getIntExtra("media_id", 0),
                voteAverage = intent.getDoubleExtra("vote_average", 0.0),
                mediaTitle = intent.getStringExtra("media_title") ?: "",
                posterUrl = intent.getStringExtra("poster_url"),
                backdropUrl = intent.getStringExtra("backdrop_url"),
                trailerUrl = intent.getStringExtra("youtube_trailer_url"),
                overview = intent.getStringExtra("overview"),
                logoUrl = intent.getStringExtra("logo_url"),
                releaseDate = intent.getStringExtra("release_date"),
                genres = intent.getStringExtra("genres"),
                numberOfSeasons = intent.getIntExtra("number_of_seasons", 0),
                status = intent.getStringExtra("status"),
                profileId = intent.getIntExtra("PROFILE_ID", -1),
                sourceScreen = intent.getStringExtra("SOURCE_SCREEN"),
                searchQuery = intent.getStringExtra("SEARCH_QUERY"),
                startInExtras = intent.getBooleanExtra("start_in_extras", false),
                lastSeason = intent.getIntExtra("last_season", 1),
                lastEpisode = intent.getIntExtra("last_episode", 1),
                resumeToEpisode = intent.hasExtra("last_season") || intent.hasExtra("last_episode"),
            )
        }
    }
}
