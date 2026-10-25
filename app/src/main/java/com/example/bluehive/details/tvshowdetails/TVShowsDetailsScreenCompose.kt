package com.example.bluehive.details.tvshowdetails

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import com.example.bluehive.utilities.WatchedCornerFold
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.example.bluehive.utilities.*
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import android.os.SystemClock
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.example.bluehive.repository.EpisodesRepository
import com.example.bluehive.models.Episode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.sidebarComponents.FavoritesScreenActivity
import com.example.bluehive.sidebarComponents.HistoryScreenActivity
import kotlinx.coroutines.async
import com.example.bluehive.searchBarComponent.SearchBarActivity
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.webview.BeeLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withContext
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.SourceScreen
import com.example.bluehive.details.shared.playback.AnimeServerOverlays
import com.example.bluehive.details.shared.playback.PlaybackErrorOverlay
import com.example.bluehive.details.shared.playback.PlaybackLoadingOverlay
import com.example.bluehive.details.shared.playback.StreamSource
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


// ⬅️➡️ Hold-to-repeat navigation speed for the episode LazyRow: the minimum time (ms)
// between focus moves while LEFT/RIGHT is HELD DOWN. HIGHER = SLOWER. (Continue Watching
// uses CW_NAV_REPEAT_MS = 70L; episode tiles are chunkier, so this starts a touch slower.)
private const val EPISODE_NAV_REPEAT_MS = 140L

class TVShowsDetailsScreenCompose: ComponentActivity() {


    internal var profileId: Int = -1
    private var mediaType: String = "tv"
    private var mediaId: Int = 0
    internal var currentSeasonForWatch: Int = 1
    internal var currentEpisodeForWatch: Int = 1
    internal var currentEpisodeNameForWatch: String? = null
    private var voteAverage: Double = 0.0
    private var mediaTitle: String = ""
    private var posterUrl: String? = null
    private var backdropUrl: String? = null
    private var trailerUrl: String? = null
    private var overview: String? = null
    private var contentRating: String? = null
    private var releaseDate: String? = null
    private var originalLanguage: String? = null
    private var logoUrl: String? = null
    private var genres: String? = null
    private var numberOfSeasons: Int = 0
    private var status: String? = null
    private var sourceScreen: String? = null
    private var searchQuery:  String? = null


    companion object {
        private const val TAG = "TVShowsDetailsScreenCompose"
    }

    fun openMediaDetails(media: MediaItem) {
        val app = application as BlueHiveApplication
        app.pushDetailsHistory(MediaItem(
            tmdbId = mediaId,
            mediaId = mediaId,
            title = mediaTitle,
            mediaType = mediaType,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            overview = overview,
            releaseDate = releaseDate,
            status = status,
            voteAverage = voteAverage,
            voteCount = null,
            popularity = null,
            popularityRank = null,
            originalLanguage = originalLanguage,
            numberOfSeasons = numberOfSeasons,
            numberOfEpisodes = null,
            contentRating = contentRating,
            runtime = null,
            budget = null,
            revenue = null,
            trailerUrl = trailerUrl,
            genres = genres?.split(", ")?.map { it.trim() },
            similarItems = null,
            whereToWatch = null
        ))
        Log.d(
            "DetailsHistory",
            "Details -> openMediaDetails: pushed '${media.title}', size=${app.historySize()}"
        )

        val intent = Intent(this, TVShowsDetailsScreenCompose::class.java).apply {
            putExtra("media_type", media.mediaType)
            putExtra("media_id",          media.tmdbId)                     // Int (non-null)
            putExtra("media_title", media.title)
            putExtra("poster_url",        media.posterUrl ?: "")
            putExtra("backdrop_url",      media.backdropUrl ?: "")
            putExtra("youtube_trailer_url", media.trailerUrl ?: "")
            putExtra("overview",          media.overview ?: "")
            putExtra("vote_average",      media.voteAverage ?: 0.0)         // Double, NOT Double?
            putExtra("contentRating",     media.contentRating ?: "N/A")
            putExtra("original_language", media.originalLanguage ?: "N/A")
            putExtra("release_date",      media.releaseDate ?: "N/A")
            putExtra("logo_url",          media.logoUrl ?: "")
            putExtra("number_of_seasons", media.numberOfSeasons ?: 0)
            putExtra("status",            media.status ?: "N/A")

            val genresString = media.genres?.joinToString(", ")
            putExtra("genres", genresString ?: "N/A")
            putExtra("PROFILE_ID", profileId)
            sourceScreen?.let { putExtra("SOURCE_SCREEN", it) }
            searchQuery?.let  { putExtra("SEARCH_QUERY",  it) }
        }

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
                BlueHiveApplication.playBackOutSound()

                val app = application as BlueHiveApplication
                val previous = app.popPreviousDetails()

                if (previous == null) {
                    when (sourceScreen) {
                        SourceScreen.HISTORY -> {
                            Log.d("DetailsHistory", "Back: returning to History")
                            startActivity(
                                Intent(this@TVShowsDetailsScreenCompose, HistoryScreenActivity::class.java)
                                    .putExtra("PROFILE_ID", profileId)
                            )
                            finish()
                            return
                        }
                        SourceScreen.FAVORITES -> {
                            Log.d("DetailsHistory", "Back: returning to Favorites")
                            startActivity(
                                Intent(this@TVShowsDetailsScreenCompose, FavoritesScreenActivity::class.java)
                                    .putExtra("PROFILE_ID", profileId)
                            )
                            finish()
                            return
                        }
                        SourceScreen.SEARCH -> {
                            Log.d("DetailsHistory", "Back: returning to Search")
                            startActivity(
                                Intent(this@TVShowsDetailsScreenCompose, SearchBarActivity::class.java)
                                    .putExtra("PROFILE_ID",    profileId)
                                    .putExtra("INITIAL_QUERY", searchQuery ?: "")
                            )
                            finish()
                            return
                        }
                        else -> {
                            Log.d("DetailsHistory", "Back: no previous details, finishing to Home")
                            finish()
                            return
                        }
                    }
                }

                Log.d("DetailsHistory", "Back: navigating to previous '${previous.title}'")

                val intent = Intent(this@TVShowsDetailsScreenCompose, TVShowsDetailsScreenCompose::class.java).apply {
                    putExtra("media_type", previous.mediaType)
                    putExtra("media_id",          previous.tmdbId)
                    putExtra("media_title", previous.title)
                    putExtra("poster_url",        previous.posterUrl ?: "")
                    putExtra("backdrop_url",      previous.backdropUrl ?: "")
                    putExtra("youtube_trailer_url", previous.trailerUrl ?: "")
                    putExtra("overview",          previous.overview ?: "")
                    putExtra("vote_average",      previous.voteAverage ?: 0.0)
                    putExtra("contentRating",     previous.contentRating ?: "N/A")
                    putExtra("original_language", previous.originalLanguage ?: "N/A")
                    putExtra("release_date",      previous.releaseDate ?: "N/A")
                    putExtra("budget",            previous.budget ?: 0L)
                    putExtra("revenue",           previous.revenue ?: 0L)
                    putExtra("logo_url",          previous.logoUrl ?: "")
                    putExtra("genres",            previous.genres?.joinToString(", ") ?: "N/A")
                    putExtra("number_of_seasons", previous.numberOfSeasons ?: 0)
                    putExtra("status",            previous.status ?: "N/A")
                    putExtra("start_in_extras", true)
                    putExtra("PROFILE_ID",         profileId)
                    sourceScreen?.let { putExtra("SOURCE_SCREEN", it) }
                    searchQuery?.let  { putExtra("SEARCH_QUERY",  it) }
                }
                startActivity(intent)
                finish()
            }
        })


        // Extract media data from intent
        mediaType = intent.getStringExtra("media_type") ?: "tv"
        contentRating = intent.getStringExtra("contentRating")
        originalLanguage = intent.getStringExtra("original_language")
        mediaId = intent.getIntExtra("media_id", 0)
        voteAverage = intent.getDoubleExtra("vote_average", 0.0)
        mediaTitle = intent.getStringExtra("media_title") ?: ""
        posterUrl = intent.getStringExtra("poster_url")
        backdropUrl = intent.getStringExtra("backdrop_url")
        trailerUrl = intent.getStringExtra("youtube_trailer_url")
        overview = intent.getStringExtra("overview")
        logoUrl = intent.getStringExtra("logo_url")
        releaseDate = intent.getStringExtra("release_date")
        genres = intent.getStringExtra("genres")
        numberOfSeasons = intent.getIntExtra("number_of_seasons", 0)
        status = intent.getStringExtra("status")
        profileId = intent.getIntExtra("PROFILE_ID", -1)
        sourceScreen = intent.getStringExtra("SOURCE_SCREEN")
        searchQuery  = intent.getStringExtra("SEARCH_QUERY")

        val startInExtras  = intent.getBooleanExtra("start_in_extras", false)
        val lastSeason     = intent.getIntExtra("last_season",  1)
        val lastEpisode    = intent.getIntExtra("last_episode", 1)
        // Resume entries (Continue Watching) explicitly attach these extras
        // even for S1E1; fresh opens (search/trending/recommendations) never
        // do. Presence — not "episode > 1" — is what should drive episode focus.
        val resumeToEpisode = intent.hasExtra("last_season") || intent.hasExtra("last_episode")
        val recommendationsRepository = RecommendationsRepository()
        val episodesRepository = EpisodesRepository()


        setContent {
            TVShowsDetailsScreenContent(
                mediaTitle = mediaTitle,
                mediaType = mediaType,
                mediaId = mediaId,
                backdropUrl = backdropUrl,
                trailerUrl = trailerUrl,
                logoUrl = logoUrl,
                overview = overview,
                contentRating = contentRating,
                originalLanguage = originalLanguage,
                voteAverage = voteAverage,
                releaseDate = releaseDate,
                numberOfSeasons = numberOfSeasons,
                status = status,
                genres = genres,
                tmdbId = mediaId,
                startInExtras = startInExtras,
                lastSeason    = lastSeason,
                lastEpisode   = lastEpisode,
                resumeToEpisode = resumeToEpisode,
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
private fun getStreamingSources(
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
 * How long a first press on an unaired episode stays armed.
 *
 * Long enough to read the notice and answer it; short enough that the answer is
 * clearly to THAT question. 6s is about two unhurried reads of the band.
 */
private const val UNAIRED_OVERRIDE_WINDOW_MS = 6_000L

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


    var selectedSeason by remember { mutableIntStateOf(1) }  // default to Season 1
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    // 🔹 Focus requesters for episode tiles (rebuilds safely when episodes change)
    val episodeFocusRequesters = remember(episodes) {
        List(episodes.size) { FocusRequester() }
    }

    val seasonFocusRequesters = remember(numberOfSeasons) {
        if (numberOfSeasons > 0) {
            List(numberOfSeasons) { FocusRequester() }
        } else {
            emptyList()
        }
    }



    var lastFocusedEpisodeIndex by remember { mutableIntStateOf(0) }  // ✅ Remember the last focused episode so RIGHT from seasons always restores correctly
    val episodesRowState = rememberLazyListState() // ✅ Give LazyRow a state so we can scroll before restoring focus
    // Hold-to-repeat throttle timestamp for the episode row (see EPISODE_NAV_REPEAT_MS).
    // Plain 1-slot array (NOT observable state) so ticking it never triggers recomposition.
    val episodeNavGate = remember { longArrayOf(0L) }

    // Hoisted season-column scroll state (the LazyColumn below now uses this
    // instead of its own local rememberLazyListState) so the episode row's Back
    // handler can scroll a season button into view before focusing it.
    val seasonsListState = rememberLazyListState()

    // Reliable focus for a season button. A season button lives in a LazyColumn,
    // so its FocusRequester only attaches once the row is composed AND placed.
    // Presence in visibleItemsInfo == laid out == requester attached. We scroll
    // it in, wait for that, then requestFocus — with retries so a busy main
    // thread (entering from Continue Watching) can't make Back silently no-op.
    suspend fun focusSeasonReliably(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= seasonFocusRequesters.size) return
        seasonsListState.scrollToItem(targetIndex)
        var attempts = 0
        while (attempts < 40) {
            awaitFrame()
            val laidOut = seasonsListState.layoutInfo
                .visibleItemsInfo.any { it.index == targetIndex }
            if (laidOut) {
                seasonFocusRequesters.getOrNull(targetIndex)?.requestFocus()
                return
            }
            attempts++
        }
        // Last-ditch: try anyway so focus is never stranded on the episode tile.
        seasonFocusRequesters.getOrNull(targetIndex)?.requestFocus()
    }




    var selectedEpisodeOverlayIndex by remember { mutableIntStateOf(-1) }

    // ── THE WAY PAST THE UNAIRED GATE ─────────────────────────────────
    //
    // Which unaired episode has been pressed once, and so will go through on the
    // next press. -1 for none.
    //
    // WHY AN OVERRIDE EXISTS AT ALL. isEpisodeUnaired is a PREDICTION made on
    // this device from TMDB's air_date -- it is not a fact reported by the
    // sources that would actually serve the episode. The two can disagree:
    // provisional simulcast dates, a schedule that moved, a source that has the
    // file early. Without a way past, one wrong date removes an episode with no
    // appeal, and that is the only thing in this app that cannot be retried.
    //
    // A SECOND PRESS, not a longer one: it needs no new gesture to learn, it
    // cannot happen by accident, and the first press has already said why.
    var unairedArmedIndex by remember { mutableIntStateOf(-1) }

    // ✅ Focus requesters for overlay buttons.
    // Pool sized to the largest provider set we might show — the tv source list
    // (the anime overlay shows fewer). Deriving the size from getStreamingSources()
    // means adding a source there also grows this pool, so the inline grid can
    // never index past it. coerceAtLeast(2) keeps the anime DuB/SuB pair safe.
    val providerFocusRequesters = remember {
        List(getStreamingSources(mediaType, mediaId, 1, 1).size.coerceAtLeast(2)) { FocusRequester() }
    }

    // ✅ Position of the selected episode still in root coordinates
    val density = LocalDensity.current
    var overlayAnchor by remember { mutableStateOf<Offset?>(null) }


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

    // Focus requesters
    // Focus requesters
    val favoriteFocusRequester = remember { FocusRequester() }
    val extrasFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }

    // Armed state is a MOMENT, not a mode. Someone who presses once, reads the
    // notice and walks away should not find the episode silently pressable when
    // they come back -- the second press has to be a deliberate answer to the
    // first, which means it has to be soon after it.
    LaunchedEffect(unairedArmedIndex) {
        if (unairedArmedIndex >= 0) {
            delay(UNAIRED_OVERRIDE_WINDOW_MS)
            unairedArmedIndex = -1
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

    // Set true by the focused episode tile's onFocusChanged (see Edit 3).
    // Lets the initial-focus effect below confirm focus actually landed
    // instead of assuming a fire-and-forget requestFocus() succeeded.
    var episodeFocusLanded by remember { mutableStateOf(false) }

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
    LaunchedEffect(episodes) {
        if (episodes.isEmpty()) return@LaunchedEffect

        if (isInitialEpisodeLoad) {
            val targetIndex = (lastEpisode - 1).coerceIn(0, episodes.lastIndex)
            lastFocusedEpisodeIndex = targetIndex
            episodesRowState.scrollToItem(targetIndex)

            episodeFocusLanded = false
            var landed = false
            var attempts = 0
            while (attempts < 40 && !landed) {
                awaitFrame()
                val laidOut = episodesRowState.layoutInfo
                    .visibleItemsInfo.any { it.index == targetIndex }
                if (laidOut) {
                    episodeFocusRequesters.getOrNull(targetIndex)?.requestFocus()
                    awaitFrame()
                    if (episodeFocusLanded) landed = true
                }
                attempts++
            }

            // Never strand the user with no focus: if the episode tile truly
            // never took focus, un-block the header and focus Favorites.
            if (!landed) {
                Log.w("TVDetails", "⚠️ Episode focus never landed — falling back to header")
                blockHeaderInitialFocus = false
                awaitFrame()
                favoriteFocusRequester.requestFocus()
            }

            blockHeaderInitialFocus = false
            isInitialEpisodeLoad = false
        } else {
            lastFocusedEpisodeIndex = 0
            episodesRowState.scrollToItem(0)
        }
    }


    val activity = LocalActivity.current as? TVShowsDetailsScreenCompose
    val favoriteProfileId = activity?.profileId ?: -1

    // Favorites: checked on open, toggled by the header button -- see
    // FavoriteState. This screen sends the title with a toggle (§10, #3).
    val favorite = rememberFavoriteState(
        scope     = coroutineScope,
        profileId = favoriteProfileId,
        tmdbId    = tmdbId,
        mediaType = mediaType,
        title     = mediaTitle,
    )

    // ── Watched-episode markers ──────────────────────────────────────────────
    // (season, episode) pairs this profile has started, for THIS show. Drives the
    // corner fold on each episode card. Fetched once per show; failures leave the
    // set empty so the grid simply shows no markers (never blocks the screen).
    var watchedEpisodes by remember(tmdbId) { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }

    suspend fun refreshWatchedEpisodes() {
        if (favoriteProfileId == -1) return
        try {
            val marks = withContext(Dispatchers.IO) {
                ApiClient.bluehiveApi.getWatchedEpisodes(favoriteProfileId, tmdbId)
            }
            watchedEpisodes = marks.map { it.season_number to it.episode_number }.toSet()
        } catch (e: Exception) {
            Log.w("TVShowsDetails", "watched-episodes fetch failed (non-fatal): ${e.message}")
        }
    }

    LaunchedEffect(tmdbId, favoriteProfileId) { refreshWatchedEpisodes() }

    // Re-read on every resume. Nothing is marked optimistically any more — the
    // marker follows the backend row, and that row is only written once the player
    // confirms video is actually playing. Coming back from the player is therefore
    // the moment a genuinely-watched episode should light up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tmdbId, favoriteProfileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { refreshWatchedEpisodes() }
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
    var pendingSeason      by remember { mutableIntStateOf(1) }
    var pendingEpisode     by remember { mutableIntStateOf(1) }
    var pendingEpisodeName by remember { mutableStateOf<String?>(null) }
    var lastStreamFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

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
        request      = { PlaybackRequest.Episode(tmdbId, pendingSeason, pendingEpisode, pendingEpisodeName) },
        onLaunch     = {},
        restoreFocus = {
            coroutineScope.launch {
                awaitFrame()
                try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
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
        lastStreamFocusRequester?.let { streamButton ->
            listOfNotNull(
                streamButton,
                episodeFocusRequesters.getOrNull(lastFocusedEpisodeIndex),
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
        request    = { PlaybackRequest.Episode(tmdbId, pendingSeason, pendingEpisode, pendingEpisodeName) },
        onStart    = { stopTrailerAndSelectEpisode(pendingSeason, pendingEpisode, pendingEpisodeName) },
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

    LaunchedEffect(showNotYetAvailable) {
        if (showNotYetAvailable) { delay(4000); showNotYetAvailable = false }
    }

    // ── Season loads are SERIALISED, and stale answers are discarded ─────────
    //
    // This used to fire a bare coroutineScope.launch per call with no job held
    // and no staleness check, while nothing debounced the season buttons or
    // disabled them during a load. Two presses in quick succession therefore left
    // two requests in flight, and the FIRST to return cleared the spinner and
    // painted its episodes — under the newly highlighted season heading.
    //
    // That is worse than a cosmetic mismatch. Opening an episode from that grid
    // calls onEpisodeSelected(selectedSeason, episodeNumber, ...) and sets
    // pendingSeason = selectedSeason, so extractAndPlay and the player extras get
    // the season the user is LOOKING at paired with an episode number from a
    // different season — the wrong episode plays, and that pair is what lands in
    // watch history and drives the watched-corner markers.
    //
    // Guarded by a sequence number rather than by comparing seasonNumber, because
    // that comparison depends on selectedSeason being assigned before the call
    // (it is today, at the button) and would silently rot if a future caller
    // ordered it differently. The sequence answers the only question that
    // matters: is this still the newest request?
    var seasonLoadSeq by remember { mutableIntStateOf(0) }
    var seasonLoadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 🔹 Helper to load episodes for a given season
    fun loadSeasonEpisodes(seasonNumber: Int) {
        seasonLoadJob?.cancel()
        val mySeq = ++seasonLoadSeq
        seasonLoadJob = coroutineScope.launch {
            isLoadingEpisodes = true
            try {
                val response = episodesRepository.getSeasonEpisodes(
                    tmdbId = tmdbId,
                    seasonNumber = seasonNumber
                )
                if (mySeq != seasonLoadSeq) {
                    Log.d("TVEpisodes", "S$seasonNumber arrived late — discarding")
                    return@launch
                }
                episodes = response.episodes
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Superseded by a newer season press. Not a failure, and it must
                // not reach the catch below or it would blank the grid the newer
                // request is about to fill.
                throw e
            } catch (e: Exception) {
                Log.e("TVEpisodes", "Error loading episodes for S$seasonNumber", e)
                if (mySeq == seasonLoadSeq) episodes = emptyList()
            } finally {
                // Only the newest request owns the spinner. Clearing it from a
                // superseded one is what let the previous season's episodes read
                // as final while the real request was still out.
                if (mySeq == seasonLoadSeq) isLoadingEpisodes = false
            }
        }
    }

    // 🔹 On first entry, auto-load Season 1 if we have seasons
    LaunchedEffect(tmdbId, numberOfSeasons) {
        if (numberOfSeasons > 0) {
            selectedSeason = lastSeason.coerceIn(1, numberOfSeasons)
            val prefetchedEpisodes = prefetch?.episodes
            if (prefetchedEpisodes != null && lastSeason == 1) {
                episodes = prefetchedEpisodes
                isLoadingEpisodes = false
                Log.d("Prefetch", "✅ Episodes loaded from prefetch cache (${prefetchedEpisodes.size} eps)")
            } else {
                loadSeasonEpisodes(selectedSeason)
            }
        }
    }

    // The season check can leave off the selected season -- the screen was
    // opened on a season with nothing aired. Move to the newest listed one, so
    // the grid never shows a season the column does not.
    LaunchedEffect(listedSeasons) {
        val listed = listedSeasons ?: return@LaunchedEffect
        if (selectedSeason > listed) {
            selectedSeason = listed
            loadSeasonEpisodes(listed)
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
            favoriteFocusRequester = favoriteFocusRequester,
            extrasFocusRequester   = extrasFocusRequester,
            trailerFocusRequester  = trailerFocusRequester,
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
            extrasFocusRequester.requestFocus()
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

                    // 🔴 Inline Seasons LazyRow (no separate TVSeasonsRow composable)
                    // Lists Season 1..listedSeasons, so a season with nothing
                    // aired yet never renders. Waits for that answer rather than
                    // listing every season and then dropping one.
                    val listed = listedSeasons ?: 0
                    if (numberOfSeasons > 0 && listed > 0) {
                        val seasons = remember(listed) { (1..listed).toList() }
                        val listState = seasonsListState

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 28.5.dp, y = 313.25.dp)   // tweak these to line up visually
                                .width(180.25.dp)
                                .height(201.5.dp)
                                //.background(Color.Red)            // debug; remove later
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement  = Arrangement.spacedBy((-6).dp),   // ① between items
                                contentPadding = PaddingValues(horizontal = 11.5.dp, vertical = 19.5.dp)      // ② padding at edges and top margin
                            ) {

                                itemsIndexed(seasons) { seasonIndex, seasonNumber ->
                                    SeasonButton(
                                        label = "Season $seasonNumber",
                                        isSelected = (seasonNumber == selectedSeason),
                                        fontFamily = passionFont,
                                        focusRequester = seasonFocusRequesters[seasonIndex],
                                                modifier = Modifier.onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionDown -> {
                                                        if (seasonIndex == seasons.lastIndex) {
                                                            // Last season — nothing below it. Swallow the
                                                            // key so holding DOWN at the bottom stops
                                                            // repeating the nav sound (and doesn't move).
                                                            true
                                                        } else {
                                                            BlueHiveApplication.playTitleCardNavigation()
                                                            false
                                                        }
                                                    }
                                                    Key.DirectionUp -> {
                                                        BlueHiveApplication.playTitleCardNavigation()
                                                        false
                                                    }
                                                    Key.DirectionRight -> {
                                                        if (episodes.isNotEmpty() && episodeFocusRequesters.isNotEmpty()) {
                                                            BlueHiveApplication.playHoverSound()

                                                            val targetIndex = lastFocusedEpisodeIndex
                                                                .coerceIn(0, episodeFocusRequesters.lastIndex)

                                                            coroutineScope.launch {
                                                                episodesRowState.scrollToItem(targetIndex)
                                                                episodeFocusRequesters
                                                                    .getOrNull(targetIndex)
                                                                    ?.requestFocus()
                                                                    ?: episodeFocusRequesters.first().requestFocus()
                                                            }

                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        },
                                        onClick = {
                                            if (selectedSeason != seasonNumber) {
                                                selectedSeason = seasonNumber
                                                loadSeasonEpisodes(seasonNumber)
                                            }
                                        },
                                    )
                                }

                            }
                        }
                    }

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

            // ================================================================> episode lazy row
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 236.dp, y = 299.5.dp) // adjust to sit between side decorations
                    .width(683.75.dp)
                    .height(260.dp) //122.25
                //.background(Color.Red)
            ) {
                when {
                    isLoadingEpisodes -> {
                        // Simple loading state (optional)
                        Text(
                            text = "Loading episodes...",
                            color = Color(0xFFAAAAAA),
                            fontFamily = dongleRegular,
                            fontSize = 23.sp,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .offset(y = 45.dp)
                        )
                    }

                    episodes.isEmpty() -> {
                        // Optional empty state
                        Text(
                            text = "No episodes found for Season $selectedSeason",
                            color = Color(0xFFAAAAAA),
                            fontFamily = dongleRegular,
                            fontSize = 23.sp,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .offset(y = 45.dp)
                        )
                    }

                    else -> {
                        val totalEpisodes = episodes.size

                        // ✅ "Exposure" style darkening using a color matrix (no alpha usage)
                        val darkenFilter = remember {
                            ColorFilter.colorMatrix(
                                ColorMatrix().apply {
                                    // Lower RGB scale = darker image
                                    setToScale(0.40f, 0.40f, 0.40f, 1f)
                                }
                            )
                        }

                        // The border diapers whenever the still path is focused
                        val episodeShape = remember { RoundedCornerShape(7.5.dp) }

                        val baseStillModifier = remember(episodeShape) {
                            Modifier
                                .width(174.27.dp)
                                .height(97.5.dp)
                                .clip(episodeShape)
                        }


                        LazyRow(
                            state = episodesRowState,
                            userScrollEnabled = selectedEpisodeOverlayIndex == -1,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(
                                start = 18.dp,   // same as before
                                end = 40.dp,     // ⬅ bump this up until the last card clears the decor nicely
                                top = 12.dp,
                                bottom = 12.dp
                            )
                        ) {

                            itemsIndexed(
                                items = episodes,
                                key = { _, ep -> ep.tmdbEpisodeId }
                            ) { index, episode ->

                                var isEpisodeFocused by remember { mutableStateOf(false) }

                                val isEpisodeSelectOverlay = selectedEpisodeOverlayIndex == index

                                // Watched marker for THIS episode. watchedEpisodes changes only on
                                // load and when the user starts a stream, so reading it per item
                                // costs nothing during D-pad navigation.
                                val isWatched = (selectedSeason to episode.episodeNumber) in watchedEpisodes

                                // Still FOCUSABLE when unaired -- people want to read what is
                                // coming -- but not pressable. See the scrim below and the
                                // Enter branch above.
                                val isUnaired = remember(episode.airDate, todayYmd) {
                                    isEpisodeUnaired(episode.airDate, todayYmd)
                                }

                                // Try the episode still first, fallback to show's backdropUrl
                                val stillUrl = episode.stillImageUrl ?: backdropUrl

                                val titleText = episode.title

                                // ✅ If your Episode model uses a different field name, change this line.
                                val episodeOverviewText = episode.overview ?: ""


                                // 👇 true only when the whole item is within the viewport
                                val isFullyVisible by remember(episodesRowState, index) {
                                    derivedStateOf {
                                        val layoutInfo = episodesRowState.layoutInfo
                                        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                            ?: return@derivedStateOf false

                                        val itemStart = itemInfo.offset
                                        val itemEnd   = itemInfo.offset + itemInfo.size

                                        val viewportStart = layoutInfo.viewportStartOffset
                                        val viewportEnd   = layoutInfo.viewportEndOffset

                                        itemStart >= viewportStart && itemEnd <= viewportEnd
                                    }
                                }

                                // ✅ This outer column is the "focus target"
                                //    It gives the title real space UNDER the still image.
                                Column(
                                    modifier = Modifier
                                        // keep item layout sized to the still image (prevents spacing weirdness)
                                        .width(174.27.dp)
                                        .focusRequester(episodeFocusRequesters[index])
                                        .onFocusChanged { state ->
                                            // hasFocus stays true when any child (like provider buttons) is focused
                                            isEpisodeFocused = state.hasFocus
                                            if (state.hasFocus) {
                                                lastFocusedEpisodeIndex = index
                                                episodeFocusLanded = true  // confirms initial focus actually landed
                                                // Arming belongs to ONE episode. Landing on a
                                                // different one drops it, so a press here can
                                                // never be the second half of a pair begun on a
                                                // neighbour three cards away.
                                                if (unairedArmedIndex != index) unairedArmedIndex = -1
                                            }
                                        }

                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.Back -> {
                                                        when {
                                                            selectedEpisodeOverlayIndex == index -> {
                                                                selectedEpisodeOverlayIndex = -1
                                                                true
                                                            }
                                                            (listedSeasons ?: numberOfSeasons) > 1 && seasonFocusRequesters.isNotEmpty() -> {
                                                                val seasonIdx = (selectedSeason - 1)
                                                                    .coerceIn(0, seasonFocusRequesters.lastIndex)
                                                                BlueHiveApplication.playHoverSound()
                                                                // Don't assume the season button is attached. On the
                                                                // Continue Watching path the episode tile is focused
                                                                // programmatically under load, before the season
                                                                // LazyColumn has placed its buttons — a direct
                                                                // requestFocus() no-ops and Back appears dead. Scroll
                                                                // the season in, wait for layout, then focus.
                                                                coroutineScope.launch {
                                                                    focusSeasonReliably(seasonIdx)
                                                                }
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    }


                                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                                        if (isUnaired && unairedArmedIndex != index) {
                                                            // REFUSED, NOT IGNORED. Silence here is
                                                            // indistinguishable from a dropped press,
                                                            // and the answer to that is to press again
                                                            // harder. The sound says the box heard it
                                                            // -- and the notice says what a second
                                                            // press will do.
                                                            BlueHiveApplication.playErrorSound()
                                                            unairedArmedIndex = index
                                                        } else {
                                                            BlueHiveApplication.playClickSound()
                                                            unairedArmedIndex = -1
                                                            selectedEpisodeOverlayIndex = index
                                                        }
                                                        true
                                                    }


                                                    Key.DirectionDown -> {
                                                        if (selectedEpisodeOverlayIndex == index) {
                                                            // move focus into the provider grid
                                                            providerFocusRequesters.firstOrNull()?.requestFocus()
                                                            true
                                                        } else {
                                                            // Nothing below a tile now that the corner button is
                                                            // gone. Consumed with no move, so the press is silent
                                                            // rather than sending focus somewhere arbitrary.
                                                            true
                                                        }
                                                    }
                                                    Key.DirectionLeft, Key.DirectionRight -> {
                                                        if (selectedEpisodeOverlayIndex == index) {
                                                            true
                                                        } else if (event.key == Key.DirectionRight && index == episodes.lastIndex) {
                                                            // Dead-end: RIGHT on the last episode has nowhere to go —
                                                            // consume it silently (no scroll sound, no no-op traversal).
                                                            true
                                                        } else {
                                                            // Hold-to-repeat throttle (see EPISODE_NAV_REPEAT_MS): swallow
                                                            // repeats that land sooner than the interval so holding L/R
                                                            // scrolls at a controlled pace, like the Continue Watching list.
                                                            val now = SystemClock.uptimeMillis()
                                                            if (now - episodeNavGate[0] < EPISODE_NAV_REPEAT_MS) {
                                                                true   // too soon — consume, no move (and no sound)
                                                            } else {
                                                                episodeNavGate[0] = now
                                                                BlueHiveApplication.playTitleCardNavigation()
                                                                false  // allow the move
                                                            }
                                                        }
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable()
                                ) {

                                    // ✅ Air date — above the still, right-aligned. Same font and
                                    // focus colours as the episode title below it, two points smaller.
                                    //
                                    // Rendered UNCONDITIONALLY, unlike that title. The title sits
                                    // UNDER the image, so it can come and go without disturbing
                                    // anything; this sits ABOVE it, where appearing and disappearing
                                    // would shove every thumbnail up and down as the row scrolls.
                                    // So no isFullyVisible gate here — and a blank string still
                                    // occupies its line, which keeps unaired episodes (null
                                    // air_date) exactly the same height as aired ones.
                                    // Measured at its NATURAL size, then reported as zero height and
                                    // placed above the baseline — see ZeroHeightAbove.
                                    ZeroHeightAbove {
                                        Text(
                                            text = formatEpisodeAirDate(episode.airDate),
                                            fontFamily = dongleRegular,
                                            fontSize = 11.sp,          // episode title is 13.sp
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (!isEpisodeFocused) Color(0xFF737373) else Color.White,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    // ✅ Image/BADGE/GLOW stack stays inside this Box
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(174.27.dp)
                                            .height(97.5.dp)
                                            .onGloballyPositioned { coords ->
                                                // Only care about the box for the *selected* episode’s overlay
                                                if (isEpisodeSelectOverlay) {
                                                    overlayAnchor = coords.positionInRoot()
                                                }
                                            }
                                    ) {

                                        // ✅ Glow behind image when focused
                                        if (isEpisodeFocused && !isEpisodeSelectOverlay) {
                                            Image(
                                                painter = painterResource(id = R.drawable.episode_focus_glow),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .offset(x = (-0.25).dp, y = (-0.3).dp) // offset the position of the glow
                                                    .wrapContentSize(unbounded = true) // allow the glow to exceed the layout bounds
                                                    // force exact glow size even if parent is smaller
                                                    .requiredSize(width = 206.3.dp, height = 129.dp),  // length and width of the focus glow
                                                contentScale = ContentScale.FillBounds
                                            )
                                        }

                                        // ✅ Still image centered over glow
                                        if (!stillUrl.isNullOrBlank()) {

                                            val stillModifier = baseStillModifier.then(
                                                if (!isEpisodeFocused || isEpisodeSelectOverlay) {
                                                    Modifier.border(
                                                        width = 1.1.dp,
                                                        color = Color(0xFF505050), // border color
                                                        shape = episodeShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )

                                            AsyncImage(
                                                model = stillUrl,
                                                contentDescription = episode.title,
                                                modifier = stillModifier,
                                                contentScale = ContentScale.Crop,
                                                colorFilter = if (!isEpisodeFocused) darkenFilter else null
                                            )

                                            // ✅ Episode badge – bottom-left overlay, on top of still
                                            EpisodeBadge(
                                                current = episode.episodeNumber,
                                                total = totalEpisodes,
                                                isFocused = isEpisodeFocused,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(start = 4.dp, bottom = 76.5.dp)
                                            )

                                            // ✅ Watched marker — corner fold + tick, TOP-RIGHT so it
                                            // never collides with the episode-number badge. Drawn in a
                                            // single Canvas (no Material-Icons dependency) and clipped
                                            // to the card shape so it follows the rounded corner.
                                            if (isWatched) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(episodeShape)
                                                ) {
                                                    WatchedCornerFold(
                                                        modifier = Modifier.align(Alignment.TopEnd)
                                                    )
                                                }
                                            }
                                        }


                                        // ✅ UNAIRED — dark scrim over the still, saying why.
                                        //
                                        // Over the still rather than instead of it: the card still
                                        // has to read as the episode it is, so the artwork stays
                                        // visible underneath. Drawn after the badge and the watched
                                        // fold so it covers both, and before the select overlay,
                                        // which an unaired episode can never reach anyway -- the
                                        // press that raises it is refused above.
                                        if (isUnaired) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .clip(episodeShape)
                                                    .background(Color(0xD9101014))
                                                    // Border re-added ON TOP, same as the select
                                                    // overlay does, or the scrim squares the corners.
                                                    .border(
                                                        width = 1.1.dp,
                                                        color = Color(0xFF505050),
                                                        shape = episodeShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "EPISODE NOT AVAILABLE",
                                                    fontFamily = passionFont,
                                                    fontSize = 15.sp,
                                                    lineHeight = 17.sp,
                                                    color = Color(0xFFD6D6DB),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp)
                                                )
                                            }
                                        }

                                        if (isEpisodeSelectOverlay) {  // overlay for when a episode is selected
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .clip(episodeShape)
                                                    .background(Color.Black)
                                                    // ✅ re-add border ON TOP of the overlay
                                                    .border(
                                                        width = 1.1.dp,
                                                        color = Color(0xFF505050),
                                                        shape = episodeShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "SELECT A SITE BELOW TO WATCH EPISODE ON",
                                                    fontFamily = passionFont,
                                                    fontSize = 20.sp,
                                                    color = Color.White,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    // ✅ add breathing room left/right like your Figma
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp)
                                                )
                                            }
                                        }
                                    }



                                    // ✅ Episode title under each respective image
                                    // Darken state whenever the episode isn't focused
                                    val titleYOffset by animateDpAsState(
                                        targetValue = if (isEpisodeFocused) 1.5.dp else (-1.5).dp,
                                        label = "episodeTitleYOffset"
                                    )

                                    if (!isEpisodeSelectOverlay && isFullyVisible) {
                                        Text(
                                            text = titleText,
                                            fontFamily = dongleRegular,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (!isEpisodeFocused) Color(0xFF737373) else Color.White,
                                            modifier = Modifier
                                                .offset(x = 4.dp, y = titleYOffset)
                                        )
                                    }

                                    // ✅ NEW: Overview appears only when focused, with 1.5s delayed fade-in
                                    var showOverview by remember(index) { mutableStateOf(false) }

                                    LaunchedEffect(isEpisodeFocused, episodeOverviewText, isEpisodeSelectOverlay) {
                                        if (!isEpisodeSelectOverlay && isEpisodeFocused && episodeOverviewText.isNotBlank()) {
                                            showOverview = false
                                            delay(1500)
                                            if (isEpisodeFocused) {
                                                showOverview = true
                                            }
                                        } else {
                                            showOverview = false
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = showOverview && !isEpisodeSelectOverlay,
                                        enter = fadeIn(animationSpec = tween(durationMillis = 280)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = 160))
                                    ) {
                                        val overviewAlpha by animateFloatAsState(
                                            targetValue = if (showOverview) 1f else 0f,
                                            animationSpec = tween(
                                                durationMillis = if (showOverview) 420 else 260,
                                                easing = LinearOutSlowInEasing
                                            ),
                                            label = "overviewAlpha"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .padding(start = 20.dp, top = 6.dp)
                                                .offset(x = (-9.7).dp, y = (-2).dp) // position of the overview background
                                                .wrapContentSize(unbounded = true)
                                                .graphicsLayer { alpha = overviewAlpha }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .requiredWidth(196.dp)
                                                    .heightIn(max = 98.5.dp)
                                                    .background(
                                                        color = Color(0xFF949494).copy(alpha = 0.20f), // opacity of the overview background
                                                        shape = RoundedCornerShape(6.5.dp)
                                                    )
                                                    .padding(start = 6.dp, top = 2.dp, end = 5.dp, bottom = 0.dp)
                                            ) {
                                                Text(
                                                    text = episodeOverviewText,
                                                    fontFamily = dongleRegular,
                                                    lineHeight = 11.sp,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFFE7E3E3),
                                                    maxLines = 8,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                }
                            }

                        }
                    }
                }
            }
            // ================================================================> episode lazy row



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


            if (
                !extras.isOn &&
                selectedEpisodeOverlayIndex != -1 &&
                overlayAnchor != null &&
                selectedEpisodeOverlayIndex in episodes.indices
            ) {
                val anchor = overlayAnchor!!

                val selectedEpisode = episodes[selectedEpisodeOverlayIndex]

                // fall back to index+1 if episodeNumber is null
                val episodeNumber = selectedEpisode.episodeNumber

                // Build sources for this episode.
                // Japanese animation → 2 buttons (Miruro + VidFast); else the standard set.
                val episodeSources by produceState(
                    initialValue = emptyList(),
                    mediaType, mediaId, selectedSeason, episodeNumber, genres, originalLanguage
                ) {
                    if (isJapaneseAnimation(genres, originalLanguage)) {
                        value = buildJapaneseAnimationSources(
                            mediaType     = mediaType,
                            tmdbId        = mediaId,
                            seasonNumber  = selectedSeason,
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
                            seasonNumber = selectedSeason,
                            episodeNumber = episodeNumber
                        )
                        com.example.bluehive.api.SourceCatalog
                            .warm(BlueHiveApplication.appContext ?: return@produceState)
                        value = getStreamingSources(
                            mediaType = mediaType,
                            mediaId = mediaId,
                            seasonNumber = selectedSeason,
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
                            focusRequesters = providerFocusRequesters,
                            onDub = { url ->
                                val epName = selectedEpisode.title
                                stopTrailerAndSelectEpisode(selectedSeason, episodeNumber, epName)
                                // NOT marked watched here. Picking DuB only opens the
                                // server prompt — extraction can still fail, find no
                                // servers, or be cancelled. The mark happens where the
                                // stream actually plays (see the extractor result
                                // handler, alongside logWatchHistory).
                                lastStreamFocusRequester = providerFocusRequesters.getOrNull(0)
                                anime.pendingUrl         = url
                                anime.pendingDub         = true
                                pendingSeason      = selectedSeason
                                pendingEpisode     = episodeNumber
                                pendingEpisodeName = epName
                                anime.showDefaultPrompt  = true
                            },
                            onSub = { url ->
                                val epName = selectedEpisode.title
                                stopTrailerAndSelectEpisode(selectedSeason, episodeNumber, epName)
                                // NOT marked watched here — same reason as onDub above.
                                lastStreamFocusRequester = providerFocusRequesters.getOrNull(1)
                                anime.pendingUrl         = url
                                anime.pendingDub         = false
                                pendingSeason      = selectedSeason
                                pendingEpisode     = episodeNumber
                                pendingEpisodeName = epName
                                anime.showDefaultPrompt  = true
                            },
                            onUnavailable = { showNotYetAvailable = true },
                            onBack = {
                                selectedEpisodeOverlayIndex = -1
                                val targetIndex = lastFocusedEpisodeIndex
                                    .coerceIn(0, episodeFocusRequesters.lastIndex)
                                episodeFocusRequesters[targetIndex].requestFocus()
                            }
                        )
                    } else {
                        EpisodeProviderInlineButtons(
                            passionFont = passionFont,
                            sources = episodeSources,
                            focusRequesters = providerFocusRequesters,
                            onSourceSelected = { source ->
                                val epName = selectedEpisode.title

                                // Remember which button launched this so focus comes
                                // back here when the player exits (THE RULE).
                                lastStreamFocusRequester = providerFocusRequesters.getOrNull(
                                    episodeSources.indexOfFirst { it.name == source.name }
                                )

                                // Every source: server-side m3u8 extraction, then our
                                // own player. The server reports every source as
                                // extractable; one it could not extract fails into the
                                // same chain as a dead one. The episode overlay STAYS
                                // open behind the player, which keeps this button
                                // attached so focus can return to it on the way back.
                                pendingSeason      = selectedSeason
                                pendingEpisode     = episodeNumber
                                pendingEpisodeName = epName
                                sourcePlayback.start(source)
                            },
                            onBack = {
                                selectedEpisodeOverlayIndex = -1

                                val targetIndex = lastFocusedEpisodeIndex
                                    .coerceIn(0, episodeFocusRequesters.lastIndex)

                                episodeFocusRequesters[targetIndex].requestFocus()
                            },
                        )
                    }
                }
            }
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
                        try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
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

            // Says what the first press did and what a second will do. Carries the
            // date so the viewer can judge for themselves whether we have it wrong --
            // which is the entire point of offering the override.
            if (unairedArmedIndex >= 0) {
                val armedDate = formatEpisodeAirDate(
                    episodes.getOrNull(unairedArmedIndex)?.airDate
                )
                DetailsToast(
                    if (armedDate.isNotEmpty())
                        "This episode airs $armedDate — press again to try anyway"
                    else
                        "This episode hasn't aired yet — press again to try anyway",
                    dongleRegular,
                    centered = true,
                    maxWidth = 720.dp,
                )
            }

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
