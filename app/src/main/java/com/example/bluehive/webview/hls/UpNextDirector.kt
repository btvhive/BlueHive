package com.example.bluehive.webview.hls

import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.bluehive.webview.exoplayer_resources.ExoPlayerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Up Next for a player activity (v581): the clock, the remote and the overlay,
 * shared by the HLS player (TV shows) and the Miruro player (anime) so the two
 * cannot drift apart. The activity keeps only its hooks: [onKeyDown] for every
 * press, [handleKey] before its own key handling, [autoplayAtEnd] at
 * STATE_ENDED, [Overlay] drawn last, and [stop] on the way out.
 */
internal class UpNextDirector(
    private val tag: String,
    /** The episode after this one; null at a season's end or for a film -- then nothing shows. */
    val next: NextEpisode?,
    /** Episodes in a row before this one that played with no remote press. */
    private val unattendedBefore: Int,
    private val showTitle: String?,
    /** The routing name this episode played from, handed back to be tried first. */
    private val sourceName: String,
) {
    var phase by mutableStateOf(UpNextPhase.Hidden)
        private set
    private var remainingMs by mutableLongStateOf(0L)
    /** 0 = Watch next episode / Keep watching, 1 = Cancel / I'm done. */
    private var focus by mutableIntStateOf(0)
    /** False after an unanswered prompt: the card counts nothing and nothing plays by itself. */
    private var autoplay by mutableStateOf(true)

    /** Any remote press during this episode. */
    private var touched = false
    /** Cancel on the card: this episode ends the old way. */
    private var cancelled = false
    /** Whether this episode asks "Still watching?" -- decided once, as the prompt's window opens. */
    private var askThisEpisode = false
    private var askDecided = false
    /** Keep watching (or any button) answered the prompt. */
    private var promptDismissed = false
    private var job: Job? = null

    /** Reads the player every [UP_NEXT_TICK_MS] while there is a next episode. */
    fun start(scope: CoroutineScope, player: () -> Player?) {
        if (next == null) return
        Log.d(tag, "⏭ up next armed: s${next.season}e${next.episode} (unattended before: $unattendedBefore)")
        job = scope.launch {
            while (isActive) {
                tick(player())
                delay(UP_NEXT_TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        phase = UpNextPhase.Hidden
    }

    /** Somebody is there: every press resets the unattended count. */
    fun onKeyDown() { touched = true }

    /**
     * The stream's position against its duration. Paused or stalled, it
     * holds; seeked back before the windows, everything re-arms.
     */
    private fun tick(p: Player?) {
        p ?: return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur <= 0 || p.isCurrentMediaItemLive) {
            phase = UpNextPhase.Hidden
            return
        }
        val remaining = (dur - p.currentPosition).coerceAtLeast(0L)
        if (remaining > STILL_WATCHING_LEAD_MS) {
            cancelled = false
            askDecided = false
            promptDismissed = false
        } else if (!askDecided) {
            askThisEpisode = shouldAskStillWatching(unattendedBefore, touched)
            askDecided = true
            if (askThisEpisode) Log.i(tag, "⏭ ${unattendedBefore + 1} episodes without a press — asking Still watching")
        }
        autoplay = !askThisEpisode || touched
        val now = upNextPhaseFor(remaining, dur, next != null, cancelled, askThisEpisode, promptDismissed)
        if (now != phase) {
            Log.d(tag, "⏭ up next: $now at ${remaining}ms left (autoplay=$autoplay)")
            phase = now
            focus = 0
        }
        if (now != UpNextPhase.Hidden) remainingMs = remaining
    }

    /** At the end of the stream: does the next episode start by itself? */
    fun autoplayAtEnd(p: Player?): Boolean {
        if (next == null || cancelled) return false
        val dur = p?.duration ?: return false
        if (dur == C.TIME_UNSET || dur < UP_NEXT_MIN_DURATION_MS) return false
        val ask = if (askDecided) askThisEpisode else shouldAskStillWatching(unattendedBefore, touched)
        return !ask || touched
    }

    /** The data sent back with RESULT_PLAY_NEXT: what to open, from where, and the count. */
    fun playNextResult(): Intent? = next?.let {
        Intent()
            .putExtra(HlsPlayerActivity.EXTRA_PLAY_SEASON, it.season)
            .putExtra(HlsPlayerActivity.EXTRA_PLAY_EPISODE, it.episode)
            .putExtra(HlsPlayerActivity.EXTRA_PLAY_TITLE, it.title)
            .putExtra(HlsPlayerActivity.EXTRA_PLAY_SOURCE, sourceName)
            .putExtra(HlsPlayerActivity.EXTRA_UNATTENDED, unattendedAfter(unattendedBefore, touched))
    }

    /**
     * The remote while the card or the prompt is up: left and right pick a
     * button, OK presses it, BACK closes the card (or answers the prompt).
     * Anything else on the prompt also answers it -- "press any button".
     * Play/pause and seeks stay the player's while the card is up.
     */
    fun handleKey(event: KeyEvent, onWatchNext: () -> Unit, onDone: () -> Unit): Boolean {
        val shown = phase
        if (shown == UpNextPhase.Hidden) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) focus = 0; return true }
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) focus = 1; return true }
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> return true
            ExoPlayerConfig.isCenterKey(event.keyCode) -> {
                if (down && event.repeatCount == 0) {
                    when {
                        shown == UpNextPhase.Card && focus == 0 -> onWatchNext()
                        shown == UpNextPhase.Card -> cancel()
                        focus == 0 -> dismissPrompt()
                        else -> {
                            Log.i(tag, "⏭ still watching: I'm done — closing")
                            stop()
                            onDone()
                        }
                    }
                }
                return true
            }
            event.keyCode == KeyEvent.KEYCODE_BACK -> {
                if (down) { if (shown == UpNextPhase.Card) cancel() else dismissPrompt() }
                return true
            }
            shown == UpNextPhase.StillWatching -> {
                if (down) dismissPrompt()
                return true
            }
            else -> return false
        }
    }

    private fun cancel() {
        Log.d(tag, "⏭ up next cancelled")
        cancelled = true
        phase = UpNextPhase.Hidden
    }

    private fun dismissPrompt() {
        Log.d(tag, "⏭ still watching: keep watching")
        promptDismissed = true
        autoplay = true
        phase = UpNextPhase.Hidden
    }

    /** Drawn LAST in the player's Box: over the video, its control bar and the captions alike. */
    @Composable
    fun Overlay(box: BoxScope) {
        val n = next ?: return
        when (phase) {
            UpNextPhase.Card -> box.UpNextCard(n, remainingMs, focus, counting = autoplay)
            UpNextPhase.StillWatching -> box.StillWatchingPrompt(
                showTitle = showTitle,
                focus     = focus,
                progress  = (STILL_WATCHING_LEAD_MS - remainingMs).toFloat() /
                            (STILL_WATCHING_LEAD_MS - UP_NEXT_WINDOW_MS),
            )
            UpNextPhase.Hidden -> Unit
        }
    }

    companion object {
        /** The player's Up Next, from the extras the details screen put on its intent. */
        fun fromIntent(intent: Intent, tag: String, defaultSource: String): UpNextDirector = UpNextDirector(
            tag = tag,
            next = intent.getIntExtra(HlsPlayerActivity.EXTRA_NEXT_EPISODE, 0).takeIf { it > 0 }?.let {
                NextEpisode(
                    season   = intent.getIntExtra("SEASON_NUMBER", 0),
                    episode  = it,
                    title    = intent.getStringExtra(HlsPlayerActivity.EXTRA_NEXT_TITLE),
                    stillUrl = intent.getStringExtra(HlsPlayerActivity.EXTRA_NEXT_STILL),
                )
            },
            unattendedBefore = intent.getIntExtra(HlsPlayerActivity.EXTRA_UNATTENDED, 0).coerceAtLeast(0),
            showTitle = intent.getStringExtra("MEDIA_TITLE"),
            sourceName = intent.getStringExtra("SOURCE_NAME")?.takeIf { it.isNotBlank() } ?: defaultSource,
        )
    }
}
