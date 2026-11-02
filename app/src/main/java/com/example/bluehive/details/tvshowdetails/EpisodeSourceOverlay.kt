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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import com.example.bluehive.utilities.*
import kotlinx.coroutines.delay

// The overlay card (design B1, 2026-10-27): the episode still across the top,
// fading into the card under the title, then the overview and the Watch
// buttons two by two. Sizes are the approved mockup's (scratchpad
// ep_overlay_mock.py / ep_overlay_b.py, b1(12)).
private val OVERLAY_WIDTH = 400.dp
/** The whole card drawn at 85% of the mockup's size (the user's call, 2026-10-27). */
private const val OVERLAY_SCALE = 0.85f
private val OVERLAY_SHAPE = RoundedCornerShape(10.dp)
private val OVERLAY_EDGE = Color(0xFF343648)
// The app's background black, 0B0B0B, with no blue in it (the user's call,
// 2026-10-27; was the dark grey-blue 0xFF12131C, then 0A0A0C). The still
// fades into it too.
private val OVERLAY_FACE = Color(0xFF0B0B0B)
private val OVERLAY_STILL_HEIGHT = 190.dp

/** The Watch buttons: the movie page's, 162 dp wide, two to a row. */
private val SOURCE_BUTTON_WIDTH = 162.dp
private val SOURCE_COLUMN_STEP = 176.dp   // 162 + a 14 dp gap
private val SOURCE_ROW_STEP = 52.dp       // the 40 dp button + a 12 dp gap
private val SOURCE_BUTTON_HEIGHT = 40.dp  // face + the strip under it
/**
 * A button's own box is its glow's size (ModularButton), 55.8 dp tall. The
 * grid leaves that much room under its last row -- with only 40 dp, a single
 * row (anime) squashed the focus glow to the button's height.
 */
private val SOURCE_BUTTON_ROOM = 55.8.dp

/** Which step of the anime flow the card shows (v585): the server choice lives in the card. */
internal enum class AnimeCardStep { Language, Pair, List }

/**
 * The step the anime flow asks for. The pair stays while "Visiting the site
 * to list its servers…" runs, so the language row does not flash up behind
 * the loading screen.
 */
internal fun animeCardStep(
    isAnime: Boolean,
    showServerPicker: Boolean,
    hasServers: Boolean,
    showDefaultPrompt: Boolean,
    checkingServers: Boolean,
): AnimeCardStep = when {
    !isAnime -> AnimeCardStep.Language
    showServerPicker && hasServers -> AnimeCardStep.List
    showDefaultPrompt || checkingServers -> AnimeCardStep.Pair
    else -> AnimeCardStep.Language
}

/** The still fades into the card from 45% of its height down, as in the mockup. */
private val StillFade = Brush.verticalGradient(
    0.45f to Color.Transparent,
    1.00f to OVERLAY_FACE,
)

/**
 * The episode still at full size. The server stores w300 (300 px wide) for the
 * episode tiles, which is soft across the overlay's 400 dp; TMDB's "original"
 * of the same picture is what this card shows. Other urls pass through.
 */
internal fun fullSizeStill(url: String?): String? =
    url?.replace("/t/p/w300/", "/t/p/original/")

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
internal fun episodeOverlayOpen(episodesState: EpisodesState): Boolean =
    episodesState.selectedEpisodeOverlayIndex in episodesState.episodes.indices

/**
 * The episode overlay: OK on an episode opens it over the page. The still
 * (full size) runs across the top and fades into the card under the title
 * and "Season · Episode · air date"; the overview follows, then the "Watch
 * on ..." buttons two by two (English / Japanese for an anime). A press records the episode in
 * [episodesState] and starts the anime flow or the source chain; Back closes
 * it and returns to the tile.
 *
 * The page behind blurs on API 31+ (the onn box -- detailsBackdropBlur on the
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
    /** Every launch from the overlay calls this first: the trailer stops (see the screen's stopTrailerForLaunch). */
    stopTrailer: () -> Unit,
    /** A brand-new anime with no AniList mapping yet: the screen shows its notice. */
    onUnavailable: () -> Unit,
    /** The screen's snap: a Watch on ... press turns the card to dust first -- see EpisodeSnapState. */
    snap: EpisodeSnapState,
) {
    // The anime flow's server choice snaps the card too, and its own failure
    // notices give the card back -- see EpisodeSnapState.
    DisposableEffect(anime, snap) {
        anime.launchGate = { start -> snap.snapThenOrNow(start) }
        snap.flowFailed = { anime.showServerError || anime.showNoDub }
        onDispose {
            anime.launchGate = null
            snap.flowFailed = { false }
        }
    }

    val open = episodeOverlayOpen(episodesState)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val dim = animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "episodeOverlayDim",
        )
        // Only whether it shows is read here; the fade itself is read in the
        // layer, so the 200 ms fade no longer recomposes the overlay every
        // frame (v515).
        val dimShows by remember { derivedStateOf { dim.value > 0f } }
        if (dimShows) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim.value }
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

    // The anime step. HELD while the card snaps: Default server or a picked
    // server closes the flow's state in the same frame the snap starts, and
    // the dust should be the step that was pressed, not the language row.
    val wantedStep = animeCardStep(
        isAnime           = isAnime,
        showServerPicker  = anime.showServerPicker,
        hasServers        = anime.serverOptions.isNotEmpty(),
        showDefaultPrompt = anime.showDefaultPrompt,
        checkingServers   = anime.isCheckingServers,
    )
    val cardIdle by remember(snap) { derivedStateOf { snap.phase == EpisodeSnapState.Phase.Idle } }
    val heldStep = remember { arrayOf(AnimeCardStep.Language) }
    val fromList = remember { booleanArrayOf(false) }
    // The language a server choice was for, so BACK lands on its button again.
    val pairLanguage = remember { intArrayOf(-1) }
    val step = if (!cardIdle && wantedStep == AnimeCardStep.Language) heldStep[0] else wantedStep
    SideEffect {
        if (wantedStep != AnimeCardStep.Language || cardIdle) heldStep[0] = wantedStep
        when (step) {
            AnimeCardStep.List -> fromList[0] = true
            AnimeCardStep.Language -> fromList[0] = false
            AnimeCardStep.Pair -> pairLanguage[0] = if (anime.pendingDub) 0 else 1
        }
    }
    val pairFocus = remember { List(2) { FocusRequester() } }

    val closeToTile: () -> Unit = {
        episodesState.selectedEpisodeOverlayIndex = -1
        val targetIndex = episodesState.lastFocusedEpisodeIndex
            .coerceIn(0, episodesState.focusRequesters.lastIndex)
        episodesState.focusRequesters[targetIndex].requestFocus()
    }

    // The snap: a Watch on ... press turns the card to dust, and the source
    // chain starts only once the dust is gone -- see EpisodeSnapState.

    // The whole card at OVERLAY_SCALE: a smaller density scales every dp and sp
    // inside it -- the still, the text and the buttons alike -- and centring
    // keeps it in the middle of whatever screen it is on.
    val density = LocalDensity.current
    val scaled = remember(density) { Density(density.density * OVERLAY_SCALE, density.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .episodeSnapCard(snap)
            .width(OVERLAY_WIDTH)
            .clip(OVERLAY_SHAPE)
            .background(OVERLAY_FACE)
            .border(1.dp, OVERLAY_EDGE, OVERLAY_SHAPE)
    ) {
        EpisodeOverlayHeader(
            stillUrl = fullSizeStill(selectedEpisode.stillImageUrl) ?: backdropUrl,
            season = season,
            episodeNumber = episodeNumber,
            title = selectedEpisode.title,
            airDate = formatEpisodeAirDate(selectedEpisode.airDate),
            passionFont = passionFont,
        )

        val overview = selectedEpisode.overview.orEmpty()
        // The server list takes the overview's place as well as the buttons'.
        if (overview.isNotBlank() && step != AnimeCardStep.List) {
            Text(
                text = overview,
                fontFamily = dongleRegular,
                fontSize = 16.sp,
                lineHeight = 11.5.sp,
                color = Color(0xFFE7E3E3),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }

        // The buttons sit 12 dp lower than the mockup's first cut (the user's
        // pick, 2026-10-27): 30 dp under the overview, 36 dp above the edge
        // (20.2 dp under the glow room, which holds the other 15.8).
        val buttonCount = if (isAnime) 2 else episodeSources.size.coerceAtMost(focusMap.providers.size)
        val rows = (buttonCount + 1) / 2
        val gridWidth = SOURCE_COLUMN_STEP + SOURCE_BUTTON_WIDTH
        // Every button here is 36 dp: the source buttons since v611, the
        // anime ones since v622.
        val extra = SOURCE_BUTTON_EXTRA_HEIGHT
        val gridHeight = (SOURCE_ROW_STEP + extra) * (rows - 1).coerceAtLeast(0) + SOURCE_BUTTON_ROOM + extra
        if (step == AnimeCardStep.List) {
            AnimeInlineServerList(
                servers      = anime.serverOptions,
                defaultIndex = anime.serverDefaultIdx,
                dub          = anime.pendingDub,
                onSelect     = { name -> anime.onServerPicked(name) },
                // Held from BEFORE the list goes, so focus never drops onto
                // the page behind the card (v587) -- see EpisodeSnapState.stepping.
                onBack       = { snap.holdFocusForStep(); anime.onPromptBack() },
                onFocusTaken = { snap.stepFocused() },
                modifier     = Modifier
                    .padding(top = 14.dp, bottom = 24.dp)
                    .padding(horizontal = 31.dp)
                    .width(gridWidth)
                    .focusGroup(),
            )
        } else Box(
            modifier = Modifier
                .padding(top = 30.dp, bottom = 36.dp - (SOURCE_BUTTON_ROOM - SOURCE_BUTTON_HEIGHT))
                .padding(horizontal = 31.dp)
                .size(width = gridWidth, height = gridHeight)
                .focusGroup()
        ) {
            if (isAnime && step == AnimeCardStep.Pair) {
                // Default server / Choose a server, where English / Japanese
                // were -- the same buttons and glow, faded in once the row's
                // dust is gone (v585).
                AnimeServerStepLabel(
                    dub = anime.pendingDub,
                    text = "which server?",
                    modifier = Modifier.offset(y = (-22).dp),
                )
                AnimeServerChoiceButtons(
                    dub             = anime.pendingDub,
                    passionFont     = passionFont,
                    focusRequesters = pairFocus,
                    startOn         = if (fromList[0]) 1 else 0,
                    onDefault       = { anime.onDefaultServer() },
                    onChoose        = { snap.holdFocusForStep(); anime.onChooseServer() },
                    onBack          = { snap.holdFocusForStep(); anime.onPromptBack() },
                    onFocusTaken    = { snap.stepFocused() },
                )
                Text(
                    text = "BACK: change language",
                    color = Color(0xFF8B8D9A),
                    fontFamily = AppTypography.interSemiBold,
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp),
                )
            } else if (isAnime) {
                // The row's own box, the glow's overhang included (12 dp each
                // side, 3 dp on top), so the dust takes the whole of both
                // buttons -- see RowSnapState. Centred over the grid by
                // requiredSize, the buttons padded back to where they were.
                Box(
                    Modifier
                        .requiredSize(gridWidth + 24.dp, gridHeight + 6.dp)
                        .rowSnap(snap.row)
                ) {
                Box(Modifier.padding(start = 12.dp, top = 3.dp).size(gridWidth, gridHeight)) {
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
                        stopTrailer()
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
                        // The row turns to dust, then the server choice fades in.
                        snap.row.snapThen { anime.showDefaultPrompt = true }
                    },
                    onSub = { url ->
                        val epName = selectedEpisode.title
                        stopTrailer()
                        // NOT marked watched here — same reason as onDub above.
                        focusMap.lastStream = focusMap.providers.getOrNull(1)
                        anime.pendingUrl         = url
                        anime.pendingDub         = false
                        episodesState.pendingSeason      = season
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        snap.row.snapThen { anime.showDefaultPrompt = true }
                    },
                    onUnavailable = onUnavailable,
                    onBack = closeToTile,
                    startOn = pairLanguage[0].coerceAtLeast(0),
                    onFocusTaken = { snap.stepFocused() },
                )
                }
                }
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
                        snap.snapThen { sourcePlayback.start(source) }
                    },
                    onBack = closeToTile,
                )
            }
        }
    }
    }

    // The dust's surface, only from a press until the dust is gone -- see
    // EpisodeSnapRenderer.
    EpisodeSnapRendererWhenWanted(snap)
}

/**
 * [EpisodeSnapRenderer] while the snap wants it. Its own scope (v515): a
 * press turning the renderer on and off recomposes this, not the overlay.
 */
@Composable
private fun EpisodeSnapRendererWhenWanted(snap: EpisodeSnapState) {
    if (snap.rendererWanted || snap.row.rendererWanted) EpisodeSnapRenderer(snap)
}

/**
 * The top of the card: the still, fading into the card from 45% down, with
 * the title (and its Watch-button label shadow) and "Season 6 · Episode 7 ·
 * 11/25/2007" over the faded part.
 */
@Composable
private fun EpisodeOverlayHeader(
    stillUrl: String?,
    season: Int,
    episodeNumber: Int,
    title: String,
    airDate: String,
    passionFont: FontFamily,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(OVERLAY_STILL_HEIGHT)
    ) {
        AsyncImage(
            model = stillUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(StillFade))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 3.dp)
        ) {
            Text(
                text = title,
                fontFamily = passionFont,
                fontSize = 22.sp,
                lineHeight = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The Watch buttons' label shadow -- see ButtonLabelShadow.
                style = TextStyle(shadow = ButtonLabelShadow),
            )
            Text(
                text = listOfNotNull(
                    "Season $season",
                    "Episode $episodeNumber",
                    airDate.takeIf { it.isNotBlank() },
                ).joinToString("  ·  "),
                fontFamily = AppTypography.interSemiBold,
                fontSize = 9.5.sp,
                color = Color(0xFFBEC0CD),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The "Play / from Website N" buttons, two to a row; focus starts on the first. */
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
            text = "Play",
            label = streamSourceLabel(source.coverName),
            extraHeight = SOURCE_BUTTON_EXTRA_HEIGHT,
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
 * One of the overlay's buttons, at grid cell [index] (two to a row; a lone
 * last button is centred under the others): the movie page's Watch button.
 * The arrows move between [focusRequesters]; at an edge they are consumed
 * with no move and no sound, so focus cannot wander onto the page behind.
 * Back is [onBack].
 */
@Composable
internal fun OverlayWatchButton(
    text: String,
    /** The whole label, for the source buttons' two lines; null is [text] alone, centred. */
    label: ModularButtonTextConfig? = null,
    /** Added to the face, strip offset, glow and row step: SOURCE_BUTTON_EXTRA_HEIGHT for the source buttons. */
    extraHeight: Dp = 0.dp,
    colors: ModularButtonColors,
    passionFont: FontFamily,
    focusRequester: FocusRequester,
    index: Int,
    focusRequesters: List<FocusRequester>,
    onBack: () -> Unit,
    onClick: () -> Unit,
) {
    val total = focusRequesters.size
    ModularButton(
        textConfig = label ?: ModularButtonTextConfig(text = text, fontSize = 14f, shadow = ButtonLabelShadow),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(
                x = if (index % 2 == 0 && index == total - 1) SOURCE_COLUMN_STEP / 2 else SOURCE_COLUMN_STEP * (index % 2),
                y = (SOURCE_ROW_STEP + extraHeight) * (index / 2),
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val target = when (event.key) {
                    Key.DirectionLeft  -> if (index % 2 == 1) index - 1 else -1
                    Key.DirectionRight -> if (index % 2 == 0 && index + 1 < total) index + 1 else -1
                    Key.DirectionUp    -> index - 2
                    // From the right column above a lone last button, Down goes to it.
                    Key.DirectionDown  -> if (total % 2 == 1 && index == total - 2) total - 1 else index + 2
                    Key.Back -> { onBack(); return@onPreviewKeyEvent true }   // consume so Activity back doesn't fire
                    else -> return@onPreviewKeyEvent false
                }
                focusRequesters.getOrNull(target)?.let {
                    it.requestFocus()
                    BlueHiveApplication.playHoverSound()
                }
                true
            },
        dimensions = ModularButtonDimensions(
            mainWidth = SOURCE_BUTTON_WIDTH, mainHeight = 33.dp + extraHeight, mainYOffset = 0.7.dp,
            secondWidth = SOURCE_BUTTON_WIDTH, secondHeight = 22.dp, secondYOffset = 18.dp + extraHeight,
            shadowHeight = 12.dp, glowWidth = SOURCE_BUTTON_WIDTH + 23.8.dp, glowHeight = 55.8.dp + extraHeight,
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
