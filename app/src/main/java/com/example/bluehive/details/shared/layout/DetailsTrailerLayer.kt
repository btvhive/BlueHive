package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.bluehive.utilities.DetailsTrailerPlayer

/**
 * The trailer player and its release-on-leave, lifted out of
 * TVShowsDetailsScreenContent.
 *
 * WHY THIS IS ITS OWN FUNCTION. In v371-v375 R8 mis-allocated registers in that
 * one huge method: on one path a Long sat in the register it later tested as
 * `trailerUrl` here, ART rejected the whole TVShowsDetailsScreenComposeKt class
 * ("type Long (Low Half) unexpected as arg to if-eqz") and every TV details
 * page -- and every movie source button, which calls into this file -- crashed
 * with a VerifyError. Moving this block out shrinks the method R8 has to colour.
 * Check any release with `cmd package compile -m verify -f` before shipping.
 */
@UnstableApi
@Composable
fun DetailsTrailerLayer(
    trailerUrl: String?,
    trailerPlayer: ExoPlayer?,
    isPlaying: Boolean,
    showPlayerView: Boolean,
    shouldExtract: Boolean,
    onPlaybackStateChanged: (Boolean) -> Unit,
    onPlayerReady: (ExoPlayer?) -> Unit,
    modifier: Modifier,
    /**
     * Release the player again when the screen leaves. The TV screen has
     * always done this; the movie screen never has. It is redundant --
     * DetailsTrailerPlayer releases its own player -- and kept per screen
     * until that difference is settled (architecture §10, #12).
     */
    releaseOnLeave: Boolean,
) {
    if (releaseOnLeave) {
        val currentTrailerPlayer by rememberUpdatedState(trailerPlayer)

        // ✅ Make sure we release ExoPlayer when the details screen leaves the tree
        DisposableEffect(Unit) {
            onDispose {
                currentTrailerPlayer?.release()
            }
        }
    }

    if (!trailerUrl.isNullOrBlank()) {
        DetailsTrailerPlayer(
            trailerUrl = trailerUrl,
            isPlaying = isPlaying,
            showPlayerView = showPlayerView,
            shouldExtract = shouldExtract,
            onPlaybackStateChanged = onPlaybackStateChanged,
            onPlayerReady = onPlayerReady,
            modifier = modifier
        )
    }
}
