package com.example.bluehive.details.tvshowdetails

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback
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
import com.example.bluehive.details.shared.layout.DetailsScaffold
import com.example.bluehive.details.shared.layout.DetailsToast
import com.example.bluehive.details.shared.state.DetailsBackHandlers
import com.example.bluehive.details.shared.state.rememberExtrasState
import com.example.bluehive.details.shared.state.rememberFavoriteState
import com.example.bluehive.details.shared.state.rememberTrailerState
import com.example.bluehive.details.shared.navigation.DetailsScreen
import com.example.bluehive.details.shared.navigation.DetailsIntents
import com.example.bluehive.details.shared.navigation.DetailsBackNavigation



class TVShowsDetailsScreenCompose: ComponentActivity() {


    private lateinit var args: TvShowDetailsArgs
    internal var currentSeasonForWatch: Int = 1
    internal var currentEpisodeForWatch: Int = 1
    internal var currentEpisodeNameForWatch: String? = null


    companion object {
        private const val TAG = "TVShowsDetailsScreenCompose"
    }

    fun openMediaDetails(media: MediaItem) {
        DetailsBackNavigation.pushCurrent(
            this,
            args.toHistoryItem(),
            media,
        )

        val intent = DetailsIntents.open(
            this, TVShowsDetailsScreenCompose::class.java, DetailsScreen.Tv,
            media, args.profileId, args.sourceScreen, args.searchQuery,
        )

        startActivity(intent)

        // ✅ Only one Details Activity alive at a time
        finish()
    }



    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as BlueHiveApplication).setAppState(
            BlueHiveApplication.AppState.DETAILS_VIEWING
        )

        // ✅ Handle system back with sound + history stack
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                DetailsBackNavigation.goBack(
                    this@TVShowsDetailsScreenCompose, TVShowsDetailsScreenCompose::class.java, DetailsScreen.Tv,
                    args.profileId, args.sourceScreen, args.searchQuery,
                )
            }
        })


        // Extract media data from intent
        args = TvShowDetailsArgs.from(intent)

        val recommendationsRepository = RecommendationsRepository()
        val episodesRepository = EpisodesRepository()


        setContent {
            TVShowsDetailsScreenContent(
                mediaTitle = args.mediaTitle,
                mediaType = args.mediaType,
                mediaId = args.mediaId,
                backdropUrl = args.backdropUrl,
                trailerUrl = args.trailerUrl,
                logoUrl = args.logoUrl,
                overview = args.overview,
                contentRating = args.contentRating,
                originalLanguage = args.originalLanguage,
                voteAverage = args.voteAverage,
                releaseDate = args.releaseDate,
                numberOfSeasons = args.numberOfSeasons,
                status = args.status,
                genres = args.genres,
                tmdbId = args.mediaId,
                profileId = args.profileId,
                startInExtras = args.startInExtras,
                lastSeason    = args.lastSeason,
                lastEpisode   = args.lastEpisode,
                resumeToEpisode = args.resumeToEpisode,
                recommendationsRepository = recommendationsRepository,
                episodesRepository = episodesRepository,
                onEpisodeSelected = { season, episode, epName ->
                    currentSeasonForWatch       = season
                    currentEpisodeForWatch      = episode
                    currentEpisodeNameForWatch  = epName
                },
                onMediaClick = { media ->
                    openMediaDetails(media)
                }
            )
        }
    }


    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            (application as BlueHiveApplication).setAppState(
                BlueHiveApplication.AppState.DETAILS_VIEWING
            )
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            (application as BlueHiveApplication).setAppState(
                BlueHiveApplication.AppState.HOME_ACTIVE
            )
        }
        super.onDestroy()
    }
}









/**
 * How the header buttons press on this screen: quicker than the movie screen's
 * 180/140 ms. Kept apart until that is decided (architecture §10, #1).
 */
private val TvHeaderPressTiming = DetailsPressTiming(durationMillis = 110, bounceBackDelayMillis = 80)

@SuppressLint("DefaultLocale")
@UnstableApi
@Composable
fun TVShowsDetailsScreenContent(
    mediaTitle: String,
    mediaType: String,
    mediaId: Int,
    logoUrl: String?,
    backdropUrl: String?,
    trailerUrl: String?,
    overview: String?,
    voteAverage: Double,
    contentRating: String?,
    originalLanguage: String?,
    releaseDate: String?,
    numberOfSeasons: Int,
    status: String?,
    genres: String?,
    tmdbId: Int,
    profileId: Int,
    startInExtras: Boolean = false,
    lastSeason:    Int     = 1,
    lastEpisode:   Int     = 1,
    resumeToEpisode: Boolean = false,
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
    val prefetch = remember(tmdbId) { app?.consumePrefetch(tmdbId) }

    // Decode the loading-overlay bee logo now, while the screen is first
    // composing and memory is comparatively free, and cache it process-wide so
    // the Dub/Sub overlay never decodes it under pressure (the 2 GB crash).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BeeLogo.prime(context) }
    }


    // 🔹 Precomputed display strings (recomputed only when inputs change)
    val seasonsLabel = remember(numberOfSeasons) {
        "Seasons:          " + if (numberOfSeasons > 0) numberOfSeasons.toString() else "N/A"
    }

    val statusLabel = remember(status) {
        "Status:            " + if (!status.isNullOrBlank() && status != "N/A") status else "N/A"
    }

    val languageLabel = remember(originalLanguage) {
        "Language:        " + (
                if (!originalLanguage.isNullOrBlank() && originalLanguage != "N/A")
                    originalLanguage
                else
                    "N/A"
                )
    }

    val releaseDateLabel = remember(releaseDate) {
        if (!releaseDate.isNullOrBlank() && releaseDate != "N/A") {
            val dateParts = releaseDate.split("-")
            if (dateParts.size == 3) {
                // Format as MM/DD/YYYY
                "Release Date:     ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"
            } else {
                "Release Date:     $releaseDate"
            }
        } else {
            "Release Date:      N/A"
        }
    }




    val coroutineScope = rememberCoroutineScope()

    // Extras: the switch and its recommendations row -- see ExtrasState. On
    // this screen the row loads on the main thread, and a page opened from
    // another show's Extras uses the prefetched row (architecture §10, #5 and
    // #13).
    val extras = rememberExtrasState(
        scope            = coroutineScope,
        repository       = recommendationsRepository,
        tmdbId           = tmdbId,
        mediaType        = mediaType,
        startInExtras    = startInExtras,
        loadInBackground = false,
        prefetched       = prefetch?.recommendations,
    )

    var blockHeaderInitialFocus by remember(startInExtras) {
        mutableStateOf(startInExtras || resumeToEpisode)
    }
    var isInitialEpisodeLoad by remember { mutableStateOf(resumeToEpisode) }

    LaunchedEffect(startInExtras, extras.recommendations) {
        if (startInExtras && extras.recommendations.isNotEmpty()) {
            blockHeaderInitialFocus = false
        }
    }


    // The season on show, its episodes, the row's scroll and focus, the open
    // source overlay and the watched marks -- see EpisodesState. The focus
    // requesters the sections share -- see TvFocusMap.
    val episodesState = rememberEpisodesState(coroutineScope, episodesRepository, tmdbId)
    val focusMap = rememberTvFocusMap(numberOfSeasons) {
        getStreamingSources(mediaType, mediaId, 1, 1).size.coerceAtLeast(2)
    }

    // Which unaired episode has been pressed once -- see UnairedGate.
    val unairedGate = rememberUnairedGate()



    // The backdrop trailer: playing, started, extracting, its player -- see
    // TrailerState.
    val trailer = rememberTrailerState(trailerUrl)

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

    // Armed state is a MOMENT, not a mode. Someone who presses once, reads the
    // notice and walks away should not find the episode silently pressable when
    // they come back -- the second press has to be a deliberate answer to the
    // first, which means it has to be soon after it.
    LaunchedEffect(unairedGate.armedIndex) {
        if (unairedGate.armedIndex >= 0) {
            delay(UNAIRED_OVERRIDE_WINDOW_MS)
            unairedGate.armedIndex = -1
        }
    }

    // Today's date and the season list are both re-read every time this screen
    // comes back into view, so an episode or a season that airs while the app
    // is open unlocks without reopening the show.
    val resumeCount = rememberResumeCount()

    // Today's date, read once per resume. Per card it would be the same answer
    // recomputed on every recomposition of a scrolling row.
    val todayYmd = remember(resumeCount) { currentYmd() }

    // How many seasons the season column lists, from Season 1: the newest ones
    // with nothing aired yet are left off. Null until the first check answers.
    val listedSeasons = rememberListedSeasons(tmdbId, numberOfSeasons, resumeCount, episodesRepository)

    // ✅ Episode row scroll + initial focus (moved here from above so the
    //    fallback can reach the header requesters).
    //
    //    Why this is a retry loop instead of delay(150) + requestFocus():
    //    the target tile lives in a LazyRow, so its FocusRequester is only
    //    attached once the tile is composed AND placed. On entry from
    //    Continue Watching the main thread is busy (heavy frames + Coil cache
    //    thrashing), so a fixed delay elapses before layout finishes and
    //    requestFocus() no-ops ("FocusRequester is not initialized"). Because
    //    the header is focus-blocked in this mode, that leaves the whole
    //    screen with nothing focused = stuck. So: scroll, wait until the tile
    //    is actually laid out (presence in visibleItemsInfo => node attached),
    //    request focus, confirm it landed, and only then release the gates.
    LaunchedEffect(episodesState.episodes) {
        if (episodesState.episodes.isEmpty()) return@LaunchedEffect

        if (isInitialEpisodeLoad) {
            val targetIndex = (lastEpisode - 1).coerceIn(0, episodesState.episodes.lastIndex)
            episodesState.lastFocusedEpisodeIndex = targetIndex
            episodesState.rowState.scrollToItem(targetIndex)

            episodesState.episodeFocusLanded = false
            var landed = false
            var attempts = 0
            while (attempts < 40 && !landed) {
                awaitFrame()
                val laidOut = episodesState.rowState.layoutInfo
                    .visibleItemsInfo.any { it.index == targetIndex }
                if (laidOut) {
                    episodesState.focusRequesters.getOrNull(targetIndex)?.requestFocus()
                    awaitFrame()
                    if (episodesState.episodeFocusLanded) landed = true
                }
                attempts++
            }

            // Never strand the user with no focus: if the episode tile truly
            // never took focus, un-block the header and focus Favorites.
            if (!landed) {
                Log.w("TVDetails", "⚠️ Episode focus never landed — falling back to header")
                blockHeaderInitialFocus = false
                awaitFrame()
                focusMap.favorite.requestFocus()
            }

            blockHeaderInitialFocus = false
            isInitialEpisodeLoad = false
        } else {
            episodesState.lastFocusedEpisodeIndex = 0
            episodesState.rowState.scrollToItem(0)
        }
    }


    val favoriteProfileId = profileId

    // Favorites: checked on open, toggled by the header button -- see
    // FavoriteState. This screen sends the title with a toggle (§10, #3).
    val favorite = rememberFavoriteState(
        scope     = coroutineScope,
        profileId = favoriteProfileId,
        tmdbId    = tmdbId,
        mediaType = mediaType,
        title     = mediaTitle,
    )

    LaunchedEffect(tmdbId, favoriteProfileId) { episodesState.refreshWatchedEpisodes(favoriteProfileId) }

    // Re-read on every resume. Nothing is marked optimistically any more — the
    // marker follows the backend row, and that row is only written once the player
    // confirms video is actually playing. Coming back from the player is therefore
    // the moment a genuinely-watched episode should light up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tmdbId, favoriteProfileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { episodesState.refreshWatchedEpisodes(favoriteProfileId) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Anime DuB / SuB extraction flow (ported from MoviesDetailsScreenCompose).
    //  The Miruro source URL already carries ?ep=N, so the extractor clicks the
    //  right episode itself — we just feed it the URL like the movie screen does.
    // ════════════════════════════════════════════════════════════════════════
    // The page's loading screen, shared by the source chain and the anime
    // flow: extracting, pending, stage, the job BACK cancels -- see
    // PlaybackSession.
    val session = rememberPlaybackSession()

    // Shown when an anime's AniList mapping doesn't exist yet (resolveAnime 404 →
    // miruroUrl null). Brand-new titles aren't in anime_id_map until AniBridge
    // ingests them, so instead of leaving DuB/SuB inert we tell the user why.
    var showNotYetAvailable by remember { mutableStateOf(false) }

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
        mediaType    = mediaType,
        mediaTitle   = mediaTitle,
        request      = { PlaybackRequest.Episode(tmdbId, episodesState.pendingSeason, episodesState.pendingEpisode, episodesState.pendingEpisodeName) },
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
        mediaType  = mediaType,
        mediaTitle = mediaTitle,
        request    = { PlaybackRequest.Episode(tmdbId, episodesState.pendingSeason, episodesState.pendingEpisode, episodesState.pendingEpisodeName) },
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

    LaunchedEffect(showNotYetAvailable) {
        if (showNotYetAvailable) { delay(4000); showNotYetAvailable = false }
    }

    // 🔹 On first entry, auto-load Season 1 if we have seasons
    LaunchedEffect(tmdbId, numberOfSeasons) {
        if (numberOfSeasons > 0) {
            episodesState.selectedSeason = lastSeason.coerceIn(1, numberOfSeasons)
            val prefetchedEpisodes = prefetch?.episodes
            if (prefetchedEpisodes != null && lastSeason == 1) {
                episodesState.episodes = prefetchedEpisodes
                episodesState.isLoadingEpisodes = false
                Log.d("Prefetch", "✅ Episodes loaded from prefetch cache (${prefetchedEpisodes.size} eps)")
            } else {
                episodesState.loadSeasonEpisodes(episodesState.selectedSeason)
            }
        }
    }

    // The season check can leave off the selected season -- the screen was
    // opened on a season with nothing aired. Move to the newest listed one, so
    // the grid never shows a season the column does not.
    LaunchedEffect(listedSeasons) {
        val listed = listedSeasons ?: return@LaunchedEffect
        if (episodesState.selectedSeason > listed) {
            episodesState.selectedSeason = listed
            episodesState.loadSeasonEpisodes(listed)
        }
    }

    // ✅ Log cache stats when screen loads — deferred + off the main thread.
    // gatherStats() does THREE full directory walks; running it inline on entry
    // was a main-thread stall. Paint first, then walk on IO.
    LaunchedEffect(Unit) {
        delay(1500)
        withContext(Dispatchers.IO) {
            CacheMonitor.logCacheStats(
                context = context,
                screenName = "TVShowsDetailsScreenCompose",
                imageCount = 0  // Initial load
            )
        }
    }

    // ✅ Log again when recommendations load — also off the main thread.
    LaunchedEffect(extras.recommendations) {
        if (extras.recommendations.isNotEmpty()) {
            // 1 backdrop + 1 logo + 20 recommendations + 6 provider buttons
            val totalImages = 1 + 1 + extras.recommendations.size + 6

            withContext(Dispatchers.IO) {
                CacheMonitor.logCacheStats(
                    context = context,
                    screenName = "TVShowsDetailsScreenCompose",
                    imageCount = totalImages
                )
            }
        }
    }




    // Main container with background: the frame both screens share, with this
    // screen's labels, play area and overlays plugged in -- see DetailsScaffold.
    DetailsScaffold(
        info = DetailsInfo(
            mediaTitle          = mediaTitle,
            logoUrl             = logoUrl,
            backdropUrl         = backdropUrl,
            backdropDescription = "TVShowsDetailsScreenCompose backdrop",
            contentRating       = contentRating,
            voteAverage         = voteAverage,
            genres              = genres,
            overview            = overview,
        ),
        trailer = trailer,
        favorite = favorite,
        extras = extras,
        header = DetailsHeaderSpec(
            pressTiming            = TvHeaderPressTiming,
            enableFocus            = !blockHeaderInitialFocus,
            favoriteFocusRequester = focusMap.favorite,
            extrasFocusRequester   = focusMap.extras,
            trailerFocusRequester  = focusMap.trailer,
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
            BlueHiveApplication.playHoverSound()
            focusMap.extras.requestFocus()
            Log.d("SeekDebug", "R.drawable.button_focus_narrow_nonglow should be the extra button's focus glow")
        },
        labels = {
            TvInfoLabels(
                seasonsLabel     = seasonsLabel,
                statusLabel      = statusLabel,
                releaseDateLabel = releaseDateLabel,
                languageLabel    = languageLabel,
                dongleRegular    = dongleRegular,
            )
        },
        playArea = {
            // The play area: decorations, seasons, episodes and the source
            // overlay -- see TvPlayArea.
            TvPlayArea(
                listedSeasons               = listedSeasons,
                numberOfSeasons             = numberOfSeasons,
                todayYmd                    = todayYmd,
                episodesState               = episodesState,
                focusMap                    = focusMap,
                unairedGate                 = unairedGate,
                extras                      = extras,
                anime                       = anime,
                sourcePlayback              = sourcePlayback,
                mediaType                   = mediaType,
                mediaId                     = mediaId,
                genres                      = genres,
                originalLanguage            = originalLanguage,
                backdropUrl                 = backdropUrl,
                passionFont                 = passionFont,
                dongleRegular               = dongleRegular,
                coroutineScope              = coroutineScope,
                stopTrailerAndSelectEpisode = { season, episode, epName ->
                    stopTrailerAndSelectEpisode(season, episode, epName)
                },
                onUnavailable               = { showNotYetAvailable = true },
            )
        },
        overlays = {
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

            if (showNotYetAvailable) {
                DetailsToast(
                    "This show isn't in our search engines yet — it's brand new, so check back in a week or so",
                    dongleRegular,
                    centered = true,
                    maxWidth = 720.dp,
                )
            }
        },
    )
}
