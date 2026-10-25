package com.example.bluehive.details.shared.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.bluehive.models.MediaItem

/**
 * One Intent extra of a details launch, typed the way that launch has always
 * put it, so it goes through the same putExtra overload: media_id stays an
 * Int, vote_average a Double, budget and revenue Longs, start_in_extras a
 * Boolean.
 */
sealed interface DetailsExtra {
    val key: String

    data class Text(override val key: String, val value: String?) : DetailsExtra
    data class Whole(override val key: String, val value: Int) : DetailsExtra
    data class Real(override val key: String, val value: Double) : DetailsExtra
    data class Amount(override val key: String, val value: Long) : DetailsExtra
    data class Flag(override val key: String, val value: Boolean) : DetailsExtra
}

/** Puts [extras] on this Intent, each with the overload of its type. */
fun Intent.putDetailsExtras(extras: List<DetailsExtra>): Intent = apply {
    for (extra in extras) {
        when (extra) {
            is DetailsExtra.Text -> putExtra(extra.key, extra.value)
            is DetailsExtra.Whole -> putExtra(extra.key, extra.value)
            is DetailsExtra.Real -> putExtra(extra.key, extra.value)
            is DetailsExtra.Amount -> putExtra(extra.key, extra.value)
            is DetailsExtra.Flag -> putExtra(extra.key, extra.value)
        }
    }
}

/** Which details screen a launch is for: their extras differ, and are kept as they are (architecture §10). */
enum class DetailsScreen { Tv, Movie }

/**
 * Every "open details" launch the two details screens make between themselves:
 * a recommendation picked, and Back to the title before on the history stack.
 *
 * The two screens' launches already differed, and still do: the movie screen
 * sends budget and revenue and falls back to "movie" and "" for a missing type
 * or title (which Gson can leave null at runtime, whatever the model says);
 * the TV screen sends its season count and status, and Back on the TV screen
 * also sends the budget and revenue it never reads. The extras are built as
 * plain lists that the JVM tests compare key by key, in order, against the
 * record taken before the redesign (DetailsLaunchExtrasGolden); each Intent
 * function only wraps its list.
 *
 * Moved here from the two Activities in Stage 9 of the details redesign. This
 * file may not name the screens (checkDetailsBoundaries), so each Activity
 * passes its own class as [target].
 */
object DetailsIntents {

    /** A recommendation picked on [screen]: open [media] there. */
    fun openExtras(
        screen: DetailsScreen,
        media: MediaItem,
        profileId: Int,
        sourceScreen: String?,
        searchQuery: String?,
    ): List<DetailsExtra> = when (screen) {
        DetailsScreen.Tv -> buildList {
            add(DetailsExtra.Text("media_type", media.mediaType))
            add(DetailsExtra.Whole("media_id", media.tmdbId))                     // Int (non-null)
            add(DetailsExtra.Text("media_title", media.title))
            add(DetailsExtra.Text("poster_url", media.posterUrl ?: ""))
            add(DetailsExtra.Text("backdrop_url", media.backdropUrl ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", media.trailerUrl ?: ""))
            add(DetailsExtra.Text("overview", media.overview ?: ""))
            add(DetailsExtra.Real("vote_average", media.voteAverage ?: 0.0))         // Double, NOT Double?
            add(DetailsExtra.Text("contentRating", media.contentRating ?: "N/A"))
            add(DetailsExtra.Text("original_language", media.originalLanguage ?: "N/A"))
            add(DetailsExtra.Text("release_date", media.releaseDate ?: "N/A"))
            add(DetailsExtra.Text("logo_url", media.logoUrl ?: ""))
            add(DetailsExtra.Whole("number_of_seasons", media.numberOfSeasons ?: 0))
            add(DetailsExtra.Text("status", media.status ?: "N/A"))

            val genresString = media.genres?.joinToString(", ")
            add(DetailsExtra.Text("genres", genresString ?: "N/A"))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            sourceScreen?.let { add(DetailsExtra.Text("SOURCE_SCREEN", it)) }
            searchQuery?.let  { add(DetailsExtra.Text("SEARCH_QUERY",  it)) }
        }
        DetailsScreen.Movie -> buildList {
            add(DetailsExtra.Text("media_type", media.mediaType ?: "movie"))
            add(DetailsExtra.Whole("media_id", media.tmdbId))                     // Int (non-null)
            add(DetailsExtra.Text("media_title", media.title ?: ""))
            add(DetailsExtra.Text("poster_url", media.posterUrl ?: ""))
            add(DetailsExtra.Text("backdrop_url", media.backdropUrl ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", media.trailerUrl ?: ""))
            add(DetailsExtra.Text("overview", media.overview ?: ""))
            add(DetailsExtra.Real("vote_average", media.voteAverage ?: 0.0))         // Double, NOT Double?
            add(DetailsExtra.Text("contentRating", media.contentRating ?: "N/A"))
            add(DetailsExtra.Text("original_language", media.originalLanguage ?: "N/A"))
            add(DetailsExtra.Text("release_date", media.releaseDate ?: "N/A"))
            add(DetailsExtra.Amount("budget", media.budget ?: 0L))               // Long, NOT Long?
            add(DetailsExtra.Amount("revenue", media.revenue ?: 0L))              // Long
            add(DetailsExtra.Text("logo_url", media.logoUrl ?: ""))
            add(DetailsExtra.Text("genres", media.genres?.joinToString(", ") ?: "N/A"))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            sourceScreen?.let { add(DetailsExtra.Text("SOURCE_SCREEN", it)) }
            searchQuery?.let  { add(DetailsExtra.Text("SEARCH_QUERY",  it)) }
        }
    }

    /** Back on [screen] with [previous] on the history stack: reopen it, in Extras. */
    fun backExtras(
        screen: DetailsScreen,
        previous: MediaItem,
        profileId: Int,
        sourceScreen: String?,
        searchQuery: String?,
    ): List<DetailsExtra> = when (screen) {
        DetailsScreen.Tv -> buildList {
            add(DetailsExtra.Text("media_type", previous.mediaType))
            add(DetailsExtra.Whole("media_id", previous.tmdbId))
            add(DetailsExtra.Text("media_title", previous.title))
            add(DetailsExtra.Text("poster_url", previous.posterUrl ?: ""))
            add(DetailsExtra.Text("backdrop_url", previous.backdropUrl ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", previous.trailerUrl ?: ""))
            add(DetailsExtra.Text("overview", previous.overview ?: ""))
            add(DetailsExtra.Real("vote_average", previous.voteAverage ?: 0.0))
            add(DetailsExtra.Text("contentRating", previous.contentRating ?: "N/A"))
            add(DetailsExtra.Text("original_language", previous.originalLanguage ?: "N/A"))
            add(DetailsExtra.Text("release_date", previous.releaseDate ?: "N/A"))
            add(DetailsExtra.Amount("budget", previous.budget ?: 0L))
            add(DetailsExtra.Amount("revenue", previous.revenue ?: 0L))
            add(DetailsExtra.Text("logo_url", previous.logoUrl ?: ""))
            add(DetailsExtra.Text("genres", previous.genres?.joinToString(", ") ?: "N/A"))
            add(DetailsExtra.Whole("number_of_seasons", previous.numberOfSeasons ?: 0))
            add(DetailsExtra.Text("status", previous.status ?: "N/A"))
            add(DetailsExtra.Flag("start_in_extras", true))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            sourceScreen?.let { add(DetailsExtra.Text("SOURCE_SCREEN", it)) }
            searchQuery?.let  { add(DetailsExtra.Text("SEARCH_QUERY",  it)) }
        }
        DetailsScreen.Movie -> buildList {
            add(DetailsExtra.Text("media_type", previous.mediaType ?: "movie"))
            add(DetailsExtra.Whole("media_id", previous.tmdbId))
            add(DetailsExtra.Text("media_title", previous.title ?: ""))
            add(DetailsExtra.Text("poster_url", previous.posterUrl ?: ""))
            add(DetailsExtra.Text("backdrop_url", previous.backdropUrl ?: ""))
            add(DetailsExtra.Text("youtube_trailer_url", previous.trailerUrl ?: ""))
            add(DetailsExtra.Text("overview", previous.overview ?: ""))
            add(DetailsExtra.Real("vote_average", previous.voteAverage ?: 0.0))
            add(DetailsExtra.Text("contentRating", previous.contentRating ?: "N/A"))
            add(DetailsExtra.Text("original_language", previous.originalLanguage ?: "N/A"))
            add(DetailsExtra.Text("release_date", previous.releaseDate ?: "N/A"))
            add(DetailsExtra.Amount("budget", previous.budget ?: 0L))
            add(DetailsExtra.Amount("revenue", previous.revenue ?: 0L))
            add(DetailsExtra.Text("logo_url", previous.logoUrl ?: ""))
            add(DetailsExtra.Text("genres", previous.genres?.joinToString(", ") ?: "N/A"))
            add(DetailsExtra.Flag("start_in_extras", true))
            add(DetailsExtra.Whole("PROFILE_ID", profileId))
            sourceScreen?.let { add(DetailsExtra.Text("SOURCE_SCREEN", it)) }
            searchQuery?.let  { add(DetailsExtra.Text("SEARCH_QUERY",  it)) }
        }
    }

    fun open(
        context: Context,
        target: Class<out Activity>,
        screen: DetailsScreen,
        media: MediaItem,
        profileId: Int,
        sourceScreen: String?,
        searchQuery: String?,
    ): Intent = Intent(context, target).putDetailsExtras(openExtras(screen, media, profileId, sourceScreen, searchQuery))

    fun back(
        context: Context,
        target: Class<out Activity>,
        screen: DetailsScreen,
        previous: MediaItem,
        profileId: Int,
        sourceScreen: String?,
        searchQuery: String?,
    ): Intent = Intent(context, target).putDetailsExtras(backExtras(screen, previous, profileId, sourceScreen, searchQuery))
}
