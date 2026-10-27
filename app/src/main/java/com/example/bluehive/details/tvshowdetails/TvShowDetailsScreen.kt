package com.example.bluehive.details.tvshowdetails

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import kotlinx.coroutines.launch
import com.example.bluehive.repository.EpisodesRepository
import com.example.bluehive.models.Episode
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import com.example.bluehive.webview.BeeLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withContext
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.playback.AnimeServerOverlays
import com.example.bluehive.details.shared.playback.PlaybackErrorOverlay
import com.example.bluehive.details.shared.playback.PlaybackLoadingOverlay
import com.example.bluehive.details.shared.playback.rememberPlaybackSession
import com.example.bluehive.details.shared.playback.rememberAnimePlayback
import com.example.bluehive.details.shared.playback.PlaybackRequest
import com.example.bluehive.details.shared.playback.rememberSourcePlayback
import com.example.bluehive.details.shared.layout.DetailsHeaderSpec
import com.example.bluehive.details.shared.layout.DetailsInfo
import com.example.bluehive.details.shared.layout.DetailsPressTiming
import com.example.bluehive.details.shared.layout.rememberDetailsPeek
import com.example.bluehive.details.shared.layout.DetailsCacheStats
import com.example.bluehive.details.shared.layout.DetailsScaffold
import com.example.bluehive.details.shared.layout.DetailsSearchKeyboard
import com.example.bluehive.details.shared.layout.rememberDetailsSearch
import com.example.bluehive.details.shared.layout.PeekRowStart
import com.example.bluehive.details.shared.layout.rememberDetailsHeaderGate
import com.example.bluehive.details.shared.layout.detailsGenresTop
import com.example.bluehive.searchBarComponent.openSearchScreen
import com.example.bluehive.details.shared.layout.DetailsToast
import com.example.bluehive.details.shared.layout.TimedDetailsToast
import com.example.bluehive.details.shared.layout.rememberTimedNotice
import com.example.bluehive.details.shared.layout.TrailerFailedNotice
import com.example.bluehive.details.shared.state.DetailsBackHandlers
import com.example.bluehive.details.shared.state.rememberExtrasState
import com.example.bluehive.details.shared.state.rememberFavoriteState
import com.example.bluehive.details.shared.state.rememberTrailerState
import androidx.compose.ui.Modifier
import com.example.bluehive.searchBarComponent.hiveBackdropBlur


/**
 * How the header buttons press on this screen: quicker than the movie screen's
 * 180/140 ms. Kept apart until that is decided (architecture §10, #1).
 */
private val TvHeaderPressTiming = DetailsPressTiming(durationMillis = 110, bounceBackDelayMillis = 80)

/**
 * The TV details screen. TVShowsDetailsScreenCompose opens it with what the
 * page was launched with (TvShowDetailsArgs).
 *
 * Moved here from the Activity's file in Stage 9 of the details redesign.
 */
@SuppressLint("DefaultLocale")
@UnstableApi
@Composable
fun TvShowDetailsScreen(
    args: TvShowDetailsArgs,
    recommendationsRepository: RecommendationsRepository,
    episodesRepository: EpisodesRepository,
    onEpisodeSelected: (season: Int, episode: Int, episodeName: String?) -> Unit = { _, _, _ -> },
    onMediaClick: (MediaItem) -> Unit
) {
    // Load custom fonts
    val passionFont = AppTypography.passionRegular
    val dongleRegular = AppTypography.dongleRegular
    val context = LocalContext.current

    val app = context.applicationContext as? BlueHiveApplication
    val prefetch = remember(args.mediaId) { app?.consumePrefetch(args.mediaId) }

    // Decode the loading-overlay bee logo now, while the screen is first
    // composing and memory is comparatively free, and cache it process-wide so
    // the Dub/Sub overlay never decodes it under pressure (the 2 GB crash).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BeeLogo.prime(context) }
    }


    val coroutineScope = rememberCoroutineScope()

    // The recommendations row -- see ExtrasState. This screen has no Extras
    // switch any more: the peek layout (DetailsPeekState) shows the row under
    // the episodes from the start, so it loads on open, on a background
    // thread, unless the page opened with another show's prefetched row.
    // Extras itself never turns on here.
    val extras = rememberExtrasState(
        scope            = coroutineScope,
        repository       = recommendationsRepository,
        tmdbId           = args.mediaId,
        mediaType        = args.mediaType,
        startInExtras    = false,
        loadInBackground = false,
        prefetched       = prefetch?.recommendations,
    )

    // Opened from history in Extras: the row takes focus as it loads, so the
    // page opens slid up on it. The row sits lower than the movie page's,
    // under the season cap (TV_PEEK_ROW_TOP).
    val peek = rememberDetailsPeek(startFocused = args.startInExtras, baseRowTop = TV_PEEK_ROW_TOP, baseSlide = TV_PEEK_SLIDE)

    // The Search button's keyboard, over this page -- see DetailsSearchKeyboard.
    val search = rememberDetailsSearch()

    // First focus off the header while the page opens in the row or on an
    // episode -- see DetailsHeaderGate.
    val headerGate = rememberDetailsHeaderGate(blocked = args.startInExtras || args.resumeToEpisode)


    // The season on show, its episodes, the row's scroll and focus, the open
    // source overlay and the watched marks -- see EpisodesState. The focus
    // requesters the sections share -- see TvFocusMap.
    val episodesState = rememberEpisodesState(coroutineScope, episodesRepository, args.mediaId)
    val focusMap = rememberTvFocusMap {
        getStreamingSources(args.mediaType, args.mediaId, 1, 1).size.coerceAtLeast(2)
    }

    // Which unaired episode has been pressed once -- see UnairedGate.
    val unairedGate = rememberUnairedGate()



    // The backdrop trailer: playing, started, extracting, its player -- see
    // TrailerState.
    val trailer = rememberTrailerState(args.trailerUrl)

    /**
     * Silence the backdrop trailer, then record the episode being watched.
     *
     * EVERY PLAYBACK LAUNCH ON THIS SCREEN GOES THROUGH HERE, and that is the
     * point: the reset used to be missing entirely, so starting a trailer and
     * then picking an episode left the trailer playing UNDERNEATH the stream for
     * the whole episode. Nothing arbitrates audio focus anywhere in this app
     * (no requestAudioFocus / AudioManager in the tree), so the two genuinely
     * mix, and a second ExoPlayer stays allocated behind the stream player — on
     * a 2GB box that is the shape of thing that has OOM-killed us before.
     *
     * The movies screen has always reset both flags inline at each of its five
     * launch sites. This screen has five launch sites too and reset at none of
     * them. Wrapping rather than copying that pattern means a sixth path cannot
     * quietly forget: onEpisodeSelected is what a launch path must call anyway.
     *
     * trailer.stop() clears isPlaying, which DetailsTrailerPlayer pauses on,
     * and hasPlayed, which hides the player view so the backdrop image returns.
     */
    fun stopTrailerAndSelectEpisode(season: Int, episode: Int, epName: String?) {
        trailer.stop()
        onEpisodeSelected(season, episode, epName)
    }

    // A first press on an unaired episode stays armed for
    // UNAIRED_OVERRIDE_WINDOW_MS -- see UnairedPressTimeout.
    UnairedPressTimeout(unairedGate)

    // Today's date and the season list are both re-read every time this screen
    // comes back into view, so an episode or a season that airs while the app
    // is open unlocks without reopening the show.
    val resumeCount = rememberResumeCount()

    // Today's date, read once per resume. Per card it would be the same answer
    // recomputed on every recomposition of a scrolling row.
    val todayYmd = remember(resumeCount) { currentYmd() }

    // How many seasons the season column lists, from Season 1: the newest ones
    // with nothing aired yet are left off. Null until the first check answers.
    val listedSeasons = rememberListedSeasons(args.mediaId, args.numberOfSeasons, resumeCount, episodesRepository)

    // The first focus when the page opens -- see FirstEpisodeFocus.
    FirstEpisodeFocus(
        episodesState    = episodesState,
        focusMap         = focusMap,
        resumeToEpisode  = args.resumeToEpisode,
        lastEpisode      = args.lastEpisode,
        headerGate       = headerGate,
    )


    // The row loads; opened in it, the header waits for its titles -- see PeekRowStart.
    PeekRowStart(peek, extras, focusMap.favorite, headerGate)

    val favoriteProfileId = args.profileId

    // Favorites: checked on open, toggled by the header button -- see
    // FavoriteState. This screen sends the title with a toggle (§10, #3).
    val favorite = rememberFavoriteState(
        scope     = coroutineScope,
        profileId = favoriteProfileId,
        tmdbId    = args.mediaId,
        mediaType = args.mediaType,
        title     = args.mediaTitle,
    )

    // The watched marks, read on open and on every resume -- see
    // WatchedEpisodesEffects.
    WatchedEpisodesEffects(episodesState, args.mediaId, favoriteProfileId, coroutineScope)

    // ════════════════════════════════════════════════════════════════════════
    //  Anime DuB / SuB extraction flow (ported from MoviesDetailsScreenCompose).
    //  The Miruro source URL already carries ?ep=N, so the extractor clicks the
    //  right episode itself — we just feed it the URL like the movie screen does.
    // ════════════════════════════════════════════════════════════════════════
    // The page's loading screen, shared by the source chain and the anime
    // flow: extracting, pending, stage, the job BACK cancels -- see
    // PlaybackSession.
    val session = rememberPlaybackSession()

    // The episode overlay's snap -- see EpisodeSnapState. Here, not in the
    // overlay, so the page can refuse focus while it runs.
    val episodeSnap = rememberEpisodeSnap(session)

    // Shown when an anime's AniList mapping doesn't exist yet (resolveAnime 404 →
    // miruroUrl null). Brand-new titles aren't in anime_id_map until AniBridge
    // ingests them, so instead of leaving DuB/SuB inert we tell the user why.
    val notYetAvailable = rememberTimedNotice()

    // The anime flow: the DuB/SuB prompt and the server picker, the extraction
    // (backend first, the on-device extractor as the fallback) and the
    // automatic failover through the server list -- see AnimePlaybackController.
    // What is played is the episode the press recorded in pendingSeason /
    // pendingEpisode; English and Japanese stop the trailer on the press, so
    // nothing more is stopped at launch (§10).
    val anime = rememberAnimePlayback(
        session      = session,
        scope        = coroutineScope,
        profileId    = favoriteProfileId,
        mediaType    = args.mediaType,
        mediaTitle   = args.mediaTitle,
        request      = { PlaybackRequest.Episode(args.mediaId, episodesState.pendingSeason, episodesState.pendingEpisode, episodesState.pendingEpisodeName) },
        onLaunch     = {},
        restoreFocus = {
            coroutineScope.launch {
                awaitFrame()
                try { focusMap.lastStream?.requestFocus() } catch (_: Exception) {}
            }
        },
    )

    // THE RULE: back out of a player and focus returns to the streaming button that
    // launched it, never the Favorites button at the top. Every streaming button's
    // onClick sets lastStreamFocusRequester; this restores it on resume.
    //
    // Second anchor: the VidFast flow closes the episode overlay before launching,
    // so that button is gone on return — fall back to its episode tile, which is
    // one OK press from the same buttons (and nowhere near the header).
    //
    // Yields entirely while the error overlay is up. That overlay appears on the
    // SAME resume this restoration fires on — the extractor activity closing is
    // what triggers both — so the two raced and this one won, dragging focus out
    // of the modal and back onto the details screen behind it.
    RestoreFocusOnPlayerReturn("TVDetailsFocus") {
        if (session.extractionError != null) return@RestoreFocusOnPlayerReturn emptyList()
        // Same race as the error overlay above, one modal further along.
        //
        // "Choose a server" on a cache MISS launches MiruroExtractorActivity to
        // enumerate, so this screen genuinely pauses and RESUMES — and the resume
        // that delivers the server list is the same resume this restoration fires
        // on. It won, pulling focus onto the source button while the picker sat
        // open on top, which is why the D-pad drove the details screen underneath.
        //
        // It looked episode-specific because the SECOND press hits serverListCache,
        // opens the picker inline with no activity launch, and therefore no resume
        // and no race at all. A new episode misses the cache and brings it back.
        if (anime.showServerPicker || anime.isCheckingServers || anime.showDefaultPrompt) {
            return@RestoreFocusOnPlayerReturn emptyList()
        }
        focusMap.lastStream?.let { streamButton ->
            listOfNotNull(
                streamButton,
                episodesState.focusRequesters.getOrNull(episodesState.lastFocusedEpisodeIndex),
            )
        } ?: emptyList()
    }

    // The source chain: a "Watch on ..." press, the backend extraction, the
    // player, and both failover halves -- see SourcePlaybackController. What
    // is played is the episode the press recorded in pendingSeason /
    // pendingEpisode, and a press also selects it (§10).
    val sourcePlayback = rememberSourcePlayback(
        session    = session,
        scope      = coroutineScope,
        profileId  = favoriteProfileId,
        mediaType  = args.mediaType,
        mediaTitle = args.mediaTitle,
        request    = { PlaybackRequest.Episode(args.mediaId, episodesState.pendingSeason, episodesState.pendingEpisode, episodesState.pendingEpisodeName) },
        onStart    = { stopTrailerAndSelectEpisode(episodesState.pendingSeason, episodesState.pendingEpisode, episodesState.pendingEpisodeName) },
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
                try { focusMap.lastStream?.requestFocus() } catch (_: Exception) {}
            }
        },
    )


    // The first season load, and the move to the newest listed season -- see
    // SeasonLoadEffects.
    SeasonLoadEffects(
        episodesState   = episodesState,
        tmdbId          = args.mediaId,
        numberOfSeasons = args.numberOfSeasons,
        lastSeason      = args.lastSeason,
        prefetch        = prefetch,
        listedSeasons   = listedSeasons,
    )

    // The image-cache stats -- see DetailsCacheStats.
    DetailsCacheStats("TVShowsDetailsScreenCompose", extras.recommendations)




    // Main container with background: the frame both screens share, with this
    // screen's labels, play area and overlays plugged in -- see DetailsScaffold.
    DetailsScaffold(
        info = DetailsInfo(
            mediaTitle          = args.mediaTitle,
            logoUrl             = args.logoUrl,
            backdropUrl         = args.backdropUrl,
            backdropDescription = "TVShowsDetailsScreenCompose backdrop",
            contentRating       = args.contentRating,
            voteAverage         = args.voteAverage,
            genres              = args.genres,
            overview            = args.overview,
            genresTop           = detailsGenresTop(
                tvInfoLineCount(args.numberOfSeasons, args.status, args.releaseDate, args.originalLanguage)
            ),
            // Clear of the episodes' air dates.
            overviewLines       = TV_OVERVIEW_LINES,
        ),
        trailer = trailer,
        favorite = favorite,
        extras = extras,
        header = DetailsHeaderSpec(
            pressTiming            = TvHeaderPressTiming,
            enableFocus            = headerGate.isOpen,
            favoriteFocusRequester = focusMap.favorite,
            extrasFocusRequester   = focusMap.extras,
            trailerFocusRequester  = focusMap.trailer,
            search                 = search,
        ),
        releaseTrailerOnLeave = true,
        onOpenRecommendation = { media ->
            trailer.stop()
            coroutineScope.launch {
                // Fire both requests concurrently during the button animation delay
                val episodesDeferred = if (media.mediaType == "tv") {
                    async {
                        try { episodesRepository.getSeasonEpisodes(media.tmdbId, 1).episodes }
                        catch (_: Exception) { null }
                    }
                } else null

                val recsDeferred = async {
                    try { recommendationsRepository.getRecommendations(media.tmdbId,
                        media.mediaType
                    ) }
                    catch (_: Exception) { emptyList() }
                }

                delay(1000) // Let button animation complete + give requests time to resolve

                app?.storePrefetch(
                    BlueHiveApplication.MediaPrefetchData(
                        tmdbId          = media.tmdbId,
                        mediaType       = media.mediaType,
                        episodes        = episodesDeferred?.await(),
                        recommendations = recsDeferred.await(),
                    )
                )

                onMediaClick(media)
            }
        },
        onUpFromRecommendations = {
            // Back to the episode Down came from -- see focusAboveRow.
            BlueHiveApplication.playHoverSound()
            focusMap.focusAboveRow(episodesState)
        },
        labels = {
            TvInfoLabels(
                numberOfSeasons  = args.numberOfSeasons,
                status           = args.status,
                releaseDate      = args.releaseDate,
                originalLanguage = args.originalLanguage,
                dongleRegular    = dongleRegular,
            )
        },
        playArea = {
            // The play area: decorations, seasons, episodes and the source
            // overlay -- see TvPlayArea.
            TvPlayArea(
                listedSeasons               = listedSeasons,
                numberOfSeasons             = args.numberOfSeasons,
                todayYmd                    = todayYmd,
                episodesState               = episodesState,
                focusMap                    = focusMap,
                unairedGate                 = unairedGate,
                backdropUrl                 = args.backdropUrl,
                passionFont                 = passionFont,
                dongleRegular               = dongleRegular,
                coroutineScope              = coroutineScope,
                rowEntry                    = peek.rowEntry,
            )
        },
        overlays = {
            // The episode overlay: OK on an episode opens it over the page,
            // first so the loading and anime overlays below still cover it --
            // see EpisodeSourceOverlay.
            EpisodeSourceOverlay(
                extras                      = extras,
                episodesState               = episodesState,
                focusMap                    = focusMap,
                anime                       = anime,
                sourcePlayback              = sourcePlayback,
                mediaType                   = args.mediaType,
                mediaId                     = args.mediaId,
                genres                      = args.genres,
                originalLanguage            = args.originalLanguage,
                backdropUrl                 = args.backdropUrl,
                passionFont                 = passionFont,
                dongleRegular               = dongleRegular,
                stopTrailerAndSelectEpisode = { season, episode, epName ->
                    stopTrailerAndSelectEpisode(season, episode, epName)
                },
                onUnavailable               = { notYetAvailable.show() },
                snap                        = episodeSnap,
            )

            // ════════════════════════════════════════════════════════════════════
            //  Anime DuB/SuB overlays — last so they render on top of everything
            // ════════════════════════════════════════════════════════════════════
            // The loading screen, trapped -- see PlaybackLoadingOverlay. This
            // screen's headline keeps the source chain's status (§10).
            PlaybackLoadingOverlay(session, useBackendStatus = true)

            // Actionable failure (currently: an ISP/router filter blocking the source).
            // Without this the screen just returned to the source buttons and the user
            // had no way to tell a blocked domain from a dead one.
            PlaybackErrorOverlay(
                session,
                onDismissed = {
                    coroutineScope.launch {
                        awaitFrame()
                        try { focusMap.lastStream?.requestFocus() } catch (_: Exception) {}
                    }
                },
            )

            if (anime.showNoDub) {
                DetailsToast("No dub available for this episode", dongleRegular)
            }

            // The anime flow's server overlays -- the "Checking servers" trap, the
            // default-server prompt, the picker and the "couldn't reach" notice,
            // in that order -- see AnimeServerOverlays.
            AnimeServerOverlays(anime, dongleRegular)

            // The press-again notice of an armed unaired episode -- see UnairedNotice.
            UnairedNotice(unairedGate, episodesState, dongleRegular)

            TimedDetailsToast(
                notYetAvailable,
                "This show isn't in our search engines yet — it's brand new, so check back in a week or so",
                dongleRegular,
                centered = true,
                maxWidth = 720.dp,
            )

            // The trailer could not be loaded -- see TrailerFailedNotice.
            TrailerFailedNotice(trailer, dongleRegular)

            DetailsSearchKeyboard(search, favoriteProfileId)
        },
        peek = peek,
        // The page blurs behind the episode overlay and the search keyboard
        // (API 31+; older boxes get their dark scrims instead) -- the overlays
        // themselves stay sharp.
        pageModifier = Modifier
            .hiveBackdropBlur(search.isOpen || episodeOverlayOpen(extras, episodesState))
            .blockFocusWhileSnapping(episodeSnap),
    )
}
