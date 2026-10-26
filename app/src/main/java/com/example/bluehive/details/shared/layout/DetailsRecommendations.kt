package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
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

/**
 * The movie screen's peek row (DetailsPeekState): always under the play
 * area, from the moment its titles arrive. Its heading is smaller and grey
 * until the row has focus; it does not take focus on load unless the page
 * opened in Extras; and its focus slides the page.
 */
@Composable
fun DetailsPeekRecommendations(
    extras: ExtrasState,
    peek: DetailsPeekState,
    onOpen: (MediaItem) -> Unit,
    onNavigateUp: () -> Unit,
) {
    if (extras.recommendations.isNotEmpty()) {
        RecommendedMediaSection(
            recommendations = extras.recommendations,
            onMediaClick = onOpen,
            onNavigateUp = onNavigateUp,
            topOffset = peek.rowTop,
            titleFontSize = 18.sp,
            titleOffsetY = PEEK_TITLE_OFFSET_Y,
            dimTitleUntilFocused = true,
            autoFocusFirst = peek.startFocused,
            entry = peek.rowEntry,
            onRowFocusChanged = { peek.isScrolled = it },
        )
    }
}
