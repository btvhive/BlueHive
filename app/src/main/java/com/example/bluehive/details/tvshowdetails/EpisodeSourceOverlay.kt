package com.example.bluehive.details.tvshowdetails

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.details.shared.playback.AnimePlaybackController
import com.example.bluehive.details.shared.playback.SourcePlaybackController
import com.example.bluehive.details.shared.playback.StreamSource
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.utilities.*
import kotlinx.coroutines.delay

// The overlay card (design E, 2026-10-27): the episode still as its backdrop,
// darkened from the left (the text) and the right (the buttons).
private val OVERLAY_WIDTH = 660.dp
private val OVERLAY_HEIGHT = 262.dp
private val OVERLAY_SHAPE = RoundedCornerShape(12.dp)
private val OVERLAY_EDGE = Color(0xFF343648)
private val OVERLAY_FACE = Color(0xFF12131C)
private val OVERLAY_SHADE = Color(0xEB0A0B12)
private val OVERLAY_SHADE_LIGHT = Color(0x550A0B12)
private val OVERLAY_GOLD = Color(0xFFD19814)

/** The Watch buttons: the movie page's, 190 dp wide, one under another. */
private val SOURCE_BUTTON_WIDTH = 190.dp
private val SOURCE_BUTTON_STEP = 52.dp

private val OverlayShade = Brush.horizontalGradient(
    0.00f to OVERLAY_SHADE,
    0.30f to OVERLAY_SHADE,
    0.50f to OVERLAY_SHADE_LIGHT,
    0.60f to OVERLAY_SHADE_LIGHT,
    0.68f to OVERLAY_SHADE,
    1.00f to OVERLAY_SHADE,
)

/**
 * The source buttons for this episode.
 *
 * NOW BACKEND-OWNED. The list, its order, the button labels and the url shapes
 * all come from config/sources.conf on the server via SourceCatalog, so a dead
 * source is switched off by editing one line there rather than by shipping a
 * build. The hardcoded list that used to live here is still compiled in as
 * SourceCatalog's built-in fallback, so an unreachable backend behaves exactly
 * as this function did before.
 *
 * Still synchronous: SourceCatalog answers from memory or disk and never does
 * IO here, which is what lets the focus-requester pool size itself during
 * composition.
 */
internal fun getStreamingSources(
    mediaType: String,
    mediaId: Int,
    seasonNumber: Int,
    episodeNumber: Int
): List<StreamSource> {
    if (!mediaType.equals("movie", true)) {
        return com.example.bluehive.api.SourceCatalog
            .sources(BlueHiveApplication.appContext, "tv", mediaId, seasonNumber, episodeNumber)
            .map { StreamSource(it.label, it.name, it.url) }
    }
    // Only a title of the other type gets here, and it has no buttons on
    // this screen. The hard-coded list that followed could never run and
    // was removed in Stage 7 of the details redesign; the same sources are
    // SourceCatalog's built-ins.
    return emptyList()
}

/** The overlay is open: the screen blurs the page behind it (API 31+). */
internal fun episodeOverlayOpen(extras: ExtrasState, episodesState: EpisodesState): Boolean =
    !extras.isOn && episodesState.selectedEpisodeOverlayIndex in episodesState.episodes.indices

/**
 * The episode overlay: OK on an episode opens it over the page. The still
 * fills the card; the episode's number, title, air date and overview sit on
 * the left, and the "Watch on ..." buttons (English / Japanese for an anime)
 * one under another on the right. A press records the episode in
 * [episodesState] and starts the anime flow or the source chain; Back closes
 * it and returns to the tile.
 *
 * The page behind blurs on API 31+ (the onn box -- hiveBackdropBlur on the
 * scaffold's page); older boxes (Fire TV, no RenderEffect) get the dark scrim
 * the movie page's search keyboard uses, faded in. Drawn in the overlay slot,
 * under the loading and anime overlays, so those still cover it. It STAYS
 * open behind the player, which keeps the pressed button attached so focus
 * can return to it on the way back.
 *
 * Replaced, 2026-10-27, the small 2x2 grid under the tile, the tile's "Select
 * a site below" cover, and the overview that faded in under a focused tile.
 */
@Composable
internal fun BoxScope.EpisodeSourceOverlay(
    extras: ExtrasState,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    anime: AnimePlaybackController,
    sourcePlayback: SourcePlaybackController,
    mediaType: String,
    mediaId: Int,
    genres: String?,
    originalLanguage: String?,
    backdropUrl: String?,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
    stopTrailerAndSelectEpisode: (season: Int, episode: Int, epName: String?) -> Unit,
    /** A brand-new anime with no AniList mapping yet: the screen shows its notice. */
    onUnavailable: () -> Unit,
) {
    val open = episodeOverlayOpen(extras, episodesState)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val dim by animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "episodeOverlayDim",
        )
        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim }
                    .background(overlayScrimColor())
            )
        }
    }
    if (!open) return

    val selectedEpisode = episodesState.episodes[episodesState.selectedEpisodeOverlayIndex]
    val episodeNumber = selectedEpisode.episodeNumber
    val season = episodesState.selectedSeason
    val isAnime = isJapaneseAnimation(genres, originalLanguage)

    // Build sources for this episode.
    // Japanese animation → 2 buttons (Miruro + VidFast); else the standard set.
    val episodeSources by produceState(
        initialValue = emptyList(),
        mediaType, mediaId, season, episodeNumber, genres, originalLanguage
    ) {
        if (isAnime) {
            value = buildJapaneseAnimationSources(
                mediaType     = mediaType,
                tmdbId        = mediaId,
                seasonNumber  = season,
                episodeNumber = episodeNumber,
            ) { c, n, u -> StreamSource(c, n, u) }
        } else {
            // READ FIRST, REFRESH SECOND, READ AGAIN. The first read
            // is synchronous and fills the buttons immediately from
            // cache; the refresh then upgrades them if the server has
            // changed since. Fetching before the first assignment
            // would make every title wait on the network to draw its
            // own buttons, which is a worse trade than being one
            // screen late to a kill switch.
            value = getStreamingSources(mediaType, mediaId, season, episodeNumber)
            com.example.bluehive.api.SourceCatalog
                .warm(BlueHiveApplication.appContext ?: return@produceState)
            value = getStreamingSources(mediaType, mediaId, season, episodeNumber)
        }
    }

    val closeToTile: () -> Unit = {
        episodesState.selectedEpisodeOverlayIndex = -1
        val targetIndex = episodesState.lastFocusedEpisodeIndex
            .coerceIn(0, episodesState.focusRequesters.lastIndex)
        episodesState.focusRequesters[targetIndex].requestFocus()
    }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(OVERLAY_WIDTH, OVERLAY_HEIGHT)
            .clip(OVERLAY_SHAPE)
            .background(OVERLAY_FACE)
    ) {
        AsyncImage(
            model = selectedEpisode.stillImageUrl ?: backdropUrl,
            contentDescription = selectedEpisode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(OverlayShade))

        EpisodeOverlayText(
            season = season,
            episodeNumber = episodeNumber,
            title = selectedEpisode.title,
            airDate = formatEpisodeAirDate(selectedEpisode.airDate),
            overview = selectedEpisode.overview.orEmpty(),
            passionFont = passionFont,
            dongleRegular = dongleRegular,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 24.dp)
                .width(SOURCE_BUTTON_WIDTH)
                .focusGroup()
        ) {
            if (isAnime) {
                // Anime: exactly English / Japanese. Both run the Miruro flow
                // (the URL already carries ?ep=, so the extractor clicks
                // the right episode).
                val miruroUrl = episodeSources.firstOrNull { it.name.equals("Miruro", true) }?.url
                AnimeEpisodeProviderButtons(
                    passionFont     = passionFont,
                    miruroUrl       = miruroUrl,
                    // Empty means produceState has not finished; once it
                    // has, the list always holds at least VidFast.
                    resolving       = episodeSources.isEmpty(),
                    focusRequesters = focusMap.providers,
                    onDub = { url ->
                        val epName = selectedEpisode.title
                        stopTrailerAndSelectEpisode(season, episodeNumber, epName)
                        // NOT marked watched here. Picking English only opens the
                        // server prompt — extraction can still fail, find no
                        // servers, or be cancelled. The mark happens where the
                        // stream actually plays (see the extractor result
                        // handler, alongside logWatchHistory).
                        focusMap.lastStream = focusMap.providers.getOrNull(0)
                        anime.pendingUrl         = url
                        anime.pendingDub         = true
                        episodesState.pendingSeason      = season
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        anime.showDefaultPrompt  = true
                    },
                    onSub = { url ->
                        val epName = selectedEpisode.title
                        stopTrailerAndSelectEpisode(season, episodeNumber, epName)
                        // NOT marked watched here — same reason as onDub above.
                        focusMap.lastStream = focusMap.providers.getOrNull(1)
                        anime.pendingUrl         = url
                        anime.pendingDub         = false
                        episodesState.pendingSeason      = season
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        anime.showDefaultPrompt  = true
                    },
                    onUnavailable = onUnavailable,
                    onBack = closeToTile,
                )
            } else {
                EpisodeSourceButtons(
                    passionFont = passionFont,
                    sources = episodeSources,
                    focusRequesters = focusMap.providers,
                    onSourceSelected = { source ->
                        val epName = selectedEpisode.title

                        // Remember which button launched this so focus comes
                        // back here when the player exits (THE RULE).
                        focusMap.lastStream = focusMap.providers.getOrNull(
                            episodeSources.indexOfFirst { it.name == source.name }
                        )

                        // Every source: server-side m3u8 extraction, then our
                        // own player. The server reports every source as
                        // extractable; one it could not extract fails into the
                        // same chain as a dead one.
                        episodesState.pendingSeason      = season
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        sourcePlayback.start(source)
                    },
                    onBack = closeToTile,
                )
            }
        }

        // The edge on top, over the still's corners.
        Box(Modifier.fillMaxSize().border(1.dp, OVERLAY_EDGE, OVERLAY_SHAPE))
    }
}

/** The left column: S6 · E7 in gold, the title, the air date, the overview. */
@Composable
private fun BoxScope.EpisodeOverlayText(
    season: Int,
    episodeNumber: Int,
    title: String,
    airDate: String,
    overview: String,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 24.dp, top = 28.dp, bottom = 22.dp)
            .width(236.dp)
            .fillMaxHeight()
    ) {
        Text(
            text = "S$season · E$episodeNumber",
            fontFamily = AppTypography.interBold,
            fontSize = 10.sp,
            color = OVERLAY_GOLD,
        )
        Text(
            text = title,
            fontFamily = passionFont,
            fontSize = 26.sp,
            lineHeight = 27.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // The Watch buttons' label shadow -- see ButtonLabelShadow.
            style = TextStyle(shadow = ButtonLabelShadow),
            modifier = Modifier.padding(top = 4.dp),
        )
        if (airDate.isNotBlank()) {
            Text(
                text = airDate,
                fontFamily = AppTypography.interSemiBold,
                fontSize = 9.5.sp,
                color = Color(0xFFAAACB9),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (overview.isNotBlank()) {
            // As tall as the card leaves room for; a long overview ends in "...".
            Text(
                text = overview,
                fontFamily = dongleRegular,
                fontSize = 16.sp,
                lineHeight = 11.5.sp,
                color = Color(0xFFE7E3E3),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .weight(1f, fill = false),
            )
        }
    }
}

/** The "Watch on ..." buttons, one under another; focus starts on the first. */
@Composable
private fun EpisodeSourceButtons(
    passionFont: FontFamily,
    sources: List<StreamSource>,
    focusRequesters: List<FocusRequester>,
    onSourceSelected: (StreamSource) -> Unit,
    onBack: () -> Unit,
) {
    // Capped to the focus-requester pool so we never index past it; the pool
    // is sized to the source list, so in practice this keeps every source.
    val shown = sources.take(focusRequesters.size)

    LaunchedEffect(shown.isNotEmpty()) {
        if (shown.isNotEmpty()) {
            // give Compose a frame to attach focus nodes
            delay(50)
            focusRequesters.firstOrNull()?.requestFocus()
        }
    }

    shown.forEachIndexed { i, source ->
        OverlayWatchButton(
            text = "Watch on ${source.name}",
            colors = StreamSourceButtonColors,
            passionFont = passionFont,
            focusRequester = focusRequesters[i],
            index = i,
            focusRequesters = focusRequesters.subList(0, shown.size),
            onBack = onBack,
            onClick = { onSourceSelected(source) },
        )
    }
}

/**
 * One of the overlay's buttons, at row [index]: the movie page's Watch
 * button, 190 dp wide. Up / Down move between [focusRequesters]; every
 * other direction, and both ends, are consumed with no move and no sound,
 * so focus cannot wander onto the page behind. Back is [onBack].
 */
@Composable
internal fun OverlayWatchButton(
    text: String,
    colors: ModularButtonColors,
    passionFont: FontFamily,
    focusRequester: FocusRequester,
    index: Int,
    focusRequesters: List<FocusRequester>,
    onBack: () -> Unit,
    onClick: () -> Unit,
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(text = text, fontSize = 14f, shadow = ButtonLabelShadow),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(y = SOURCE_BUTTON_STEP * index)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val target = index + if (event.key == Key.DirectionUp) -1 else 1
                        focusRequesters.getOrNull(target)?.let {
                            it.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    Key.Back -> { onBack(); true }   // consume so Activity back doesn't fire
                    else -> false
                }
            },
        dimensions = ModularButtonDimensions(
            mainWidth = SOURCE_BUTTON_WIDTH, mainHeight = 33.dp, mainYOffset = 0.7.dp,
            secondWidth = SOURCE_BUTTON_WIDTH, secondHeight = 22.dp, secondYOffset = 18.dp,
            shadowHeight = 12.dp, glowWidth = SOURCE_BUTTON_WIDTH + 23.8.dp, glowHeight = 55.8.dp,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        colors = colors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the movie screen -- see WatchButtonAnimation.
        animationConfig = WatchButtonAnimation,
        // No playClickSound() here: ModularButton already plays it.
        onClick = onClick
    )
}
