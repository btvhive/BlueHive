package com.example.bluehive.details.moviesdetails

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import com.example.bluehive.searchBarComponent.hiveBackdropBlur
import com.example.bluehive.searchBarComponent.openSearchScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.focusRequester
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.webview.BeeLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.android.awaitFrame
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.playback.AnimeServerOverlays
import com.example.bluehive.details.shared.playback.PlaybackErrorOverlay
import com.example.bluehive.details.shared.playback.PlaybackLoadingOverlay
import com.example.bluehive.details.shared.playback.PlaybackRequest
import com.example.bluehive.details.shared.playback.StreamSource
import com.example.bluehive.details.shared.playback.rememberAnimePlayback
import com.example.bluehive.details.shared.playback.rememberPlaybackSession
import com.example.bluehive.details.shared.playback.rememberSourcePlayback
import com.example.bluehive.details.shared.layout.DetailsHeaderSpec
import com.example.bluehive.details.shared.layout.DetailsInfo
import com.example.bluehive.details.shared.layout.rememberDetailsPeek
import com.example.bluehive.details.shared.layout.PEEK_RIGHT_COLUMN_X
import com.example.bluehive.details.shared.layout.PEEK_SOURCE_ROW_STEP
import com.example.bluehive.details.shared.layout.peekExtraSourceRows
import com.example.bluehive.details.shared.layout.DetailsPressTiming
import com.example.bluehive.details.shared.layout.DetailsCacheStats
import com.example.bluehive.details.shared.layout.DetailsScaffold
import com.example.bluehive.details.shared.layout.recommendedRowTitle
import com.example.bluehive.details.shared.layout.DetailsSearchKeyboard
import com.example.bluehive.details.shared.layout.rememberDetailsSearch
import com.example.bluehive.details.shared.layout.PeekRowStart
import com.example.bluehive.details.shared.layout.rememberDetailsHeaderGate
import com.example.bluehive.details.shared.layout.detailsGenresTop
import com.example.bluehive.details.shared.layout.DetailsToast
import com.example.bluehive.details.shared.layout.TimedDetailsToast
import com.example.bluehive.details.shared.layout.rememberTimedNotice
import com.example.bluehive.details.shared.layout.TrailerFailedNotice
import com.example.bluehive.details.shared.state.DetailsBackHandlers
import com.example.bluehive.details.shared.state.rememberExtrasState
import com.example.bluehive.details.shared.state.rememberFavoriteState
import com.example.bluehive.details.shared.state.rememberTrailerState


/**
 * The source buttons for this film. Backend-owned — see the twin on the TV
 * screen for the full note, and SourceCatalog for the fallback chain.
 */
private fun getStreamingSources(mediaType: String, mediaId: Int): List<StreamSource> {
    if (mediaType.equals("movie", true)) {
        return com.example.bluehive.api.SourceCatalog
            .sources(BlueHiveApplication.appContext, "movie", mediaId)
            .map { StreamSource(it.label, it.name, it.url) }
    }
    // Only a title of the other type gets here, and it has no buttons on
    // this screen. The hard-coded list that followed could never run and
    // was removed in Stage 7 of the details redesign; the same sources are
    // SourceCatalog's built-ins.
    return emptyList()
}







/**
 * How the header buttons press on this screen: slower than the TV screen's
 * 110/80 ms. Kept apart until that is decided (architecture §10, #1).
 */
private val MovieHeaderPressTiming = DetailsPressTiming(durationMillis = 180, bounceBackDelayMillis = 140)

/**
 * The movie details screen. MoviesDetailsScreenCompose opens it with what the
 * page was launched with (MovieDetailsArgs).
 *
 * Moved here from the Activity's file in Stage 9 of the details redesign.
 */
@UnstableApi
@Composable
fun MovieDetailsScreen(
    args: MovieDetailsArgs,
    recommendationsRepository: RecommendationsRepository,
    onMediaClick: (MediaItem) -> Unit
) {
    // Load custom fonts
    val passionFont = AppTypography.passionRegular
    val dongleRegular = AppTypography.dongleRegular
    val context = LocalContext.current

    // Decode the loading-overlay bee logo now, while the screen is first
    // composing and memory is comparatively free, and cache it process-wide so
    // the Dub/Sub overlay never decodes it under pressure (the 2 GB crash).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BeeLogo.prime(context) }
    }

    val coroutineScope = rememberCoroutineScope()

    // The recommendations row -- see ExtrasState. This screen has no Extras
    // switch any more: the peek layout (DetailsPeekState) shows the row under
    // the play buttons from the start, so it loads on open, on a background
    // thread. Extras itself never turns on here.
    val extras = rememberExtrasState(
        scope            = coroutineScope,
        repository       = recommendationsRepository,
        tmdbId           = args.mediaId,
        mediaType        = args.mediaType,
        startInExtras    = false,
        loadInBackground = true,
        prefetched       = null,
    )

    // Opened from history in Extras: the row takes focus as it loads, so the
    // page opens slid up on it.
    val peek = rememberDetailsPeek(startFocused = args.startInExtras)
    // The play button Down entered the row from, for Up to return to.
    var rowEnteredFrom by remember { mutableStateOf<FocusRequester?>(null) }

    // The Search button's keyboard, over this page -- see DetailsSearchKeyboard.
    val search = rememberDetailsSearch()

    val headerGate = rememberDetailsHeaderGate(blocked = args.startInExtras)

    // The backdrop trailer: playing, started, extracting, its player -- see
    // TrailerState.
    val trailer = rememberTrailerState(args.trailerUrl)

    /**
     * Silence the backdrop trailer. Called where a playback ACTUALLY starts, and
     * deliberately NOT when a source button merely opens a prompt.
     *
     * WHY THE DISTINCTION. This screen used to clear the flags in each source
     * button's onClick, so the trailer died the instant you pressed VidFast even
     * though the next thing on screen was "Watch with our player, or open the
     * site directly?" — the backdrop went silent and static while you were still
     * choosing. The TV screen never behaved that way: a backend source only sets
     * its choice state and the trailer plays on until something really launches.
     * This matches it.
     *
     * Idempotent, so a path crossing two launch points costs nothing.
     */
    fun stopTrailer() {
        trailer.stop()
    }

    // Focus requesters
    val favoriteFocusRequester = remember { FocusRequester() }
    val extrasFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }

    // The page's loading screen, shared by the source chain and the anime
    // flow: extracting, pending, stage, the job BACK cancels -- see
    // PlaybackSession.
    val session = rememberPlaybackSession()
    // Shown when this anime movie has no AniList mapping yet, so there is no Miruro
    // URL to extract from. Without this, DuB/SuB were a SILENT no-op — the guard
    // below just found sources.size <= 2 and did nothing at all.
    val notYetAvailable = rememberTimedNotice()

    // Named requesters for DuB/Sub so we know exactly where to send focus back.
    val animeDubFocusRequester = remember { FocusRequester() }
    val animeSubFocusRequester = remember { FocusRequester() }
    // Whichever button the user pressed last — restored when any overlay closes.
    var lastStreamFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

    val favoriteProfileId = args.profileId

    // The anime flow: the DuB/SuB prompt and the server picker, the extraction
    // (backend first, the on-device extractor as the fallback) and the
    // automatic failover through the server list -- see AnimePlaybackController.
    val anime = rememberAnimePlayback(
        session      = session,
        scope        = coroutineScope,
        profileId    = favoriteProfileId,
        mediaType    = args.mediaType,
        mediaTitle   = args.mediaTitle,
        request      = { PlaybackRequest.Movie(args.mediaId) },
        onLaunch     = { stopTrailer() },
        restoreFocus = {
            coroutineScope.launch {
                awaitFrame()
                try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
            }
        },
    )

    // THE RULE: back out of a player and focus returns to the streaming button that
    // launched it, never the Favorites button at the top.
    //
    // ...but YIELD while any modal is up. "Choose a server" on a cache MISS launches
    // MiruroExtractorActivity to enumerate, so this screen genuinely pauses and
    // RESUMES — and the resume that delivers the server list is the same resume this
    // restoration fires on. It won that race, pulling focus onto the DuB/Sub button
    // while the picker sat open on top, so the D-pad drove the details screen behind
    // the overlay.
    //
    // It reads as episode-specific because the SECOND press hits the server-list cache and
    // opens the picker inline — no activity launch, no resume, no race.
    //
    // extractionError is in the list for the same reason: that overlay appears on the
    // very resume this fires on. The TV screen already carried both guards; this one
    // carried neither.
    RestoreFocusOnPlayerReturn("MovieDetailsFocus") {
        if (session.extractionError != null) return@RestoreFocusOnPlayerReturn emptyList()
        if (anime.showServerPicker || anime.isCheckingServers || anime.showDefaultPrompt) {
            return@RestoreFocusOnPlayerReturn emptyList()
        }
        listOfNotNull(lastStreamFocusRequester)
    }

    // Favorites: checked on open, toggled by the header button -- see
    // FavoriteState. This screen sends no title with a toggle (§10, #3).
    val favorite = rememberFavoriteState(
        scope     = coroutineScope,
        profileId = favoriteProfileId,
        tmdbId    = args.mediaId,
        mediaType = args.mediaType,
        title     = null,
    )

    // The source chain: a "Watch on ..." press, the backend extraction, the
    // player, and both failover halves -- see SourcePlaybackController.
    val sourcePlayback = rememberSourcePlayback(
        session    = session,
        scope      = coroutineScope,
        profileId  = favoriteProfileId,
        mediaType  = args.mediaType,
        mediaTitle = args.mediaTitle,
        request    = { PlaybackRequest.Movie(args.mediaId) },
        onStart    = { stopTrailer() },
    )


    // Back: Extras collapses, the server prompt closes, an extraction cancels
    // -- one call, after every holder, registered in that order (C2). See
    // DetailsBackHandlers.
    DetailsBackHandlers(
        extras  = extras,
        anime   = anime,
        session = session,
        onExtractionCancelled = {
            coroutineScope.launch {
                awaitFrame()
                try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
            }
        },
    )


    // Get streaming sources based on media type.
    // Japanese animation (anime) → dedicated DuB / SuB set.
    // Everything else keeps the standard 6 buttons.
    val isAnime = remember(args.genres, args.originalLanguage) {
        val result = isJapaneseAnimation(args.genres, args.originalLanguage)
        Log.d("AnimeDetect", "genres='${args.genres}' | originalLanguage='${args.originalLanguage}' | isAnime=$result")
        result
    }
    val sources by produceState(
        initialValue = if (isAnime) emptyList()
        else getStreamingSources(args.mediaType, args.mediaId),
        args.mediaType, args.mediaId, isAnime
    ) {
        if (isAnime) {
            value = buildJapaneseAnimationSources(
                mediaType     = args.mediaType,
                tmdbId        = args.mediaId,
                seasonNumber  = null,
                episodeNumber = null,
            ) { c, n, u -> StreamSource(c, n, u) }
        }
        else {
            // initialValue already holds the cached list, so the buttons are
            // already on screen. Refresh and re-read so a source switched off on
            // the server reaches a session that never backgrounds. Throttled
            // inside warm(); usually a no-op.
            com.example.bluehive.api.SourceCatalog
                .warm(BlueHiveApplication.appContext ?: return@produceState)
            value = getStreamingSources(args.mediaType, args.mediaId)
        }
    }

    /**
     * A DuB/SuB press made before the AniList resolve landed. true = dub.
     *
     * WHY IT EXISTS: `sources` starts empty and fills asynchronously, so the
     * size check the buttons use could not tell "not resolved yet" from "this
     * title has no Miruro mapping" — both read as too few sources. A press in
     * that window reported the episode unavailable, and the same press a
     * second later worked. Empty means still loading; a resolved list always
     * carries VidFast, so it is never empty.
     */
    var queuedAnimePress by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(queuedAnimePress, sources) {
        val want = queuedAnimePress ?: return@LaunchedEffect
        if (sources.size > 2) {
            queuedAnimePress = null
            lastStreamFocusRequester =
                if (want) animeDubFocusRequester else animeSubFocusRequester
            anime.pendingUrl = sources[2].url
            anime.pendingDub = want
            anime.showDefaultPrompt = true
            return@LaunchedEffect
        }
        if (sources.isNotEmpty()) {          // resolved, and carries no Miruro
            queuedAnimePress = null
            notYetAvailable.show()
            return@LaunchedEffect
        }
        // Still resolving. Bounded, so a resolve that never completes cannot
        // leave the button looking dead.
        delay(8_000)
        if (queuedAnimePress != null) {
            queuedAnimePress = null
            notYetAvailable.show()
        }
    }

    // One requester per "Watch on ..." button, made here rather than in the
    // play area so Up from the row can reach them.
    val streamerFocus = remember(sources.size) {
        List(sources.size) { FocusRequester() }
    }
    LaunchedEffect(sources.size, isAnime) {
        peek.extraSourceRows = if (isAnime) 0 else peekExtraSourceRows(sources.size)
    }

    LaunchedEffect(sources) {
        Log.d(
            "scraper",
            "Movie sources resolved (${sources.size}): " +
                    sources.mapIndexed { index, source ->
                        "[$index] ${source.name} -> ${source.url}"
                    }.joinToString(" | ")
        )
    }


    // The image-cache stats -- see DetailsCacheStats.
    DetailsCacheStats("MoviesDetailsScreen", extras.recommendations)


    // The row loads; opened in it, the header waits for its titles -- see PeekRowStart.
    PeekRowStart(peek, extras, favoriteFocusRequester, headerGate)

    var displayBudget by remember { mutableLongStateOf(args.budget) }
    var displayRevenue by remember { mutableLongStateOf(args.revenue) }

    LaunchedEffect(args.mediaId, args.mediaType) {
        try {
            val detail = ApiClient.trailerApi.getMediaDetails(args.mediaId, args.mediaType)
            detail.budget?.let { if (it > 0) displayBudget = it }
            detail.revenue?.let { if (it > 0) displayRevenue = it }
        } catch (_: Exception) {}
    }




    // How many metadata lines this title has: the genres line follows the
    // last one. The lines themselves are built in MovieInfoLabels.
    val infoRowCount = movieInfoRowCount(displayRevenue, displayBudget, args.releaseDate, args.originalLanguage)

    // Main container with background: the frame both screens share, with this
    // screen's labels, play area and overlays plugged in -- see DetailsScaffold.
    DetailsScaffold(
        info = DetailsInfo(
            mediaTitle          = args.mediaTitle,
            logoUrl             = args.logoUrl,
            backdropUrl         = args.backdropUrl,
            backdropDescription = "Movie backdrop",
            contentRating       = args.contentRating,
            voteAverage         = args.voteAverage,
            genres              = args.genres,
            overview            = args.overview,
            genresTop           = detailsGenresTop(infoRowCount),
            rowTitle            = recommendedRowTitle(args.mediaType, args.genres, args.originalLanguage),
        ),
        trailer = trailer,
        favorite = favorite,
        extras = extras,
        header = DetailsHeaderSpec(
            pressTiming            = MovieHeaderPressTiming,
            enableFocus            = headerGate.isOpen,
            favoriteFocusRequester = favoriteFocusRequester,
            extrasFocusRequester   = extrasFocusRequester,
            trailerFocusRequester  = trailerFocusRequester,
            search                 = search,
        ),
        releaseTrailerOnLeave = false,
        onOpenRecommendation = { media ->
            trailer.stop()
            onMediaClick(media)
        },
        onUpFromRecommendations = {
            // Back to the play button Down came from; after opening straight
            // into the row, the bottom-left one.
            BlueHiveApplication.playHoverSound()
            val target = rowEnteredFrom
                ?: if (isAnime) animeDubFocusRequester
                else streamerFocus.getOrNull(((streamerFocus.size - 1) / 2) * 2)
            try { target?.requestFocus() } catch (_: Exception) {}
        },
        labels = {
            MovieInfoLabels(
                displayRevenue   = displayRevenue,
                displayBudget    = displayBudget,
                releaseDate      = args.releaseDate,
                originalLanguage = args.originalLanguage,
                dongleRegular    = dongleRegular,
            )
        },
        playArea = {
            // The Watch buttons -- see MovieSourceGrid.
            MovieSourceGrid(
                isAnime                = isAnime,
                sources                = sources,
                fontFamily             = passionFont,
                playTop                = peek.playTop,
                animeDubFocusRequester = animeDubFocusRequester,
                animeSubFocusRequester = animeSubFocusRequester,
                streamerFocus          = streamerFocus,
                onDownIntoRow          = { from ->
                    peek.rowEntry.focus().also { if (it) rowEnteredFrom = from }
                },
                onAnimePress           = { dub ->
                    // No stopTrailer() here: this only OPENS the DuB/SuB prompt.
                    // The trailer plays on until the prompt is answered, which
                    // is where a launch actually happens.
                    val name = if (dub) "DuB" else "SuB"
                    if (sources.size > 2) {
                        Log.d("scraper", "$name pressed → prompt: ${sources[2].url}")
                        lastStreamFocusRequester = if (dub) animeDubFocusRequester else animeSubFocusRequester
                        anime.pendingUrl = sources[2].url
                        anime.pendingDub = dub
                        anime.showDefaultPrompt = true
                    } else if (sources.isEmpty()) {
                        // Still resolving — honour the press when it lands.
                        queuedAnimePress = dub
                    } else {
                        // Resolved, and still no Miruro/M Extract source: this
                        // title genuinely has no AniList mapping yet. Say so
                        // instead of silently doing nothing.
                        Log.w("scraper", "$name pressed but no Miruro source (tmdb=${args.mediaId}) — unmapped")
                        notYetAvailable.show()
                    }
                },
                onSourcePress          = { from, source ->
                    // Remember which button launched this so focus comes back
                    // here when the player exits (THE RULE).
                    lastStreamFocusRequester = from
                    // Every source: server-side m3u8 extraction, then our own
                    // player (the overlay shows progress). The server reports
                    // every source as extractable; one it could not extract
                    // fails into the same chain as a dead one.
                    sourcePlayback.start(source)
                },
            )
        },
        overlays = {
            // Instant extraction overlay — last in Box so it renders on top of everything
            PlaybackLoadingOverlay(session)

            // "No dub available" toast
            if (anime.showNoDub) {
                DetailsToast("No dub available for this title", dongleRegular)
            }

            // ── "Not in our search engine yet" — no AniList mapping for this movie ──
            TimedDetailsToast(
                notYetAvailable,
                "This title isn't in our anime search engine yet — try VidFast below",
                dongleRegular,
                centered = true,
            )

            // Extraction failure the user can act on ("try another source").
            PlaybackErrorOverlay(session)

            // The anime flow's server overlays -- the "Checking servers" trap, the
            // default-server prompt, the picker and the "couldn't reach" notice,
            // in that order -- see AnimeServerOverlays.
            AnimeServerOverlays(anime, dongleRegular)

            // The trailer could not be loaded -- see TrailerFailedNotice.
            TrailerFailedNotice(trailer, dongleRegular)

            DetailsSearchKeyboard(search, favoriteProfileId)
        },
        peek = peek,
        modifier = Modifier.hiveBackdropBlur(search.isOpen),
    )
}   // ← MovieDetailsScreen closes here


//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x20000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)