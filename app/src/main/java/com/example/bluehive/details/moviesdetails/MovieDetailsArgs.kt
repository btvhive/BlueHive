package com.example.bluehive.details.moviesdetails

import android.content.Intent
import androidx.compose.runtime.Immutable
import com.example.bluehive.models.MediaItem

/**
 * Everything the movie details page is opened with, read once from its Intent.
 *
 * Moved here from MoviesDetailsScreenCompose's fields in Stage 9 of the
 * details redesign, with the same keys, defaults and getters, so a title opens
 * exactly as it did. The keys are the ones DetailsIntents and the screens
 * outside details/ send.
 */
@Immutable
class MovieDetailsArgs(
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
    val budget: Long,
    val revenue: Long,
    val logoUrl: String?,
    val releaseDate: String?,
    val genres: String?,
    val profileId: Int,
    val sourceScreen: String?,
    val searchQuery: String?,
    /** Opened in the recommended row. Its intent key keeps the old Extras name, start_in_extras, which history entries carry. */
    val startInRow: Boolean,
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
        status = null,
        voteAverage = voteAverage,
        voteCount = null,
        popularity = null,
        popularityRank = null,
        originalLanguage = originalLanguage,
        numberOfSeasons = null,
        numberOfEpisodes = null,
        contentRating = contentRating,
        runtime = null,
        budget = budget,
        revenue = revenue,
        trailerUrl = trailerUrl,
        genres = genres?.split(", ")?.map { it.trim() },
        similarItems = null,
        whereToWatch = null
    )

    companion object {
        fun from(intent: Intent): MovieDetailsArgs {
            return MovieDetailsArgs(
                mediaType = intent.getStringExtra("media_type") ?: "movie",
                contentRating = intent.getStringExtra("contentRating"),
                originalLanguage = intent.getStringExtra("original_language"),
                mediaId = intent.getIntExtra("media_id", 0),
                voteAverage = intent.getDoubleExtra("vote_average", 0.0),
                mediaTitle = intent.getStringExtra("media_title") ?: "",
                posterUrl = intent.getStringExtra("poster_url"),
                backdropUrl = intent.getStringExtra("backdrop_url"),
                trailerUrl = intent.getStringExtra("youtube_trailer_url"),
                overview = intent.getStringExtra("overview"),
                budget = intent.getLongExtra("budget", 0L),
                revenue = intent.getLongExtra("revenue", 0L),
                logoUrl = intent.getStringExtra("logo_url"),
                releaseDate = intent.getStringExtra("release_date"),
                genres = intent.getStringExtra("genres"),
                profileId = intent.getIntExtra("PROFILE_ID", -1),
                sourceScreen = intent.getStringExtra("SOURCE_SCREEN"),
                searchQuery = intent.getStringExtra("SEARCH_QUERY"),
                startInRow = intent.getBooleanExtra("start_in_extras", false),
            )
        }
    }
}
