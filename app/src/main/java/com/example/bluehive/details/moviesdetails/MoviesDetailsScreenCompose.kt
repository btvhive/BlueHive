package com.example.bluehive.details.moviesdetails

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.*
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.focus.focusRequester
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.webview.miruro.MiruroPlayerActivity
import com.example.bluehive.webview.BeeLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.foundation.background
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.playback.usesBackendExtraction
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



class MoviesDetailsScreenCompose : ComponentActivity() {
    private lateinit var args: MovieDetailsArgs


    companion object {
        private const val TAG = "MoviesDetailsScreenCompose"
    }

    fun openMediaDetails(media: MediaItem) {
        DetailsBackNavigation.pushCurrent(
            this,
            args.toHistoryItem(),
            media,
        )

        val intent = DetailsIntents.open(
            this, MoviesDetailsScreenCompose::class.java, DetailsScreen.Movie,
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
                    this@MoviesDetailsScreenCompose, MoviesDetailsScreenCompose::class.java, DetailsScreen.Movie,
                    args.profileId, args.sourceScreen, args.searchQuery,
                )
            }
        })


        // Extract media data from intent
        args = MovieDetailsArgs.from(intent)

        val recommendationsRepository = RecommendationsRepository()


        // ✅ Debug logging to see what we received
        Log.d(TAG, "📝 Intent extras received:")
        Log.d(TAG, "  mediaTitle: ${args.mediaTitle}")
        Log.d(TAG, "  mediaType: ${args.mediaType}")
        Log.d(TAG, "  mediaId: ${args.mediaId}")
        Log.d(TAG, "  backdropUrl: ${args.backdropUrl}")
        Log.d(TAG, "  trailerUrl: ${args.trailerUrl}")
        Log.d(TAG, "  logoUrl (raw): '${args.logoUrl}'")
        Log.d(TAG, "  logoUrl isEmpty: ${args.logoUrl.isNullOrEmpty()}")
        Log.d(TAG, "  logoUrl isBlank: ${args.logoUrl.isNullOrBlank()}")

        Log.d(TAG, "Received media: ${args.mediaTitle} (ID: ${args.mediaId}, Type: ${args.mediaType})")

        Log.d(TAG, "🔍 Intent extras:")
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            Log.d(TAG, "  $key: $value (type: ${value?.javaClass?.simpleName})")
        }

        setContent {
            MoviesDetailsScreenContent(
                mediaTitle = args.mediaTitle,
                mediaType = args.mediaType,
                mediaId = args.mediaId,
                backdropUrl = args.backdropUrl,
                trailerUrl = args.trailerUrl,
                logoUrl = args.logoUrl,
                overview = args.overview,
                revenue = args.revenue,
                budget = args.budget,
                contentRating = args.contentRating,
                originalLanguage = args.originalLanguage,
                voteAverage = args.voteAverage,
                releaseDate = args.releaseDate,
                genres = args.genres,
                tmdbId = args.mediaId,
                profileId = args.profileId,
                startInExtras = args.startInExtras,
                recommendationsRepository = recommendationsRepository,
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

@UnstableApi
@Composable
fun MoviesDetailsScreenContent(
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
    revenue: Long,
    budget: Long,
    genres: String?,
    tmdbId: Int,
    profileId: Int,
    startInExtras: Boolean = false,
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

    // Extras: the switch and its recommendations row -- see ExtrasState. On
    // this screen the row loads on a background thread, and opening in Extras
    // always fetches (architecture §10, #5 and #13).
    val extras = rememberExtrasState(
        scope            = coroutineScope,
        repository       = recommendationsRepository,
        tmdbId           = tmdbId,
        mediaType        = mediaType,
        startInExtras    = startInExtras,
        loadInBackground = true,
        prefetched       = null,
    )

    var blockHeaderInitialFocus by remember(startInExtras) { mutableStateOf(startInExtras) }

    // The backdrop trailer: playing, started, extracting, its player -- see
    // TrailerState.
    val trailer = rememberTrailerState(trailerUrl)

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
    var showNotYetAvailable by remember { mutableStateOf(false) }

    // Named requesters for DuB/Sub so we know exactly where to send focus back.
    val animeDubFocusRequester = remember { FocusRequester() }
    val animeSubFocusRequester = remember { FocusRequester() }
    // Whichever button the user pressed last — restored when any overlay closes.
    var lastStreamFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

    val favoriteProfileId = profileId

    // The anime flow: the DuB/SuB prompt and the server picker, the extraction
    // (backend first, the on-device extractor as the fallback) and the
    // automatic failover through the server list -- see AnimePlaybackController.
    val anime = rememberAnimePlayback(
        session      = session,
        scope        = coroutineScope,
        profileId    = favoriteProfileId,
        mediaType    = mediaType,
        mediaTitle   = mediaTitle,
        request      = { PlaybackRequest.Movie(tmdbId) },
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
        tmdbId    = tmdbId,
        mediaType = mediaType,
        title     = null,
    )

    // The source chain: a "Watch on ..." press, the backend extraction, the
    // player, and both failover halves -- see SourcePlaybackController.
    val sourcePlayback = rememberSourcePlayback(
        session    = session,
        scope      = coroutineScope,
        profileId  = favoriteProfileId,
        mediaType  = mediaType,
        mediaTitle = mediaTitle,
        request    = { PlaybackRequest.Movie(tmdbId) },
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
    val isAnime = remember(genres, originalLanguage) {
        val result = isJapaneseAnimation(genres, originalLanguage)
        Log.d("AnimeDetect", "genres='$genres' | originalLanguage='$originalLanguage' | isAnime=$result")
        result
    }
    val sources by produceState(
        initialValue = if (isAnime) emptyList()
        else getStreamingSources(mediaType, mediaId),
        mediaType, mediaId, isAnime
    ) {
        if (isAnime) {
            value = buildJapaneseAnimationSources(
                mediaType     = mediaType,
                tmdbId        = mediaId,
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
            value = getStreamingSources(mediaType, mediaId)
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
            showNotYetAvailable = true
            return@LaunchedEffect
        }
        // Still resolving. Bounded, so a resolve that never completes cannot
        // leave the button looking dead.
        delay(8_000)
        if (queuedAnimePress != null) {
            queuedAnimePress = null
            showNotYetAvailable = true
        }
    }





    LaunchedEffect(showNotYetAvailable) {
        if (showNotYetAvailable) {
            delay(4000)
            showNotYetAvailable = false
        }
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


    // ✅ Log cache stats when screen loads — deferred + off the main thread.
    // gatherStats() does THREE full directory walks (disk cache + cacheDir +
    // filesDir); running it inline on entry was a main-thread stall. Let the
    // screen paint first, then walk on IO.
    LaunchedEffect(Unit) {
        delay(1500)
        withContext(Dispatchers.IO) {
            CacheMonitor.logCacheStats(
                context = context,
                screenName = "MoviesDetailsScreen",
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
                    screenName = "MoviesDetailsScreen",
                    imageCount = totalImages
                )
            }
        }
    }


    LaunchedEffect(startInExtras, extras.recommendations) {
        if (startInExtras && extras.recommendations.isNotEmpty()) {
            blockHeaderInitialFocus = false
        }
    }

    var displayBudget by remember { mutableLongStateOf(budget) }
    var displayRevenue by remember { mutableLongStateOf(revenue) }

    LaunchedEffect(tmdbId, mediaType) {
        try {
            val detail = ApiClient.trailerApi.getMediaDetails(tmdbId, mediaType)
            detail.budget?.let { if (it > 0) displayBudget = it }
            detail.revenue?.let { if (it > 0) displayRevenue = it }
        } catch (_: Exception) {}
    }




    // Main container with background: the frame both screens share, with this
    // screen's labels, play area and overlays plugged in -- see DetailsScaffold.
    DetailsScaffold(
        info = DetailsInfo(
            mediaTitle          = mediaTitle,
            logoUrl             = logoUrl,
            backdropUrl         = backdropUrl,
            backdropDescription = "Movie backdrop",
            contentRating       = contentRating,
            voteAverage         = voteAverage,
            genres              = genres,
            overview            = overview,
        ),
        trailer = trailer,
        favorite = favorite,
        extras = extras,
        header = DetailsHeaderSpec(
            pressTiming            = MovieHeaderPressTiming,
            enableFocus            = !blockHeaderInitialFocus,
            favoriteFocusRequester = favoriteFocusRequester,
            extrasFocusRequester   = extrasFocusRequester,
            trailerFocusRequester  = trailerFocusRequester,
        ),
        releaseTrailerOnLeave = false,
        onOpenRecommendation = { media ->
            trailer.stop()
            onMediaClick(media)
        },
        onUpFromRecommendations = {
            BlueHiveApplication.playHoverSound()
            extrasFocusRequester.requestFocus()
            extrasFocusRequester.requestFocus()
            Log.d("SeekDebug", "R.drawable.button_focus_narrow_nonGlow should be the extra button's focus glow")
        },
        labels = {
            MovieInfoLabels(
                displayRevenue   = displayRevenue,
                displayBudget    = displayBudget,
                releaseDate      = releaseDate,
                originalLanguage = originalLanguage,
                dongleRegular    = dongleRegular,
            )
        },
        playArea = {
            if (isAnime) {
                // ── Anime: DuB / SuB ────────────────────────────────────
                AnimeDubButton(
                    fontFamily = passionFont,
                    focusRequester = animeDubFocusRequester,
                    offsetX = 27.75.dp, offsetY = 332.5.dp,
                    onClick = {
                        // No stopTrailer() here: this only OPENS the DuB/SuB prompt.
                        // The trailer plays on until the prompt is answered, which
                        // is where a launch actually happens.
                        if (sources.size > 2) {
                            Log.d("scraper", "DuB pressed → prompt: ${sources[2].url}")
                            lastStreamFocusRequester = animeDubFocusRequester
                            anime.pendingUrl = sources[2].url
                            anime.pendingDub = true
                            anime.showDefaultPrompt = true
                        } else if (sources.isEmpty()) {
                            // Still resolving — honour the press when it lands.
                            queuedAnimePress = true
                        } else {
                            // Resolved, and still no Miruro/M Extract source: this
                            // title genuinely has no AniList mapping yet. Say so
                            // instead of silently doing nothing.
                            Log.w("scraper", "DuB pressed but no Miruro source (tmdb=$mediaId) — unmapped")
                            showNotYetAvailable = true
                        }
                    }
                )
                AnimeSubButton(
                    fontFamily = passionFont,
                    focusRequester = animeSubFocusRequester,
                    offsetX = 228.dp, offsetY = 332.5.dp,
                    onClick = {
                        // No stopTrailer() here — see the DuB button above.
                        if (sources.size > 2) {
                            Log.d("scraper", "SuB pressed → prompt: ${sources[2].url}")
                            lastStreamFocusRequester = animeSubFocusRequester
                            anime.pendingUrl = sources[2].url
                            anime.pendingDub = false
                            anime.showDefaultPrompt = true
                        } else if (sources.isEmpty()) {
                            queuedAnimePress = false
                        } else {
                            Log.w("scraper", "SuB pressed but no Miruro source (tmdb=$mediaId) — unmapped")
                            showNotYetAvailable = true
                        }
                    }
                )
            } else {
                // ── Non-anime: 2-column grid laid out FROM the sources list ──
                // Adding/removing an entry in getStreamingSources() now "just
                // works" — no per-index block to keep in sync (that mismatch is
                // what silently dropped the 6th button). Column X and the first
                // rows' Y are the exact legacy offsets, so nothing moves on screen;
                // a 7th+ source simply continues the grid downward.
                val colX = listOf(27.75.dp, 228.dp)          // [left column, right column]
                val rowY = listOf(332.5.dp, 388.dp, 443.dp)  // exact legacy row offsets
                val streamerFocus = remember(sources.size) {
                    List(sources.size) { FocusRequester() }
                }
                sources.forEachIndexed { i, source ->
                    val col = i % 2
                    val row = i / 2
                    // A trailing odd button (alone on its row) is centered between the two
                    // columns instead of hugging the left column.
                    val aloneInRow = i % 2 == 0 && i == sources.lastIndex
                    MoviesStreamerButton(
                        source = source,
                        fontFamily = passionFont,
                        focusRequester = streamerFocus[i],
                        offsetX = if (aloneInRow) (colX[0] + colX[1]) / 2f else colX[col],
                        offsetY = if (row < rowY.size) rowY[row]
                                  else rowY.last() + 55.dp * (row - rowY.lastIndex),
                        disableLeftSound  = aloneInRow || col == 0, // left column / lone centered → mute left edge
                        disableRightSound = aloneInRow || col == 1, // right column / lone centered → mute right edge
                        disableDownSound  = i + 2 >= sources.size,  // no button directly below → mute down
                        onClick = {
                            // Remember which button launched this so focus comes back
                            // here when the player exits (THE RULE).
                            lastStreamFocusRequester = streamerFocus[i]
                            // Every source: server-side m3u8 extraction, then our own
                            // player (the overlay shows progress). The server reports
                            // every source as extractable; one it could not extract
                            // fails into the same chain as a dead one.
                            sourcePlayback.start(source)
                        }
                    )
                }
            }
        },
        overlays = {
            // Instant extraction overlay — last in Box so it renders on top of everything
            PlaybackLoadingOverlay(session)

            // "No dub available" toast
            if (anime.showNoDub) {
                DetailsToast("No dub available for this title", dongleRegular)
            }

            // ── "Not in our search engine yet" — no AniList mapping for this movie ──
            if (showNotYetAvailable) {
                DetailsToast(
                    "This title isn't in our anime search engine yet — try VidFast below",
                    dongleRegular,
                    centered = true,
                )
            }

            // Extraction failure the user can act on ("try another source").
            PlaybackErrorOverlay(session)

            // The anime flow's server overlays -- the "Checking servers" trap, the
            // default-server prompt, the picker and the "couldn't reach" notice,
            // in that order -- see AnimeServerOverlays.
            AnimeServerOverlays(anime, dongleRegular)
        },
    )
}   // ← MoviesDetailsScreenContent closes here


@Composable
fun MoviesStreamerButton(
    source: StreamSource,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp,
    offsetY: Dp,
    disableLeftSound: Boolean = false,
    disableRightSound: Boolean = false,
    disableUpSound: Boolean = false,
    disableDownSound: Boolean = false,
    onClick: () -> Unit
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = if (source.name == "M Extract") "M Extract" else "Watch on ${source.name}",
            fontSize = 14f
        ),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (disableLeftSound) {
                                // edge: no sound, no navigation
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false   // let focus move normally
                            }
                        }
                        Key.DirectionRight -> {
                            if (disableRightSound) {
                                // Right edge: a plain dead end, no sound, no move.
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            if (disableUpSound) {
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (disableDownSound) {
                                false   // bottom row: nothing below, no sound
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp,
            mainHeight = 33.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 162.dp,
            secondHeight = 22.dp,
            secondYOffset = 18.dp,
            shadowHeight = (12).dp,
            glowWidth = 185.8.dp,
            glowHeight = 55.8.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared with the TV screen — see StreamSourceButtonColors in
        // utilities/ModularButton.kt. Change the blues there, not here.
        colors = StreamSourceButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the TV screen — see WatchButtonAnimation in
        // utilities/ModularButton.kt. Change the press feel there, not here.
        animationConfig = WatchButtonAnimation,
        // No playClickSound() here: ModularButton already plays it, immediately,
        // and this second call fired a duplicate. Harmless while the two landed
        // on the same frame — audible as a real double-click once onClick became
        // deferred by clickDelayMillis.
        onClick = onClick
    )
}


@Composable
fun AnimeDubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp = 27.75.dp,
    offsetY: Dp = 332.5.dp,
    onClick: () -> Unit
) {
    ModularButton(
        // "English" / "Japanese" rather than "DuB" / "SuB". Dub and sub are
        // fan shorthand; the language is what a viewer is actually choosing.
        textConfig = ModularButtonTextConfig(text = "Watch in English", fontSize = 14f),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> true   // left edge — block + no sound
                        Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeDubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeDubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}

@Composable
fun AnimeSubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp = 228.dp,
    offsetY: Dp = 332.5.dp,
    onClick: () -> Unit
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(text = "Watch in Japanese", fontSize = 14f),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> true  // right edge — block + no sound
                        Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeSubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeSubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}


//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x20000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)