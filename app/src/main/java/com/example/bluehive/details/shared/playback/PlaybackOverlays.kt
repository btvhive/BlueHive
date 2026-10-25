package com.example.bluehive.details.shared.playback

import androidx.compose.runtime.Composable
import com.example.bluehive.webview.ExtractionErrorOverlay
import com.example.bluehive.webview.ExtractionFocusTrap
import com.example.bluehive.webview.ExtractionLoadingOverlay

/**
 * The details screens' playback overlays. Each screen calls them from its own
 * overlay slot, in its own order (architecture §10, #14). Each reads the
 * session itself, so a stage tick re-runs only the loading overlay.
 *
 * Moved here from the screens in Stage 6 of the details redesign.
 */

/**
 * The loading screen, TRAPPED: without the trap, the focused button behind the
 * scrim keeps the D-pad -- on the movie screen it swallows the first BACK for
 * its own onBack and the extraction keeps running; on the TV screen the episode
 * tile eats BACK twice before a third press reaches the handler that cancels.
 */
@Composable
fun PlaybackLoadingOverlay(
    session: PlaybackSession,
    /**
     * The TV screen's headline: while the extraction on screen is not the
     * source chain's, show the session's backendStatus when it is set. The
     * movie screen has always shown the stage words alone.
     */
    useBackendStatus: Boolean = false,
) {
    if (session.isExtracting) {
        ExtractionFocusTrap {
            val stageWords = when (session.extractStage) {
                0, 1 -> "Locating stream source…"
                2    -> "Loading stream source…"
                3    -> "Preparing video player…"
                else -> "Almost there…"
            }
            ExtractionLoadingOverlay(
                status = if (useBackendStatus && !session.isBackendExtract) {
                    session.backendStatus.ifBlank { stageWords }
                } else {
                    stageWords
                },
                stage = session.extractStage,
                // Source chain only: anime extraction is not a chain.
                note  = if (session.isBackendExtract) session.extractNote else null,
                chain = if (session.isBackendExtract) session.extractAttempt to session.chainTotal else null,
            )
        }
    }
}

/**
 * An extraction failure the user can act on ("try another source"). Without it
 * the screen just returned to the source buttons, and a blocked domain looked
 * the same as a dead one.
 */
@Composable
fun PlaybackErrorOverlay(
    session: PlaybackSession,
    /**
     * After it closes. The TV screen returns focus to the source button; the
     * movie screen does nothing (architecture §10, #2).
     */
    onDismissed: () -> Unit = {},
) {
    session.extractionError?.let { msg ->
        ExtractionErrorOverlay(
            message = msg,
            onDismiss = {
                session.extractionError = null
                onDismissed()
            }
        )
    }
}
