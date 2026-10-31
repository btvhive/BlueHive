package com.example.bluehive.details.shared.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.api.extractStreamAwait
import com.example.bluehive.auth.SessionManager
import com.example.bluehive.utilities.launchIfActive
import com.example.bluehive.utilities.runCatchingCancellable
import com.example.bluehive.webview.hls.HlsPlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The source chain of a details page: a "Watch on ..." press, the backend
 * extraction, the player, and both halves of the failover -- a source that
 * fails to extract hands over to the next in the chain, and so does one that
 * extracted fine and then died in the player.
 *
 * Moved here from the movie screen in Stage 6 of the details redesign, with
 * the screen's state in the shared [PlaybackSession]. The code is the movie
 * screen's, word for word; what differs for an episode comes from the request
 * ([apiType], season and episode), and the TV screen's log lines and error
 * wording follow it too; the TV screen switched over in Stage 7 unchanged.
 *
 * Every source button goes straight to server-side m3u8 extraction and our own
 * player. The "open the site directly" alternative was GeckoView, which was
 * removed in v371 along with the Settings > Playback method switch.
 */
@Stable
class SourcePlaybackController internal constructor(
    private val session: PlaybackSession,
    private val scope: CoroutineScope,
    private val context: Context,
    private val profileId: Int,
    private val mediaType: String,
    private val mediaTitle: String,
    /** What is played, read at each step: the TV screen's episode differs from press to press. */
    private val request: () -> PlaybackRequest,
    /** Before a press starts: the movie screen stops the trailer; the TV screen also records the episode. */
    private val onStart: () -> Unit,
    /** The episode after the requested one, for the player's Up Next card; none on the movie screen. */
    private val nextEpisode: () -> UpNextEpisode? = { null },
    /** Up Next: make (season, episode, title) the requested episode, as a press on it would. */
    private val onNextEpisode: (Int, Int, String?) -> Unit = { _, _, _ -> },
) {
    internal lateinit var playerLauncher: ActivityResultLauncher<Intent>

    // Which chain step the player currently on screen came from. Read when it
    // reports a playback failure, so the chain keeps counting rather than
    // restarting.
    private var chainAttempt by mutableIntStateOf(1)

    // Set by the player launcher, consumed by the playback-half effect in
    // rememberSourcePlayback. A hop through state because the launcher has to
    // exist before extractAndPlay does, and its callback is not a coroutine.
    // (dead source, where it died in ms) -- the next source starts there.
    internal var playbackFailedSource by mutableStateOf<Pair<String, Long>?>(null)

    // Up Next's hop, the same way: set by the player launcher when the player
    // ended on its Up Next card, consumed by the effect in rememberSourcePlayback.
    internal var playNextHop by mutableStateOf<PlayNextHop?>(null)

    // Episodes in a row played with no remote press, as the last player
    // counted them. A press on a source button starts the count again.
    private var unattended = 0

    /**
     * A source button. One body shared by every way a press starts the
     * built-in player, so they cannot drift apart.
     */
    fun start(src: StreamSource) {
        unattended = 0
        onStart()
        try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}
        session.extractJob = scope.launch { extractAndPlay(src) }
    }

    /**
     * Server-side extraction for VidFast / VidEasy / VidSpark, then our own player.
     *
     * WHY SERVER-SIDE: `vidfast.pro` is refused to every client on a Shield-filtered
     * line — the filter reads the hostname out of the TLS ClientHello and severs
     * the handshake (measured from the TV 2026-08-02: 0/3 with Spectrum Shield on,
     * 3/3 with it off), so the on-device extractor's retry ladder just spent 60 s
     * failing identically. The backend loads the embed where the filter isn't and
     * returns only the URL; the TV still streams from the CDN directly, so no
     * video bytes cross the backend. That works because CDN tokens are
     * Referer-bound, not IP-bound (verified: extracted through a VPN, played 105 s
     * with the tunnel down, zero load errors).
     *
     * No retry loop: the server caches per title and deduplicates concurrent
     * requests, so a second client attempt just re-asks for the same answer.
     */
    internal suspend fun extractAndPlay(
        src: StreamSource,
        /** 1 for the source the viewer pressed; higher for each chain step. */
        attempt: Int = 1,
        /** The sources already tried and failed, oldest first. */
        tried: List<String> = emptyList(),
        /** Where to start, in ms: 0 for a press, the dead source's spot for a failover. */
        startAtMs: Long = 0L,
    ) {
        val req = request()
        session.extractCancelled = false
        session.isExtracting     = true
        session.isBackendExtract = true
        session.extractPending   = true
        session.extractStage     = 1
        // EVERY attempt stamps this, not only the failover ones. Setting it only
        // on failover left it holding the previous title's last attempt number,
        // so the first failover of the NEXT title could write the same value,
        // fail to change the key, and freeze the stage dots -- the exact bug
        // this field exists to prevent.
        session.extractAttempt   = attempt
        session.backendStatus    = "Finding your stream…"
        session.extractSourceLabel = src.coverName
        session.extractStep      = null
        // A fresh press starts a fresh chain: count what it can try, and drop
        // any note left from the last one.
        if (attempt == 1) {
            session.chainTotal  = SourceCatalog.plannedAttempts(
                BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode, src.name,
            ) { usesBackendExtraction(it) }
            session.extractNote = null
        }

        // Display name IS the backend key once lowercased — see usesBackendExtraction.
        // Keeps one source of truth instead of a mapping that can silently drift
        // from the backend's _SOURCES.
        val sourceKey = src.name.lowercase()

        val startedAt = System.currentTimeMillis()
        // Cancellable: leaving the page mid-extract must stop here, not read as
        // a miss and walk on to the next source (see PageCoroutines.kt).
        val result = runCatchingCancellable {
            ApiClient.bluehiveApi.extractStreamAwait(
                type    = req.apiType,
                tmdbId  = req.tmdbId,
                season  = req.apiSeason,
                episode = req.apiEpisode,
                source  = sourceKey,
                // Accounting only — rides through to proxy_url so that IF this
                // playback later falls back to the relay, the row says which
                // title and source cost us the bytes instead of just "1.4 GB".
                mtype   = mediaType,
                title   = mediaTitle,
                profile = profileId,
                device  = SessionManager.get().deviceFingerprint,
                onStep  = { session.extractStep = it },
            )
        }
        val tookMs = System.currentTimeMillis() - startedAt

        session.isExtracting     = false
        session.isBackendExtract = false
        session.extractPending   = false
        session.backendStatus    = ""
        session.extractStep      = null

        // The user left. Do not raise an error and do not start a player over
        // whatever they are looking at now.
        if (session.extractCancelled) {
            Log.d(req.cancelLogTag, "extraction finished after BACK — discarding result")
            return
        }

        val body = result.getOrNull()?.takeIf { it.isSuccessful }?.body()
        if (body == null || body.m3u8.isBlank()) {
            val resp   = result.getOrNull()
            val code   = resp?.code()
            val reason = parseExtractReason(resp)
            Log.e("scraper-vidfast", "${req.logPrefix}extraction failed (http=$code reason=$reason src=${src.name}) " +
                    "${result.exceptionOrNull()?.javaClass?.simpleName ?: ""}")
            // ── FAILOVER ────────────────────────────────────────────────
            // Walk down the chain rather than hand the viewer an error, while the
            // backend allows failover and the attempts last. The order is
            // sources.conf's ORDER, so the fallback and the buttons can never
            // disagree.
            //
            // extractCancelled is already checked above, and BACK cancels
            // extractJob outright — which kills this recursion with it, so a
            // viewer who backs out mid-chain is not followed by three more
            // attempts finishing in the background.
            val next = if (SourceCatalog.failoverEnabled && attempt < SourceCatalog.maxAttempts) {
                SourceCatalog.nextInChain(
                    BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode, src.name,
                ) { usesBackendExtraction(it) }
            } else null

            if (next != null) {
                Log.e("scraper-vidfast",
                      "failover ${attempt}/${SourceCatalog.maxAttempts}: " +
                      "${src.name} failed ($reason) -> trying ${next.name}")
                // Put the flags back up BEFORE recursing: they were cleared
                // above for the success path, and leaving them down would blink
                // the overlay off and on between attempts.
                session.isExtracting     = true
                session.isBackendExtract = true
                session.extractPending   = true
                session.extractStage     = 1
                session.backendStatus    = "${src.coverName}\u2019s page had no working video, opening ${next.label}\u2026"
                session.extractNote      = "${src.coverName}\u2019s page had no working video, opening ${next.label}\u2026"
                extractAndPlay(
                    StreamSource(next.label, next.name, next.url),
                    attempt + 1,
                    tried + src.name,
                    startAtMs,
                )
                return
            }

            session.extractionError =
                extractionErrorMessage(code, reason, src.name, req.errorUnit, tried + src.name)
            return
        }

        // Host + timing so an extraction can be matched to the playback it
        // produced. `cached` explains the latency (a hit is a Redis GET, a miss
        // is a real browser run) and `expires_in` says how stale the manifest
        // already was when playback started — the number that mattered when an
        // episode died 31 minutes in.
        Log.d("scraper-vidfast", "▶️ ${req.playingLabel(src)} " +
                "playlist=${runCatching { android.net.Uri.parse(body.m3u8).host }.getOrNull()} " +
                "cached=${body.cached} expires_in=${body.expires_in}s extract=${tookMs}ms${req.playingTail(body.referer)}")

        // launch(), not startActivity(): the result is how a playback failure
        // gets back here to continue the chain. This was startActivity until
        // 2026-10-19, so a movie that died in the player never failed over.
        chainAttempt = attempt
        playerLauncher.launchIfActive(
            PlayerIntents.hlsPlayer(
                context    = context,
                request    = req,
                body       = body,
                embedUrl   = src.url,
                sourceName = src.name,
                profileId  = profileId,
                mediaType  = mediaType,
                mediaTitle = mediaTitle,
                startAtMs  = startAtMs,
                next       = if (req is PlaybackRequest.Episode) nextEpisode() else null,
                unattended = unattended,
            )
        )
    }

    /** The player's result: a source that died in playback is handed to the hop. */
    internal fun onPlayerResult(result: ActivityResult) {
        if (result.resultCode == HlsPlayerActivity.RESULT_PLAY_NEXT) {
            val data = result.data ?: return
            val episode = data.getIntExtra(HlsPlayerActivity.EXTRA_PLAY_EPISODE, 0)
            if (episode <= 0) return
            playNextHop = PlayNextHop(
                season     = data.getIntExtra(HlsPlayerActivity.EXTRA_PLAY_SEASON, 0),
                episode    = episode,
                title      = data.getStringExtra(HlsPlayerActivity.EXTRA_PLAY_TITLE),
                source     = data.getStringExtra(HlsPlayerActivity.EXTRA_PLAY_SOURCE).orEmpty(),
                unattended = data.getIntExtra(HlsPlayerActivity.EXTRA_UNATTENDED, 0),
            )
            return
        }
        if (result.resultCode == HlsPlayerActivity.RESULT_SOURCE_FAILED) {
            val atMs = result.data?.getLongExtra(HlsPlayerActivity.EXTRA_FAILED_AT_MS, 0L) ?: 0L
            result.data?.getStringExtra(HlsPlayerActivity.EXTRA_FAILED_SOURCE)
                ?.takeIf { it.isNotBlank() }
                ?.let { playbackFailedSource = it to atMs }
        }
    }

    /**
     * Up Next: the player's episode ended on its card, so open the next one --
     * the same source first, then the chain, with the loading screen, exactly
     * as a press on that episode's source button would. Run by the effect
     * keyed on the hop -- see there.
     */
    internal fun continueToNextEpisode() {
        val hop = playNextHop ?: return
        playNextHop = null
        unattended = hop.unattended
        onNextEpisode(hop.season, hop.episode, hop.title)
        val req = request()
        val sources = SourceCatalog.sources(
            BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode,
        ).filter { usesBackendExtraction(it.name) }
        val src = sources.firstOrNull { it.name.equals(hop.source, ignoreCase = true) }
            ?: sources.firstOrNull()
        if (src == null) {
            Log.e("scraper-vidfast", "up next: no source for s${hop.season}e${hop.episode}")
            return
        }
        Log.i("scraper-vidfast", "\u23ED up next: s${hop.season}e${hop.episode} on ${src.name} " +
                "(unattended ${hop.unattended})")
        try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}
        session.extractJob = scope.launch { extractAndPlay(StreamSource(src.label, src.name, src.url)) }
    }

    /** The playback half of the failover; run by the effect keyed on the hop -- see there. */
    internal fun continueAfterPlaybackFailure() {
        val (failed, atMs) = playbackFailedSource ?: return
        playbackFailedSource = null
        val req = request()

        val next = if (SourceCatalog.failoverEnabled &&
                       chainAttempt < SourceCatalog.maxAttempts) {
            SourceCatalog.nextInChain(
                BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode, failed,
            ) { usesBackendExtraction(it) }
        } else null

        if (next == null) {
            Log.e("scraper-vidfast",
                  "${req.logPrefix}playback failed on $failed — chain exhausted, staying put")
            return
        }
        Log.e("scraper-vidfast",
              "failover ${chainAttempt}/${SourceCatalog.maxAttempts} (playback): " +
              "$failed died in the player at ${atMs}ms -> trying ${next.name}")
        val step = chainAttempt + 1
        // The player only reports the source's routing name; the viewer knows it
        // by its button label.
        val failedLabel = SourceCatalog.sources(BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode)
            .firstOrNull { it.name.equals(failed, ignoreCase = true) }?.label ?: failed
        session.extractNote = "$failedLabel\u2019s stream dropped, reopening on ${next.label}\u2026"
        session.extractJob = scope.launch {
            extractAndPlay(
                StreamSource(next.label, next.name, next.url),
                step,
                listOf(failed),
                atMs,
            )
        }
    }
}

/** The player's hand-over for Up Next. */
internal data class PlayNextHop(
    val season: Int,
    val episode: Int,
    val title: String?,
    /** The routing name the last episode played from, tried first. */
    val source: String,
    val unattended: Int,
)

// Each screen's own log wording, kept (see logPrefix). Logs only -- nothing reads them.
private val PlaybackRequest.cancelLogTag: String get() = if (this is PlaybackRequest.Movie) "scraper-vidapi" else "scraper-vidfast"
private fun PlaybackRequest.playingLabel(src: StreamSource): String = when (this) {
    is PlaybackRequest.Movie -> "movie ${src.name}"
    is PlaybackRequest.Episode -> "s${season}e${episode}"
}
private fun PlaybackRequest.playingTail(referer: String?): String =
    if (this is PlaybackRequest.Movie) "" else " referer=$referer"

/** What the error message calls the title: "movie" or "episode". */
private val PlaybackRequest.errorUnit: String get() = if (this is PlaybackRequest.Movie) "movie" else "episode"

/**
 * The page's source chain. [request] and [onStart] may change from one
 * composition to the next; the controller always uses the latest.
 */
@Composable
fun rememberSourcePlayback(
    session: PlaybackSession,
    scope: CoroutineScope,
    profileId: Int,
    mediaType: String,
    mediaTitle: String,
    request: () -> PlaybackRequest,
    onStart: () -> Unit,
    nextEpisode: () -> UpNextEpisode? = { null },
    onNextEpisode: (Int, Int, String?) -> Unit = { _, _, _ -> },
): SourcePlaybackController {
    val context = LocalContext.current
    val currentRequest by rememberUpdatedState(request)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentNext by rememberUpdatedState(nextEpisode)
    val currentOnNext by rememberUpdatedState(onNextEpisode)
    val controller = remember(session, scope, context, profileId, mediaType, mediaTitle) {
        SourcePlaybackController(
            session = session, scope = scope, context = context,
            profileId = profileId, mediaType = mediaType, mediaTitle = mediaTitle,
            request = { currentRequest() }, onStart = { currentOnStart() },
            nextEpisode = { currentNext() },
            onNextEpisode = { season, episode, title -> currentOnNext(season, episode, title) },
        )
    }
    controller.playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.onPlayerResult(result)
    }
    SourcePlaybackEffects(controller)
    return controller
}

/**
 * The source chain's effect. A composable of its own so that the state it is
 * keyed on is read here, not in the screen function: a source dying in the
 * player re-runs this, not the whole screen (architecture P1).
 */
@Composable
private fun SourcePlaybackEffects(controller: SourcePlaybackController) {
    // ── FAILOVER, PLAYBACK HALF ─────────────────────────────────────────────
    // The extraction half lives inside extractAndPlay; this is the case it
    // cannot see -- a source whose url looked fine and then died in the player,
    // which only the player knows and reports through its activity result.
    // Same walk, same order, same budget as the extraction half: both call
    // nextInChain, so the two can never disagree about what comes next.
    //
    // MEASURED 2026-10-19, In the Beginning: VidNest died at 1:30, the player
    // reported the dead source, and nothing here was listening -- the viewer was
    // left on the buttons while MoviesAPI had the film the whole time.
    //
    // THE NEXT STEP RUNS IN THE SCREEN'S SCOPE, NOT THIS EFFECT'S. Clearing the
    // key recomposes and cancels this effect, so an extraction started inside it
    // would die at its first suspension -- and runCatching would read that
    // cancellation as a failed extraction and race down the whole chain.
    // Holding it in extractJob also lets BACK cancel it like any other attempt.
    LaunchedEffect(controller.playbackFailedSource) {
        controller.continueAfterPlaybackFailure()
    }
    // ── UP NEXT ─────────────────────────────────────────────────────────────
    // The player ended on its Up Next card: the next episode starts here, in the
    // screen's scope, for the same reason as the failover above.
    LaunchedEffect(controller.playNextHop) {
        controller.continueToNextEpisode()
    }
}
