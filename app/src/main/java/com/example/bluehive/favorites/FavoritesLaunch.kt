package com.example.bluehive.favorites

import android.content.Context
import android.content.Intent
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.SourceScreen
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.MediaDetailResponse
import com.example.bluehive.details.moviesdetails.MoviesDetailsScreenCompose
import com.example.bluehive.details.shared.navigation.DetailsExtra
import com.example.bluehive.details.shared.navigation.putDetailsExtras
import com.example.bluehive.details.tvshowdetails.TVShowsDetailsScreenCompose
import com.example.bluehive.repository.EpisodesRepository
import com.example.bluehive.repository.RecommendationsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * Opening a title from Favorites.
 *
 * The extras are Favorites' own, not DetailsIntents': "" and "N/A" defaults,
 * the genres the favourites fetch already parsed, and SOURCE_SCREEN =
 * FAVORITES (docs/favorites-home-redesign-architecture.md §9, #5). They are
 * built as plain lists that FavoritesLaunchTest compares key by key, in order,
 * with the record taken before the redesign (FavoritesLaunchExtrasGolden).
 *
 * Moved here from FavoritesScreen in Stage F1 of the Favorites and Home redesign.
 */
internal object FavoritesLaunch {

    /** A movie opens the movie screen; any other type, the TV screen. */
    fun isMovie(entry: FavoriteEntry): Boolean = entry.mediaType == "movie"

    /** The launch's extras. [details] is the getMediaDetails answer fetched on the press. */
    fun extras(entry: FavoriteEntry, details: MediaDetailResponse, profileId: Int): List<DetailsExtra> {
        val logoUrl      = (details.logos as? String)?.takeIf { it.isNotBlank() } ?: ""
        val genresString = entry.genresString

        return if (isMovie(entry)) buildList {
            add(DetailsExtra.Text("media_type", details.media_type))
            add(DetailsExtra.Whole("media_id", details.tmdb_id))
            add(DetailsExtra.Text("media_title", details.title ?: ""))
            add(DetailsExtra.Text("poster_url", details.poster_path ?: ""))
            add(DetailsExtra.Text("backdrop_url", details.backdrop_path ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", details.youtube_trailer_url ?: ""))
            add(DetailsExtra.Text("overview", details.overview ?: ""))
            add(DetailsExtra.Real("vote_average", details.vote_average ?: 0.0))
            add(DetailsExtra.Text("contentRating", details.content_rating ?: "N/A"))
            add(DetailsExtra.Text("original_language", details.original_language ?: "N/A"))
            add(DetailsExtra.Text("release_date", details.release_date ?: "N/A"))
            add(DetailsExtra.Text("logo_url", logoUrl))
            add(DetailsExtra.Text("genres", genresString))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            add(DetailsExtra.Text("SOURCE_SCREEN", SourceScreen.FAVORITES))
        } else buildList {
            add(DetailsExtra.Text("media_type", details.media_type))
            add(DetailsExtra.Whole("media_id", details.tmdb_id))
            add(DetailsExtra.Text("media_title", details.title ?: ""))
            add(DetailsExtra.Text("poster_url", details.poster_path ?: ""))
            add(DetailsExtra.Text("backdrop_url", details.backdrop_path ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", details.youtube_trailer_url ?: ""))
            add(DetailsExtra.Text("overview", details.overview ?: ""))
            add(DetailsExtra.Real("vote_average", details.vote_average ?: 0.0))
            add(DetailsExtra.Text("contentRating", details.content_rating ?: "N/A"))
            add(DetailsExtra.Text("original_language", details.original_language ?: "N/A"))
            add(DetailsExtra.Text("release_date", details.release_date ?: "N/A"))
            add(DetailsExtra.Text("logo_url", logoUrl))
            add(DetailsExtra.Text("genres", genresString))
            add(DetailsExtra.Whole("number_of_seasons", details.number_of_seasons ?: 0))
            add(DetailsExtra.Text("status", details.status ?: "N/A"))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            add(DetailsExtra.Text("SOURCE_SCREEN", SourceScreen.FAVORITES))
        }
    }

    fun intent(context: Context, entry: FavoriteEntry, details: MediaDetailResponse, profileId: Int): Intent =
        Intent(context, if (isMovie(entry)) MoviesDetailsScreenCompose::class.java else TVShowsDetailsScreenCompose::class.java)
            .putDetailsExtras(extras(entry, details, profileId))
}

/**
 * The press-and-prefetch: starts the details, first-season and recommendations
 * requests, waits out the press, hands the episodes and recommendations to the
 * details screen through the app's prefetch slot, and returns the launch.
 *
 * It runs on the pressing coroutine's scope, as it did inline, so the three
 * requests are that coroutine's children exactly as before
 * (docs/favorites-home-redesign-architecture.md §9, #6).
 */
internal suspend fun CoroutineScope.prefetchDetailsLaunch(
    context: Context,
    entry: FavoriteEntry,
    profileId: Int,
): Intent {
    val detailsDeferred = async {
        ApiClient.trailerApi.getMediaDetails(
            tmdbId    = entry.tmdbId,
            mediaType = entry.mediaType,
        )
    }
    val episodesDeferred = if (entry.mediaType == "tv") {
        async {
            try {
                EpisodesRepository().getSeasonEpisodes(
                    tmdbId       = entry.tmdbId,
                    seasonNumber = 1,
                ).episodes
            } catch (_: Exception) { null }
        }
    } else null
    val recsDeferred = async {
        try {
            RecommendationsRepository().getRecommendations(
                entry.tmdbId, entry.mediaType,
            )
        } catch (_: Exception) { emptyList() }
    }

    delay(800)

    val details = detailsDeferred.await()
    val app = context.applicationContext as? BlueHiveApplication
    app?.storePrefetch(
        BlueHiveApplication.MediaPrefetchData(
            tmdbId          = entry.tmdbId,
            mediaType       = entry.mediaType,
            episodes        = episodesDeferred?.await(),
            recommendations = recsDeferred.await(),
        )
    )

    return FavoritesLaunch.intent(context, entry, details, profileId)
}
