package com.example.bluehive.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.bluehive.api.ApiClient

/**
 * The favourites: every one of the profile's, the loading and navigating
 * flags, remove mode with the titles removed in it, and the requests that load
 * them, commit the removals or undo them.
 *
 * Moved here from FavoritesScreen in Stage F2 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); each method is the
 * code it replaces. Plain snapshot state, read where it is used.
 */
@Stable
internal class FavoritesData {

    /** Every favourite, in the server's order. */
    val entries = mutableStateListOf<FavoriteEntry>()
    var isLoading    by mutableStateOf(true)
    var isNavigating by mutableStateOf(false)

    var removeMode        by mutableStateOf(false)
    /** Back in remove mode asked for "Exit Remove Mode". */
    var showDeleteConfirm by mutableStateOf(false)
    /** Removed in remove mode, not committed yet. */
    val removed = mutableStateListOf<FavoriteEntry>()

    /** "Exit Remove Mode" shows: asked for, or every card has been removed. */
    val showExitRemoveOverlay: Boolean by derivedStateOf {
        showDeleteConfirm || (removeMode && removed.isNotEmpty() && entries.isEmpty())
    }

    /** The fetch: the favourites, then their details in one call per 200. */
    suspend fun load(profileId: Int) {
        if (profileId == -1) { isLoading = false; return }
        // First load shows the loading state. Subsequent resumes (refreshTrigger > 1)
        // re-fetch silently in the background so existing cards stay visible — no flash, no jerk.
        val silent = entries.isNotEmpty()
        if (!silent) isLoading = true
        try {
            val rawList = ApiClient.bluehiveApi.getFavorites(profileId)

            // ONE REQUEST FOR EVERY FAVOURITE, not one per favourite.
            //
            // This used to fire a getMediaDetails per entry and awaitAll() them.
            // MEASURED 2026-09-04 on a profile with 131 favourites: the server
            // answered each in ~3.5ms, but OkHttp allows only 5 concurrent
            // requests per host by default, so the screen waited on ~27
            // sequential round trips. The same details now arrive in one call.
            //
            // CHUNKED because the endpoint caps a call at 300 pairs. At 131 this
            // is a single request; it only matters for someone who hoards.
            val details = rawList
                .map { "${it.media_tmdb_id}:${it.media_type}" }
                .chunked(200)
                .flatMap { chunk ->
                    try {
                        ApiClient.trailerApi.getMediaBatch(chunk.joinToString(","))
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "FavoritesScreen", "batch details failed: ${e.message}"
                        )
                        emptyList()
                    }
                }
                .associateBy { it.tmdb_id to it.media_type }

            // Built from rawList, so the server's ordering is preserved and every
            // favourite gets a card. A title the batch had nothing for falls back
            // to the same stub the per-item 404 produced before.
            val detailed = rawList.map { fav ->
                details[fav.media_tmdb_id to fav.media_type]
                    ?.toFavoriteEntry(favoriteId = fav.id, addedAt = fav.added_at)
                    ?: FavoriteEntry(
                        favoriteId = fav.id,
                        tmdbId     = fav.media_tmdb_id,
                        mediaType  = fav.media_type,
                        addedAt    = fav.added_at,
                        title      = fav.media_title ?: "Unknown",
                    )
            }
            entries.clear()
            entries.addAll(detailed)
        } catch (e: Exception) {
            android.util.Log.e("FavoritesScreen", "Failed to load favorites: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    /**
     * One toggleFavorite per removed title, then the list is empty. [failure]
     * starts the log line of one that fails.
     */
    suspend fun commitRemovals(profileId: Int, failure: String) {
        removed.forEach { entry ->
            try {
                ApiClient.bluehiveApi.toggleFavorite(
                    com.example.bluehive.api.FavoriteRequest(
                        profile_id    = profileId,
                        media_tmdb_id = entry.tmdbId,
                        media_type    = entry.mediaType,
                        media_title   = entry.title,
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("FavoritesScreen", "$failure: ${e.message}")
            }
        }
        removed.clear()
    }

    /** Undo: the removed titles come back. */
    fun undoRemovals() {
        entries.addAll(removed)
        removed.clear()
    }
}

/** Effect 2 of the screen's seven (F-C5): the fetch, on open and again on every resume (refreshTrigger). */
@Composable
internal fun FavoritesFetch(data: FavoritesData, profileId: Int, refreshTrigger: Int) {
    LaunchedEffect(profileId, refreshTrigger) {
        data.load(profileId)
    }
}
