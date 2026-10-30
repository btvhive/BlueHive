package com.example.bluehive.details.shared.state

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The recommended row's titles, for the peek layout's row under the play area
 * (DetailsPeekRecommendations). Loads on open ([loadRow]) unless the page
 * opened with a prefetched row (a title picked from another TV show's row).
 *
 * Moved here from both screens in Stage 4 of the details redesign as the
 * Extras switch and its row. The switch went with the peek layout (movie v417,
 * TV v469) and its code -- isOn, toggle, close and the open-in-Extras preload
 * -- was removed in v515; the name stayed.
 */
@Stable
class ExtrasState internal constructor(
    private val scope: CoroutineScope,
    private val repository: RecommendationsRepository,
    private val tmdbId: Int,
    private val mediaType: String,
    prefetched: List<MediaItem>?,
) {
    /**
     * The row's titles. Empty until they arrive, or the prefetched row from
     * the start -- set here rather than by an effect, so [loadRow] (run as
     * the page opens) never fetches a row the page already has.
     */
    var recommendations by mutableStateOf(prefetched.orEmpty())
        private set

    /**
     * Load the row. Runs on Dispatchers.IO, and a failed fetch only leaves
     * the row empty. A no-op when the row is already there.
     */
    fun loadRow() {
        if (recommendations.isNotEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val result = repository.getRecommendations(tmdbId, mediaType)
                withContext(Dispatchers.Main) {
                    recommendations = result
                    Log.d("Recommendations", "Loaded ${recommendations.size} recommendations for the row")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Recommendations", "Error loading recommendations for $tmdbId ($mediaType)", e)
            }
        }
    }
}

/**
 * The page's ExtrasState. [scope] is the screen's own, so leaving the page
 * cancels a load in flight, as before. [prefetched] is the TV screen's
 * prefetched row, if it opened with one.
 */
@Composable
fun rememberExtrasState(
    scope: CoroutineScope,
    repository: RecommendationsRepository,
    tmdbId: Int,
    mediaType: String,
    prefetched: List<MediaItem>?,
): ExtrasState = remember(scope, repository, tmdbId, mediaType) {
    ExtrasState(scope, repository, tmdbId, mediaType, prefetched)
}
