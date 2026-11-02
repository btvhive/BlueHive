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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.BuildConfig
import com.example.bluehive.IdleWatcher
import com.example.bluehive.api.ApiClient
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
 * Remote: Up/Down change channel (a run of presses settles for 600 ms and tunes
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

    /**
     * Built per session: the headers come from the server's answer. zlive's own
     * player sends Origin/Referer, its transcode.cfd proxy refuses chunks without
     * them, and the other hosts (TikTok, our API) accept them.
     */
    private fun dataSourcesFor(headers: Map<String, String>?) = ChunkUnwrapDataSource.Factory(
        ExoPlayerConfig.dataSourceFactory(ExoPlayerConfig.CHROME_USER_AGENT, headers ?: SITE_HEADERS, context = this)
    )

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (phase != Phase.TUNING) return
            phase = Phase.PLAYING
            preparedAt = 0L
            showBanner()
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
        shown = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(channels.indices)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        // The tuning screen's globe carries the bee; decoded once, before the first tune.
        BeeLogo.prime(this)

        player = ExoPlayerConfig.buildPlayer(this).also {
            it.addListener(listener)
            it.addAnalyticsListener(loadErrors)
            // Local News streams list their captions as on by default (Amagi, Wurl;
            // 2026-11-01). Channels play without them, as they always have here.
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        ExoPlayerConfig.keepScreenOnWhilePlaying(this, player)
        setContent { PlayerScreen() }
        tune(shown)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1_000)
                    stillWatchingTick()
                    stallTick()
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
        phase = Phase.TUNING
        tuneStage = 1
        preparedAt = 0L
        stall.reset()
        bannerVisible = false
        player.stop()
        val channel = channels[index]
        tuneJob = lifecycleScope.launch {
            val response = runCatchingCancellable { ApiClient.bluehiveApi.startLiveSession(channel.id) }.getOrNull()
            val session = response?.body()?.takeIf { response.isSuccessful }
            if (session == null) {
                Log.e(TAG, "live ${channel.id}: no session (HTTP ${response?.code() ?: "unreachable"})")
                offAir()
                return@launch
            }
            tuned = index
            tuneStage = 2
            when {
                session.backup && afterDrop -> showNotice("${channel.name} dropped, switched to the backup feed")
                session.backup -> showNotice("${channel.name}'s main feed is down, playing the backup feed")
            }
            player.setMediaSource(
                ExoPlayerConfig.hlsMediaSource(
                    BuildConfig.API_BASE_URL.trimEnd('/') + session.playlist, dataSourcesFor(session.headers)
                )
            )
            player.prepare()
            player.playWhenReady = true
            preparedAt = SystemClock.elapsedRealtime()
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
        phase = Phase.TUNING
        tuneStage = 1
        preparedAt = 0L
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            delay(ZAP_SETTLE_MS)
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IdleWatcher.setPlaying(IdleWatcher.SOURCE_LIVE, false)
        if (::player.isInitialized) player.release()
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
        private const val ZAP_SETTLE_MS = 600L
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
