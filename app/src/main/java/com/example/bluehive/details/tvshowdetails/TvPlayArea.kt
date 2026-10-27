package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.example.bluehive.R
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope
import com.example.bluehive.details.shared.layout.PEEK_ROW_TOP
import com.example.bluehive.details.shared.layout.PEEK_SLIDE
import com.example.bluehive.details.shared.layout.OverviewLines
import com.example.bluehive.utilities.RecommendedRowEntry

/**
 * The right-hand caps' left edge: 10 dp right of where they were (902.75), so
 * the last card visible when a season first draws sits clearly outside the
 * cap (the user's calls, 2026-10-27: 6 dp, 16, then 10). The row ends
 * 17 dp under them, as it always did (EPISODE_ROW_END).
 */
internal val RIGHT_CAP_X = 912.75.dp

/**
 * How much lower the recommended row peeks here than on the movie page: the
 * season cap ends at 421.25 dp, about 25 dp under the movie's bottom play
 * buttons (~396), plus 7 dp so the heading clears the episode titles (v470;
 * v469 had 25 and the heading touched them). The slide grows by the same, so
 * the slid-up row ends where the movie's does.
 */
private val TV_PEEK_ROW_DROP = 32.dp

/**
 * The overview's most lines on the TV page: 5 at full size, so the last one
 * clears the air dates (v470; it showed 7), and 6 once the font has shrunk
 * (v473).
 */
internal val TV_OVERVIEW_LINES = OverviewLines(atFullSize = 5, whenShrunk = 6)

/** The TV page's recommended row top (DetailsPeekState.baseRowTop). */
internal val TV_PEEK_ROW_TOP = PEEK_ROW_TOP + TV_PEEK_ROW_DROP

/** How far the TV page slides up while the row has focus. */
internal val TV_PEEK_SLIDE = PEEK_SLIDE + TV_PEEK_ROW_DROP

/**
 * The TV screen's play area: the episode row between its side caps and the
 * season accordion built into the left cap, drawn into the scaffold's Box.
 * (The episode overlay is drawn with the screen's overlays, over the blurred
 * page, since 2026-10-27 -- see EpisodeSourceOverlay.)
 *
 * Moved here from the screen in Stage 8 of the details redesign. Since
 * 2026-10-27 the left cap is extended_mini_left_upper_body, lined up with Add
 * to Favorites, and holds the season accordion; the season column and its
 * frame pieces are gone.
 */
@Composable
internal fun BoxScope.TvPlayArea(
    listedSeasons: Int?,
    numberOfSeasons: Int,
    todayYmd: Int,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    unairedGate: UnairedGate,
    backdropUrl: String?,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
    coroutineScope: CoroutineScope,
    /** Down on an episode enters the recommended row through this. */
    rowEntry: RecommendedRowEntry,
) {
    // ===============================================================================> episode lAZY ROW DECORATIONS
    // The strip behind the season cap's right edge, where the cards come out.
    Image(
        painter = painterResource(id = R.drawable.extended_mini_left_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 9.dp, height = 115.dp)
            .offset(x = SEASON_CAP_X + SEASON_CAP_WIDTH + 0.2.dp - 9.dp, y = 302.75.dp),
        contentScale = ContentScale.Fit
    )

    Image(
        painter = painterResource(id = R.drawable.mini_right_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 7.75.dp, height = 115.dp)
            .offset(x = RIGHT_CAP_X, y = 302.75.dp),
        //.padding(start = 240.75.dp, top = 299.dp),
        contentScale = ContentScale.Fit
    )

    // The episode row -- see EpisodeRow.
    EpisodeRow(
        episodesState   = episodesState,
        focusMap        = focusMap,
        unairedGate     = unairedGate,
        listedSeasons   = listedSeasons,
        numberOfSeasons = numberOfSeasons,
        todayYmd        = todayYmd,
        backdropUrl     = backdropUrl,
        passionFont     = passionFont,
        dongleRegular   = dongleRegular,
        coroutineScope  = coroutineScope,
        rowEntry        = rowEntry,
    )



    // The season cap, on Add to Favorites' left edge, and the accordion inside it.
    Image(
        painter = painterResource(id = R.drawable.extended_mini_left_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = SEASON_CAP_WIDTH, height = SEASON_CAP_HEIGHT)
            .offset(x = SEASON_CAP_X, y = SEASON_CAP_Y),
        contentScale = ContentScale.Fit
    )

    SeasonAccordion(
        listedSeasons   = listedSeasons,
        numberOfSeasons = numberOfSeasons,
        episodesState   = episodesState,
        focusMap        = focusMap,
        passionFont     = passionFont,
        coroutineScope  = coroutineScope,
    )

    Image(
        painter = painterResource(id = R.drawable.mini_right_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 30.5.dp, height = 122.25.dp)
            .offset(x = RIGHT_CAP_X, y = 299.dp),
        //.padding(start = 240.75.dp, top = 299.dp),
        contentScale = ContentScale.Fit
    )
    // ===============================================================================> episode lAZY ROW DECORATIONS
}
