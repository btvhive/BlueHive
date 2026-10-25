package com.example.bluehive.details.shared.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.details.shared.state.FavoriteState
import com.example.bluehive.details.shared.state.TrailerState
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.AppTypography

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
) {
    val dongleRegular = AppTypography.dongleRegular

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background, content rating, star and score, and the backdrop picture
        // (hidden once the trailer has played) -- see DetailsBackdrop.
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
        DetailsOverview(genres = info.genres, overview = info.overview, dongleRegular = dongleRegular)

        // Favorites, Extras, Trailer, "Loading trailer...", Rewind, Fast-forward:
        // see DetailsHeaderButtons. Each press runs a state holder's method.
        DetailsHeaderButtons(
            fontFamily = AppTypography.passionRegular,
            pressTiming = header.pressTiming,
            enableFocus = header.enableFocus,
            favoriteFocusRequester = header.favoriteFocusRequester,
            extrasFocusRequester = header.extrasFocusRequester,
            trailerFocusRequester = header.trailerFocusRequester,
            favorite = favorite,
            extras = extras,
            trailer = trailer,
        )

        // ✅ Conditionally show StreamerButtons OR RecommendedMediaSection
        if (!extras.isOn) {
            playArea()
        } else {
            DetailsRecommendations(
                extras = extras,
                onOpen = onOpenRecommendation,
                onNavigateUp = onUpFromRecommendations,
            )
        }

        // The screen's overlay stack, last so it draws on top of everything.
        overlays()
    }
}
