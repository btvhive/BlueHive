package com.example.bluehive.details.shared.state

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * The Extras switch and the recommendations row it shows in place of the play
 * area.
 *
 * Moved here word for word from both screens in Stage 4 of the details
 * redesign. The two screens load the row differently, and that is kept as
 * [loadInBackground] (architecture §10, #5 and #13):
 *   - false, the TV screen: the fetch runs on the main thread, and a page that
 *     opens with a prefetched row (a title picked from another show's Extras)
 *     uses it;
 *   - true, the movie screen: the fetch runs on Dispatchers.IO in the screen's
 *     scope and hands back on the main thread, and opening in Extras always
 *     fetches.
 * Each branch below is that screen's code, unchanged.
 */
@Stable
class ExtrasState internal constructor(
    private val scope: CoroutineScope,
    private val repository: RecommendationsRepository,
    private val tmdbId: Int,
    private val mediaType: String,
    private val loadInBackground: Boolean,
    startOn: Boolean,
) {
    /** Extras is on: the recommendations row replaces the play area. */
    var isOn by mutableStateOf(startOn)
        private set

    /** The row's titles. Empty until they arrive. */
    var recommendations by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    /** The Extras button. The first time Extras opens, it loads the row. */
    fun toggle() {
        isOn = !isOn
        if (loadInBackground) {
            if (isOn && recommendations.isEmpty()) {   // Load recommendations when toggled on
                scope.launch(Dispatchers.IO) {   // 👈 run on background thread
                    val result = repository.getRecommendations(tmdbId, mediaType)

                    withContext(Dispatchers.Main) {       // 👈 switch back to UI to update state
                        recommendations = result
                        Log.d("Recommendations", "Loaded ${recommendations.size} recommendations")
                    }
                }
            }
        } else {
            // Load recommendations when toggled on
            if (isOn && recommendations.isEmpty()) {
                scope.launch {                            // runs on Main
                    val result = repository.getRecommendations(tmdbId, mediaType)
                    recommendations = result                       // safe: returns on Main
                }
            }
        }
    }

    /**
     * Load the row without switching Extras on: the movie screen's peek
     * layout shows it under the play area from the start. Runs on
     * Dispatchers.IO like the movie screen's toggle, and a failed fetch only
     * leaves the row empty.
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

    /** Back while Extras is on: return to the play area. */
    fun close() {
        isOn = false
    }

    /**
     * When the page opens in Extras (returning from history), preload the row
     * and keep Extras on. [prefetched] is the TV screen's prefetched row.
     */
    internal suspend fun preload(startInExtras: Boolean, prefetched: List<MediaItem>?) {
        if (loadInBackground) {
            if (startInExtras && recommendations.isEmpty()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = repository.getRecommendations(tmdbId, mediaType)
                        withContext(Dispatchers.Main) {
                            recommendations = result
                            isOn = true
                            Log.d(
                                "Recommendations",
                                "Preloaded ${recommendations.size} recommendations for $tmdbId ($mediaType)"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "Recommendations",
                            "Error preloading recommendations for $tmdbId ($mediaType)",
                            e
                        )
                    }
                }
            }
        } else {
            val prefetchedRecs = prefetched
            if (!prefetchedRecs.isNullOrEmpty()) {
                recommendations = prefetchedRecs
                isOn = startInExtras
                Log.d("Prefetch", "✅ Recommendations loaded from prefetch cache (${prefetchedRecs.size} items)")
            } else if (startInExtras && recommendations.isEmpty()) {
                try {
                    val result = repository.getRecommendations(tmdbId, mediaType)
                    recommendations = result
                    isOn = true
                } catch (e: Exception) {
                    Log.e("TVRecommendations", "Error preloading recommendations", e)
                }
            }
        }
    }
}

/**
 * The page's ExtrasState. [scope] is the screen's own, so leaving the page
 * cancels a load in flight, as before.
 */
@Composable
fun rememberExtrasState(
    scope: CoroutineScope,
    repository: RecommendationsRepository,
    tmdbId: Int,
    mediaType: String,
    startInExtras: Boolean,
    loadInBackground: Boolean,
    prefetched: List<MediaItem>?,
): ExtrasState {
    val state = remember(scope, repository, tmdbId, mediaType, loadInBackground, startInExtras) {
        ExtrasState(scope, repository, tmdbId, mediaType, loadInBackground, startOn = startInExtras)
    }
    // ✅ When returning from history and asked to start in Extras,
    // preload recommendations and keep Extras toggled on.
    LaunchedEffect(startInExtras, tmdbId, mediaType) {
        state.preload(startInExtras, prefetched)
    }
    return state
}
