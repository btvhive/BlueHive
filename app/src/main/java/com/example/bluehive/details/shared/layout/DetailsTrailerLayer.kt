package com.example.bluehive.details.shared.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.details.shared.state.TrailerState
import com.example.bluehive.utilities.DetailsTrailerPlayer
import kotlinx.coroutines.delay

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
 *
 * Since Stage 4 of the details redesign it reads the page's [TrailerState]
 * itself, so a play/pause no longer re-runs the whole screen function.
 */
@UnstableApi
@Composable
fun DetailsTrailerLayer(
    trailer: TrailerState,
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
        val currentTrailerPlayer by rememberUpdatedState(trailer.player)

        // ✅ Make sure we release ExoPlayer when the details screen leaves the tree
        DisposableEffect(Unit) {
            onDispose {
                currentTrailerPlayer?.release()
            }
        }
    }

    val trailerUrl = trailer.url
    if (!trailerUrl.isNullOrBlank()) {
        DetailsTrailerPlayer(
            trailerUrl = trailerUrl,
            isPlaying = trailer.isPlaying,
            showPlayerView = trailer.hasPlayed,
            shouldExtract = trailer.shouldExtract,
            onPlayerReady = { player ->
                trailer.onPlayerReady(player)
            },
            onExtractionFailed = { trailer.onExtractionFailed() },
            modifier = modifier
        )
    }
}

/** How long "Couldn't load this trailer" stays up: as long as the other short failure notices. */
private const val TRAILER_FAILED_NOTICE_MS = 2_500L

/**
 * "Couldn't load this trailer" at the bottom centre, after the trailer's
 * extraction fails while it is still wanted ([TrailerState.loadFailed]). Each
 * screen calls it last in its overlay stack, so it draws on top and is always
 * composed, which keeps its timer running.
 */
@Composable
fun BoxScope.TrailerFailedNotice(trailer: TrailerState, dongleRegular: FontFamily) {
    LaunchedEffect(trailer.loadFailed) {
        if (trailer.loadFailed) {
            delay(TRAILER_FAILED_NOTICE_MS)
            trailer.dismissLoadFailed()
        }
    }
    if (trailer.loadFailed) {
        DetailsToast("Couldn't load this trailer", dongleRegular)
    }
}
