package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import com.example.bluehive.details.shared.state.RecommendedRowState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.RecommendedMediaSection
import com.example.bluehive.utilities.offsetWithY

/**
 * The details screens' peek row (DetailsPeekState): always under the play
 * area, from the moment its titles arrive. Its heading is smaller and grey
 * until the row has focus; it does not take focus on load unless the page
 * opened in the row; and its focus slides the page.
 *
 * What picking a title and pressing Up do stay each screen's own (architecture
 * §10, #4 and #11): the TV screen prefetches the picked title's first season
 * and row for a second before opening it, the movie screen opens it at once.
 * (The old Extras row, shown in place of the play area, was removed in v515.)
 */
@Composable
fun DetailsPeekRecommendations(
    recommendedRow: RecommendedRowState,
    peek: DetailsPeekState,
    title: String,
    onOpen: (MediaItem) -> Unit,
    onNavigateUp: () -> Unit,
) {
    if (recommendedRow.recommendations.isNotEmpty()) {
        RecommendedMediaSection(
            recommendations = recommendedRow.recommendations,
            onMediaClick = onOpen,
            onNavigateUp = onNavigateUp,
            title = title,
            // Read while placing (v515): the row's top waits on the overview's
            // first layout, and reading it here recomposed the row as it arrived.
            modifier = Modifier.offsetWithY(0.dp) { peek.rowTop },
            titleFontSize = PEEK_TITLE_FONT_SIZE,
            // Trying Asap Condensed Bold in place of Passion One (the
            // user's experiment, 2026-10-28).
            titleFontFamily = AppTypography.asapCondensedBold,
            titleOffsetY = PEEK_TITLE_OFFSET_Y,
            dimTitleUntilFocused = true,
            autoFocusFirst = peek.startFocused,
            entry = peek.rowEntry,
            onRowFocusChanged = { peek.isScrolled = it },
        )
    }
}

/** How long a page opened in the row waits for its titles before giving the header focus instead. */
const val PEEK_START_WAIT_MS = 6_000L

/**
 * The peek row's start: it loads as the page opens (RecommendedRowState.loadRow; a
 * no-op when a prefetched row is already there).
 *
 * A page opened in the row (from history, [DetailsPeekState.startFocused])
 * keeps the header unfocusable until the row's titles arrive, so the first
 * focus lands on the row and not on Add to Favorites; then [headerGate]
 * opens.
 *
 * If no titles arrive within [PEEK_START_WAIT_MS] -- the fetch failed, or the
 * title has none -- the header is released anyway and [fallback] (Add to
 * Favorites) takes focus, so the page never sits with nothing focused, and
 * the row no longer takes focus when titles arrive after all. Before
 * 2026-10-27 both screens waited for the titles with no limit.
 */
@Composable
fun PeekRowStart(
    peek: DetailsPeekState,
    recommendedRow: RecommendedRowState,
    fallback: FocusRequester,
    headerGate: DetailsHeaderGate,
) {
    LaunchedEffect(recommendedRow) { recommendedRow.loadRow() }
    val hasTitles = recommendedRow.recommendations.isNotEmpty()
    LaunchedEffect(peek, hasTitles) {
        if (!peek.startFocused) return@LaunchedEffect
        if (hasTitles) {
            headerGate.open()
            return@LaunchedEffect
        }
        delay(PEEK_START_WAIT_MS)
        peek.giveUpStartFocus()
        headerGate.open()
        awaitFrame()   // the header's canFocus is applied on the next frame
        try { fallback.requestFocus() } catch (_: IllegalStateException) { }
    }
}
