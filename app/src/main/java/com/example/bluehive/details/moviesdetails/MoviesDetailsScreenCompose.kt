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
import com.example.bluehive.utilities.launchIfActive
import com.example.bluehive.utilities.runCatchingCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.focus.focusRequester
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.ExtractOutcome
import com.example.bluehive.sidebarComponents.FavoritesScreenActivity
import com.example.bluehive.sidebarComponents.HistoryScreenActivity
import com.example.bluehive.searchBarComponent.SearchBarActivity
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.webview.miruro.MiruroPlayerActivity
import com.example.bluehive.auth.SessionManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import com.example.bluehive.webview.BeeLogo
import com.example.bluehive.webview.ExtractionFocusTrap
import com.example.bluehive.webview.ExtractionLoadingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.foundation.background
import com.example.bluehive.api.extractStreamAwait
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.SourceScreen
import com.example.bluehive.details.shared.playback.miruroSourceLabel
import com.example.bluehive.details.shared.playback.usesBackendExtraction
import com.example.bluehive.details.shared.playback.PlaybackErrorOverlay
import com.example.bluehive.details.shared.playback.PlaybackLoadingOverlay
import com.example.bluehive.details.shared.playback.PlaybackRequest
import com.example.bluehive.details.shared.playback.PlayerIntents
import com.example.bluehive.details.shared.playback.StreamSource
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



class MoviesDetailsScreenCompose : ComponentActivity() {
    private var mediaType: String = "movie"
    private var mediaId: Int = 0
    private var voteAverage: Double = 0.0
    private var mediaTitle: String = ""
    private var posterUrl: String? = null
    private var backdropUrl: String? = null
    private var trailerUrl: String? = null
    private var overview: String? = null
    private var contentRating: String? = null
    private var releaseDate: String? = null
    private var budget: Long = 0
    private var revenue: Long = 0
    private var originalLanguage: String? = null
    private var logoUrl: String? = null
    private var genres: String? = null
    internal var profileId: Int = -1
    private var sourceScreen: String? = null
    private var searchQuery:  String? = null


    companion object {
        private const val TAG = "MoviesDetailsScreenCompose"
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
            status = null,
            voteAverage = voteAverage,
            voteCount = null,
            popularity = null,
            popularityRank = null,
            originalLanguage = originalLanguage,
            numberOfSeasons = null,
            numberOfEpisodes = null,
            contentRating = contentRating,
            runtime = null,
            budget = budget,
            revenue = revenue,
            trailerUrl = trailerUrl,
            genres = genres?.split(", ")?.map { it.trim() },
            similarItems = null,
            whereToWatch = null
        ))
        Log.d(
            "DetailsHistory",
            "Details -> openMediaDetails: pushed '${media.title}', size=${app.historySize()}"
        )

        val intent = Intent(this, MoviesDetailsScreenCompose::class.java).apply {
            putExtra("media_type",        media.mediaType ?: "movie")
            putExtra("media_id",          media.tmdbId)                     // Int (non-null)
            putExtra("media_title",       media.title ?: "")
            putExtra("poster_url",        media.posterUrl ?: "")
            putExtra("backdrop_url",      media.backdropUrl ?: "")
            putExtra("youtube_trailer_url", media.trailerUrl ?: "")
            putExtra("overview",          media.overview ?: "")
            putExtra("vote_average",      media.voteAverage ?: 0.0)         // Double, NOT Double?
            putExtra("contentRating",     media.contentRating ?: "N/A")
            putExtra("original_language", media.originalLanguage ?: "N/A")
            putExtra("release_date",      media.releaseDate ?: "N/A")
            putExtra("budget",            media.budget ?: 0L)               // Long, NOT Long?
            putExtra("revenue",           media.revenue ?: 0L)              // Long
            putExtra("logo_url",          media.logoUrl ?: "")
            putExtra("genres",            media.genres?.joinToString(", ") ?: "N/A")
            putExtra("PROFILE_ID",        profileId)
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
                                Intent(this@MoviesDetailsScreenCompose, HistoryScreenActivity::class.java)
                                    .putExtra("PROFILE_ID", profileId)
                            )
                            finish()
                            return
                        }
                        SourceScreen.FAVORITES -> {
                            Log.d("DetailsHistory", "Back: returning to Favorites")
                            startActivity(
                                Intent(this@MoviesDetailsScreenCompose, FavoritesScreenActivity::class.java)
                                    .putExtra("PROFILE_ID", profileId)
                            )
                            finish()
                            return
                        }
                        SourceScreen.SEARCH -> {
                            Log.d("DetailsHistory", "Back: returning to Search")
                            startActivity(
                                Intent(this@MoviesDetailsScreenCompose, SearchBarActivity::class.java)
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

                val intent = Intent(this@MoviesDetailsScreenCompose, MoviesDetailsScreenCompose::class.java).apply {
                    putExtra("media_type",        previous.mediaType ?: "movie")
                    putExtra("media_id",          previous.tmdbId)
                    putExtra("media_title",       previous.title ?: "")
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
        mediaType = intent.getStringExtra("media_type") ?: "movie"
        contentRating = intent.getStringExtra("contentRating")
        originalLanguage = intent.getStringExtra("original_language")
        mediaId = intent.getIntExtra("media_id", 0)
        voteAverage = intent.getDoubleExtra("vote_average", 0.0)
        mediaTitle = intent.getStringExtra("media_title") ?: ""
        posterUrl = intent.getStringExtra("poster_url")
        backdropUrl = intent.getStringExtra("backdrop_url")
        trailerUrl = intent.getStringExtra("youtube_trailer_url")
        overview = intent.getStringExtra("overview")
        budget = intent.getLongExtra("budget", 0L)
        revenue = intent.getLongExtra("revenue", 0L)
        logoUrl = intent.getStringExtra("logo_url")
        releaseDate = intent.getStringExtra("release_date")
        genres    = intent.getStringExtra("genres")
        profileId = intent.getIntExtra("PROFILE_ID", -1)
        sourceScreen = intent.getStringExtra("SOURCE_SCREEN")
        searchQuery  = intent.getStringExtra("SEARCH_QUERY")

        val startInExtras = intent.getBooleanExtra("start_in_extras", false)

        val recommendationsRepository = RecommendationsRepository()


        // ✅ Debug logging to see what we received
        Log.d(TAG, "📝 Intent extras received:")
        Log.d(TAG, "  mediaTitle: $mediaTitle")
        Log.d(TAG, "  mediaType: $mediaType")
        Log.d(TAG, "  mediaId: $mediaId")
        Log.d(TAG, "  backdropUrl: $backdropUrl")
        Log.d(TAG, "  trailerUrl: $trailerUrl")
        Log.d(TAG, "  logoUrl (raw): '$logoUrl'")
        Log.d(TAG, "  logoUrl isEmpty: ${logoUrl.isNullOrEmpty()}")
        Log.d(TAG, "  logoUrl isBlank: ${logoUrl.isNullOrBlank()}")

        Log.d(TAG, "Received media: $mediaTitle (ID: $mediaId, Type: $mediaType)")

        Log.d(TAG, "🔍 Intent extras:")
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            Log.d(TAG, "  $key: $value (type: ${value?.javaClass?.simpleName})")
        }

        setContent {
            MoviesDetailsScreenContent(
                mediaTitle = mediaTitle,
                mediaType = mediaType,
                mediaId = mediaId,
                backdropUrl = backdropUrl,
                trailerUrl = trailerUrl,
                logoUrl = logoUrl,
                overview = overview,
                revenue = revenue,
                budget = budget,
                contentRating = contentRating,
                originalLanguage = originalLanguage,
                voteAverage = voteAverage,
                releaseDate = releaseDate,
                genres = genres,
                tmdbId = mediaId,
                startInExtras = startInExtras,
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
    return when(mediaType) {
        "movie" -> listOf(
            StreamSource(
                "Royal Jelly",
                "VidNest",
                "https://vidnest.fun/movie/$mediaId"
            ),
            // VidLove — added 2026-08-30 as the second source, beside VidNest. Backend-extracted:
            // "VidLove" is listed in usesBackendExtraction and keyed as "vidlove"
            // in the backend's _SOURCES, and the URL shape here MUST match that
            // template exactly or the two request different pages.
            //
            // PLACED BESIDE VIDNEST DELIBERATELY. Both pass the ISP filter
            // with cleartext SNI (verified 2026-08-30: a2.shows.st and
            // tiktoks.animanga.fun return certificates, moon.peakstorm.top and
            // finalroom.top are severed), and neither appears in
            // relay_sessions at all — while moon.peakstorm.top alone accounts
            // for 22 of the last 24 hours' 34 relays. Order is the cheapest
            // lever on relay volume, so the two clean sources lead.
            //
            // Verified before listing: both endpoints extract (movie 27205 and
            // tv 1396 S1E1), and its CDNs a2.shows.st / d.shows.st both publish
            // ECH with alpn=h3 — so it is covered by the QUIC/ECH path rather
            // than relying on the relay.
            StreamSource(
                "Nectar Stream",
                "VidLove",
                "https://player.vidlove.cc/embed/movie/$mediaId"
            ),
            StreamSource(
                "Queen Stream",
                "VidFast",
                "https://vidfast.pro/movie/$mediaId?autoPlay=true",
            ),
            StreamSource(
                "Purple Stream",
                "MoviesAPI",
                    "https://moviesapi.to/movie/$mediaId"
            )
            // VidSrc (\"Swarm Stream\") and VidApi ("Honeycomb") were removed on
            // 2026-08-30 at the user's request. Both still work server-side — the
            // 403s that made them look broken were our own Referer/Origin headers,
            // fixed the same day — so this is a menu decision, not a capability
            // one. Re-adding them is just restoring the entries; usesBackendExtraction
            // already matches both names.
        )
        else -> listOf()
    }
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

    // Back collapses Extras before it leaves the page. Registered here, above
    // the overlay handlers further down, so those still win while one is open
    // -- see DetailsBackHandlers.
    DetailsBackHandlers(extras)

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
    var showNoDub      by remember { mutableStateOf(false) }   // transient "No dub available" toast
    // Shown when this anime movie has no AniList mapping yet, so there is no Miruro
    // URL to extract from. Without this, DuB/SuB were a SILENT no-op — the guard
    // below just found sources.size <= 2 and did nothing at all.
    var showNotYetAvailable by remember { mutableStateOf(false) }

    // ── Anime server-selection flow ─────────────────────────────────────────
    var showDefaultPrompt by remember { mutableStateOf(false) } // instant "default server?" Yes/No
    var isCheckingServers by remember { mutableStateOf(false) } // phase 1: enumerating servers
    var showServerPicker  by remember { mutableStateOf(false) } // phase 1 result: the picker
    var showServerError   by remember { mutableStateOf(false) } // transient failure toast

    var pendingUrl        by remember { mutableStateOf<String?>(null) }
    var pendingDub        by remember { mutableStateOf(false) }
    var serverOptions     by remember { mutableStateOf<List<ServerOption>>(emptyList()) }
    var serverDefaultIdx  by remember { mutableIntStateOf(0) }

    // Server lists captured during a DEFAULT extraction, cached for the lifetime
    // of this detail screen (cleared when the user navigates away). Keyed by
    // url + audio so "Choose a server" can skip the enumerate webview pass when
    // the list is already known. Value = (options, defaultIndex). Plain map — only
    // read/written from callbacks, never during composition, so no snapshot state.
    val serverListCache = remember { mutableMapOf<String, Pair<List<ServerOption>, Int>>() }
    fun serverCacheKey(url: String, dub: Boolean) = "$url|${if (dub) "dub" else "sub"}"

    // Named requesters for DuB/Sub so we know exactly where to send focus back.
    val animeDubFocusRequester = remember { FocusRequester() }
    val animeSubFocusRequester = remember { FocusRequester() }
    // Whichever button the user pressed last — restored when any overlay closes.
    var lastStreamFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

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
    // It reads as episode-specific because the SECOND press hits serverListCache and
    // opens the picker inline — no activity launch, no resume, no race.
    //
    // extractionError is in the list for the same reason: that overlay appears on the
    // very resume this fires on. The TV screen already carried both guards; this one
    // carried neither.
    RestoreFocusOnPlayerReturn("MovieDetailsFocus") {
        if (session.extractionError != null) return@RestoreFocusOnPlayerReturn emptyList()
        if (showServerPicker || isCheckingServers || showDefaultPrompt) {
            return@RestoreFocusOnPlayerReturn emptyList()
        }
        listOfNotNull(lastStreamFocusRequester)
    }

    // Extractor m3u8-extraction error reason (shown as a toast; auto-dismisses).

    // Back closes whichever picker/prompt is open instead of leaving the screen.
    BackHandler(enabled = showDefaultPrompt || showServerPicker) {
        when {
            showServerPicker -> { showServerPicker = false; showDefaultPrompt = true }
            showDefaultPrompt -> {
                showDefaultPrompt = false
                // Return the cursor to whichever stream button opened the prompt.
                coroutineScope.launch {
                    awaitFrame()
                    try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                }
            }
        }
    }

    val activity = LocalActivity.current as? MoviesDetailsScreenCompose
    val favoriteProfileId = activity?.profileId ?: -1

    // Favorites: checked on open, toggled by the header button -- see
    // FavoriteState. This screen sends no title with a toggle (§10, #3).
    val favorite = rememberFavoriteState(
        scope     = coroutineScope,
        profileId = favoriteProfileId,
        tmdbId    = tmdbId,
        mediaType = mediaType,
        title     = null,
    )

    // ── Automatic server failover ────────────────────────────────────────────
    // When the default (or chosen) server is down — a hard extract failure, or a
    // dub request that fell back to sub — walk the rest of the provider list until
    // one yields a real stream. Works for any anime movie, dub or sub. triedServers
    // is reset at the start of each play (default attempt); lastExtractServer is the
    // provider of the in-flight extract (null = Miruro's default).
    val triedServers = remember { mutableSetOf<String>() }
    var lastExtractServer by remember { mutableStateOf<String?>(null) }
    // Set by the result handler to re-extract with the next server; a LaunchedEffect
    // (declared after launchExtract) performs the actual re-launch. This state hop
    // breaks the launchExtract ⇄ extractorLauncher reference cycle. (url, dub, server)
    var failoverRequest by remember { mutableStateOf<Triple<String, Boolean, String?>?>(null) }
    fun pickNextServer(url: String, dub: Boolean): String? {
        val list = serverListCache[serverCacheKey(url, dub)]?.first
            ?: serverOptions.takeIf { it.isNotEmpty() }
            ?: return null
        val untried = list.filter { it.name !in triedServers }
        if (untried.isEmpty()) return null
        val want = if (dub) "dub" else "sub"
        // Prefer a provider whose tags advertise the requested track; else any untried.
        return (untried.firstOrNull { o -> o.tags.any { it.equals(want, true) } } ?: untried.first()).name
    }

    // Receives the m3u8 back from the isolated :extractor process, then launches
    // the player here in the main process — keeps back-navigation identical.
    // Phase 2 result: receives the m3u8 (default OR chosen server) and plays it.
    val extractorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        session.isExtracting   = false
        session.extractPending = false
        if (result.resultCode == Activity.RESULT_OK) {
            val data    = result.data
            val m3u8    = data?.getStringExtra(MiruroExtractorActivity.RESULT_M3U8)
            val referer = data?.getStringExtra(MiruroExtractorActivity.RESULT_REFERER)
            val ua      = data?.getStringExtra(MiruroExtractorActivity.RESULT_UA)
            val dubFallback = data?.getBooleanExtra(MiruroExtractorActivity.RESULT_DUB_FALLBACK, false) == true
            val capUrl  = pendingUrl

            // ── Cache any server list captured in this same pass FIRST, so failover
            //    below can consult it (a default extract carries the provider list). ─
            val capNames = data?.getStringArrayListExtra(MiruroExtractorActivity.RESULT_SERVER_NAMES)
            val capTags  = data?.getStringArrayListExtra(MiruroExtractorActivity.RESULT_SERVER_TAGS)
            val capDef   = data?.getIntExtra(MiruroExtractorActivity.RESULT_SERVER_DEFAULT_INDEX, 0) ?: 0
            if (!capNames.isNullOrEmpty() && capUrl != null) {
                val opts = capNames.mapIndexed { i, name ->
                    val tagStr = capTags?.getOrNull(i) ?: ""
                    val tagList = if (tagStr.isBlank()) emptyList()
                        else tagStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    ServerOption(name = name, tags = tagList)
                }
                serverListCache[serverCacheKey(capUrl, pendingDub)] =
                    opts to capDef.coerceIn(0, opts.size - 1)
                Log.d("scraper", "🗂 cached ${opts.size} servers from default extract")
            }
            // Mark the provider we just tried (named failover server, else Miruro's default).
            val triedName = lastExtractServer
                ?: capNames?.let { it.getOrNull(capDef.coerceIn(0, it.size - 1)) }
            triedName?.let { triedServers.add(it) }

            if (m3u8.isNullOrBlank()) {
                // Nothing captured — server likely down. Fail over before erroring.
                val next = if (capUrl != null) pickNextServer(capUrl, pendingDub) else null
                if (next != null && capUrl != null) {
                    Log.d("scraper", "🔁 no stream — auto-failover to server '$next'")
                    triedServers.add(next)
                    failoverRequest = Triple(capUrl, pendingDub, next)
                } else {
                    Log.e("scraper", "M Extract: OK result but no m3u8")
                    showServerError = true
                }
                return@rememberLauncherForActivityResult
            }

            // Dub requested but we got the sub last-resort → try the next server.
            if (pendingDub && dubFallback) {
                val next = if (capUrl != null) pickNextServer(capUrl, true) else null
                if (next != null && capUrl != null) {
                    Log.d("scraper", "🔁 dub server down — auto-failover to server '$next'")
                    triedServers.add(next)
                    failoverRequest = Triple(capUrl, true, next)
                    return@rememberLauncherForActivityResult
                }
                Log.w("scraper", "🔁 dub unavailable on every server — playing sub as last resort")
            }

            // ── Play the captured stream ─────────────────────────────────────
            // DIRECT FIRST, relay only on failure — see MiruroPlayerActivity. The
            // relay works around ISPs that DNS-hijack the CDN, but costs ~1 GB of
            // backend transfer each way per episode, so it is now the fallback
            // rather than the default path.
            Log.d("scraper", "M Extract → direct=$m3u8  (relay fallback ready)")
            // ── Record the watch ────────────────────────────────────────
            // The DuB/SuB path goes straight to MiruroPlayerActivity, and watch
            // history used to be logged only by the old GeckoView viewer -- so
            // anime watched this way was invisible to the
            // backend. It IS logged, but by the player itself once video is
            // actually rolling (onIsPlayingChanged), NOT here: firing on a
            // successful extract counted a watch even when the stream never
            // played. Season/episode are omitted — these are movies.
            context.startActivity(
                PlayerIntents.miruroOnDevice(
                    context    = context,
                    request    = PlaybackRequest.Movie(tmdbId),
                    m3u8       = m3u8,
                    referer    = referer,
                    userAgent  = ua,
                    sourceName = miruroSourceLabel(null, capNames?.getOrNull(capDef)),
                    profileId  = favoriteProfileId,
                    mediaType  = mediaType,
                    mediaTitle = mediaTitle,
                    dub        = pendingDub,
                    server     = triedName,
                )
            )
        } else {
            val data   = result.data
            val noDub  = data?.getBooleanExtra(MiruroExtractorActivity.RESULT_NO_DUB, false) == true
            val failed = data?.getBooleanExtra(MiruroExtractorActivity.RESULT_FAILED, false) == true
            when {
                noDub  -> { Log.w("scraper", "M Extract: no dub available"); showNoDub = true }
                failed -> {
                    // Extraction error — server likely down. Fail over to the next one.
                    val u = pendingUrl
                    val next = if (u != null) pickNextServer(u, pendingDub) else null
                    if (next != null && u != null) {
                        Log.d("scraper", "🔁 extract failed — auto-failover to server '$next'")
                        triedServers.add(next)
                        failoverRequest = Triple(u, pendingDub, next)
                    } else {
                        Log.w("scraper", "M Extract: failed"); showServerError = true
                    }
                }
                else   -> { Log.w("scraper", "M Extract: user cancelled") /* stay silent */ }
            }
        }
    }

    // Phase 1 result: receives the server list, then shows the picker.
    val enumerateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isCheckingServers = false
        if (result.resultCode == Activity.RESULT_OK) {
            val data   = result.data
            val names  = data?.getStringArrayListExtra(MiruroExtractorActivity.RESULT_SERVER_NAMES)
            val tags   = data?.getStringArrayListExtra(MiruroExtractorActivity.RESULT_SERVER_TAGS)
            val defIdx = data?.getIntExtra(MiruroExtractorActivity.RESULT_SERVER_DEFAULT_INDEX, 0) ?: 0
            if (names != null && names.isNotEmpty()) {
                val options = names.mapIndexed { i, name ->
                    val tagStr = tags?.getOrNull(i) ?: ""
                    val tagList = if (tagStr.isBlank()) emptyList()
                    else tagStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    ServerOption(name = name, tags = tagList)
                }
                serverOptions    = options
                serverDefaultIdx = defIdx.coerceIn(0, options.size - 1)
                showServerPicker = true
                // Cache so re-opening the picker for this same episode+audio is
                // instant and a later default play won't re-read the list either.
                pendingUrl?.let { u ->
                    serverListCache[serverCacheKey(u, pendingDub)] =
                        options to defIdx.coerceIn(0, options.size - 1)
                }
            } else {
                Log.w("scraper", "M Enumerate: OK but empty server list")
                showServerError = true
            }
        } else {
            val data   = result.data
            val noDub  = data?.getBooleanExtra(MiruroExtractorActivity.RESULT_NO_DUB, false) == true
            val failed = data?.getBooleanExtra(MiruroExtractorActivity.RESULT_FAILED, false) == true
            when {
                noDub  -> { Log.w("scraper", "M Enumerate: no dub available"); showNoDub = true }
                failed -> { Log.w("scraper", "M Enumerate: failed");           showServerError = true }
                else   -> { Log.w("scraper", "M Enumerate: user cancelled") /* stay silent */ }
            }
        }
    }

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


    // ── BACK cancels an extraction in progress ───────────────────────
    // The loading overlay covers windows where THIS screen is focused -- the gap
    // between failover attempts, and the moments around a request starting and
    // finishing. BACK there used to fall through and leave the details screen
    // entirely.
    BackHandler(enabled = session.isExtracting || session.extractPending) {
        session.cancel()
        coroutineScope.launch {
            awaitFrame()
            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
        }
    }



    // Launch a real extraction (default server when server == null).
    // extractPending is set immediately to block double-fire during the animation
    // window. The loading overlay itself is delayed 300ms so the button press
    // animation fully completes before the scrim appears.
    /**
     * Mirror of the TV screen's ensureServerList — see there for why the
     * "Default server" path has no list to fail over through until this fetches
     * one, and why warming it for the track that just played is now cheap.
     */
    suspend fun ensureServerList(url: String, dub: Boolean) {
        if (serverListCache[serverCacheKey(url, dub)] != null) return
        val list = runCatchingCancellable {
            ApiClient.bluehiveApi.extractServers(
                type   = "movie",
                tmdbId = tmdbId,
                audio  = if (dub) "dub" else "sub",
                device = SessionManager.get().deviceFingerprint,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return
        val opts = list.servers.map { ServerOption(it.name, emptyList()) }
        if (opts.isEmpty()) return
        serverListCache[serverCacheKey(url, dub)] =
            opts to list.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
    }

    /**
     * Anime MOVIE via the BACKEND extractor.
     *
     * Mirror of the TV screen's extractAnimeViaBackend — see there for the full
     * reasoning. Same restriction: DEFAULT PATH ONLY (sub, default server),
     * because dub needs the Language dropdown and a named server needs the
     * Provider dropdown, and neither is expressible as a page load.
     *
     * No season/episode: /api/anime/resolve maps a movie straight to its AniList
     * id with a null episode, and the miruro movie template omits ?ep= entirely.
     *
     * @param acceptAnyTrack play the wrong track anyway — set only once every
     *        dub server has been tried. See the TV screen for the reasoning.
     */
    suspend fun extractAnimeMovieViaBackend(dub: Boolean = false,
                                            server: String? = null,
                                            acceptAnyTrack: Boolean = false): ExtractOutcome {
        // Cancellable, like its caller: a page closed mid-request must stop the
        // work, not return MISS and send the caller on to its fallback.
        val body = runCatchingCancellable {
            ApiClient.bluehiveApi.extractStreamAwait(
                type    = "movie",
                tmdbId  = tmdbId,
                source  = "miruro",
                mtype   = "anime_movie",
                title   = mediaTitle,
                profile = favoriteProfileId,
                device  = SessionManager.get().deviceFingerprint,
                // Sent always — "sub" is part of the server's cache key, not a
                // no-op, so omitting it would extract the same stream under a
                // second key.
                audio   = if (dub) "dub" else "sub",
                server  = server,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body()

        if (body == null || body.m3u8.isBlank()) return ExtractOutcome.MISS

        // Dub asked for, sub delivered — see the TV screen for the full note. A
        // null `audio` is an older backend: unknowable, so treat it as before.
        val wrongTrack = dub && body.audio?.equals("dub", ignoreCase = true) == false
        if (wrongTrack && !acceptAnyTrack) {
            Log.e("scraper", "🎧 asked for dub, backend returned '${body.audio}'" +
                    " (server=${server ?: "default"}) — not playing it yet")
            return ExtractOutcome.WRONG_TRACK
        }
        val playingDub = dub && !wrongTrack

        Log.d("scraper", "M Extract (backend movie audio=${if (dub) "dub" else "sub"}" +
                "${server?.let { " server=$it" } ?: ""}) → ${body.m3u8.take(60)} cached=${body.cached}")
        // Not a launcher, so a closed page cannot crash here -- but it would still
        // open the player after the viewer has left. Stop quietly instead.
        currentCoroutineContext().ensureActive()
        context.startActivity(
            PlayerIntents.miruroBackend(
                context    = context,
                request    = PlaybackRequest.Movie(tmdbId),
                body       = body,
                sourceName = miruroSourceLabel(body.servers, server),
                profileId  = favoriteProfileId,
                mediaType  = mediaType,
                mediaTitle = mediaTitle,
                playingDub = playingDub,
                server     = server ?: serverOptions.getOrNull(serverDefaultIdx)?.name,
            )
        )
        return ExtractOutcome.PLAYED
    }

    val launchExtract: (String, Boolean, String?) -> Unit = { url, dub, server ->
        Log.d("scraper", "M Extract (dub=$dub, server=${server ?: "default"}) → $url")
        // Inside the launcher rather than at its call sites: this is reached
        // from the DuB/SuB prompt, the SERVER PICKER and the failover retry,
        // and a future caller should not have to remember. Idempotent.
        stopTrailer()
        // Fresh play (default attempt) → reset the failover trail. Track the in-flight
        // provider so its result can mark it tried.
        if (server == null) triedServers.clear()
        lastExtractServer = server
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true
            // HELD IN extractJob, like the source chain, so BACK cancels it. It
            // was launched untracked: Back hid the loading screen, the backend
            // answered a moment later, and the film started anyway. Cancelled,
            // the backend await ends it, and ensureActive()/launchIfActive()
            // below stop the player and the on-device fallback from starting
            // after the fact.
            session.extractJob = coroutineScope.launch {
                delay(200)
                session.isExtracting = true
                try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}

                // BACKEND FIRST for the default play (sub, default server). Falls
                // THROUGH on any failure — the on-device extractor below still
                // works, so a backend outage costs a few seconds rather than the
                // film. Dub and named-server skip this: the backend cannot drive
                // those dropdowns.
                // BACKEND FIRST for EVERY anime play now — sub, dub and named
                // servers alike. The backend drives the Language and Provider
                // dropdowns itself, so there is no case left that it cannot serve.
                // Still falls THROUGH on failure: the on-device extractor below is
                // untouched, so a backend outage costs seconds, not the film.
                //
                // Burn the provider here, not in extractorLauncher — that handler
                // only sees ON-DEVICE results, so triedServers never grew and the
                // dub failover below would return the same server forever.
                server?.let { triedServers.add(it) }

                // Cancellable: leaving the page while the backend works must end
                // this coroutine, not count as a miss and launch the on-device
                // fallback through the closed page's launcher (PageCoroutines.kt).
                var outcome = runCatchingCancellable {
                    extractAnimeMovieViaBackend(dub = dub, server = server)
                }.getOrDefault(ExtractOutcome.MISS)

                if (outcome == ExtractOutcome.WRONG_TRACK) {
                    ensureServerList(url, true)
                    // The DUB list specifically, not pickNextServer — that helper
                    // falls back to `serverOptions`, which may still hold the sub
                    // list from the last picker. See the TV screen.
                    val next = serverListCache[serverCacheKey(url, true)]?.first
                        ?.firstOrNull { it.name !in triedServers }?.name
                    if (next != null) {
                        Log.e("scraper", "🔁 dub came back as sub — failing over to server '$next'")
                        triedServers.add(next)
                        session.isExtracting   = false
                        session.extractPending = false
                        failoverRequest = Triple(url, true, next)
                        return@launch
                    }
                    Log.e("scraper", "🔁 dub unavailable on every server — playing sub as last resort")
                    outcome = runCatchingCancellable {
                        extractAnimeMovieViaBackend(dub = dub, server = server, acceptAnyTrack = true)
                    }.getOrDefault(ExtractOutcome.MISS)
                }

                if (outcome == ExtractOutcome.PLAYED) {
                    // Warm the picker for the track that just played — see the
                    // TV screen. Playback has already started and the list is
                    // cached server-side by the extraction, so this is one
                    // round trip and delays nothing.
                    ensureServerList(url, dub)
                    session.isExtracting   = false
                    session.extractPending = false
                    return@launch
                }
                Log.w("scraper", "M Extract: backend miss (dub=$dub server=$server) — on-device fallback")

                extractorLauncher.launchIfActive(
                    PlayerIntents.extractorExtract(
                        context       = context,
                        url           = url,
                        dub           = dub,
                        server        = server,
                        listNotCached = serverListCache[serverCacheKey(url, dub)] == null,
                    )
                )
            }
        }
    }

    // Launch the server-list check (phase 1). Same 300ms delay before the
    // "Checking…" overlay appears so the "Choose a server" animation finishes.
    val launchEnumerate: (String, Boolean) -> Unit = { url, dub ->
        Log.d("scraper", "M Enumerate (dub=$dub) → $url")
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true      // immediate guard for the 300ms window
            coroutineScope.launch {
                delay(200)
                session.extractPending = false // hand off guard duty to isCheckingServers
                isCheckingServers = true

                // BACKEND FIRST, which also removes an ACTIVITY HOP. The on-device
                // path launched MiruroExtractorActivity, pausing and resuming this
                // screen — and that resume is what RestoreFocusOnPlayerReturn
                // races, the cause of the picker losing focus. An in-process call
                // never leaves the screen, so the race cannot arise here.
                val list = runCatchingCancellable {
                    ApiClient.bluehiveApi.extractServers(
                        type   = "movie",
                        tmdbId = tmdbId,
                        audio  = if (dub) "dub" else "sub",
                        device = SessionManager.get().deviceFingerprint,
                    )
                }.getOrNull()?.takeIf { it.isSuccessful }?.body()

                val opts = list?.servers.orEmpty().map { ServerOption(it.name, emptyList()) }
                if (opts.isNotEmpty()) {
                    val defIdx = list!!.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
                    Log.d("scraper", "M Enumerate (backend) → ${opts.size} servers, default=$defIdx")
                    serverListCache[serverCacheKey(url, dub)] = opts to defIdx
                    serverOptions     = opts
                    serverDefaultIdx  = defIdx
                    isCheckingServers = false
                    showServerPicker  = true
                    return@launch
                }

                Log.w("scraper", "M Enumerate: backend miss — on-device fallback")
                try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}
                enumerateLauncher.launchIfActive(
                    PlayerIntents.extractorEnumerate(context = context, url = url, dub = dub)
                )
            }
        }
    }

    // Auto server-failover: the result handler stashes the next attempt here; run it.
    LaunchedEffect(failoverRequest) {
        val req = failoverRequest ?: return@LaunchedEffect
        failoverRequest = null
        launchExtract(req.first, req.second, req.third)
    }


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
            pendingUrl = sources[2].url
            pendingDub = want
            showDefaultPrompt = true
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

    LaunchedEffect(showNoDub) {
        if (showNoDub) {
            delay(2500)
            showNoDub = false
        }
    }

    // 30s on the picker → close it and send the user back to the Yes/No prompt.
    // NO AUTO-DISMISS. This used to close itself after 30s and drop the viewer
    // back on the default-server prompt. Reading six provider names, deciding,
    // and walking a D-pad there is easily slower than that -- and the dialog
    // vanishing mid-decision looks like the app crashed, then silently undoes
    // the choice they were making. A picker closes when it is picked or backed
    // out of; BackHandler already covers the second.

    LaunchedEffect(showServerError) {
        if (showServerError) {
            delay(2500)
            showServerError = false
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
                            pendingUrl = sources[2].url
                            pendingDub = true
                            showDefaultPrompt = true
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
                            pendingUrl = sources[2].url
                            pendingDub = false
                            showDefaultPrompt = true
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
            if (showNoDub) {
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

            // ── "Checking servers" overlay (No path, phase 1) ───────────────────
            if (isCheckingServers) {
                // Focus TRAP, not decoration. ExtractionLoadingOverlay draws a scrim
                // but takes no focus of its own, so the D-pad still reaches the details
                // screen behind it.
                //
                // NOTE ON SCOPE: this only covers the window where this Activity is in
                // FOREGROUND with the overlay up. It does NOT cover the enumerate pass
                // itself — that runs in MiruroExtractorActivity and this screen is
                // paused throughout. The bug people actually hit lives on the RESUME
                // from that activity and is guarded in RestoreFocusOnPlayerReturn above.
                //
                // BACK and Escape pass through so the existing BackHandler can still
                // cancel; swallowing them would leave no way out on a TV remote.
                ExtractionFocusTrap {
                    ExtractionLoadingOverlay(
                        status = "Checking the site for available servers…",
                        stage  = 2
                    )
                }
            }

            // ── Instant "default server?" Yes/No prompt ─────────────────────────
            if (showDefaultPrompt) {
                DefaultServerPrompt(
                    onDefault = {
                        showDefaultPrompt = false
                        // Snap focus back to the DuB/Sub button immediately so it
                        // glows during the 300ms animation window before the loading
                        // overlay appears.
                        coroutineScope.launch {
                            awaitFrame()
                            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                        }
                        stopTrailer()
                        pendingUrl?.let { launchExtract(it, pendingDub, null) }
                    },
                    onChoose = {
                        showDefaultPrompt = false
                        // NO focus restore here, unlike onDefault above. onDefault ENDS
                        // the dialog flow, so returning focus to the DuB/Sub button is
                        // right. onChoose CONTINUES into the picker, and handing focus to
                        // a button on the screen underneath is exactly what the picker
                        // then has to fight for it.
                        val cached = pendingUrl?.let { serverListCache[serverCacheKey(it, pendingDub)] }
                        if (cached != null) {
                            Log.d("scraper", "🗂 server picker from cache (${cached.first.size} servers)")
                            serverOptions    = cached.first
                            serverDefaultIdx = cached.second
                            showServerPicker = true
                        } else {
                            pendingUrl?.let { launchEnumerate(it, pendingDub) }
                        }
                    }
                )
            }

            // ── Server picker (No path, phase 1 result) ─────────────────────────
            if (showServerPicker && serverOptions.isNotEmpty()) {
                ServerPickerOverlay(
                    servers      = serverOptions,
                    audioLabel   = if (pendingDub) "Dub" else "Sub",
                    onSelect     = { serverName ->
                        showServerPicker = false
                        // Same: light the original button back up during the 300ms
                        // window before the loading overlay takes over.
                        coroutineScope.launch {
                            awaitFrame()
                            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                        }
                        pendingUrl?.let { launchExtract(it, pendingDub, serverName) }
                    }
                )
            }

            // ── Transient "couldn't load" toast ─────────────────────────────────
            if (showServerError) {
                DetailsToast("Couldn't reach the site — please try again", dongleRegular)
            }
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