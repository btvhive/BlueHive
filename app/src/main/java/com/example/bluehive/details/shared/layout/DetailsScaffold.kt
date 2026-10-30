package com.example.bluehive.details.shared.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.details.shared.state.FavoriteState
import com.example.bluehive.details.shared.state.TrailerState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.AppTypography
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** How long the peek layout's page takes to slide up or back. */
private const val PEEK_SLIDE_MS = 350

/** What the frame shows about the title. */
@Immutable
data class DetailsInfo(
    val mediaTitle: String,
    val logoUrl: String?,
    val backdropUrl: String?,
    /** The backdrop picture's accessibility label. The two screens word it differently (§10, #15). */
    val backdropDescription: String,
    val contentRating: String?,
    val voteAverage: Double,
    val genres: String?,
    val overview: String?,
    /** Where the genres line goes: under the metadata lines the page has (detailsGenresTop). */
    val genresTop: Dp,
    /** The overview's most lines; null leaves it to its box. See OverviewLines. */
    val overviewLines: OverviewLines? = null,
    /** The recommended row's heading in the peek layout -- see recommendedRowTitle. */
    val rowTitle: String = "Recommended Media",
)

/**
 * Keeps first focus off the header while the page sends it somewhere else
 * (opened in the recommended row, or back to an episode from Continue
 * Watching); [open] lets the header take focus again, for good. One holder
 * per page, so neither screen function carries the flag or the lambdas that
 * cleared it (checkMethodSizes).
 */
@Stable
class DetailsHeaderGate(blocked: Boolean) {
    var isOpen by mutableStateOf(!blocked)
        private set

    fun open() {
        isOpen = true
    }
}

/** The page's DetailsHeaderGate, made again only if [blocked] changes. */
@Composable
fun rememberDetailsHeaderGate(blocked: Boolean): DetailsHeaderGate =
    remember(blocked) { DetailsHeaderGate(blocked) }

/** A screen's settings for the five header buttons. */
@Stable
data class DetailsHeaderSpec(
    val pressTiming: DetailsPressTiming,
    /** False while the screen keeps first focus off the header. */
    val enableFocus: Boolean,
    val favoriteFocusRequester: FocusRequester,
    val trailerFocusRequester: FocusRequester,
    /** The peek layout's Search button opens this keyboard; null shows no Search button. */
    val search: DetailsSearchState? = null,
)

/**
 * The frame both details screens draw around their own content: the
 * background, rating, backdrop, trailer and gradient; the logo or title, the
 * genres and the overview; the header buttons; the play area and the
 * recommended row peeking up under it.
 *
 * Each screen plugs in only what is its own (docs/details-redesign-architecture.md §4):
 *   - [labels]: the metadata labels under the rating;
 *   - [playArea]: everything under the header, above the row;
 *   - [overlays]: its overlay stack, in its own order (the two orders differ,
 *     §10 #14), drawn last so it covers everything;
 *   - what picking a recommendation and pressing Up from the row do.
 *
 * Added in Stage 5 of the details redesign. Everything is drawn in the order
 * each screen drew it before (C1), straight into this one Box: the slots are
 * BoxScope lambdas, so whatever they draw keeps its absolute position. The
 * frame only arranges -- it holds no state and registers no Back handler.
 *
 * Each slot is its own recomposition scope: a change that only the play area
 * or only the overlays read re-runs that slot, not the whole screen.
 *
 * The page is the peek layout (both screens since 2026-10-27; the Extras
 * switch it replaced was removed in v515): the play area and the recommended
 * row both shown, and everything but the background on a layer that slides up
 * while the row has focus. The overlays stay put. See DetailsPeekState.
 */
@UnstableApi
@Composable
fun DetailsScaffold(
    info: DetailsInfo,
    trailer: TrailerState,
    favorite: FavoriteState,
    extras: ExtrasState,
    header: DetailsHeaderSpec,
    /** The TV screen releases the trailer player again on leave (§10, #12). */
    releaseTrailerOnLeave: Boolean,
    onOpenRecommendation: (MediaItem) -> Unit,
    onUpFromRecommendations: () -> Unit,
    labels: @Composable BoxScope.() -> Unit,
    playArea: @Composable BoxScope.() -> Unit,
    overlays: @Composable BoxScope.() -> Unit,
    peek: DetailsPeekState,
    /** On the whole page: the movie screen blurs it behind its search keyboard. */
    modifier: Modifier = Modifier,
    /**
     * On the page but not the overlays: the TV screen blurs the page behind its
     * episode overlay, which is one of its overlays and has to stay sharp, and
     * refuses the page focus while the overlay snaps.
     */
    pageModifier: Modifier = Modifier,
) {
    val dongleRegular = AppTypography.dongleRegular

    Box(
        modifier = Modifier.fillMaxSize().then(modifier)
    ) {
        Box(Modifier.fillMaxSize().then(pageModifier)) {
            DetailsPageBackground()
            DetailsSlidingPage(peek) {
                DetailsPage(
                    info, trailer, favorite, extras, header, releaseTrailerOnLeave,
                    onOpenRecommendation, onUpFromRecommendations, labels, playArea, peek, dongleRegular,
                )
            }
        }

        // The screen's overlay stack, last so it draws on top of everything.
        overlays()
    }
}

/**
 * Everything between the background and the overlays, in the order it has
 * always been drawn. A BoxScope extension, so it adds no layout of its own.
 */
@UnstableApi
@Composable
private fun BoxScope.DetailsPage(
    info: DetailsInfo,
    trailer: TrailerState,
    favorite: FavoriteState,
    extras: ExtrasState,
    header: DetailsHeaderSpec,
    releaseTrailerOnLeave: Boolean,
    onOpenRecommendation: (MediaItem) -> Unit,
    onUpFromRecommendations: () -> Unit,
    labels: @Composable BoxScope.() -> Unit,
    playArea: @Composable BoxScope.() -> Unit,
    peek: DetailsPeekState,
    dongleRegular: FontFamily,
) {
        // Content rating, star and score, and the backdrop picture (hidden once
        // the trailer has played) -- see DetailsBackdrop. The background is
        // drawn under the sliding page (DetailsPageBackground).
        DetailsBackdrop(
            contentRating       = info.contentRating,
            voteAverage         = info.voteAverage,
            backdropUrl         = info.backdropUrl,
            trailer             = trailer,
            backdropDescription = info.backdropDescription,
            dongleRegular       = dongleRegular,
        )

        // This renders BEHIND the gradient overlay due to Box ordering.
        // Its own function on purpose -- see DetailsTrailerLayer.
        DetailsTrailerLayer(
            trailer = trailer,
            modifier = Modifier.align(Alignment.TopEnd),
            releaseOnLeave = releaseTrailerOnLeave,
        )

        DetailsGradient(info.backdropUrl)

        // Logo or title, the screen's labels, then genres and the overview.
        DetailsLogoOrTitle(logoUrl = info.logoUrl, mediaTitle = info.mediaTitle)
        labels()
        DetailsOverview(
            genres = info.genres,
            genresTop = info.genresTop,
            overview = info.overview,
            dongleRegular = dongleRegular,
            onTextLaidOut = { bottom, lines ->
                peek.overviewBottom = bottom
                peek.overviewLines = lines
            },
            lines = info.overviewLines,
        )

        // Favorites, Search, the trailer controls and "Loading trailer...":
        // see DetailsHeaderButtons. Each press runs a state holder's method.
        DetailsHeaderButtons(
            fontFamily = AppTypography.passionRegular,
            pressTiming = header.pressTiming,
            enableFocus = header.enableFocus,
            favoriteFocusRequester = header.favoriteFocusRequester,
            trailerFocusRequester = header.trailerFocusRequester,
            favorite = favorite,
            trailer = trailer,
            // Search: the trailer stops, as it does when a recommendation
            // opens, and the keyboard opens over the page.
            onSearch = header.search?.let { search -> { trailer.stop(); search.open() } },
        )

        // No overview counts as a short one (PeekRule).
        if (info.overview.isNullOrBlank()) {
            LaunchedEffect(peek) { peek.overviewLines = 0 }
        }
        // The play area, and the row peeking up under it.
        playArea()
        DetailsPeekRecommendations(
            extras = extras,
            peek = peek,
            title = info.rowTitle,
            onOpen = onOpenRecommendation,
            onNavigateUp = onUpFromRecommendations,
        )
}

/**
 * The peek layout's sliding layer: up by [DetailsPeekState.slide] while the
 * row has focus. The offset is read only while placing, so a slide re-places
 * the page without recomposing or re-measuring it.
 *
 * NOT A GPU LAYER, measured: moving the page with graphicsLayer translationY
 * (v448) was no cheaper -- same-session A/B on the onn box, slide p50 34-44 ms
 * as a layer against 26-27 ms re-placing, slow UI-thread frames alike (48-61).
 * The slide's cost is the work that runs on the row's focus change, not the
 * move. A layer also cannot move the trailer's SurfaceView.
 */
@Composable
private fun DetailsSlidingPage(peek: DetailsPeekState, content: @Composable BoxScope.() -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(peek) {
        snapshotFlow { peek.isScrolled }.collectLatest { scrolled ->
            progress.animateTo(
                if (scrolled) 1f else 0f,
                tween(durationMillis = PEEK_SLIDE_MS, easing = FastOutSlowInEasing),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, -(peek.slide.toPx() * progress.value).roundToInt()) },
        content = content,
    )
}
