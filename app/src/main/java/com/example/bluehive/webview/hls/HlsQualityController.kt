package com.example.bluehive.webview.hls

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.example.bluehive.webview.exoplayer_resources.ByteMeter
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The website player's quality: Auto (QualityRules, with a 4K ceiling where
 * the TV shows 4K) or a height the viewer picked with the Quality button.
 *
 * Owns its state ACROSS PLAYER REBUILDS: HlsPlayerActivity builds a new player
 * for a relay switch, a re-extraction or a frozen decoder, and calls [attach]
 * on each; the viewer's choice and Auto's cap carry over.
 *
 * Only [enabled] for the sources it was built and tested on (VidNest). For any
 * other source it does nothing and the player keeps the old 1080p policy.
 */
@UnstableApi
class HlsQualityController(
    private val activity: ComponentActivity,
    private val tag: String,
    val enabled: Boolean,
    /** Shows the viewer a short notice ("Switched to 1080p…"). */
    private val notify: (String) -> Unit,
) {
    /** The picker is open. */
    val overlayOpen = mutableStateOf(false)

    /** Bumped when the labels or the selection change, for the overlay to recompose. */
    val version = mutableIntStateOf(0)

    val ceiling: Int = QualityRules.ceiling(displayModeHeight(activity))

    private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    private var sampler: Job? = null
    private var button: View? = null

    private var rungs: List<Rung> = emptyList()
    private var playingHeight = 0
    private var manualHeight: Int? = null          // null = Auto
    private val auto: AutoQuality = AutoQuality(
        rungs = QualityRules.TYPICAL,
        ceiling = ceiling,
        startCap = QualityRules.startHeight(QualityRules.TYPICAL, ceiling, lastBps(activity)),
        now = SystemClock.elapsedRealtime(),
    )

    // Stall tracking for the player now attached.
    private var wasReady = false
    private var stallStartedAt = -1L
    private var attachedAt = 0L

    /** A newly built player. Applies the current cap and starts watching it. */
    fun attach(p: ExoPlayer) {
        detach()
        player = p
        if (!enabled) return
        wasReady = false
        stallStartedAt = -1L
        attachedAt = SystemClock.elapsedRealtime()
        // Adaptive, not one forced rung (see trackSelector).
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(false)
            .build()
        applyCap("attach")
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val now = SystemClock.elapsedRealtime()
                when (state) {
                    Player.STATE_READY -> {
                        if (stallStartedAt >= 0) Log.e(tag, "🎚 stall ended after ${now - stallStartedAt} ms (${playingHeight}p)")
                        if (!wasReady) {
                            Log.e(tag, "🎚 first picture ${now - attachedAt} ms after the player opened (cap ${manualHeight ?: auto.cap}p)")
                            auto.onFirstFrame()
                        }
                        wasReady = true; stallStartedAt = -1L
                    }
                    Player.STATE_BUFFERING -> if (wasReady && p.playWhenReady && stallStartedAt < 0) {
                        stallStartedAt = now
                        Log.e(tag, "🎚 stall started (${playingHeight}p, buffer ${p.totalBufferedDuration / 1000}s)")
                        if (manualHeight == null) auto.onStallStart(now, playingHeight)?.let { changed(it, down = true) }
                    }
                    else -> stallStartedAt = -1L
                }
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    auto.onSeek(SystemClock.elapsedRealtime())
                }
            }

            override fun onTracksChanged(tracks: Tracks) = readTracks(tracks)
        }
        listener = l
        p.addListener(l)
        readTracks(p.currentTracks)
        sampler = activity.lifecycleScope.launch {
            var tick = 0L
            // (time, bytes read) for the last 3 s: the live download speed.
            val reads = ArrayDeque<Pair<Long, Long>>()
            while (true) {
                delay(1_000)
                val pl = player ?: break
                val now = SystemClock.elapsedRealtime()
                reads.addLast(now to ByteMeter.bytes())
                while (reads.size > 4) reads.removeFirst()
                // WHAT IS ON SCREEN, from the renderer. Not the track groups'
                // "selected" flags: under an adaptive selection every allowed
                // rung reads as selected, which pinned this at 2160p on the
                // Fire Stick long after 1080p was playing.
                val vf = pl.videoFormat
                val onScreen = if (vf != null && vf.height > 0) QualityRules.rungFor(rungs, vf.height, vf.bitrate) else 0
                if (onScreen > 0 && onScreen != playingHeight) {
                    playingHeight = onScreen
                    Log.e(tag, "🎚 now playing ${onScreen}p [frame ${vf?.width}x${vf?.height}] " +
                            "(cap ${manualHeight ?: auto.cap}p, ceiling ${ceiling}p, " +
                            "ladder ${QualityRules.ladder(rungs).joinToString("/") { "${it.height}" }}, " +
                            "buffer ${pl.totalBufferedDuration / 1000}s)")
                    version.intValue++
                    updateButton()          // the pill says what is on screen
                }
                // A SLOW START: no picture yet, well after the player opened.
                if (manualHeight == null && !wasReady && pl.playWhenReady) {
                    // Measured live, not media3's estimate: that one only
                    // updates when a chunk finishes, and the 4K chunk has not.
                    val startBps = ByteMeter.bps(reads.first().second, reads.first().first,
                        reads.last().second, reads.last().first)
                    auto.onStartupWait(now, now - attachedAt, startBps)?.let {
                        changed(it, down = true)
                        // RELOAD AT HD FROM THE SAME SPOT. MEASURED on the Fire
                        // Stick: without this the first picture came from the
                        // half-loaded 4K and then stalled 9.8 s while HD arrived.
                        // A seek is NOT enough (measured: media3 seeks inside the
                        // half-loaded 4K and keeps it); stop() drops it all.
                        // Nothing is on screen yet, so nothing is lost.
                        val pos = pl.currentPosition
                        pl.stop()
                        pl.prepare()
                        pl.seekTo(pos)
                        Log.e(tag, "🎚 slow start: reloading at ${it}p from ${pl.currentPosition / 1000}s")
                    }
                }
                if (manualHeight == null && stallStartedAt >= 0 && pl.playbackState == Player.STATE_BUFFERING) {
                    auto.onStallTick(now, now - stallStartedAt, playingHeight)?.let { changed(it, down = true) }
                }
                if (++tick % (QualityRules.SAMPLE_MS / 1_000) == 0L && pl.isPlaying) {
                    val bps = DefaultBandwidthMeter.getSingletonInstance(activity).bitrateEstimate
                    saveBps(activity, bps)
                    // Log.e: R8 strips the quieter levels from release builds.
                    Log.e(tag, "🎚 sample ${bps / 1000} kbps, playing ${playingHeight}p, " +
                            "buffer ${pl.totalBufferedDuration / 1000}s, cap ${manualHeight ?: auto.cap}p" +
                            if (manualHeight != null) " (picked)" else " (auto)")
                    if (manualHeight == null) {
                        val before = auto.cap
                        auto.onSample(now, bps, playingHeight, pl.totalBufferedDuration)
                            ?.let { changed(it, down = it < before) }
                    }
                }
            }
        }
    }

    fun detach() {
        sampler?.cancel(); sampler = null
        listener?.let { l -> player?.removeListener(l) }
        listener = null
        player = null
    }

    /** The control-bar button; shown once the stream offers two or more qualities. */
    fun bindButton(v: View?, hideBar: () -> Unit) {
        button = v
        v?.setOnClickListener {
            // THE BAR GOES FIRST. MEASURED on the Fire Stick: with the bar left
            // up, its 3 s auto-hide hid the still-focused Quality button, Android
            // cleared that focus, and the clear reset Compose's focus too -- the
            // picker stayed on screen with no row focused and the remote dead.
            hideBar()
            highlight.intValue = selectedIndex()
            overlayOpen.value = true
        }
        updateButton()
    }

    /** The picker row drawn as focused; moved by [handleKey], not by focus. */
    val highlight = mutableIntStateOf(0)

    /**
     * The remote while the picker is open. Every key is consumed: Up/Down move
     * the highlight, OK picks it (on release, so the press cannot also reach
     * the player), Back closes.
     */
    fun handleKey(event: android.view.KeyEvent): Boolean {
        val rows = options().size
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> if (event.action == android.view.KeyEvent.ACTION_DOWN)
                highlight.intValue = (highlight.intValue - 1).coerceAtLeast(0)
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (event.action == android.view.KeyEvent.ACTION_DOWN)
                highlight.intValue = (highlight.intValue + 1).coerceAtMost(rows - 1)
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.action == android.view.KeyEvent.ACTION_UP)
                select(highlight.intValue)
            android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_ESCAPE ->
                if (event.action == android.view.KeyEvent.ACTION_UP) overlayOpen.value = false
        }
        return true
    }

    /** Picker rows: "Auto · now 2160p (4K)", then each height within the ceiling. */
    fun options(): List<String> {
        val now = playingHeight.takeIf { it > 0 }?.let { " · now ${QualityRules.label(it)}" } ?: ""
        return listOf("Auto$now") + choices().map { QualityRules.label(it) }
    }

    /** The row that is selected: 0 for Auto, else the picked height's row. */
    fun selectedIndex(): Int = manualHeight?.let { h -> choices().indexOf(h) + 1 } ?: 0

    /** A row was picked. */
    fun select(index: Int) {
        overlayOpen.value = false
        manualHeight = if (index <= 0) null else choices().getOrNull(index - 1)
        Log.e(tag, "🎚 quality picked: ${manualHeight?.let { "${it}p" } ?: "Auto (cap ${auto.cap}p)"}")
        applyCap("picked")
        version.intValue++
    }

    private fun choices(): List<Int> = QualityRules.ladder(rungs).map { it.height }.filter { it <= ceiling }

    private fun readTracks(tracks: Tracks) {
        val found = mutableListOf<Rung>()
        tracks.groups.forEach { g ->
            if (g.type != C.TRACK_TYPE_VIDEO) return@forEach
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                if (f.height > 0) found += Rung(f.height, maxOf(f.bitrate, f.peakBitrate, 0))
            }
        }
        if (found.isEmpty()) return
        val firstLadder = rungs.isEmpty()
        rungs = found
        auto.updateRungs(found)
        // The real ladder: re-pick the start rung from real bitrates once.
        if (firstLadder && manualHeight == null) {
            val start = QualityRules.startHeight(found, ceiling, lastBps(activity))
            // LOWER ONLY. MEASURED on the Fire Stick: the ladder arrived 9.6 s
            // after the player opened (a slow master playlist), AFTER the
            // slow-start rule had already dropped to 1080p -- and this put the
            // cap straight back to 4K on an 8 Mbps line.
            if (start < auto.cap) {
                Log.e(tag, "🎚 start rung ${start}p (last measured speed ${lastBps(activity) ?: 0} bps)")
                auto.startAt(start, SystemClock.elapsedRealtime())
                applyCap("start")
            }
        }
        updateButton()
        version.intValue++
    }

    private fun changed(newCap: Int, down: Boolean) {
        Log.e(tag, "🎚 Auto ${if (down) "down" else "up"} to ${newCap}p — ${auto.lastReason}")
        applyCap(if (down) "auto down" else "auto up")
        notify(when {
            !wasReady -> "Starting in ${if (newCap >= QualityRules.HD) "HD" else "${newCap}p"}. 4K will follow when it's ready"
            down -> "Switched to ${QualityRules.label(newCap)} for smoother playback"
            else -> "Back up to ${QualityRules.label(newCap)}"
        })
        version.intValue++
    }

    /**
     * Auto moves [liveCap] only: the one CappedAdaptiveSelection reads it on
     * every chunk, so a step lands at the next chunk with the buffer kept.
     * A PICK changes the selection parameters instead -- media3 then reloads
     * from the playhead, so the picked quality shows at once.
     */
    private fun applyCap(why: String) {
        val p = player
        val picked = manualHeight
        liveCap = picked ?: auto.cap
        if (p != null) {
            val want = picked ?: Int.MAX_VALUE
            val now = p.trackSelectionParameters
            if (now.maxVideoHeight != want || now.maxVideoWidth != Int.MAX_VALUE) {
                p.trackSelectionParameters = now.buildUpon()
                    .setMaxVideoSize(Int.MAX_VALUE, want)
                    .build()
            }
        }
        Log.e(tag, "🎚 cap ${liveCap}p ($why)")
    }

    /** What CappedAdaptiveSelection reads, from the player's loading thread. */
    @Volatile private var liveCap: Int = auto.cap

    /**
     * The selector for a player this controller is [enabled] on: adaptive under
     * [liveCap], forced to no fixed highest rung (attach turns
     * FORCE_HIGHEST_BITRATE off for it -- that would pin ONE rung and make
     * every step a reload).
     */
    fun trackSelector(): androidx.media3.exoplayer.trackselection.DefaultTrackSelector =
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(
            activity, CappedAdaptiveSelection.Factory { liveCap })

    private fun updateButton() {
        val b = button ?: return
        b.visibility = if (enabled && choices().size >= 2) View.VISIBLE else View.GONE
        (b as? android.widget.TextView)?.text = QualityRules.pillText(playingHeight)
    }

    companion object {
        private const val PREFS = "hls_quality"
        private const val KEY_BPS = "last_bps"

        /** Sources this is on for. VidNest first; widen once others are tested. */
        val SOURCES = setOf("VidNest")

        /** The display's OUTPUT height (3840x2160 on a 4K TV even when menus draw at 1080p). */
        fun displayModeHeight(context: Context): Int = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 1080
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            @Suppress("DEPRECATION")
            val mode = wm.defaultDisplay.mode
            maxOf(minOf(mode.physicalWidth, mode.physicalHeight), 0)
        }.getOrDefault(1080)

        fun lastBps(context: Context): Long? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_BPS, 0L).takeIf { it > 0 }

        fun saveBps(context: Context, bps: Long) {
            if (bps > 0) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_BPS, bps).apply()
        }
    }
}
