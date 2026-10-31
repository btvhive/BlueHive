package com.example.bluehive.details.shared.playback

import android.app.Activity
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
import com.example.bluehive.api.ExtractOutcome
import com.example.bluehive.api.extractStreamAwait
import com.example.bluehive.auth.SessionManager
import com.example.bluehive.utilities.ServerOption
import com.example.bluehive.utilities.launchIfActive
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.webview.hls.HlsPlayerActivity
import com.example.bluehive.utilities.runCatchingCancellable
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The anime flow of a details page (Miruro): the "default server?" prompt and
 * the server picker, the extraction -- BACKEND first, the on-device extractor
 * as the fallback -- and the automatic failover through the server list.
 *
 * Moved here from the movie screen in Stage 6 of the details redesign, with the
 * shared flags in [PlaybackSession] (one loading screen, one BACK-to-cancel,
 * never two extractions at once). The code is the movie screen's, word for
 * word; what differs for an episode comes from the request (type, season and
 * episode, "anime_tv"). The TV screen switched over in Stage 7: its own copy
 * was the same code once the request was mapped to its episode.
 */
@Stable
class AnimePlaybackController internal constructor(
    private val session: PlaybackSession,
    private val scope: CoroutineScope,
    private val context: Context,
    private val profileId: Int,
    private val mediaType: String,
    private val mediaTitle: String,
    /** What is played, read where it is used: the TV screen's episode differs from press to press. */
    private val request: () -> PlaybackRequest,
    /**
     * At the start of every extraction. The movie screen stops the trailer here;
     * the TV screen stops it when English or Japanese is pressed (§10, #6).
     */
    private val onLaunch: () -> Unit,
    /** Returns the cursor to whichever stream button opened the flow. */
    private val restoreFocus: () -> Unit,
    /** The episode after the requested one, for the player's Up Next card; none on the movie screen. */
    private val nextEpisode: () -> UpNextEpisode? = { null },
    /** Up Next: make (season, episode, title) the requested episode, as a press on it would. */
    private val onNextEpisode: (Int, Int, String?) -> Unit = { _, _, _ -> },
) {
    internal lateinit var extractorLauncher: ActivityResultLauncher<Intent>
    internal lateinit var enumerateLauncher: ActivityResultLauncher<Intent>
    /**
     * The player itself, launched for its result since v581: Up Next hands
     * the next episode back through it. Was context.startActivity.
     */
    internal lateinit var playerLauncher: ActivityResultLauncher<Intent>

    // Up Next's hop, as in SourcePlaybackController: set by the player's
    // result, consumed by the effect in AnimePlaybackEffects.
    internal var playNextHop by mutableStateOf<PlayNextHop?>(null)

    // Episodes in a row played with no remote press, as the last player
    // counted them. A server choice (a press) starts the count again.
    private var unattended = 0

    /** The next episode to hand the player, for an episode only. */
    private fun upNextFor(req: PlaybackRequest): UpNextEpisode? =
        if (req is PlaybackRequest.Episode) nextEpisode() else null

    // ── Anime server-selection flow ─────────────────────────────────────────
    var showDefaultPrompt by mutableStateOf(false) // instant "default server?" Yes/No
    var isCheckingServers by mutableStateOf(false) // phase 1: enumerating servers
        private set
    var showServerPicker  by mutableStateOf(false) // phase 1 result: the picker
    var showServerError   by mutableStateOf(false) // transient failure toast
        internal set
    var showNoDub         by mutableStateOf(false) // transient "No dub available" toast
        internal set

    /**
     * Runs the extraction a viewer's server choice starts ("Default server",
     * a server picked): handed the start, it may run it now or later. The TV
     * page's episode overlay snaps its card first (EpisodeSnapState); null --
     * the movie page -- starts at once. Failover retries are not gated.
     */
    var launchGate: ((start: () -> Unit) -> Unit)? = null

    var pendingUrl        by mutableStateOf<String?>(null)
    var pendingDub        by mutableStateOf(false)
    var serverOptions     by mutableStateOf<List<ServerOption>>(emptyList())
    var serverDefaultIdx  by mutableIntStateOf(0)

    /** The server lists and the failover trail through them. */
    val servers = AnimeServerList()

    // The provider of the in-flight extract (null = Miruro's default).
    private var lastExtractServer by mutableStateOf<String?>(null)

    // Set by the result handler to re-extract with the next server; an effect in
    // AnimePlaybackEffects performs the actual re-launch. This state hop breaks
    // the launchExtract ⇄ extractorLauncher reference cycle. (url, dub, server)
    internal var failoverRequest by mutableStateOf<Triple<String, Boolean, String?>?>(null)

    private fun pickNextServer(url: String, dub: Boolean): String? = servers.pickNext(url, dub, serverOptions)

    private suspend fun ensureServerList(url: String, dub: Boolean) = servers.ensure(url, dub, request())

    /**
     * Receives the m3u8 back from the isolated :extractor process, then launches
     * the player here in the main process — keeps back-navigation identical.
     * Phase 2 result: receives the m3u8 (default OR chosen server) and plays it.
     */
    internal fun onExtractorResult(result: ActivityResult) {
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
                servers.cache[servers.key(capUrl, pendingDub)] =
                    opts to capDef.coerceIn(0, opts.size - 1)
                Log.d("scraper", "🗂 cached ${opts.size} servers from default extract")
            }
            // Mark the provider we just tried (named failover server, else Miruro's default).
            val triedName = lastExtractServer
                ?: capNames?.let { it.getOrNull(capDef.coerceIn(0, it.size - 1)) }
            triedName?.let { servers.tried.add(it) }

            if (m3u8.isNullOrBlank()) {
                // Nothing captured — server likely down. Fail over before erroring.
                val next = if (capUrl != null) pickNextServer(capUrl, pendingDub) else null
                if (next != null && capUrl != null) {
                    Log.d("scraper", "🔁 no stream — auto-failover to server '$next'")
                    servers.tried.add(next)
                    failoverRequest = Triple(capUrl, pendingDub, next)
                } else {
                    Log.e("scraper", "M Extract: OK result but no m3u8")
                    showServerError = true
                }
                return
            }

            // Dub requested but we got the sub last-resort → try the next server.
            if (pendingDub && dubFallback) {
                val next = if (capUrl != null) pickNextServer(capUrl, true) else null
                if (next != null && capUrl != null) {
                    Log.d("scraper", "🔁 dub server down — auto-failover to server '$next'")
                    servers.tried.add(next)
                    failoverRequest = Triple(capUrl, true, next)
                    return
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
            // played.
            val req = request()
            playerLauncher.launch(
                PlayerIntents.miruroOnDevice(
                    context    = context,
                    request    = req,
                    m3u8       = m3u8,
                    referer    = referer,
                    userAgent  = ua,
                    sourceName = miruroSourceLabel(null, capNames?.getOrNull(capDef)),
                    profileId  = profileId,
                    mediaType  = mediaType,
                    mediaTitle = mediaTitle,
                    dub        = pendingDub,
                    server     = triedName,
                    next       = upNextFor(req),
                    unattended = unattended,
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
                        servers.tried.add(next)
                        failoverRequest = Triple(u, pendingDub, next)
                    } else {
                        Log.w("scraper", "M Extract: failed"); showServerError = true
                    }
                }
                else   -> { Log.w("scraper", "M Extract: user cancelled") /* stay silent */ }
            }
        }
    }

    /** Phase 1 result: receives the server list, then shows the picker. */
    internal fun onEnumerateResult(result: ActivityResult) {
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
                    servers.cache[servers.key(u, pendingDub)] =
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

    /**
     * Anime via the BACKEND extractor.
     *
     * MEASURED 2026-08-07 from the extractor container: miruro.tv extracts in
     * 2.0 s / 2.6 s / 1.9 s (3/3) through the SAME generic path every other
     * source uses, well inside the default budget -- which is why this is a
     * call rather than a port.
     *
     * TMDB coordinates go over the wire, not a Miruro URL: the backend resolves
     * tmdb -> AniList itself via /api/anime/resolve (AniBridge primary, Fribb
     * fill-in, multi-cour seasons reconstructed). Sending a URL would fork that
     * mapping into two places. A film maps straight to its AniList id with a
     * null episode, and the miruro movie template omits ?ep= entirely.
     *
     * @param acceptAnyTrack play whatever came back even if it is the wrong
     *        track. Set only once every dub server has been tried, so a dub
     *        request ends in sub audio rather than a black screen -- labelled
     *        Sub, because that is what it is.
     */
    internal suspend fun extractViaBackend(dub: Boolean = false,
                                           server: String? = null,
                                           acceptAnyTrack: Boolean = false): ExtractOutcome {
        val req = request()
        // Cancellable, like its caller: a page closed mid-request must stop the
        // work, not return MISS and send the caller on to its fallback.
        val body = runCatchingCancellable {
            ApiClient.bluehiveApi.extractStreamAwait(
                type    = req.apiType,
                tmdbId  = req.tmdbId,
                season  = req.apiSeason,
                episode = req.apiEpisode,
                source  = "miruro",
                // anime_movie / anime_tv, not the screen's mediaType -- the whole
                // point of the column is telling anime traffic apart in
                // relay_sessions.
                mtype   = req.animeType,
                title   = mediaTitle,
                profile = profileId,
                device  = SessionManager.get().deviceFingerprint,
                // Sent always — "sub" is part of the server's cache key, not a
                // no-op, so omitting it would extract the same stream under a
                // second key.
                audio   = if (dub) "dub" else "sub",
                server  = server,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body()

        if (body == null || body.m3u8.isBlank()) return ExtractOutcome.MISS

        // Dub asked for, sub delivered. The manifest is real, so every other
        // check passes -- this is the only place it can be caught, and only since
        // ExtractResponse started carrying the track it produced. A null `audio`
        // is an older backend: unknowable, so treat it as before.
        val wrongTrack = dub && body.audio?.equals("dub", ignoreCase = true) == false
        if (wrongTrack && !acceptAnyTrack) {
            Log.e("scraper", "🎧 asked for dub, backend returned '${body.audio}'" +
                    " (server=${server ?: "default"}) — not playing it yet")
            return ExtractOutcome.WRONG_TRACK
        }
        val playingDub = dub && !wrongTrack

        Log.d("scraper", "M Extract (backend ${req.logPrefix}audio=${if (dub) "dub" else "sub"}" +
                "${server?.let { " server=$it" } ?: ""}) → ${body.m3u8.take(60)} cached=${body.cached}")
        // Not a launcher, so a closed page cannot crash here -- but it would still
        // open the player after the viewer has left. Stop quietly instead.
        playerLauncher.launchIfActive(
            PlayerIntents.miruroBackend(
                context    = context,
                request    = req,
                body       = body,
                sourceName = miruroSourceLabel(body.servers, server),
                profileId  = profileId,
                mediaType  = mediaType,
                mediaTitle = mediaTitle,
                playingDub = playingDub,
                server     = server ?: serverOptions.getOrNull(serverDefaultIdx)?.name,
                next       = upNextFor(req),
                unattended = unattended,
            )
        )
        return ExtractOutcome.PLAYED
    }

    /**
     * The player's result: Up Next ended the episode on its card. The page's
     * episode moves at once, so the source overlay is closed before the page
     * draws again; the extraction starts from the effect.
     */
    internal fun onPlayerResult(result: ActivityResult) {
        if (result.resultCode != HlsPlayerActivity.RESULT_PLAY_NEXT) return
        val hop = playNextHopFrom(result.data) ?: return
        onNextEpisode(hop.season, hop.episode, hop.title)
        playNextHop = hop
    }

    /**
     * Up Next: open the next episode the way English or Japanese would --
     * the same track, Miruro's default server (the provider names differ per
     * episode), backend first with the on-device fallback, the loading screen.
     */
    internal fun continueToNextEpisode() {
        val hop = playNextHop ?: return
        playNextHop = null
        unattended = hop.unattended
        val req = request()
        val url = SourceCatalog.sources(
            BlueHiveApplication.appContext, req.apiType, req.tmdbId, req.catalogSeason, req.catalogEpisode,
        ).firstOrNull { it.name.equals("Miruro", ignoreCase = true) }?.url
        if (url == null) {
            Log.e("scraper", "up next: no Miruro url for s${hop.season}e${hop.episode}")
            return
        }
        Log.i("scraper", "\u23ED up next: s${hop.season}e${hop.episode} ${if (pendingDub) "dub" else "sub"} " +
                "(unattended ${hop.unattended})")
        pendingUrl = url
        launchExtract(url, pendingDub, null)
    }

    /**
     * Launch a real extraction (default server when server == null).
     * extractPending is set immediately to block double-fire during the animation
     * window. The loading overlay itself is delayed so the button press
     * animation fully completes before the scrim appears.
     */
    fun launchExtract(url: String, dub: Boolean, server: String?) {
        Log.d("scraper", "M Extract (dub=$dub, server=${server ?: "default"}) → $url")
        // Inside the launcher rather than at its call sites: this is reached
        // from the DuB/SuB prompt, the SERVER PICKER and the failover retry,
        // and a future caller should not have to remember. Idempotent.
        onLaunch()
        // Fresh play (default attempt) → reset the failover trail. Track the in-flight
        // provider so its result can mark it tried.
        if (server == null) servers.tried.clear()
        lastExtractServer = server
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true
            // HELD IN extractJob, like the source chain, so BACK cancels it. It
            // was launched untracked: Back hid the loading screen, the backend
            // answered a moment later, and the film started anyway. Cancelled,
            // the backend await ends it, and ensureActive()/launchIfActive()
            // below stop the player and the on-device fallback from starting
            // after the fact.
            session.extractJob = scope.launch {
                delay(200)
                session.isExtracting = true
                try { BlueHiveApplication.coilImageLoader.memoryCache?.clear() } catch (_: Exception) {}

                // BACKEND FIRST for EVERY anime play — sub, dub and named
                // servers alike. The backend drives the Language and Provider
                // dropdowns itself (measured: dub in 1.9 s vs 18.4 s on-device),
                // so there is no case left that it cannot serve.
                // Still falls THROUGH on failure: the on-device extractor below is
                // untouched, so a backend outage costs seconds, not the film --
                // and that fallback is what makes routing every play here safe.
                //
                // Burn the provider here, not in extractorLauncher — that handler
                // only sees ON-DEVICE results, so the tried set never grew and the
                // dub failover below would return the same server forever.
                server?.let { servers.tried.add(it) }

                // Cancellable: leaving the page while the backend works must end
                // this coroutine, not count as a miss and launch the on-device
                // fallback through the closed page's launcher -- the v350 crash
                // (PageCoroutines.kt).
                var outcome = runCatchingCancellable {
                    extractViaBackend(dub = dub, server = server)
                }.getOrDefault(ExtractOutcome.MISS)

                // Dub asked for, sub delivered. Same response the on-device path
                // has always had (RESULT_DUB_FALLBACK) and the same answer: walk
                // the rest of the dub providers before settling.
                if (outcome == ExtractOutcome.WRONG_TRACK) {
                    ensureServerList(url, true)
                    // The DUB list specifically, not pickNextServer — that helper
                    // falls back to `serverOptions`, which may still hold the sub
                    // list from the last picker. Handing a `…-sub` provider to an
                    // audio=dub extract is how a dub failover would quietly become
                    // another sub playback.
                    val next = servers.cache[servers.key(url, true)]?.first
                        ?.firstOrNull { it.name !in servers.tried }?.name
                    if (next != null) {
                        Log.e("scraper", "🔁 dub came back as sub — failing over to server '$next'")
                        servers.tried.add(next)
                        session.isExtracting   = false
                        session.extractPending = false
                        failoverRequest = Triple(url, true, next)
                        return@launch
                    }
                    // Out of servers. Play the sub rather than nothing -- but say so
                    // at a level the release build keeps, because silence here is
                    // exactly how Japanese audio arrived under a Dub button with no
                    // trace anywhere.
                    Log.e("scraper", "🔁 dub unavailable on every server — playing sub as last resort")
                    outcome = runCatchingCancellable {
                        extractViaBackend(dub = dub, server = server, acceptAnyTrack = true)
                    }.getOrDefault(ExtractOutcome.MISS)
                }

                if (outcome == ExtractOutcome.PLAYED) {
                    // Warm the picker for the track that just played. Playback
                    // has already started and the list is cached server-side by
                    // the extraction, so this is one round trip and delays nothing.
                    // This is the "back out and choose a different server" path:
                    // without it that press paid for a whole browser run to
                    // enumerate a page we had open seconds earlier.
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
                        listNotCached = servers.cache[servers.key(url, dub)] == null,
                    )
                )
            }
        }
    }

    /**
     * Launch the server-list check (phase 1). The same short delay before the
     * "Checking…" overlay appears so the "Choose a server" animation finishes.
     */
    fun launchEnumerate(url: String, dub: Boolean) {
        Log.d("scraper", "M Enumerate (dub=$dub) → $url")
        if (!session.isExtracting && !session.extractPending && !isCheckingServers) {
            session.extractPending = true      // immediate guard for the 300ms window
            // HELD IN extractJob, like an extraction, so BACK can stop it: in the
            // first 200 ms through the extraction handler (extractPending), after
            // that through cancelServerCheck. It was launched untracked, and BACK
            // during "Checking the site…" left the details page.
            session.extractJob = scope.launch {
                delay(200)
                session.extractPending = false // hand off guard duty to isCheckingServers
                isCheckingServers = true

                // BACKEND FIRST, which also removes an ACTIVITY HOP. The on-device
                // path launched MiruroExtractorActivity, pausing and resuming this
                // screen — and that resume is what RestoreFocusOnPlayerReturn
                // races, the cause of the picker losing focus. An in-process call
                // never leaves the screen, so the race cannot arise here.
                val req = request()
                val list = runCatchingCancellable {
                    ApiClient.bluehiveApi.extractServers(
                        type    = req.apiType,
                        tmdbId  = req.tmdbId,
                        season  = req.apiSeason,
                        episode = req.apiEpisode,
                        audio   = if (dub) "dub" else "sub",
                        device  = SessionManager.get().deviceFingerprint,
                    )
                }.getOrNull()?.takeIf { it.isSuccessful }?.body()

                val opts = list?.servers.orEmpty().map { ServerOption(it.name, emptyList()) }
                if (opts.isNotEmpty()) {
                    val defIdx = list!!.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
                    Log.d("scraper", "M Enumerate (backend) → ${opts.size} servers, default=$defIdx")
                    servers.cache[servers.key(url, dub)] = opts to defIdx
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

    /** Auto server-failover: the result handler stashed the next attempt; run it. */
    internal fun runFailoverRequest() {
        val req = failoverRequest ?: return
        failoverRequest = null
        launchExtract(req.first, req.second, req.third)
    }

    /**
     * BACK while "Visiting the site to list its servers…" is up. The check
     * looks like the loading screen and now acts like it: it stops, and the
     * cursor returns to the button that opened the flow. No Back handler was
     * enabled while it ran, so BACK fell through and left the details page.
     *
     * The check runs in session.extractJob (see launchEnumerate), so this ends
     * the backend call, and launchIfActive keeps the on-device fallback from
     * starting after the fact. Once that fallback's activity is up, BACK is
     * its own and comes back through onEnumerateResult as a cancel.
     */
    fun cancelServerCheck() {
        Log.d("scraper", "↩️ back during the server check — cancelling")
        session.extractJob?.cancel()
        session.extractJob = null
        isCheckingServers = false
        restoreFocus()
    }

    /** BACK closes whichever picker/prompt is open instead of leaving the screen. */
    fun onPromptBack() {
        when {
            showServerPicker -> { showServerPicker = false; showDefaultPrompt = true }
            showDefaultPrompt -> {
                showDefaultPrompt = false
                // Return the cursor to whichever stream button opened the prompt.
                restoreFocus()
            }
        }
    }

    /**
     * "Default server" on the prompt. It ENDS the dialog flow, so the cursor
     * snaps straight back to the DuB/SuB button -- it glows during the short
     * press animation before the loading overlay appears -- and the extraction
     * starts.
     */
    fun onDefaultServer() {
        unattended = 0
        showDefaultPrompt = false
        restoreFocus()
        val dub = pendingDub
        pendingUrl?.let { url -> gated { launchExtract(url, dub, null) } }
    }

    /**
     * "Choose a server" on the prompt: the picker, at once from the cache, or
     * after the server-list check.
     *
     * NO focus restore here, unlike onDefaultServer. That one ENDS the dialog
     * flow; this one CONTINUES into the picker, and handing focus to a button
     * on the screen underneath was the bug: on a cache MISS the picker is
     * seconds away (an enumerate pass), so the D-pad spent that window driving
     * the details screen behind the "Checking servers…" overlay. On a cache HIT
     * it looked fine only because showServerPicker flips in this same frame and
     * ServerPickerOverlay's own focus request (Overlays.kt) beat the restore --
     * which is why the second press on the same episode behaved, and a new
     * episode brought it straight back.
     */
    fun onChooseServer() {
        showDefaultPrompt = false
        val cached = pendingUrl?.let { servers.cache[servers.key(it, pendingDub)] }
        if (cached != null) {
            Log.d("scraper", "🗂 server picker from cache (${cached.first.size} servers)")
            serverOptions    = cached.first
            serverDefaultIdx = cached.second
            showServerPicker = true
        } else {
            pendingUrl?.let { launchEnumerate(it, pendingDub) }
        }
    }

    /**
     * A server picked in the picker. Same as "Default server": the original
     * button lights back up during the short window before the loading overlay
     * takes over, and that server is extracted.
     */
    fun onServerPicked(serverName: String) {
        unattended = 0
        showServerPicker = false
        restoreFocus()
        val dub = pendingDub
        pendingUrl?.let { url -> gated { launchExtract(url, dub, serverName) } }
    }

    /** [start] through [launchGate], or at once without one. */
    private fun gated(start: () -> Unit) {
        val gate = launchGate
        if (gate == null) start() else gate(start)
    }
}

/**
 * The page's anime flow. [request], [onLaunch] and [restoreFocus] may change
 * from one composition to the next; the controller always uses the latest.
 */
@Composable
fun rememberAnimePlayback(
    session: PlaybackSession,
    scope: CoroutineScope,
    profileId: Int,
    mediaType: String,
    mediaTitle: String,
    request: () -> PlaybackRequest,
    onLaunch: () -> Unit,
    restoreFocus: () -> Unit,
    nextEpisode: () -> UpNextEpisode? = { null },
    onNextEpisode: (Int, Int, String?) -> Unit = { _, _, _ -> },
): AnimePlaybackController {
    val context = LocalContext.current
    val currentRequest by rememberUpdatedState(request)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentRestoreFocus by rememberUpdatedState(restoreFocus)
    val currentNext by rememberUpdatedState(nextEpisode)
    val currentOnNext by rememberUpdatedState(onNextEpisode)
    val controller = remember(session, scope, context, profileId, mediaType, mediaTitle) {
        AnimePlaybackController(
            session = session, scope = scope, context = context,
            profileId = profileId, mediaType = mediaType, mediaTitle = mediaTitle,
            request = { currentRequest() }, onLaunch = { currentOnLaunch() },
            restoreFocus = { currentRestoreFocus() },
            nextEpisode = { currentNext() },
            onNextEpisode = { season, episode, title -> currentOnNext(season, episode, title) },
        )
    }
    controller.extractorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.onExtractorResult(result)
    }
    controller.enumerateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.onEnumerateResult(result)
    }
    controller.playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.onPlayerResult(result)
    }
    AnimePlaybackEffects(controller)
    return controller
}

/**
 * The anime flow's effects. A composable of their own so that the state they
 * are keyed on is read here, not in the screen function: a failover or a toast
 * re-runs this, not the whole screen (architecture P1).
 */
@Composable
private fun AnimePlaybackEffects(controller: AnimePlaybackController) {
    // Auto server-failover: the result handler stashes the next attempt here; run it.
    LaunchedEffect(controller.failoverRequest) {
        controller.runFailoverRequest()
    }

    // Up Next: the player ended on its card; the next episode starts here, in
    // the screen's scope (see SourcePlaybackEffects).
    LaunchedEffect(controller.playNextHop) {
        controller.continueToNextEpisode()
    }

    LaunchedEffect(controller.showNoDub) {
        if (controller.showNoDub) {
            delay(2500)
            controller.showNoDub = false
        }
    }

    // 30s on the picker → close it and send the user back to the Yes/No prompt.
    // NO AUTO-DISMISS. This used to close itself after 30s and drop the viewer
    // back on the default-server prompt. Reading six provider names, deciding,
    // and walking a D-pad there is easily slower than that -- and the dialog
    // vanishing mid-decision looks like the app crashed, then silently undoes
    // the choice they were making. A picker closes when it is picked or backed
    // out of; DetailsBackHandlers already covers the second.

    LaunchedEffect(controller.showServerError) {
        if (controller.showServerError) {
            delay(2500)
            controller.showServerError = false
        }
    }
}
