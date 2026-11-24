package com.example.bluehive.livetv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.BuildConfig
import com.example.bluehive.IdleWatcher
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.LiveSessionResponse
import com.example.bluehive.utilities.runCatchingCancellable
import com.example.bluehive.webview.BeeLogo
import com.example.bluehive.webview.exoplayer_resources.ExoPlayerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Live TV's player.
 *
 * Tuning goes through bluehive-live-api: the session call makes sure the channel
 * has a working link (its backup's when the main feed is down) and answers with
 * a playlist path that carries its own grant. ExoPlayer then loads that playlist
 * from the API host and the chunks straight from TikTok's CDN, turned back into
 * MPEG-TS by [ChunkUnwrapDataSource].
 *
 * Separate from HlsPlayerActivity on purpose: a live channel has no position, so
 * nothing here resumes, seeks, re-extracts or reports progress, and the film
 * player stays exactly as it was.
 *
 * Remote: Up/Down change channel (a run of presses settles for 300 ms and tunes
 * once), Click opens the channel list, Back leaves. Any key shows the banner.
 *
 * A channel that freezes without ExoPlayer raising an error (Comedy Central,
 * 2026-10-29: a broken source, a stall loop, a frozen picture until a press), or
 * a tune that never shows a picture, gets a fresh session from [stallTick].
 */
@UnstableApi
class LivePlayerActivity : ComponentActivity() {

    /** A channel as the tuning card shows it: an On now game has no number, and its start, league and sport. */
    data class Channel(
        val id: String,
        val name: String,
        val row: String,
        val quality: String,
        val number: Int = 0,
        val rowColor: Long = 0xFF2644A6,
        val start: Long = 0L,
        val league: String = "",
        val sport: String = "",
    )

    private enum class Phase { TUNING, PLAYING, OFF_AIR }

    // ── What the overlays show ────────────────────────────────────────────────
    private var phase by mutableStateOf(Phase.TUNING)
    /** How far a tune has got ([LiveTuning.STAGES]): 1 = the server finding the stream, 2 = the picture loading. */
    private var tuneStage by mutableIntStateOf(1)
    private var shown by mutableIntStateOf(0)
    private var bannerVisible by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var listOpen by mutableStateOf(false)
    private var listFocus by mutableIntStateOf(0)
    private var offAirButton by mutableIntStateOf(0)
    private var stillWatchingLeft by mutableStateOf<Int?>(null)

    private lateinit var channels: List<Channel>
    private lateinit var player: ExoPlayer
    private var listTitle = ""
    private var profileId = -1
    /** The channel whose session is loaded in the player, -1 for none. */
    private var tuned = -1
    private var tuneJob: Job? = null
    private var zapJob: Job? = null
    private var bannerJob: Job? = null
    private var noticeJob: Job? = null
    private var stoppedInBackground = false
    private var lastKeyAt = SystemClock.elapsedRealtime()
    private val recoveries = ArrayDeque<Long>()
    private val stall = StallWatch()
    private val timelineWindow = Timeline.Window()
    /** When the current tune's media source was prepared; 0 once a picture shows. */
    private var preparedAt = 0L
    /** When the press (or list pick) behind the current tune happened, for [mark]. */
    private var markT0 = 0L

    /**
     * Test builds only (BLUEHIVE_LIVE_TEST=1): where a zap's time goes, at Log.e
     * so the release-style build keeps it. `adb logcat -s LIVEZAP`.
     */
    private fun mark(what: String) {
        if (!BuildConfig.LIVE_TEST) return
        Log.e("LIVEZAP", "+${SystemClock.elapsedRealtime() - markT0} ms ${channels[shown].id}: $what")
    }

    // ── Neighbour preloading (2026-11-18) ─────────────────────────────────────
    //
    // MEASURED on .241 (Fire TV Stick 4K) with warm server links: a zap still took
    // 1.8-2.5 s, all of it on the TV -- the session call, the playlist, and ~2 MB
    // of first chunk before a picture. So while a channel plays, the ones above
    // and below get their session and their first [PRELOAD_MS] loaded by a
    // DefaultPreloadManager (no second decoder; the bytes sit in the player's
    // allocator), and Up/Down onto one starts from memory.
    //
    // THE COST IS DATA, so it is bounded: a preload is good for
    // [PRELOAD_FRESH_MS] (a live window is ~15 s); stale ones are redone only
    // while the viewer is surfing (a zap in the last [SURF_WINDOW_MS]) and are
    // dropped once they settle on a channel. Background drops everything.

    /** A neighbour ready to play: its session, and the item the manager preloads. */
    private class Spare(val session: LiveSessionResponse, val item: MediaItem, val at: Long)

    private lateinit var preloads: DefaultPreloadManager
    /** Channel index -> its preloaded start. */
    private val spares = HashMap<Int, Spare>()
    /** Channel id -> (session, when it was answered): a tune or a spare reuses it. */
    private val sessionCache = HashMap<String, Pair<LiveSessionResponse, Long>>()
    /** The indexes the manager should load; read on its thread. */
    @Volatile private var preloadTargets: Set<Int> = emptySet()
    /** The preloaded item the player is playing, released from the manager once replaced. */
    private var inPlayer: MediaItem? = null
    private var preloadJob: Job? = null
    private var preloadSeq = 0
    private var lastZapAt = 0L
    /** +1 Down, -1 Up: a refresh keeps only the next channel that way fresh. */
    private var lastZapDirection = 1

    private val preloadTarget = TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> { index ->
        if (index in preloadTargets) DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(PRELOAD_MS) else null
    }

    private fun neighbours(i: Int): List<Int> =
        if (channels.size < 2) emptyList()
        else listOf((i - 1).mod(channels.size), (i + 1).mod(channels.size)).distinct().filter { it != i }

    private fun cachedSession(id: String): LiveSessionResponse? =
        sessionCache[id]?.takeIf { SystemClock.elapsedRealtime() - it.second < SESSION_CACHE_MS }?.first

    private fun freshSpare(index: Int): Spare? =
        spares[index]?.takeIf { SystemClock.elapsedRealtime() - it.at <= PRELOAD_FRESH_MS }

    private fun dropSpare(index: Int) {
        spares.remove(index)?.let { preloads.remove(it.item) }
    }

    private fun dropAllSpares() {
        preloadJob?.cancel()
        spares.keys.toList().forEach(::dropSpare)
        preloadTargets = emptySet()
    }

    /** After a picture: get the channels above and below [center] ready. */
    private fun preloadNeighbours(center: Int, after: Long = PRELOAD_AFTER_PICTURE_MS, only: Int? = null) {
        if (!PRELOAD_ON) return
        preloadJob?.cancel()
        preloadJob = lifecycleScope.launch {
            delay(after)                        // the channel on screen fills its buffer first
            val want = only?.let { listOf(it) } ?: neighbours(center)
            spares.keys.filter { it !in want }.forEach(::dropSpare)
            for (n in want) {
                if (freshSpare(n) != null) continue
                dropSpare(n)
                val ch = channels[n]
                val session = cachedSession(ch.id) ?: runCatchingCancellable {
                    ApiClient.bluehiveApi.startLiveSession(ch.id)
                }.getOrNull()?.let { r -> r.body()?.takeIf { r.isSuccessful } }
                    ?.also { sessionCache[ch.id] = it to SystemClock.elapsedRealtime() }
                    ?: continue                 // off air right now: tuned the slow way if chosen
                val playlist = BuildConfig.API_BASE_URL.trimEnd('/') + session.playlist
                val source = ExoPlayerConfig.hlsMediaSource(
                    playlist, dataSourcesFor(session.headers, playlist), mediaId = "${ch.id}#${++preloadSeq}",
                )
                spares[n] = Spare(session, source.mediaItem, SystemClock.elapsedRealtime())
                preloads.add(source, n)
                mark("preloading ${ch.id}")
            }
            preloadTargets = spares.keys.toSet()
            preloads.setCurrentPlayingIndex(center)
            preloads.invalidate()
        }
    }

    /** Once a second while playing: keep spares fresh while surfing, drop them once settled. */
    private fun preloadTick() {
        if (!PRELOAD_ON || phase != Phase.PLAYING || spares.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (spares.values.none { now - it.at > PRELOAD_FRESH_MS }) return
        if (now - lastZapAt < SURF_WINDOW_MS) {
            // Still surfing: keep the next channel the way they are going fresh
            // (half the data of refreshing both); the other one is let go.
            if (preloadJob?.isActive != true && channels.size > 1) {
                preloadNeighbours(shown, after = 0L, only = (shown + lastZapDirection).mod(channels.size))
            }
        } else {
            mark("settled: dropping preloads")
            dropAllSpares()
        }
    }

    /**
     * Built per session: the headers come from the server's answer. zlive's own
     * player sends Origin/Referer, its transcode.cfd proxy refuses chunks without
     * them, and the other hosts (TikTok, our API) accept them.
     */
    private fun dataSourcesFor(headers: Map<String, String>?, playlist: String) = ChunkUnwrapDataSource.Factory(
        ExoPlayerConfig.dataSourceFactory(
            ExoPlayerConfig.CHROME_USER_AGENT,
            (headers ?: SITE_HEADERS) + LiveTvData.testHeaders(),
            context = this,
        ),
        relay = ChunkUnwrapDataSource.relayFor(android.net.Uri.parse(playlist)),
    )

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (phase != Phase.TUNING) return
            mark("FIRST FRAME")
            phase = Phase.PLAYING
            preparedAt = 0L
            showBanner()
            preloadNeighbours(shown)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            IdleWatcher.setPlaying(IdleWatcher.SOURCE_LIVE, isPlaying)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
        ) = stall.reset()

        override fun onPlayerError(error: PlaybackException) = recover(error)
    }

    /** Playlist and chunk load failures, so the next stall shows its cause in logcat. */
    private val loadErrors = object : AnalyticsListener {
        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean,
        ) {
            Log.w(TAG, "live ${channels[shown].id}: load error from ${loadEventInfo.uri.host}: $error")
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData, retryCount: Int,
        ) {
            if (phase == Phase.TUNING) mark("load start ${kindOf(mediaLoadData)} ${loadEventInfo.uri.host}")
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (phase == Phase.TUNING) mark(
                "load done ${kindOf(mediaLoadData)} ${loadEventInfo.uri.host} " +
                    "${loadEventInfo.bytesLoaded / 1024} KB in ${loadEventInfo.loadDurationMs} ms"
            )
        }

        private fun kindOf(d: MediaLoadData) = when (d.dataType) {
            C.DATA_TYPE_MANIFEST -> "playlist"
            C.DATA_TYPE_MEDIA -> "chunk"
            else -> "type${d.dataType}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channels = channelsOf(intent)
        if (channels.isEmpty()) {
            finish()
            return
        }
        profileId = intent.getIntExtra(EXTRA_PROFILE, -1)
        listTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        BlueHiveApplication.freeImageMemory("Live TV opened")
        shown = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(channels.indices)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        // The tuning screen's globe carries the bee; decoded once, before the first tune.
        BeeLogo.prime(this)

        val preloadBuilder = DefaultPreloadManager.Builder(this, preloadTarget)
            .setRenderersFactory(ExoPlayerConfig.renderersFactory(this))
            .setLoadControl(ExoPlayerConfig.loadControl(this))
            .setTrackSelectorFactory { context -> ExoPlayerConfig.trackSelector(context) }
        player = preloadBuilder.buildExoPlayer(ExoPlayerConfig.playerBuilder(this)).also {
            it.addListener(listener)
            it.addAnalyticsListener(loadErrors)
            // Local News streams list their captions as on by default (Amagi, Wurl;
            // 2026-11-01). Channels play without them, as they always have here.
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        preloads = preloadBuilder.build()
        ExoPlayerConfig.keepScreenOnWhilePlaying(this, player)
        setContent { PlayerScreen() }
        tune(shown)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1_000)
                    stillWatchingTick()
                    stallTick()
                    preloadTick()
                }
            }
        }
    }

    @Composable
    private fun PlayerScreen() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        isFocusable = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = this@LivePlayerActivity.player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val channel = channels[shown]
            when (phase) {
                Phase.TUNING -> TuningOverlay(channel, tuneStage, notice)
                Phase.OFF_AIR -> OffAirCard(channel.name, offAirButton)
                Phase.PLAYING -> if (bannerVisible) {
                    ChannelBanner(channel.name, channel.quality, channel.row)
                    PlayerHintBar()
                }
            }
            // While tuning, the card's amber line shows the notice instead.
            if (phase != Phase.TUNING) notice?.let { NoticeBar(it) }
            if (listOpen) ChannelListOverlay(listTitle, channels.map { it.name }, listFocus)
            stillWatchingLeft?.let { StillWatchingCard(channel.name, it) }
        }
    }

    // ── Tuning ────────────────────────────────────────────────────────────────

    private fun tune(index: Int, afterDrop: Boolean = false) {
        zapJob?.cancel()
        tuneJob?.cancel()
        shown = index
        // A tune not started by Up/Down (a list pick, a retry) counts from here.
        if (SystemClock.elapsedRealtime() - markT0 > ZAP_SETTLE_MS + 200) markT0 = SystemClock.elapsedRealtime()
        mark("tune")
        phase = Phase.TUNING
        tuneStage = 1
        preparedAt = 0L
        stall.reset()
        bannerVisible = false
        player.stop()
        val channel = channels[index]
        // A retry (afterDrop) asks the server afresh: the stored session or the
        // preloaded start may be what just failed.
        val spare = if (afterDrop) null else freshSpare(index)
        val spareSource = spare?.let { preloads.getMediaSource(it.item) }
        spares.remove(index)
        if (spareSource == null) spare?.let { preloads.remove(it.item) }
        if (afterDrop) sessionCache.remove(channel.id)
        // Any tune the viewer made (a zap, a list pick, the first channel) starts the
        // surf window: without it the first channel's spares went stale unused.
        if (!afterDrop) lastZapAt = SystemClock.elapsedRealtime()
        tuneJob = lifecycleScope.launch {
            val session = spare?.takeIf { spareSource != null }?.session
                ?: (if (afterDrop) null else cachedSession(channel.id))
                ?: run {
                    // The neighbours Up/Down would tune next, warmed by the server meanwhile.
                    val warm = neighbours(index).joinToString(",") { channels[it].id }.ifEmpty { null }
                    mark("session sent")
                    val response = runCatchingCancellable {
                        ApiClient.bluehiveApi.startLiveSession(channel.id, warm)
                    }.getOrNull()
                    mark("session back HTTP ${response?.code()}")
                    val answer = response?.body()?.takeIf { response.isSuccessful }
                    if (answer == null) {
                        Log.e(TAG, "live ${channel.id}: no session (HTTP ${response?.code() ?: "unreachable"})")
                        offAir()
                        return@launch
                    }
                    sessionCache[channel.id] = answer to SystemClock.elapsedRealtime()
                    answer
                }
            tuned = index
            tuneStage = 2
            when {
                session.backup && afterDrop -> showNotice("${channel.name} dropped, switched to the backup feed")
                session.backup -> showNotice("${channel.name}'s main feed is down, playing the backup feed")
            }
            val playlist = BuildConfig.API_BASE_URL.trimEnd('/') + session.playlist
            player.setMediaSource(
                spareSource ?: ExoPlayerConfig.hlsMediaSource(playlist, dataSourcesFor(session.headers, playlist))
            )
            // The preloaded source the player just let go of leaves the manager too.
            inPlayer?.let { preloads.remove(it) }
            inPlayer = spare?.item?.takeIf { spareSource != null }
            mark(
                if (spareSource != null) "source set: PRELOADED (${SystemClock.elapsedRealtime() - (spare?.at ?: 0L)} ms old)"
                else "source set"
            )
            player.prepare()
            player.playWhenReady = true
            preparedAt = SystemClock.elapsedRealtime()
            mark("prepared")
            LiveTvData.noteWatched(this@LivePlayerActivity, profileId, channel.id)
        }
    }

    /** Up/Down: the next channel's name at once, the tune once the presses stop. */
    private fun zap(delta: Int) {
        if (channels.size < 2) return
        BlueHiveApplication.playHoverSound()
        tuneJob?.cancel()
        player.stop()
        shown = (shown + delta).mod(channels.size)
        markT0 = SystemClock.elapsedRealtime()
        mark("press")
        phase = Phase.TUNING
        tuneStage = 1
        preparedAt = 0L
        zapJob?.cancel()
        lastZapAt = SystemClock.elapsedRealtime()
        lastZapDirection = if (delta < 0) -1 else 1
        zapJob = lifecycleScope.launch {
            delay(if (freshSpare(shown) != null) PRELOADED_SETTLE_MS else ZAP_SETTLE_MS)
            tune(shown)
        }
    }

    /**
     * A playing channel failed. Falling behind the live window just rejoins the
     * edge; anything else gets a fresh session ([retune]).
     */
    private fun recover(error: PlaybackException) {
        Log.e(TAG, "live ${channels[shown].id}: ${error.errorCodeName}")
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        retune()
    }

    /**
     * Asks for a fresh session for the channel on screen (the server re-resolves,
     * or moves to a backup feed), at most [MAX_RECOVERIES] times a minute; past
     * that the channel is off air. [notice] says why, when the viewer saw nothing.
     */
    private fun retune(notice: String? = null) {
        val now = SystemClock.elapsedRealtime()
        while (recoveries.isNotEmpty() && now - recoveries.first() > RECOVERY_WINDOW_MS) recoveries.removeFirst()
        if (recoveries.size >= MAX_RECOVERIES) {
            offAir()
            return
        }
        recoveries.addLast(now)
        notice?.let(::showNotice)
        tune(shown, afterDrop = true)
    }

    /**
     * Once a second: a channel stuck while "playing" ([StallWatch]; ExoPlayer
     * raised no error, so [recover] never ran), or a tune with no picture
     * [TUNE_TIMEOUT_MS] after its source was prepared, is re-tuned -- what Up/Down
     * and back did by hand for Comedy Central.
     */
    private fun stallTick() {
        val now = SystemClock.elapsedRealtime()
        val channel = channels[shown]
        when {
            phase == Phase.TUNING && preparedAt > 0 && now - preparedAt > TUNE_TIMEOUT_MS -> {
                Log.w(TAG, "live ${channel.id}: no picture ${TUNE_TIMEOUT_MS / 1000} s after tuning")
                retune("${channel.name} isn't starting, trying again")
            }
            phase == Phase.PLAYING && player.playWhenReady ->
                if (stall.stuck(now, periodPosition(), renderedFrames())) {
                    Log.w(TAG, "live ${channel.id}: stuck for ${StallWatch.WINDOW_MS / 1000} s")
                    retune("${channel.name} froze, reconnecting")
                }
            else -> stall.reset()
        }
    }

    /** Where playback is in the stream's period; does not jump back as a live window slides. */
    private fun periodPosition(): Long {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return player.currentPosition
        return player.currentPosition +
            timeline.getWindow(player.currentMediaItemIndex, timelineWindow).positionInFirstPeriodMs
    }

    private fun renderedFrames(): Int? = player.videoDecoderCounters?.let {
        it.ensureUpdated()
        it.renderedOutputBufferCount
    }

    private fun offAir() {
        player.stop()
        tuned = -1
        preparedAt = 0L
        phase = Phase.OFF_AIR
        offAirButton = 0
        bannerVisible = false
        BlueHiveApplication.playErrorSound()
    }

    // ── Remote ────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!isRemoteKey(code)) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        lastKeyAt = SystemClock.elapsedRealtime()
        if (stillWatchingLeft != null) {
            stillWatchingLeft = null
            BlueHiveApplication.playClickSound()
            return true
        }
        val first = event.repeatCount == 0
        when {
            listOpen -> onListKey(code, first)
            phase == Phase.OFF_AIR -> onOffAirKey(code, first)
            else -> onWatchKey(code, first)
        }
        return true
    }

    private fun isRemoteKey(code: Int): Boolean = code == KeyEvent.KEYCODE_BACK ||
        code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
        code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
        code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN ||
        ExoPlayerConfig.isCenterKey(code)

    private fun onWatchKey(code: Int, first: Boolean) {
        when {
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
            code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_UP -> zap(+1)
            code == KeyEvent.KEYCODE_BACK -> if (first) leave()
            ExoPlayerConfig.isCenterKey(code) -> if (first) {
                BlueHiveApplication.playClickSound()
                listFocus = shown
                listOpen = true
            }
            else -> showBanner()
        }
    }

    private fun onListKey(code: Int, first: Boolean) {
        when {
            code == KeyEvent.KEYCODE_DPAD_UP -> if (listFocus > 0) {
                listFocus--
                BlueHiveApplication.playHoverSound()
            }
            code == KeyEvent.KEYCODE_DPAD_DOWN -> if (listFocus < channels.lastIndex) {
                listFocus++
                BlueHiveApplication.playHoverSound()
            }
            code == KeyEvent.KEYCODE_BACK -> if (first) {
                listOpen = false
                BlueHiveApplication.playBackOutSound()
            }
            ExoPlayerConfig.isCenterKey(code) -> if (first) {
                BlueHiveApplication.playClickSound()
                listOpen = false
                if (listFocus != tuned || phase != Phase.PLAYING) tune(listFocus) else showBanner()
            }
        }
    }

    private fun onOffAirKey(code: Int, first: Boolean) {
        when {
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                offAirButton = if (code == KeyEvent.KEYCODE_DPAD_LEFT) 0 else 1
                BlueHiveApplication.playHoverSound()
            }
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
            code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_UP -> zap(+1)
            code == KeyEvent.KEYCODE_BACK -> if (first) leave()
            ExoPlayerConfig.isCenterKey(code) -> if (first) {
                BlueHiveApplication.playClickSound()
                if (offAirButton == 0) tune(shown) else leave()
            }
        }
    }

    private fun leave() {
        BlueHiveApplication.playBackOutSound()
        finish()
    }

    private fun showBanner() {
        bannerVisible = true
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch {
            delay(BANNER_MS)
            bannerVisible = false
        }
    }

    private fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            delay(NOTICE_MS)
            notice = null
        }
    }

    private fun stillWatchingTick() {
        if (phase != Phase.PLAYING) {
            stillWatchingLeft = null
            return
        }
        when (val state = StillWatching.state(SystemClock.elapsedRealtime(), lastKeyAt)) {
            StillWatching.State.Watching -> stillWatchingLeft = null
            is StillWatching.State.Asking -> stillWatchingLeft = state.secondsLeft
            StillWatching.State.Stop -> {
                Log.e(TAG, "live ${channels[shown].id}: no answer to Still watching? -- stopping")
                player.stop()
                finish()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        // Back from Home or the screensaver: the channel picks up at the live
        // edge rather than from wherever it was left.
        if (stoppedInBackground) {
            stoppedInBackground = false
            lastKeyAt = SystemClock.elapsedRealtime()
            tune(shown)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && ::player.isInitialized) {
            stoppedInBackground = true
            zapJob?.cancel()
            tuneJob?.cancel()
            player.stop()
            dropAllSpares()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IdleWatcher.setPlaying(IdleWatcher.SOURCE_LIVE, false)
        if (::player.isInitialized) player.release()
        if (::preloads.isInitialized) preloads.release()
    }

    companion object {
        private const val TAG = "LiveTV"
        private const val EXTRA_PROFILE = "PROFILE_ID"
        private const val EXTRA_TITLE = "LIVE_LIST_TITLE"
        private const val EXTRA_INDEX = "LIVE_INDEX"
        private const val EXTRA_IDS = "LIVE_IDS"
        private const val EXTRA_NAMES = "LIVE_NAMES"
        private const val EXTRA_ROWS = "LIVE_ROWS"
        private const val EXTRA_QUALITIES = "LIVE_QUALITIES"
        private const val EXTRA_NUMBERS = "LIVE_NUMBERS"
        private const val EXTRA_COLORS = "LIVE_COLORS"
        private const val EXTRA_STARTS = "LIVE_STARTS"
        private const val EXTRA_LEAGUES = "LIVE_LEAGUES"
        private const val EXTRA_SPORTS = "LIVE_SPORTS"

        private const val BANNER_MS = 4_000L
        private const val NOTICE_MS = 5_000L
        /** Up/Down presses closer than this are one zap (was 600 ms until 2026-11-18). */
        private const val ZAP_SETTLE_MS = 300L
        /** Onto a preloaded channel the tune costs little, so a run of presses settles sooner. */
        private const val PRELOADED_SETTLE_MS = 150L

        /** Neighbour preloading on/off (2026-11-18). */
        private const val PRELOAD_ON = true
        /**
         * How much of a neighbour is loaded. HLS loads whole chunks, so 1 s is the
         * chunk the start falls in (zlive: ~3.8 s, ~2 MB). 4 s often spanned two:
         * 59 MB in 47 s of surfing on .241 (2026-11-18).
         */
        private const val PRELOAD_MS = 1_000L
        /** After a channel's picture, before its neighbours start loading. */
        private const val PRELOAD_AFTER_PICTURE_MS = 1_500L
        /**
         * A preloaded start older than this is not used. MEASURED on .241: 3-8 s
         * old started in 0.6-1.0 s; 11-13 s old in 1.0-2.4 s (its chunk had left
         * the ~15 s live window, so it was fetched again).
         */
        private const val PRELOAD_FRESH_MS = 8_000L
        /**
         * Stale preloads are redone only this long after the last tune; then dropped.
         * 30 s cost 13 MB of refreshes per settled channel on .241 (2026-11-18); 16 s
         * allows one refresh, covering a 16 s look at a channel before moving on.
         */
        private const val SURF_WINDOW_MS = 16_000L
        /** A session answer reused by a tune (its grant lasts 6 h; the link is the server's to keep). */
        private const val SESSION_CACHE_MS = 5 * 60_000L
        private const val RECOVERY_WINDOW_MS = 60_000L
        private const val MAX_RECOVERIES = 2
        /** A tune with no picture this long after its source was prepared is retried. */
        private const val TUNE_TIMEOUT_MS = 15_000L

        /** For a session answer that names no headers (a server before the "b" image). */
        private val SITE_HEADERS = mapOf("Origin" to "https://zlive.st", "Referer" to "https://zlive.st/")

        /** Play [row]'s channel [index]; Up/Down move through that row. */
        internal fun start(context: Context, profileId: Int, row: LiveTvData.Row, index: Int) {
            val c = row.channels
            context.startActivity(
                Intent(context, LivePlayerActivity::class.java)
                    .putExtra(EXTRA_PROFILE, profileId)
                    .putExtra(EXTRA_TITLE, row.name)
                    .putExtra(EXTRA_INDEX, index)
                    .putStringArrayListExtra(EXTRA_IDS, ArrayList(c.map { it.id }))
                    .putStringArrayListExtra(EXTRA_NAMES, ArrayList(c.map { it.name }))
                    .putStringArrayListExtra(EXTRA_ROWS, ArrayList(c.map { it.row }))
                    .putStringArrayListExtra(EXTRA_QUALITIES, ArrayList(c.map { it.quality }))
                    .putExtra(EXTRA_NUMBERS, c.map { it.number }.toIntArray())
                    .putExtra(EXTRA_COLORS, c.map { it.rowColor }.toLongArray())
                    .putExtra(EXTRA_STARTS, c.map { it.start }.toLongArray())
                    .putStringArrayListExtra(EXTRA_LEAGUES, ArrayList(c.map { it.league }))
                    .putStringArrayListExtra(EXTRA_SPORTS, ArrayList(c.map { it.sport }))
            )
        }

        private fun channelsOf(intent: Intent): List<Channel> {
            val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
            val names = intent.getStringArrayListExtra(EXTRA_NAMES).orEmpty()
            val rows = intent.getStringArrayListExtra(EXTRA_ROWS).orEmpty()
            val qualities = intent.getStringArrayListExtra(EXTRA_QUALITIES).orEmpty()
            val numbers = intent.getIntArrayExtra(EXTRA_NUMBERS) ?: IntArray(0)
            val colors = intent.getLongArrayExtra(EXTRA_COLORS) ?: LongArray(0)
            val starts = intent.getLongArrayExtra(EXTRA_STARTS) ?: LongArray(0)
            val leagues = intent.getStringArrayListExtra(EXTRA_LEAGUES).orEmpty()
            val sports = intent.getStringArrayListExtra(EXTRA_SPORTS).orEmpty()
            return ids.indices.map { i ->
                Channel(
                    ids[i], names.getOrElse(i) { ids[i] }, rows.getOrElse(i) { "" }, qualities.getOrElse(i) { "" },
                    number = numbers.getOrElse(i) { 0 },
                    rowColor = colors.getOrElse(i) { 0xFF2644A6 },
                    start = starts.getOrElse(i) { 0L },
                    league = leagues.getOrElse(i) { "" },
                    sport = sports.getOrElse(i) { "" },
                )
            }
        }
    }
}
