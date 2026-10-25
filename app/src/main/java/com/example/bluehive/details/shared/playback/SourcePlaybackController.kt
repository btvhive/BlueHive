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
 * wording follow it too, so the TV can switch over in Stage 7 unchanged.
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

    /**
     * A source button. One body shared by every way a press starts the
     * built-in player, so they cannot drift apart.
     */
    fun start(src: StreamSource) {
        onStart()
        try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}
        session.extractJob = scope.launch { extractAndPlay(src) }
    }

    /**
     * Server-side extraction for VidFast / VidEasy / VidSpark, then our own player.
     *
     * WHY SERVER-SIDE: `vidfast.pro` is refused to every client on a Shield-filtered
     * line — the filter reads the hostname out of the TLS ClientHello and severs
     * the handshake. The backend loads the embed where the filter isn't and returns
     * only the URL; the TV still streams from the CDN directly, so no video bytes
     * cross the backend.
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
        // A fresh press starts a fresh chain: count what it can try, and drop
        // any note left from the last one.
        if (attempt == 1) {
            session.chainTotal  = SourceCatalog.plannedAttempts(
                BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode, src.name,
            ) { usesBackendExtraction(it) }
            session.extractNote = null
        }

        // Display name IS the backend key once lowercased — see usesBackendExtraction.
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
                // Accounting only.
                mtype   = mediaType,
                title   = mediaTitle,
                profile = profileId,
                device  = SessionManager.get().deviceFingerprint,
            )
        }
        val tookMs = System.currentTimeMillis() - startedAt

        session.isExtracting     = false
        session.isBackendExtract = false
        session.extractPending   = false
        session.backendStatus    = ""

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
            // Failover: the next source in the chain, while the backend allows
            // failover and the attempts last.
            val next = if (SourceCatalog.failoverEnabled && attempt < SourceCatalog.maxAttempts) {
                SourceCatalog.nextInChain(
                    BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode, src.name,
                ) { usesBackendExtraction(it) }
            } else null

            if (next != null) {
                Log.e("scraper-vidfast",
                      "failover ${attempt}/${SourceCatalog.maxAttempts}: " +
                      "${src.name} failed ($reason) -> trying ${next.name}")
                session.isExtracting     = true
                session.isBackendExtract = true
                session.extractPending   = true
                session.extractStage     = 1
                session.backendStatus    = "${src.coverName} didn\u2019t work \u2014 trying ${next.label}\u2026"
                session.extractNote      = "${src.coverName} didn\u2019t work \u2014 trying ${next.label}"
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
            )
        )
    }

    /** The player's result: a source that died in playback is handed to the hop. */
    internal fun onPlayerResult(result: ActivityResult) {
        if (result.resultCode == HlsPlayerActivity.RESULT_SOURCE_FAILED) {
            val atMs = result.data?.getLongExtra(HlsPlayerActivity.EXTRA_FAILED_AT_MS, 0L) ?: 0L
            result.data?.getStringExtra(HlsPlayerActivity.EXTRA_FAILED_SOURCE)
                ?.takeIf { it.isNotBlank() }
                ?.let { playbackFailedSource = it to atMs }
        }
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
        session.extractNote = "$failedLabel stopped playing — trying ${next.label}"
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

// Each screen's own log wording, kept: "movie extraction failed" / "extraction
// failed", and so on. Logs only -- nothing reads them.
private val PlaybackRequest.logPrefix: String get() = if (this is PlaybackRequest.Movie) "movie " else ""
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
): SourcePlaybackController {
    val context = LocalContext.current
    val currentRequest by rememberUpdatedState(request)
    val currentOnStart by rememberUpdatedState(onStart)
    val controller = remember(session, scope, context, profileId, mediaType, mediaTitle) {
        SourcePlaybackController(
            session = session, scope = scope, context = context,
            profileId = profileId, mediaType = mediaType, mediaTitle = mediaTitle,
            request = { currentRequest() }, onStart = { currentOnStart() },
        )
    }
    controller.playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.onPlayerResult(result)
    }

    // ── FAILOVER, PLAYBACK HALF ─────────────────────────────────────────────
    // The extraction half lives inside extractAndPlay; this is the case it
    // cannot see -- a source whose url looked fine and then died in the player,
    // which only the player knows and reports through its activity result.
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
    return controller
}
