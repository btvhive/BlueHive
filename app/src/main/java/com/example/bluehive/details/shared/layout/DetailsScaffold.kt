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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
)

/** A screen's settings for the five header buttons. */
@Stable
data class DetailsHeaderSpec(
    val pressTiming: DetailsPressTiming,
    /** False while the screen keeps first focus off the header. */
    val enableFocus: Boolean,
    val favoriteFocusRequester: FocusRequester,
    val extrasFocusRequester: FocusRequester,
    val trailerFocusRequester: FocusRequester,
    /** The peek layout's Search button press; null shows no Search button. */
    val onSearch: (() -> Unit)? = null,
)

/**
 * The frame both details screens draw around their own content: the
 * background, rating, backdrop, trailer and gradient; the logo or title, the
 * genres and the overview; the five header buttons; and the Extras switch,
 * which shows the screen's play area while Extras is off and the
 * recommendations row while it is on.
 *
 * Each screen plugs in only what is its own (docs/details-redesign-architecture.md §4):
 *   - [labels]: the metadata labels under the rating;
 *   - [playArea]: everything under the header while Extras is off;
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
 * With a [peek] (the movie screen) the page is the peek layout instead: no
 * Extras button, the play area and the recommended row both shown, and
 * everything but the background on a layer that slides up while the row has
 * focus. The overlays stay put. See DetailsPeekState.
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
    peek: DetailsPeekState? = null,
    /** On the whole page: the movie screen blurs it behind its search keyboard. */
    modifier: Modifier = Modifier,
) {
    val dongleRegular = AppTypography.dongleRegular

    Box(
        modifier = Modifier.fillMaxSize().then(modifier)
    ) {
        if (peek == null) {
            DetailsPage(
                info, trailer, favorite, extras, header, releaseTrailerOnLeave,
                onOpenRecommendation, onUpFromRecommendations, labels, playArea, null, dongleRegular,
            )
        } else {
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
    peek: DetailsPeekState?,
    dongleRegular: FontFamily,
) {
        // Background, content rating, star and score, and the backdrop picture
        // (hidden once the trailer has played) -- see DetailsBackdrop. In the
        // peek layout the background is drawn under the sliding page instead.
        DetailsBackdrop(
            contentRating       = info.contentRating,
            voteAverage         = info.voteAverage,
            backdropUrl         = info.backdropUrl,
            trailer             = trailer,
            backdropDescription = info.backdropDescription,
            dongleRegular       = dongleRegular,
            drawBackground      = peek == null,
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
            overview = info.overview,
            dongleRegular = dongleRegular,
            onTextBottom = peek?.let { p -> { bottom -> p.overviewBottom = bottom } },
        )

        // Favorites, Extras, Trailer, "Loading trailer...", Rewind, Fast-forward:
        // see DetailsHeaderButtons. Each press runs a state holder's method.
        DetailsHeaderButtons(
            // The peek layout's labels are lighter: Inter Bold, between the
            // heavy Passion One and Inter SemiBold (the user's calls, 2026-10-26).
            fontFamily = if (peek != null) AppTypography.interBold else AppTypography.passionRegular,
            labelSize = if (peek != null) PEEK_HEADER_LABEL_SP else 12f,
            // A little heavier than the Inter Bold file: Android's synthetic
            // bold (the user's call, 2026-10-26).
            labelWeight = if (peek != null) FontWeight.ExtraBold else null,
            pressTiming = header.pressTiming,
            enableFocus = header.enableFocus,
            favoriteFocusRequester = header.favoriteFocusRequester,
            extrasFocusRequester = header.extrasFocusRequester,
            trailerFocusRequester = header.trailerFocusRequester,
            favorite = favorite,
            extras = extras,
            trailer = trailer,
            showExtras = peek == null,
            onSearch = header.onSearch,
        )

        if (peek != null) {
            // Peek layout: the play area, and the row peeking up under it.
            playArea()
            DetailsPeekRecommendations(
                extras = extras,
                peek = peek,
                onOpen = onOpenRecommendation,
                onNavigateUp = onUpFromRecommendations,
            )
        } else if (!extras.isOn) {
            // ✅ Conditionally show StreamerButtons OR RecommendedMediaSection
            playArea()
        } else {
            DetailsRecommendations(
                extras = extras,
                onOpen = onOpenRecommendation,
                onNavigateUp = onUpFromRecommendations,
            )
        }
}

/**
 * The peek layout's sliding layer: up by [DetailsPeekState.slide] while the
 * row has focus. The offset is read only while placing, so a slide re-places
 * the page without recomposing or re-measuring it.
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
