package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.RecommendedMediaSection

/**
 * The Extras row, shown in place of the play area while Extras is on. Nothing
 * shows until the recommendations arrive.
 *
 * What picking a title and pressing Up do stay each screen's own (architecture
 * §10, #4 and #11): the TV screen prefetches the picked title's first season
 * and row for a second before opening it, the movie screen opens it at once,
 * and Up from the movie row asks for focus on Extras twice.
 *
 * Moved here from both screens in Stage 4 of the details redesign.
 */
@Composable
fun DetailsRecommendations(
    extras: ExtrasState,
    onOpen: (MediaItem) -> Unit,
    onNavigateUp: () -> Unit,
) {
    // ✅ Show recommended media section
    if (extras.recommendations.isNotEmpty()) {
        RecommendedMediaSection(
            recommendations = extras.recommendations,
            onMediaClick = onOpen,
            onNavigateUp = onNavigateUp
        )
    }
}
