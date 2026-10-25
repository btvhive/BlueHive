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
import androidx.compose.ui.draw.scale
import com.example.bluehive.utilities.WatchedCornerFold
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
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
import com.example.bluehive.utilities.launchIfActive
import com.example.bluehive.utilities.runCatchingCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.ExtractOutcome
import com.example.bluehive.sidebarComponents.FavoritesScreenActivity
import com.example.bluehive.sidebarComponents.HistoryScreenActivity
import kotlinx.coroutines.async
import com.example.bluehive.searchBarComponent.SearchBarActivity
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.api.buildJapaneseAnimationSources
import com.example.bluehive.webview.BeeLogo
import com.example.bluehive.webview.ExtractionFocusTrap
import com.example.bluehive.webview.ExtractionLoadingOverlay
import com.example.bluehive.webview.miruro.MiruroPlayerActivity
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.bluehive.auth.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.bluehive.api.extractStreamAwait
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.SourceScreen
import com.example.bluehive.details.shared.playback.animeRelayUrl
import com.example.bluehive.details.shared.playback.miruroSourceLabel
import com.example.bluehive.details.shared.playback.PlaybackErrorOverlay
import com.example.bluehive.details.shared.playback.PlaybackLoadingOverlay
import com.example.bluehive.details.shared.playback.StreamSource
import com.example.bluehive.details.shared.playback.rememberPlaybackSession
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

/*
 * VidFast extraction moved to the backend (see extractAndPlay below). The old
 * on-device retry ladder is gone with it: it existed because the WebView
 * extractor was flaky, and it could never help against the real failure on a
 * filtered line, where `vidfast.vc` is refused deterministically and every retry
 * bought another 60s timeout for the same result. The server caches per episode
 * and deduplicates concurrent requests, so a client-side retry would only re-ask
 * for an answer it already has.
 */

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
    return when (mediaType) {
        "tv" -> listOf(
            StreamSource(
                "Royal Jelly",
                "VidNest",
                "https://vidnest.fun/tv/$mediaId/$seasonNumber/$episodeNumber"
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
                "https://player.vidlove.cc/embed/tv/$mediaId/$seasonNumber/$episodeNumber"
            ),
            StreamSource(
                "Queen Stream",
                "VidFast",
                 "https://vidfast.pro/tv/$mediaId/$seasonNumber/$episodeNumber?autoPlay=true"
            ),
            StreamSource(
                "Purple Stream",
                "MoviesAPI",
                "https://moviesapi.to/tv/$mediaId/$seasonNumber/$episodeNumber"
            )
            // VidSrc (\"Swarm Stream\") and VidApi ("Honeycomb") were removed on
            // 2026-08-30 at the user's request. Both still work server-side — the
            // 403s that made them look broken were our own Referer/Origin headers,
            // fixed the same day — so this is a menu decision, not a capability
            // one. Re-adding them is just restoring the entries; usesBackendExtraction
            // already matches both names.
        )
        else -> emptyList()
    }
}





/**
 * Measures [content] at its natural size, reports ZERO height to the parent, and
 * places it directly ABOVE where it would otherwise sit.
 *
 * Exists for the episode air date. That Text sits above the still inside the same
 * Column, so its line height became real layout space and pushed every thumbnail
 * down — the tiles visibly sat lower than before the date was added on
 * 2026-08-16.
 *
 * TWO EARLIER ATTEMPTS AND WHY THEY WERE WRONG, so nobody repeats them:
 *
 *   Leaving it alone — the tiles stay pushed down by however tall the font
 *   happens to render. Nothing in the code says how much, so it cannot be
 *   compensated anywhere else with confidence.
 *
 *   Pinning the Text to a fixed height and lifting the Column by the same
 *   constant — this DELETED THE DATE. dongleRegular's line box at 11.sp is taller
 *   than the 14.dp that was guessed, so the glyphs were clipped away entirely and
 *   the strip rendered blank. Any hardcoded height is the same trap with a
 *   different number: it silently clips the moment a font, size, or fontScale
 *   changes.
 *
 * This measures first and never constrains, so the text renders at whatever size
 * it actually needs, and the parent is told it took none of the column. The
 * result is exact rather than tuned — the still returns to precisely the Y it had
 * before the date existed, for any font metrics, with nothing to keep in sync.
 */
@Composable
private fun ZeroHeightAbove(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        // Natural height: pass the incoming width through, but no height bound.
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        // Report height 0 — the Column advances not at all for this child.
        layout(width, 0) {
            // Negative Y puts it in the strip above the tile's top edge.
            placeables.forEach { it.place(0, -it.height) }
        }
    }
}

/**
 * "2022-02-03" -> "02/03/2022" for the date shown above each episode still.
 *
 * The API hands us `air_date` as a Python `date.isoformat()` on both the normal
 * and the season-reconstruction paths, so the input is always ISO or null. Parsed
 * by substring rather than a DateFormat: this runs per episode card during D-pad
 * scrolling, and there's no locale question when both ends of the conversion are
 * fixed formats.
 *
 * Returns "" for a missing or malformed date — unaired episodes carry a null
 * air_date, and the caller renders the blank so the row keeps its height.
 */
/**
 * Today as a yyyyMMdd integer, in the DEVICE's own timezone.
 *
 * Calendar rather than java.time: minSdk is 25 and LocalDate needs 26 or
 * desugaring, and a plain calendar-day number needs neither.
 */
/**
 * How long a first press on an unaired episode stays armed.
 *
 * Long enough to read the notice and answer it; short enough that the answer is
 * clearly to THAT question. 6s is about two unhurried reads of the band.
 */
private const val UNAIRED_OVERRIDE_WINDOW_MS = 6_000L

private fun currentYmd(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.YEAR) * 10000 +
           (c.get(java.util.Calendar.MONTH) + 1) * 100 +
           c.get(java.util.Calendar.DAY_OF_MONTH)
}

/**
 * Has this episode not aired yet?
 *
 * Compared as yyyyMMdd INTEGERS. The input is a fixed ISO format and the
 * question is a pure calendar-day one, so there is no locale or timezone
 * conversion to get wrong -- and this runs per card during D-pad scrolling.
 *
 * FAILS OPEN IN BOTH AMBIGUOUS CASES, because wrongly blocking an episode that
 * would have played is worse than letting through one that fails:
 *
 *   * a missing or malformed date is not evidence of anything;
 *   * an episode airing TODAY is treated as available. TMDB dates carry no time
 *     of day, the box runs UTC-4, and by the time someone is looking at this row
 *     an episode dated today is usually already up.
 */
internal fun isEpisodeUnaired(raw: String?, todayYmd: Int): Boolean {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return false
    val y = s.substring(0, 4).toIntOrNull() ?: return false
    val m = s.substring(5, 7).toIntOrNull() ?: return false
    val d = s.substring(8, 10).toIntOrNull() ?: return false
    return (y * 10000 + m * 100 + d) > todayYmd
}

/**
 * Does this season have anything to watch yet? True once any episode has aired
 * by [isEpisodeUnaired]'s rule, so it fails open the same way: an episode with
 * no usable date counts. An empty season has nothing.
 */
internal fun seasonHasAiredEpisode(episodes: List<Episode>, todayYmd: Int): Boolean =
    episodes.any { !isEpisodeUnaired(it.airDate, todayYmd) }

/** How many of the newest seasons the season check looks at before listing the rest unchecked. */
internal const val MAX_SEASON_CHECKS = 3

/**
 * How many seasons the season column lists, counting from Season 1:
 * [seasonCount] less the newest seasons with nothing aired yet -- an announced
 * season with no episodes, or one whose every episode is dated after today.
 *
 * ONLY THE NEWEST SEASONS ARE EVER LEFT OFF. Seasons air in order, so "not out
 * yet" only happens at the end; an empty season in the middle is missing data,
 * not an unaired season. Keeping the list a prefix also keeps every index in
 * the column equal to season - 1, which the focus code relies on.
 *
 * Walks back from the newest season and stops at the first one that
 *   * has an aired episode -- it and every season before it are listed;
 *   * could not be checked ([episodesOf] returned null) -- unknown is listed;
 *   * is Season 1, which is always listed, so the column is never empty;
 *   * is past [maxChecks] seasons -- the rest are listed unchecked.
 *
 * Nothing carries over between calls: once an episode's date arrives, the next
 * call finds it aired and the season is listed again.
 */
internal suspend fun watchableSeasonCount(
    seasonCount: Int,
    todayYmd: Int,
    maxChecks: Int = MAX_SEASON_CHECKS,
    episodesOf: suspend (season: Int) -> List<Episode>?,
): Int {
    var listed = seasonCount
    var checks = 0
    while (listed > 1 && checks < maxChecks) {
        val episodes = episodesOf(listed) ?: return listed
        if (seasonHasAiredEpisode(episodes, todayYmd)) return listed
        listed--
        checks++
    }
    return listed
}

/** How long the season column waits for the season check before listing every season. */
private const val SEASON_CHECK_TIMEOUT_MS = 3_000L

/**
 * How many times this screen has come into view: once when it opens, then on
 * every return to it -- from the player, the screensaver, standby. Work keyed
 * on it is redone whenever the viewer comes back.
 */
@Composable
private fun rememberResumeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

/**
 * The season column's length -- it lists Season 1 through the returned season
 * ([watchableSeasonCount]). Null until the first check answers; the column
 * waits for it, so a season is never shown and then taken away.
 *
 * Checked again on every resume ([resumeCount]). A season whose first episode
 * airs while the app is open is listed the next time the viewer comes back to
 * this screen, with nothing to expire: each check asks the endpoint the episode
 * grid uses, and reads today's date fresh.
 *
 * FAILS OPEN. A check that fails or runs past [SEASON_CHECK_TIMEOUT_MS] lists
 * every season (or keeps the list it had): an empty season on the list is the
 * old behaviour, while a season left off with episodes in it is unreachable.
 */
@Composable
private fun rememberListedSeasons(
    tmdbId: Int,
    seasonCount: Int,
    resumeCount: Int,
    repository: EpisodesRepository,
): Int? {
    var listed by remember(tmdbId, seasonCount) { mutableStateOf<Int?>(null) }
    LaunchedEffect(tmdbId, seasonCount, resumeCount) {
        if (seasonCount <= 0) return@LaunchedEffect
        val counted = withTimeoutOrNull(SEASON_CHECK_TIMEOUT_MS) {
            watchableSeasonCount(seasonCount, currentYmd()) { season ->
                seasonEpisodesForCheck(repository, tmdbId, season)
            }
        }
        listed = (counted ?: listed ?: seasonCount).coerceIn(1, seasonCount)
    }
    return listed
}

/**
 * One season's episodes for the season check, or null when the check could not
 * be made. A 404 is an answer, not a failure: the backend has no such season,
 * so there is nothing in it to watch. Anything else is unknown.
 */
private suspend fun seasonEpisodesForCheck(
    repository: EpisodesRepository,
    tmdbId: Int,
    season: Int,
): List<Episode>? = try {
    repository.getSeasonEpisodes(tmdbId, season).episodes
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: retrofit2.HttpException) {
    if (e.code() == 404) emptyList() else null
} catch (e: Exception) {
    null
}

private fun formatEpisodeAirDate(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return ""
    val year  = s.substring(0, 4)
    val month = s.substring(5, 7)
    val day   = s.substring(8, 10)
    if (!year.all(Char::isDigit) || !month.all(Char::isDigit) || !day.all(Char::isDigit)) return ""
    return "$month/$day/$year"
}


@Composable
fun EpisodeBadge(
    current: Int,
    total: Int,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeText = remember(current, total) { "$current/$total" }

    // ✅ Same "exposure" scale idea used for stills
    val darkScale = 0.40f

    fun darken(color: Color): Color {
        return color.copy(
            red = color.red * darkScale,
            green = color.green * darkScale,
            blue = color.blue * darkScale
        )
    }

    val baseBg = Color(0xFF262629)
    val baseText = Color.White

    val bgColor = if (isFocused) baseBg else darken(baseBg)
    val textColor = if (isFocused) baseText else darken(baseText)

    Box(
        modifier = modifier
            // ⬇️ slightly wider than RatingBadge, same height
            .height(16.5.dp)
            .width(25.dp) // you can tweak if you want more breathing room
            .clip(AppShapes.ratingBadge)
            .border(width = 1.8.dp, color = Color(0xB0000000), shape = AppShapes.ratingBadge)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            fontSize = 7.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
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

    // Back collapses Extras before it leaves the page. Registered here, above
    // the overlay handlers further down, so those still win while one is open
    // -- see DetailsBackHandlers.
    DetailsBackHandlers(extras)

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
    var showNoDub      by remember { mutableStateOf(false) }

    var showDefaultPrompt by remember { mutableStateOf(false) }
    var isCheckingServers by remember { mutableStateOf(false) }
    var showServerPicker  by remember { mutableStateOf(false) }
    var showServerError   by remember { mutableStateOf(false) }
    // Shown when an anime's AniList mapping doesn't exist yet (resolveAnime 404 →
    // miruroUrl null). Brand-new titles aren't in anime_id_map until AniBridge
    // ingests them, so instead of leaving DuB/SuB inert we tell the user why.
    var showNotYetAvailable by remember { mutableStateOf(false) }
    var pendingUrl         by remember { mutableStateOf<String?>(null) }
    var pendingDub         by remember { mutableStateOf(false) }
    var pendingSeason      by remember { mutableIntStateOf(1) }
    var pendingEpisode     by remember { mutableIntStateOf(1) }
    var pendingEpisodeName by remember { mutableStateOf<String?>(null) }
    var serverOptions      by remember { mutableStateOf<List<ServerOption>>(emptyList()) }
    var serverDefaultIdx   by remember { mutableIntStateOf(0) }
    var lastStreamFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

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
        if (showServerPicker || isCheckingServers || showDefaultPrompt) {
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


    // Server lists captured during a DEFAULT extraction, cached for the lifetime
    // of this detail screen. Keyed by url + audio (url carries ?ep=, so each
    // episode caches its own list) so "Choose a server" can skip the enumerate
    // webview pass when the list is already known. Value = (options, defaultIndex).
    val serverListCache = remember { mutableMapOf<String, Pair<List<ServerOption>, Int>>() }
    fun serverCacheKey(url: String, dub: Boolean) = "$url|${if (dub) "dub" else "sub"}"

    // ── Automatic server failover ────────────────────────────────────────────
    // When the default (or chosen) server is down — a hard extract failure, or a
    // dub request that fell back to sub — walk the rest of the provider list until
    // one yields a real stream. Works for any anime, dub or sub. triedServers is
    // reset at the start of each play (default attempt); lastExtractServer is the
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

    // Back closes whichever prompt/picker is open instead of leaving the screen.
    BackHandler(enabled = showDefaultPrompt || showServerPicker) {
        when {
            showServerPicker -> { showServerPicker = false; showDefaultPrompt = true }
            showDefaultPrompt -> {
                showDefaultPrompt = false
                coroutineScope.launch {
                    awaitFrame()
                    try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                }
            }
        }
    }

    // ── BACK cancels an extraction in progress ───────────────────────────────
    // Extraction is an in-process HTTP call now, so the details screen is always
    // the focused Activity while the overlay is up. Without this, BACK falls
    // through to the Activity handler and leaves the page entirely instead of
    // just dismissing the overlay.
    //
    // The request itself is left to finish rather than cancelled: it is a few
    // hundred bytes, and the result lands in the server's cache where the next
    // play picks it up instantly.
    BackHandler(enabled = session.isExtracting || session.extractPending) {
        Log.d("scraper-vidfast", "↩️ back during extraction — dismissing overlay")
        session.extractCancelled      = true
        session.extractJob?.cancel()
        session.extractJob            = null
        session.isBackendExtract = false
        session.backendStatus    = ""
        session.extractPending   = false
        session.isExtracting     = false
        session.extractStage     = 0
        coroutineScope.launch {
            awaitFrame()
            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
        }
    }

    // Phase 2 result: receives the m3u8 (default OR chosen server) and plays it,
    // then logs the episode to watch history.
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
            // DIRECT FIRST, relay only on failure. The relay works around ISPs that
            // DNS-hijack the CDN host, but it pushes every video byte through the
            // backend (~1 GB per episode each way). Most networks reach the CDN
            // fine, so we hand the player both URLs and let it fall back only if
            // the direct attempt actually fails — see MiruroPlayerActivity.
            val proxied = animeRelayUrl(
                m3u8 = m3u8, tmdbId = tmdbId, mediaType = "anime_tv",
                title = mediaTitle, profileId = favoriteProfileId,
                sourceName = miruroSourceLabel(null, capNames?.getOrNull(capDef)),
                season = pendingSeason, episode = pendingEpisode,
            )
            Log.d("scraper", "M Extract → direct=$m3u8  (relay fallback ready)")
            // Everything the player needs to log the watch itself. It writes the
            // row only once video is actually rolling (onIsPlayingChanged), so a
            // stream that never starts leaves no history and no watched marker.
            context.startActivity(
                Intent(context, MiruroPlayerActivity::class.java)
                    .putExtra("M3U8_URL", m3u8)                  // try the CDN directly
                    .putExtra("M3U8_FALLBACK_URL", proxied)      // relay if that's blocked
                    .putExtra("REFERER", referer)
                    .putExtra("UA", ua)
                    .putExtra("PROFILE_ID",     favoriteProfileId)
                    .putExtra("MEDIA_ID",       tmdbId.toString())
                    .putExtra("MEDIA_TYPE",     mediaType)
                    .putExtra("MEDIA_TITLE",    mediaTitle)
                    .putExtra("SEASON_NUMBER",  pendingSeason)
                    .putExtra("EPISODE_NUMBER", pendingEpisode)
                    .putExtra("EPISODE_NAME",   pendingEpisodeName)
                    .putExtra("SOURCE_NAME",    miruroSourceLabel(null, capNames?.getOrNull(capDef)))
                    // Carried so a mid-episode token expiry can re-extract the SAME
                    // track — see MoviesDetailsScreenCompose for the measurement that
                    // made this necessary.
                    .putExtra("AUDIO",          if (pendingDub) "dub" else "sub")
                    // Same provider on a refetch as this playback started on. triedName is
                    // the manually chosen server when there was one, else the default
                    // resolved to its real name — see MoviesDetailsScreenCompose for why
                    // null is not good enough.
                    .putExtra("SERVER",         triedName)
            )
            // NOTE: watch history is deliberately NOT logged here any more. It used
            // to fire the moment extraction succeeded, which marked an episode
            // watched even if the stream never actually played. MiruroPlayerActivity
            // now logs it on confirmed playback instead (see the extras above).
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
                else   -> { Log.w("scraper", "M Extract: user cancelled") }
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
                else   -> { Log.w("scraper", "M Enumerate: user cancelled") }
            }
        }
    }

    // Default-server extract (server == null). The 200ms delay lets the button
    // press animation finish before the loading overlay appears.
    /**
     * Make sure the provider list for this episode and TRACK is cached.
     *
     * TWO CALLERS, TWO REASONS.
     *
     * The dub failover below is only as good as the list it picks from, and on
     * the "Default server" path there is no list: nothing has opened the picker,
     * so serverListCache is empty and pickNextServer returns null on the first
     * ask. That is the exact path this whole failover exists for.
     *
     * The play path calls it for whichever track actually played, so that
     * backing out of the player and opening "Choose a server" finds the list
     * already here instead of showing a spinner. It used to be dub-only, which
     * left every sub viewer paying for an enumerate the moment they went looking
     * for a different provider.
     *
     * CHEAP NOW IN A WAY IT WAS NOT BEFORE. /api/extract/servers used to mean a
     * second browser run loading the same Miruro page just to read a listbox.
     * The extractor now harvests that list during the extraction itself and the
     * API caches it, so on the play path this is a cache hit — one HTTP round
     * trip, no browser.
     *
     * Idempotent, and silent on failure: a missing list means no failover and a
     * picker that fetches its own, which is where we already were.
     */
    suspend fun ensureServerList(url: String, dub: Boolean) {
        if (serverListCache[serverCacheKey(url, dub)] != null) return
        val list = runCatchingCancellable {
            ApiClient.bluehiveApi.extractServers(
                type    = "tv",
                tmdbId  = tmdbId,
                season  = pendingSeason,
                episode = pendingEpisode,
                audio   = if (dub) "dub" else "sub",
                device  = SessionManager.get().deviceFingerprint,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return
        val opts = list.servers.map { ServerOption(it.name, emptyList()) }
        if (opts.isEmpty()) return
        serverListCache[serverCacheKey(url, dub)] =
            opts to list.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
    }

    /**
     * Anime via the BACKEND extractor.
     *
     * DEFAULT PATH ONLY — sub, default server. Those are the two things the
     * backend's generic load-and-nudge run cannot express: dub needs the Language
     * dropdown opened and the switch confirmed, and a named server needs the
     * Provider dropdown. That choreography lives in MiruroStreamExtractorWebView
     * and stays there, so this deliberately declines rather than half-serving it.
     *
     * MEASURED 2026-08-07 from the extractor container: miruro.tv extracts in
     * 2.0s / 2.6s / 1.9s (3/3) through the SAME generic path every other source
     * uses, well inside the default budget. No bespoke driver was needed, which
     * is why this is a call rather than a port.
     *
     * TMDB coordinates go over the wire, not a Miruro URL: the backend resolves
     * tmdb -> AniList itself via /api/anime/resolve (AniBridge primary, Fribb
     * fill-in, multi-cour seasons reconstructed). Sending a URL would fork that
     * mapping into two places.
     *
     * @param acceptAnyTrack play whatever came back even if it is the wrong
     *        track. Set only by the caller AFTER every dub server has been
     *        tried, so a dub request ends in sub audio rather than a black
     *        screen — labelled Sub, because that is what it is.
     */
    suspend fun extractAnimeViaBackend(dub: Boolean = false,
                                       server: String? = null,
                                       acceptAnyTrack: Boolean = false): ExtractOutcome {
        // Cancellable, like its caller: a page closed mid-request must stop the
        // work, not return MISS and send the caller on to its fallback.
        val body = runCatchingCancellable {
            ApiClient.bluehiveApi.extractStreamAwait(
                type    = "tv",
                tmdbId  = tmdbId,
                season  = pendingSeason,
                episode = pendingEpisode,
                source  = "miruro",
                // anime_tv, not the screen's mediaType — the whole point of the
                // column is telling anime traffic apart in relay_sessions.
                mtype   = "anime_tv",
                title   = mediaTitle,
                profile = favoriteProfileId,
                device  = SessionManager.get().deviceFingerprint,
                // Sent ALWAYS, not only for dub. "sub" is not a no-op here: it is
                // part of the server's cache key, so omitting it on the sub path
                // would write to a different key than an explicit sub request and
                // extract the same stream twice.
                audio   = if (dub) "dub" else "sub",
                server  = server,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body()

        if (body == null || body.m3u8.isBlank()) return ExtractOutcome.MISS

        // A dub request the site quietly served as sub. The manifest is real, so
        // every other check passes — this is the only place it can be caught, and
        // only since ExtractResponse started carrying the track it produced. A
        // null `audio` is an older backend: unknowable, so treat it as it was.
        val wrongTrack = dub && body.audio?.equals("dub", ignoreCase = true) == false
        if (wrongTrack && !acceptAnyTrack) {
            Log.e("scraper", "🎧 asked for dub, backend returned '${body.audio}'" +
                    " (server=${server ?: "default"}) — not playing it yet")
            return ExtractOutcome.WRONG_TRACK
        }
        val playingDub = dub && !wrongTrack

        Log.d("scraper", "M Extract (backend audio=${if (dub) "dub" else "sub"}" +
                "${server?.let { " server=$it" } ?: ""}) → ${body.m3u8.take(60)} cached=${body.cached}")
        // Not a launcher, so a closed page cannot crash here -- but it would still
        // open the player after the viewer has left. Stop quietly instead.
        currentCoroutineContext().ensureActive()
        context.startActivity(
            Intent(context, MiruroPlayerActivity::class.java)
                .putExtra("M3U8_URL", body.m3u8)
                // Already tagged server-side from the query params above, so this
                // needs no animeRelayUrl() call — that helper exists for the
                // ON-DEVICE path, where the backend never sees the playback.
                .putExtra("M3U8_FALLBACK_URL", body.proxy_url)
                .putExtra("REFERER", body.referer)
                .putExtra("IS_HLS", body.isHls ?: true)
                .putExtra("UA", body.user_agent)
                .putExtra("PROFILE_ID",     favoriteProfileId)
                .putExtra("MEDIA_ID",       tmdbId.toString())
                .putExtra("MEDIA_TYPE",     mediaType)
                .putExtra("MEDIA_TITLE",    mediaTitle)
                .putExtra("SEASON_NUMBER",  pendingSeason)
                .putExtra("EPISODE_NUMBER", pendingEpisode)
                .putExtra("EPISODE_NAME",   pendingEpisodeName)
                // Must reflect the track actually extracted — this string is what
                // watch history and relay_sessions record, so hardcoding "Sub"
                // would mislabel every dub playback. `playingDub`, not `dub`: a
                // last-resort sub is recorded as the sub it is, which is the whole
                // point of knowing the difference.
                .putExtra("SOURCE_NAME",    miruroSourceLabel(body.servers, server))
                // Carried so a mid-episode token expiry can re-extract the SAME track.
                // `playingDub`, not `dub`, for the same reason SOURCE_NAME uses it.
                // SERVER is best-effort: null just means "no preference".
                .putExtra("AUDIO",          if (playingDub) "dub" else "sub")
                // Manual selection wins; otherwise resolve the default to its name so a
                // refetch returns to the same provider rather than redrawing the site's
                // current default. Null only when the picker never ran.
                .putExtra("SERVER",         server ?: serverOptions.getOrNull(serverDefaultIdx)?.name)
        )
        return ExtractOutcome.PLAYED
    }

    val launchExtract: (String, Boolean, String?) -> Unit = { url, dub, server ->
        Log.d("scraper", "M Extract (dub=$dub, server=${server ?: "default"}) → $url")
        // Fresh play (default attempt) → reset the failover trail. Track the in-flight
        // provider so its result can mark it tried.
        if (server == null) triedServers.clear()
        lastExtractServer = server
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true
            // HELD IN extractJob, like the source chain, so BACK cancels it. It
            // was launched untracked: Back hid the loading screen, the backend
            // answered a moment later, and the episode started anyway. Cancelled,
            // the backend await ends it, and ensureActive()/launchIfActive()
            // below stop the player and the on-device fallback from starting
            // after the fact.
            session.extractJob = coroutineScope.launch {
                delay(200)
                session.isExtracting = true
                try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}

                // BACKEND FIRST for EVERY anime play — sub, dub, and named
                // servers alike. The backend now drives the Language and Provider
                // dropdowns itself (measured: dub in 1.9s vs 18.4s on-device), so
                // there is no longer a case this cannot serve.
                //
                // Falls THROUGH on any failure rather than surfacing an error. The
                // on-device extractor below is untouched and still works, so a
                // backend that is down, unreachable, or beaten by a site change
                // costs a few seconds and not the episode. That fallback is the
                // reason this is safe to route unconditionally.
                //
                // Burn the provider here, not in extractorLauncher. That handler
                // only ever sees ON-DEVICE results, and the backend now serves
                // every play without leaving this coroutine — so triedServers
                // never grew, and the dub failover below would hand back the same
                // first server forever.
                server?.let { triedServers.add(it) }

                // Cancellable: leaving the page while the backend works must end
                // this coroutine, not count as a miss. Counted as a miss it ran on
                // to the on-device fallback below and launched it through the
                // closed page's launcher -- the v350 crash (PageCoroutines.kt).
                var outcome = runCatchingCancellable {
                    extractAnimeViaBackend(dub = dub, server = server)
                }.getOrDefault(ExtractOutcome.MISS)

                // Dub asked for, sub delivered. Same response the on-device path
                // has always had (RESULT_DUB_FALLBACK) and the same answer: walk
                // the rest of the dub providers before settling.
                if (outcome == ExtractOutcome.WRONG_TRACK) {
                    ensureServerList(url, true)
                    // Read the DUB list straight out of the cache rather than
                    // going through pickNextServer: that helper falls back to
                    // whatever `serverOptions` currently holds, which is the last
                    // list the picker showed and can easily be the sub one. Handing
                    // a `…-sub` provider to an audio=dub extract is how a dub
                    // failover would quietly become another sub playback.
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
                    // Out of servers. Play the sub rather than nothing — but say so
                    // at a level the release build keeps, because silence here is
                    // exactly how Japanese audio arrived under a Dub button with no
                    // trace anywhere.
                    Log.e("scraper", "🔁 dub unavailable on every server — playing sub as last resort")
                    outcome = runCatchingCancellable {
                        extractAnimeViaBackend(dub = dub, server = server, acceptAnyTrack = true)
                    }.getOrDefault(ExtractOutcome.MISS)
                }

                if (outcome == ExtractOutcome.PLAYED) {
                    // WARM THE PICKER FOR THE TRACK THAT JUST PLAYED. Playback
                    // has already started, so this delays nothing the viewer is
                    // waiting on — and the extraction it rides on has just
                    // cached the list server-side, making it a cache hit.
                    //
                    // This is the "back out and choose a different server" path.
                    // Without it that press paid for a whole browser run to
                    // enumerate a page we had open seconds earlier.
                    ensureServerList(url, dub)
                    session.isExtracting   = false
                    session.extractPending = false
                    return@launch
                }
                Log.w("scraper", "M Extract: backend miss (dub=$dub server=$server) — on-device fallback")

                val intent = Intent(context, MiruroExtractorActivity::class.java)
                    .putExtra(MiruroExtractorActivity.EXTRA_URL, url)
                    .putExtra(MiruroExtractorActivity.EXTRA_DUB, dub)
                    .putExtra(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_EXTRACT)
                if (!server.isNullOrBlank()) {
                    intent.putExtra(MiruroExtractorActivity.EXTRA_SERVER_NAME, server)
                } else if (serverListCache[serverCacheKey(url, dub)] == null) {
                    // Default server, list not cached yet → grab the provider list
                    // in this same pass so "Choose a server" is instant afterward.
                    intent.putExtra(MiruroExtractorActivity.EXTRA_CAPTURE_SERVERS, true)
                }
                extractorLauncher.launchIfActive(intent)
            }
        }
    }

    val launchEnumerate: (String, Boolean) -> Unit = { url, dub ->
        Log.d("scraper", "M Enumerate (dub=$dub) → $url")
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true
            coroutineScope.launch {
                delay(200)
                session.extractPending = false
                isCheckingServers = true

                // BACKEND FIRST, and this one also removes an ACTIVITY HOP. The
                // on-device path launched MiruroExtractorActivity, which paused
                // this screen and resumed it — the resume that RestoreFocusOnPlayer-
                // Return races, which is what stole focus from the picker. An
                // in-process call never leaves the screen, so that race cannot
                // occur on this path at all.
                val list = runCatchingCancellable {
                    ApiClient.bluehiveApi.extractServers(
                        type    = "tv",
                        tmdbId  = tmdbId,
                        season  = pendingSeason,
                        episode = pendingEpisode,
                        audio   = if (dub) "dub" else "sub",
                        device  = SessionManager.get().deviceFingerprint,
                    )
                }.getOrNull()?.takeIf { it.isSuccessful }?.body()

                val opts = list?.servers.orEmpty().map { ServerOption(it.name, emptyList()) }
                if (opts.isNotEmpty()) {
                    val defIdx = list!!.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
                    Log.d("scraper", "M Enumerate (backend) → ${opts.size} servers, default=$defIdx")
                    serverListCache[serverCacheKey(url, dub)] = opts to defIdx
                    serverOptions    = opts
                    serverDefaultIdx = defIdx
                    isCheckingServers = false
                    showServerPicker = true
                    return@launch
                }

                Log.w("scraper", "M Enumerate: backend miss — on-device fallback")
                try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}
                enumerateLauncher.launchIfActive(
                    Intent(context, MiruroExtractorActivity::class.java)
                        .putExtra(MiruroExtractorActivity.EXTRA_URL, url)
                        .putExtra(MiruroExtractorActivity.EXTRA_DUB, dub)
                        .putExtra(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_ENUMERATE)
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

    LaunchedEffect(showNoDub) {
        if (showNoDub) { delay(2500); showNoDub = false }
    }

    // NO AUTO-DISMISS. This used to close itself after 30s and drop the viewer
    // back on the default-server prompt. Reading six provider names, deciding,
    // and walking a D-pad there is easily slower than that -- and the dialog
    // vanishing mid-decision looks like the app crashed, then silently undoes
    // the choice they were making. A picker closes when it is picked or backed
    // out of; BackHandler already covers the second.
    LaunchedEffect(showServerError) {
        if (showServerError) { delay(2500); showServerError = false }
    }
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
                                pendingUrl         = url
                                pendingDub         = true
                                pendingSeason      = selectedSeason
                                pendingEpisode     = episodeNumber
                                pendingEpisodeName = epName
                                showDefaultPrompt  = true
                            },
                            onSub = { url ->
                                val epName = selectedEpisode.title
                                stopTrailerAndSelectEpisode(selectedSeason, episodeNumber, epName)
                                // NOT marked watched here — same reason as onDub above.
                                lastStreamFocusRequester = providerFocusRequesters.getOrNull(1)
                                pendingUrl         = url
                                pendingDub         = false
                                pendingSeason      = selectedSeason
                                pendingEpisode     = episodeNumber
                                pendingEpisodeName = epName
                                showDefaultPrompt  = true
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

            if (showNoDub) {
                DetailsToast("No dub available for this episode", dongleRegular)
            }

            if (isCheckingServers) {
                // Focus TRAP, not decoration. ExtractionLoadingOverlay draws a scrim
                // but takes no focus of its own, so the D-pad still reaches the details
                // screen behind it.
                //
                // NOTE ON SCOPE: this only covers the window where this Activity is in
                // FOREGROUND with the overlay up — the gap between isCheckingServers
                // going true and MiruroExtractorActivity actually appearing, plus any
                // non-activity extraction. It does NOT cover the enumerate pass itself,
                // because that runs in a separate Activity and this screen is paused
                // throughout. The bug people actually hit lives on the RESUME from that
                // activity and is guarded in RestoreFocusOnPlayerReturn above.
                //
                // Directional and select keys are swallowed; BACK deliberately is not,
                // so the existing BackHandler can still cancel an enumeration that
                // hangs. Without that exemption this overlay becomes a trap with no
                // way out on a remote that has no other keys.
                ExtractionFocusTrap {
                    ExtractionLoadingOverlay(
                        status = "Checking the site for available servers…",
                        stage  = 2
                    )
                }
            }

            if (showDefaultPrompt) {
                DefaultServerPrompt(
                    onDefault = {
                        showDefaultPrompt = false
                        coroutineScope.launch {
                            awaitFrame()
                            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                        }
                        pendingUrl?.let { launchExtract(it, pendingDub, null) }
                    },
                    onChoose = {
                        showDefaultPrompt = false
                        // NO focus restore here, unlike onDefault above.
                        //
                        // onDefault ENDS the dialog flow — extraction starts and the
                        // screen is the user's again, so pulling focus back to the
                        // source button is right. onChoose CONTINUES into the picker,
                        // and handing focus to a button on the screen underneath was
                        // the bug: on a cache MISS the picker is seconds away (an
                        // enumerate webview pass), so the D-pad spent that window
                        // driving the details screen behind the "Checking servers…"
                        // overlay. On a cache HIT it looked fine only because
                        // showServerPicker flips in this same frame and
                        // ServerPickerOverlay's own focus request (Overlays.kt) beat
                        // the restore — which is why the second press on the same
                        // episode behaved, and a new episode brought it straight back.
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

            if (showServerPicker && serverOptions.isNotEmpty()) {
                ServerPickerOverlay(
                    servers      = serverOptions,
                    audioLabel   = if (pendingDub) "Dub" else "Sub",
                    onSelect     = { serverName ->
                        showServerPicker = false
                        coroutineScope.launch {
                            awaitFrame()
                            try { lastStreamFocusRequester?.requestFocus() } catch (_: Exception) {}
                        }
                        pendingUrl?.let { launchExtract(it, pendingDub, serverName) }
                    }
                )
            }

            if (showServerError) {
                DetailsToast("Couldn't reach the site — please try again", dongleRegular)
            }

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






// =====================================================================================================================>  Episode overlay section
@Composable
private fun EpisodeProviderInlineButtons(
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



@Composable
fun SeasonButton(
    label: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = label,
            fontSize = 16f
        ),
        isToggleable = true,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = modifier,
        dimensions = ModularButtonDimensions(
            mainWidth = 117.5.dp,
            mainHeight = 30.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 117.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 13.5.dp,
            shadowHeight = 12.dp,
            shadowOffset = (-3).dp,
            glowWidth = 135.6.dp,
            glowHeight = 51.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared — see SeasonButtonColors in utilities/ModularButton.kt. It is
        // derived from StreamSourceButtonColors, so these chips and the watch
        // buttons brighten identically on focus.
        colors = SeasonButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-9.25).dp,  // lesser the number the farther it moves left
            offsetY = (-2.5).dp,
            cornerRadius = 10.dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = 110,
            bounceBackDelayMillis = 80
        ),
        externalToggled = isSelected,
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}



// ====================================================================================================================>  Anime episode overlay (DuB / SuB / VidFast)
@Composable
private fun AnimeEpisodeProviderButtons(
    passionFont: FontFamily,
    miruroUrl: String?,                                            // null while resolving OR if unmapped
    /**
     * The source list has not arrived yet.
     *
     * THIS IS WHAT SEPARATES "not yet" FROM "not there", and without it the two
     * were the same thing: miruroUrl is null in both cases, so a press made in
     * the second before the AniList resolve landed reported the episode as
     * unavailable — and the identical press a moment later worked. The list is
     * empty only while loading (it always carries VidFast once built), so its
     * emptiness is the signal.
     */
    resolving: Boolean,
    focusRequesters: List<FocusRequester>,                         // [0]=DuB, [1]=SuB
    onDub: (String) -> Unit,
    onSub: (String) -> Unit,
    onUnavailable: () -> Unit,                                     // genuinely has no mapping
    onBack: () -> Unit,
) {
    /** A press made too early. true = dub, false = sub, null = none waiting. */
    var queued by remember { mutableStateOf<Boolean?>(null) }

    // REMEMBERED, NOT REFUSED. The viewer pressed a button; the right answer is
    // to honour it when the url shows up, not to tell them the episode does not
    // exist because the network was a beat behind their thumb.
    //
    // Re-runs whenever the url or the resolving flag changes, which cancels the
    // delay below — so the bounded wait only expires if nothing ever arrived.
    LaunchedEffect(queued, miruroUrl, resolving) {
        val want = queued ?: return@LaunchedEffect
        if (miruroUrl != null) {
            queued = null
            if (want) onDub(miruroUrl) else onSub(miruroUrl)
            return@LaunchedEffect
        }
        if (!resolving) {           // list arrived and carries no Miruro: real
            queued = null
            onUnavailable()
            return@LaunchedEffect
        }
        // Still resolving. Wait, but not forever — a resolve that never
        // finishes must not leave the button looking dead.
        delay(8_000)
        if (queued != null) {
            queued = null
            onUnavailable()
        }
    }
    // Auto-focus DuB when the overlay opens (matches EpisodeProviderInlineButtons).
    LaunchedEffect(Unit) {
        delay(50)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    fun moveTo(idx: Int): Boolean {
        val fr = focusRequesters.getOrNull(idx) ?: return true
        fr.requestFocus()
        BlueHiveApplication.playHoverSound()
        return true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier.offset(x = 0.3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            AnimeOverlayButton(
                text = "Watch in English",
                passionFont = passionFont,
                focusRequester = focusRequesters[0],
                buttonColors = AnimeDubButtonColors,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> moveTo(1)
                        Key.DirectionDown  -> true
                        Key.DirectionLeft  -> true
                        Key.DirectionUp    -> true
                        Key.Back           -> { onBack(); true }
                        else               -> false
                    }
                },
                onClick = {
                    when {
                        miruroUrl != null -> onDub(miruroUrl)
                        resolving -> queued = true      // honour it when it lands
                        else -> onUnavailable()
                    }
                }
            )
            AnimeOverlayButton(
                text = "Watch in Japanese",
                passionFont = passionFont,
                focusRequester = focusRequesters[1],
                buttonColors = AnimeSubButtonColors,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft  -> moveTo(0)
                        Key.DirectionDown  -> true
                        Key.DirectionRight -> true
                        Key.DirectionUp    -> true
                        Key.Back           -> { onBack(); true }
                        else               -> false
                    }
                },
                onClick = {
                    when {
                        miruroUrl != null -> onSub(miruroUrl)
                        resolving -> queued = false     // honour it when it lands
                        else -> onUnavailable()
                    }
                }
            )
        }
    }
}

@Composable
private fun AnimeOverlayButton(
    text: String,
    passionFont: FontFamily,
    focusRequester: FocusRequester,
    // ONE PALETTE, not four loose colours. Passing the parts individually is how
    // mainFocused went missing here: there was no parameter for it, so no call
    // site could have supplied one, and every focused anime button fell back to
    // the neutral grey default.
    buttonColors: ModularButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(text = text, fontSize = 9f),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequester,
        modifier = modifier,
        dimensions = ModularButtonDimensions(
            mainWidth = 83.dp,
            mainHeight = 23.62.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 83.dp,
            secondHeight = 22.5.dp,
            secondYOffset = 6.5.dp,
            shadowHeight = 6.dp,
            glowWidth = 96.dp,
            glowHeight = 41.5.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        colors = buttonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-6.5).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the movie screen; only the offsets are per-screen,
        // being tuned to this button's smaller size.
        animationConfig = WatchButtonAnimation.copy(
            pressOffset = 2.0.dp,
            textOffsetDefault = 0.6.dp,
            textOffsetPressed = 1.6.dp,
        ),
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}



//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x80000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)






// ════════════════════════════════════════════════════════════════════════════
//  @Preview — watched corner fold
//
//  Renders the REAL WatchedCornerFold composable (not a copy) on a mock episode
//  still, so tweaking WATCHED_FOLD_* above updates this preview immediately.
//  Open this file in Android Studio and hit "Split" / "Design" to see it.
// ════════════════════════════════════════════════════════════════════════════
@Preview(name = "Watched fold — on card", widthDp = 420, heightDp = 190, showBackground = true, backgroundColor = 0xFF0A0A12)
@Composable
private fun WatchedCornerFoldPreview() {
    val episodeShape = RoundedCornerShape(7.5.dp)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Watched — unfocused card (the hard case: marker must read on a dim still)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF16161E))
                .border(1.1.dp, Color(0xFF505050), episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "6/10",
                color = Color(0xFFD6D6D6),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }

        // Watched — focused card (white border + lighter surface)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF2B2B36))
                .border(1.4.dp, Color.White, episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "7/10",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }
    }
}

@Preview(name = "Watched fold — zoomed", widthDp = 150, heightDp = 150, showBackground = true, backgroundColor = 0xFF16161E)
@Composable
private fun WatchedCornerFoldZoomPreview() {
    // Isolated + enlarged so tick geometry is easy to judge while tuning
    // WATCHED_FOLD_TICK_SIZE / WATCHED_FOLD_TICK_STROKE.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16161E)),
        contentAlignment = Alignment.Center
    ) {
        WatchedCornerFold(modifier = Modifier.scale(3f))
    }
}
