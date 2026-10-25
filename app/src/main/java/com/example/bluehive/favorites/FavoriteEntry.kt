package com.example.bluehive.favorites

import com.example.bluehive.api.MediaDetailResponse


// ─────────────────────────────────────────────────────────────────────────────
//  Internal model — merges FavoriteResponse + TMDB details into one object
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Moved here from sidebarComponents/SidebarFavorites.kt in Stage F1 of the
 * Favorites and Home redesign; FavoriteEntryTest pins toFavoriteEntry.
 */
internal data class FavoriteEntry(
    val favoriteId:      Int,
    val tmdbId:          Int,
    val mediaType:       String,
    val addedAt:         String,
    val title:           String        = "",
    val posterPath:      String?       = null,
    val backdropPath:    String?       = null,
    val releaseDate:     String?       = null,
    val voteAverage:     Double?       = null,
    val overview:        String?       = null,
    val contentRating:   String?       = null,
    val originalLanguage: String?      = null,
    val numberOfSeasons: Int?          = null,
    val runtime:         Int?          = null,
    val status:          String?       = null,
    val trailerUrl:      String?       = null,
    val logoUrl:         String?       = null,
    val genresList:      List<String>  = emptyList(),
    val genresString:    String        = "N/A",
)


// ─────────────────────────────────────────────────────────────────────────────
//  Extension — MediaDetailResponse → FavoriteEntry
// ─────────────────────────────────────────────────────────────────────────────

internal fun MediaDetailResponse.toFavoriteEntry(
    favoriteId: Int,
    addedAt:    String,
): FavoriteEntry {
    val parsedGenres: List<String> = try {
        val raw = genres as? String ?: ""
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name", null) }
    } catch (_: Exception) { emptyList() }

    val genresStr = parsedGenres.joinToString(", ").ifBlank { "N/A" }
    val logoStr   = (logos as? String)?.takeIf { it.isNotBlank() } ?: ""

    return FavoriteEntry(
        favoriteId       = favoriteId,
        tmdbId           = tmdb_id,
        mediaType        = media_type,
        addedAt          = addedAt,
        title            = title ?: "Unknown",
        posterPath       = poster_path,
        backdropPath     = backdrop_path,
        releaseDate      = release_date,
        voteAverage      = vote_average,
        overview         = overview,
        contentRating    = content_rating,
        originalLanguage = original_language,
        numberOfSeasons  = number_of_seasons,
        runtime          = runtime,
        status           = status,
        trailerUrl       = youtube_trailer_url,
        logoUrl          = logoStr,
        genresList       = parsedGenres,
        genresString     = genresStr,
    )
}
