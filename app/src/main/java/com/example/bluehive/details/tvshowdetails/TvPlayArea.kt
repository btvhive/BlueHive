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
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.details.shared.playback.AnimePlaybackController
import com.example.bluehive.details.shared.playback.SourcePlaybackController

/**
 * The TV screen's play area: the decorations around the season column and
 * the episode row, the column, the row, and the source overlay under the
 * selected tile -- drawn into the scaffold's Box at the same offsets and in
 * the same order as before.
 *
 * Moved here from the screen in Stage 8 of the details redesign.
 */
@Composable
internal fun BoxScope.TvPlayArea(
    listedSeasons: Int?,
    numberOfSeasons: Int,
    todayYmd: Int,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    unairedGate: UnairedGate,
    extras: ExtrasState,
    anime: AnimePlaybackController,
    sourcePlayback: SourcePlaybackController,
    mediaType: String,
    mediaId: Int,
    genres: String?,
    originalLanguage: String?,
    backdropUrl: String?,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
    coroutineScope: CoroutineScope,
    stopTrailerAndSelectEpisode: (season: Int, episode: Int, epName: String?) -> Unit,
    onUnavailable: () -> Unit,
) {
    Image(
        painter = painterResource(id = R.drawable.top_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 132.dp, height = 7.75.dp)
            .offset(x = 32.7.dp, y = 321.6.dp),
        contentScale = ContentScale.Fit
    )

    Image(
        painter = painterResource(id = R.drawable.bottom_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 132.dp, height = 7.75.dp)
            .offset(x = 32.7.dp, y = 499.25.dp),
        contentScale = ContentScale.Fit
    )

    // The season column -- see SeasonColumn.
    SeasonColumn(
        listedSeasons   = listedSeasons,
        numberOfSeasons = numberOfSeasons,
        episodesState   = episodesState,
        focusMap        = focusMap,
        passionFont     = passionFont,
        coroutineScope  = coroutineScope,
    )

    Image(
        painter = painterResource(id = R.drawable.top_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 140.25.dp, height = 30.5.dp)
            .offset(x = 28.25.dp, y = 299.dp),
        contentScale = ContentScale.Fit
    )

    Image(
        painter = painterResource(id = R.drawable.bottom_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 140.25.dp, height = 30.5.dp)
            .offset(x = 28.25.dp, y = 499.25.dp),
        contentScale = ContentScale.Fit
    )




    // ===============================================================================> episode lAZY ROW DECORATIONS
    Image(
        painter = painterResource(id = R.drawable.mini_left_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 7.75.dp, height = 115.dp)
            .offset(x = 239.7.dp, y = 302.75.dp),
        //.padding(start = 240.75.dp, top = 299.dp),
        contentScale = ContentScale.Fit
    )

    Image(
        painter = painterResource(id = R.drawable.mini_right_lower_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 7.75.dp, height = 115.dp)
            .offset(x = 902.75.dp, y = 302.75.dp),
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
    )



    Image(
        painter = painterResource(id = R.drawable.mini_left_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 30.5.dp, height = 122.25.dp)
            .offset(x = 216.75.dp, y = 299.dp),
        //.padding(start = 240.75.dp, top = 299.dp),
        contentScale = ContentScale.Fit
    )

    Image(
        painter = painterResource(id = R.drawable.mini_right_upper_body),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(width = 30.5.dp, height = 122.25.dp)
            .offset(x = 902.75.dp, y = 299.dp),
        //.padding(start = 240.75.dp, top = 299.dp),
        contentScale = ContentScale.Fit
    )
    // ===============================================================================> episode lAZY ROW DECORATIONS


    // The source overlay under the selected tile -- see EpisodeSourceOverlay.
    EpisodeSourceOverlay(
        extras                      = extras,
        episodesState               = episodesState,
        focusMap                    = focusMap,
        anime                       = anime,
        sourcePlayback              = sourcePlayback,
        mediaType                   = mediaType,
        mediaId                     = mediaId,
        genres                      = genres,
        originalLanguage            = originalLanguage,
        passionFont                 = passionFont,
        stopTrailerAndSelectEpisode = stopTrailerAndSelectEpisode,
        onUnavailable               = onUnavailable,
    )

}
