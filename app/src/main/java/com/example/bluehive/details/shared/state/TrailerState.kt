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

    /** The trailer's player, once DetailsTrailerPlayer has made one. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

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

    /** The Play/Pause Trailer button. The first press also starts the extraction. */
    fun onButtonPressed() {
        if (!url.isNullOrBlank()) {
            if (!hasPlayed) {
                shouldExtract = true
                isExtracting = true  // ✅ Show loading state
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
        isExtracting = false  // ✅ Extraction complete
    }
}

/** The page's TrailerState. */
@Composable
fun rememberTrailerState(trailerUrl: String?): TrailerState =
    remember(trailerUrl) { TrailerState(trailerUrl) }
