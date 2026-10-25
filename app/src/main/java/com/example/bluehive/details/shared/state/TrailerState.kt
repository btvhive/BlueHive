package com.example.bluehive.details.shared.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.bluehive.utilities.seekBackward
import com.example.bluehive.utilities.seekForward

/**
 * The backdrop trailer of a details screen: whether it plays, whether it has
 * been started (its player view then replaces the backdrop picture), whether
 * its stream is still being extracted, and its player for the seek buttons.
 *
 * Both screens held these as five variables with the same rules, written out
 * twice. Moved here in Stage 4 of the details redesign; each method is the code
 * it replaces, word for word. Plain snapshot state, so a press lands in the
 * same frame as before (docs/details-redesign-architecture.md §6).
 *
 * Two fixes since, both for "Loading trailer..." staying up for good: a press
 * after stop() no longer shows it when the player is already made, and a failed
 * extraction is reported ([onExtractionFailed]) instead of never clearing it --
 * with a short "Couldn't load this trailer" notice ([loadFailed]).
 */
@Stable
class TrailerState internal constructor(
    /** The trailer's page; null or blank when the title has none. */
    val url: String?,
) {
    /** The trailer should be playing. DetailsTrailerPlayer plays and pauses on this. */
    var isPlaying by mutableStateOf(false)
        private set

    /** It has been started: the player view replaces the backdrop picture. */
    var hasPlayed by mutableStateOf(false)
        private set

    /** Asks DetailsTrailerPlayer to extract the stream. Set by the first press. */
    var shouldExtract by mutableStateOf(false)
        private set

    /** The stream is being extracted: "Loading trailer..." shows. */
    var isExtracting by mutableStateOf(false)
        private set

    /**
     * The extraction failed while the trailer was still wanted: "Couldn't load
     * this trailer" shows (TrailerFailedNotice) until [dismissLoadFailed] or the
     * next press.
     */
    var loadFailed by mutableStateOf(false)
        private set

    /** The trailer's player, once DetailsTrailerPlayer has made one. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /**
     * DetailsTrailerPlayer has reported a player. It reports one only when it
     * builds one, so after this a press must not wait for another report. Read
     * only by a press, never while drawing, so a plain field.
     */
    private var playerMade = false

    /** The title has a trailer. The button is dimmed when it does not. */
    val isAvailable: Boolean get() = !url.isNullOrBlank()

    /**
     * Silence the trailer and bring the backdrop back. DetailsTrailerPlayer
     * pauses on isPlaying, and clearing hasPlayed hides its view, so the
     * backdrop picture returns. Idempotent.
     */
    fun stop() {
        isPlaying = false
        hasPlayed = false
    }

    /**
     * The Play/Pause Trailer button. The first press also asks for the
     * extraction, and "Loading trailer..." shows until the player is made.
     */
    fun onButtonPressed() {
        if (!url.isNullOrBlank()) {
            loadFailed = false  // a press after a failure tries again
            if (!hasPlayed) {
                shouldExtract = true
                // ✅ Show loading state -- only until the player is made. After
                // stop() the player is still there and ready, and reports nothing
                // more, so showing it then left "Loading trailer..." up for good
                // while the trailer played underneath.
                isExtracting = !playerMade
                hasPlayed = true
            }
            isPlaying = !isPlaying
        }
    }

    /** Rewind 10 seconds, only while it plays. */
    fun rewind() {
        if (isPlaying) {
            seekBackward(player)
        }
    }

    /** Forward 10 seconds, only while it plays. */
    fun fastForward() {
        if (isPlaying) {
            seekForward(player)
        }
    }

    /**
     * Play/pause reported by DetailsTrailerPlayer. It no longer calls this (see
     * its onIsPlayingChanged); kept, as the screens always passed it.
     */
    fun onPlaybackStateChanged(playing: Boolean) {
        isPlaying = playing
    }

    /** DetailsTrailerPlayer has made its player: the extraction is over. */
    fun onPlayerReady(player: ExoPlayer?) {
        this.player = player
        playerMade = true
        isExtracting = false  // ✅ Extraction complete
    }

    /**
     * DetailsTrailerPlayer could not get a stream: no video id in the url, or
     * every extractor failed. Back to before the first press, so "Loading
     * trailer..." goes and the next press tries again. Unreported, the text
     * stayed up for good, and no press could retry: shouldExtract never changed
     * again, so the player's extraction never ran again.
     *
     * "Couldn't load this trailer" shows only if the trailer is still wanted.
     * After stop() -- a source, episode or recommendation pressed meanwhile --
     * the page has moved on, and the notice would land on the loading screen.
     */
    fun onExtractionFailed() {
        loadFailed = hasPlayed
        shouldExtract = false
        isExtracting = false
        hasPlayed = false
        isPlaying = false
    }

    /** The failure notice has been up long enough. */
    fun dismissLoadFailed() {
        loadFailed = false
    }
}

/** The page's TrailerState. */
@Composable
fun rememberTrailerState(trailerUrl: String?): TrailerState =
    remember(trailerUrl) { TrailerState(trailerUrl) }
