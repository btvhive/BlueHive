package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.playback.StreamSource
import androidx.compose.ui.platform.LocalDensity
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.details.shared.playback.AnimePlaybackController
import com.example.bluehive.details.shared.playback.SourcePlaybackController

// =====================================================================================================================>  Episode overlay section
@Composable
internal fun EpisodeProviderInlineButtons(
    passionFont: FontFamily,
    sources: List<StreamSource>,
    focusRequesters: List<FocusRequester>,
    onSourceSelected: (StreamSource) -> Unit,
    onBack: () -> Unit,   // ✅ NEW
) {
    val safeSources = remember {
        mutableStateListOf<StreamSource>()
    }

    LaunchedEffect(sources) {
        safeSources.clear()
        // Cap to the focus-requester pool so we never index past it; the pool is
        // sized to the source list, so in practice this keeps every source.
        safeSources.addAll(sources.take(focusRequesters.size))
    }

    LaunchedEffect(safeSources.size) {
        if (safeSources.isNotEmpty()) {
            // give Compose a frame to attach focus nodes
            delay(50)
            focusRequesters.firstOrNull()?.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-6).dp),
        modifier = Modifier
            .wrapContentWidth()
            .offset(x = 0.3.dp) // how far left or right the entire section of button goes!!!
            .focusGroup()
    ) {
        // Two per row, laid out FROM the source list — adding/removing a source
        // now just adds/removes a button (an odd count leaves a lone left button)
        // with no per-index Row block to keep in sync.
        safeSources.chunked(2).forEachIndexed { rowIndex, rowSources ->
            Row(
                horizontalArrangement = Arrangement.spacedBy((-2).dp),
                // Wrap to content (NOT fillMaxWidth): the Column's CenterHorizontally then
                // centers a lone trailing button UNDER the 2-wide grid. With fillMaxWidth
                // the row spanned the whole overlay, so centering threw it far right; the
                // full 2-button rows are the widest, so they stay anchored at the left.
                modifier = Modifier.wrapContentWidth()
            ) {
                rowSources.forEachIndexed { colIndex, source ->
                    val i = rowIndex * 2 + colIndex
                    EpisodeOverlayStreamerButton(
                        index = i,
                        total = safeSources.size,
                        // right column nudged left the same 1.5.dp as before
                        offsetX = if (colIndex == 0) (-1).dp else (-1.5).dp,
                        source = source,
                        passionFont = passionFont,
                        focusRequesters = focusRequesters,
                        onClick = { onSourceSelected(source) },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}




@Composable
private fun EpisodeOverlayStreamerButton(
    index: Int,
    total: Int,                       // total buttons in the grid — drives edge-aware D-pad nav
    offsetX: Dp = (-1).dp,
    source: StreamSource,
    passionFont: FontFamily,
    focusRequesters: List<FocusRequester>,
    onClick: () -> Unit,
    onBack: () -> Unit,
) {
    // 🔹 manual x tweak just for certain names
    val labelOffsetX = when (source.name) {
        "VidEasy" -> (2).dp   // a little left
        "VidRock" -> (2).dp   // same tweak
        else -> 0.dp
    }


    val glowWidth= when (source.name) {
        "VidEasy" -> (96).dp   // a little width
        "VidRock" -> (96).dp   // same tweak
        else -> 96.dp
    }

    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = "Watch on ${source.name}",
            fontSize = 9f,
            offsetX = labelOffsetX
        ),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequesters[index],
        modifier = Modifier
            .offset(x = offsetX)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 2-column grid: col = index % 2, row = index / 2. Targets are
                // computed so the nav is correct for ANY button count (adding a
                // source can't leave a stale index mapping behind). -1 = edge → the
                // key is consumed with no move and no sound, exactly as before.
                when (event.key) {
                    Key.DirectionLeft -> {
                        val target = if (index % 2 == 1) index - 1 else -1   // right col → left neighbour
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()   // 🔊 play on successful move
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        val target = if (index % 2 == 0 && index + 1 < total) index + 1 else -1  // left col → right neighbour
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        // Right column (and a lone trailing button): RIGHT is a
                        // dead end again now the corner button is gone.
                        true
                    }

                    Key.DirectionUp -> {
                        val target = index - 2                               // one row up, same column
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        var target = index + 2                               // one row down, same column
                        // Lone trailing button (odd source count — e.g. VidApi centered under
                        // the 2x2 grid): the right-column button directly above it (index
                        // total-2, e.g. VidLink) has no same-column neighbour below, so route
                        // DOWN to the lone button instead of dead-ending with no move.
                        if (total % 2 == 1 && index == total - 2) {
                            target = total - 1
                        }
                        if (target < total) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        // Bottom row: nothing below it inside the grid, so DOWN
                        // is consumed with no move.
                        true
                    }

                    Key.Back -> {
                        onBack()
                        true   // consume so Activity back doesn't fire
                    }

                    else -> false
                }
            },
        // just with your narrow width so 2-per-row still fit
        dimensions = ModularButtonDimensions(
            mainWidth = 83.dp,
            mainHeight = 23.62.dp,      // was 23.625.dp
            mainYOffset = 0.7.dp,
            secondWidth = 83.dp,
            secondHeight = 22.5.dp,    // was 21.dp
            secondYOffset = 6.5.dp,   // was 6.5.dp
            shadowHeight = 6.dp,    // was 6.dp
            glowWidth = glowWidth,
            glowHeight = 41.5.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared with the movie screen — see StreamSourceButtonColors in
        // utilities/ModularButton.kt. Change the blues there, not here.
        colors = StreamSourceButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            // keep using your narrow glow asset
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-6.5).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the movie screen — see WatchButtonAnimation
        // in utilities/ModularButton.kt. Only the offsets are per-screen, being
        // tuned to this button's smaller size.
        animationConfig = WatchButtonAnimation.copy(
            pressOffset = 2.0.dp,
            textOffsetDefault = 0.6.dp,
            textOffsetPressed = 1.6.dp,
        ),
        // No playClickSound() here: ModularButton already plays it, immediately,
        // and this second call fired a duplicate. Harmless while the two landed
        // on the same frame — audible as a real double-click once onClick became
        // deferred by clickDelayMillis.
        onClick = onClick
    )
}
// =====================================================================================================================> Episode overlay section

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

/**
 * The "Watch on ..." buttons under the tile whose source overlay is open,
 * placed at the still's position: English / Japanese for an anime, else the
 * source grid. A press records the episode in [episodesState] and starts the
 * anime flow or the source chain; Back closes the overlay and returns to the
 * tile.
 *
 * Moved here from the screen in Stage 8 of the details redesign.
 */
@Composable
internal fun EpisodeSourceOverlay(
    extras: ExtrasState,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    anime: AnimePlaybackController,
    sourcePlayback: SourcePlaybackController,
    mediaType: String,
    mediaId: Int,
    genres: String?,
    originalLanguage: String?,
    passionFont: FontFamily,
    stopTrailerAndSelectEpisode: (season: Int, episode: Int, epName: String?) -> Unit,
    /** A brand-new anime with no AniList mapping yet: the screen shows its notice. */
    onUnavailable: () -> Unit,
) {
    // ✅ Position of the selected episode still in root coordinates
    val density = LocalDensity.current

    if (
        !extras.isOn &&
        episodesState.selectedEpisodeOverlayIndex != -1 &&
        episodesState.overlayAnchor != null &&
        episodesState.selectedEpisodeOverlayIndex in episodesState.episodes.indices
    ) {
        val anchor = episodesState.overlayAnchor!!

        val selectedEpisode = episodesState.episodes[episodesState.selectedEpisodeOverlayIndex]

        // fall back to index+1 if episodeNumber is null
        val episodeNumber = selectedEpisode.episodeNumber

        // Build sources for this episode.
        // Japanese animation → 2 buttons (Miruro + VidFast); else the standard set.
        val episodeSources by produceState(
            initialValue = emptyList(),
            mediaType, mediaId, episodesState.selectedSeason, episodeNumber, genres, originalLanguage
        ) {
            if (isJapaneseAnimation(genres, originalLanguage)) {
                value = buildJapaneseAnimationSources(
                    mediaType     = mediaType,
                    tmdbId        = mediaId,
                    seasonNumber  = episodesState.selectedSeason,
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
                value = getStreamingSources(
                    mediaType = mediaType,
                    mediaId = mediaId,
                    seasonNumber = episodesState.selectedSeason,
                    episodeNumber = episodeNumber
                )
                com.example.bluehive.api.SourceCatalog
                    .warm(BlueHiveApplication.appContext ?: return@produceState)
                value = getStreamingSources(
                    mediaType = mediaType,
                    mediaId = mediaId,
                    seasonNumber = episodesState.selectedSeason,
                    episodeNumber = episodeNumber
                )
            }
        }

        val gridOffsetX = with(density) { anchor.x.toDp() }
        val gridOffsetY = with(density) {
            (anchor.y + 97.5.dp.toPx() + 12.dp.toPx()).toDp()
        }

        Box(
            modifier = Modifier
                .offset(x = gridOffsetX, y = gridOffsetY)
        ) {
            if (isJapaneseAnimation(genres, originalLanguage)) {
                // Anime: exactly DuB / SuB. DuB+SuB run the Miruro flow
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
                        stopTrailerAndSelectEpisode(episodesState.selectedSeason, episodeNumber, epName)
                        // NOT marked watched here. Picking DuB only opens the
                        // server prompt — extraction can still fail, find no
                        // servers, or be cancelled. The mark happens where the
                        // stream actually plays (see the extractor result
                        // handler, alongside logWatchHistory).
                        focusMap.lastStream = focusMap.providers.getOrNull(0)
                        anime.pendingUrl         = url
                        anime.pendingDub         = true
                        episodesState.pendingSeason      = episodesState.selectedSeason
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        anime.showDefaultPrompt  = true
                    },
                    onSub = { url ->
                        val epName = selectedEpisode.title
                        stopTrailerAndSelectEpisode(episodesState.selectedSeason, episodeNumber, epName)
                        // NOT marked watched here — same reason as onDub above.
                        focusMap.lastStream = focusMap.providers.getOrNull(1)
                        anime.pendingUrl         = url
                        anime.pendingDub         = false
                        episodesState.pendingSeason      = episodesState.selectedSeason
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        anime.showDefaultPrompt  = true
                    },
                    onUnavailable = onUnavailable,
                    onBack = {
                        episodesState.selectedEpisodeOverlayIndex = -1
                        val targetIndex = episodesState.lastFocusedEpisodeIndex
                            .coerceIn(0, episodesState.focusRequesters.lastIndex)
                        episodesState.focusRequesters[targetIndex].requestFocus()
                    }
                )
            } else {
                EpisodeProviderInlineButtons(
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
                        // same chain as a dead one. The episode overlay STAYS
                        // open behind the player, which keeps this button
                        // attached so focus can return to it on the way back.
                        episodesState.pendingSeason      = episodesState.selectedSeason
                        episodesState.pendingEpisode     = episodeNumber
                        episodesState.pendingEpisodeName = epName
                        sourcePlayback.start(source)
                    },
                    onBack = {
                        episodesState.selectedEpisodeOverlayIndex = -1

                        val targetIndex = episodesState.lastFocusedEpisodeIndex
                            .coerceIn(0, episodesState.focusRequesters.lastIndex)

                        episodesState.focusRequesters[targetIndex].requestFocus()
                    },
                )
            }
        }
    }
}
