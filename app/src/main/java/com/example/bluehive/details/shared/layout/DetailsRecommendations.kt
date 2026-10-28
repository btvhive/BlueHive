package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
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
 * The details screens' peek row (DetailsPeekState): always under the play
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
    /** The heading -- see recommendedRowTitle. */
    title: String,
) {
    if (extras.recommendations.isNotEmpty()) {
        RecommendedMediaSection(
            recommendations = extras.recommendations,
            onMediaClick = onOpen,
            onNavigateUp = onNavigateUp,
            topOffset = peek.rowTop,
            titleFontSize = PEEK_TITLE_FONT_SIZE,
            titleOffsetY = PEEK_TITLE_OFFSET_Y,
            dimTitleUntilFocused = true,
            autoFocusFirst = peek.startFocused,
            entry = peek.rowEntry,
            onRowFocusChanged = { peek.isScrolled = it },
            title = title,
        )
    }
}

/** How long a page opened in the row waits for its titles before giving the header focus instead. */
const val PEEK_START_WAIT_MS = 6_000L

/**
 * The peek row's start: it loads as the page opens (ExtrasState.loadRow; a
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
    extras: ExtrasState,
    fallback: FocusRequester,
    headerGate: DetailsHeaderGate,
) {
    LaunchedEffect(extras) { extras.loadRow() }
    val hasTitles = extras.recommendations.isNotEmpty()
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

/**
 * The peek row's heading, naming the kind of title the page is about: "More
 * movies you may like" / "More shows you may like", and for Japanese
 * animation "More anime movies you may like" / "More anime series you may
 * like" (the user's test, 2026-10-28; it said "Recommended Media" before).
 * [mediaType] is the page's own ("movie", else a show). Pure, so it is
 * unit-tested.
 */
fun recommendedRowTitle(mediaType: String, isAnime: Boolean): String {
    val isMovie = mediaType.equals("movie", ignoreCase = true)
    val kind = when {
        isAnime && isMovie -> "anime movies"
        isAnime -> "anime series"
        isMovie -> "movies"
        else -> "shows"
    }
    return "More $kind you may like"
}
