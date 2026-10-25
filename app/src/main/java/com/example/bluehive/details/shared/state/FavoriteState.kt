package com.example.bluehive.details.shared.state

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.FavoriteRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Whether this title is in the profile's favorites: checked when the page
 * opens, flipped by the Favorites button.
 *
 * Moved here word for word from both screens in Stage 4 of the details
 * redesign. The one difference between them is kept: the TV screen sends the
 * title with a toggle and the movie screen does not, so movie favorites may be
 * stored without one (architecture §10, #3). The movie screen passes a null
 * [title], which is FavoriteRequest's own default -- the same request as before.
 */
@Stable
class FavoriteState internal constructor(
    private val scope: CoroutineScope,
    private val profileId: Int,
    private val tmdbId: Int,
    private val mediaType: String,
    private val title: String?,
) {
    /** In the favorites. The button's label follows this. */
    var isFavorited by mutableStateOf(false)
        private set

    /** Check favorite status on load. */
    internal suspend fun check() {
        if (profileId != -1) {
            try {
                val status = ApiClient.bluehiveApi.checkFavorite(
                    profileId   = profileId,
                    mediaTmdbId = tmdbId,
                    mediaType   = mediaType,
                )
                isFavorited = status.is_favorited
            } catch (_: Exception) {}
        }
    }

    /** The Favorites button: add or remove, then show what the server says. */
    fun toggle() {
        if (profileId != -1) {
            scope.launch {
                try {
                    val result = ApiClient.bluehiveApi.toggleFavorite(
                        FavoriteRequest(
                            profile_id = profileId,
                            media_tmdb_id = tmdbId,
                            media_type = mediaType,
                            media_title = title,
                        )
                    )
                    isFavorited = result.is_favorited
                } catch (e: Exception) {
                    Log.e("Favorites", "Toggle failed: ${e.message}")
                }
            }
        }
    }
}

/**
 * The page's FavoriteState, checked once the page opens. [scope] is the
 * screen's own, so leaving the page cancels a toggle in flight, as before.
 */
@Composable
fun rememberFavoriteState(
    scope: CoroutineScope,
    profileId: Int,
    tmdbId: Int,
    mediaType: String,
    title: String?,
): FavoriteState {
    val state = remember(scope, profileId, tmdbId, mediaType, title) {
        FavoriteState(scope, profileId, tmdbId, mediaType, title)
    }
    LaunchedEffect(tmdbId, mediaType, profileId) {
        state.check()
    }
    return state
}
