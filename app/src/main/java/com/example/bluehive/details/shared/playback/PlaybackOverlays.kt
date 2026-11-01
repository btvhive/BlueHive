package com.example.bluehive.details.shared.playback

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.example.bluehive.details.shared.layout.DetailsToast
import com.example.bluehive.utilities.DefaultServerPrompt
import com.example.bluehive.utilities.ServerPickerOverlay
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
            // The browser's real step when the server reports one, else the
            // stage timer's words (LoadingWords.kt); either way it alternates
            // with reassurance once one line runs long.
            val base = session.extractStep?.let(::stepWords) ?: stageWords(session.extractStage)
            val stageLine = withSlowLines(base)
            ExtractionLoadingOverlay(
                status = if (useBackendStatus && !session.isBackendExtract) {
                    session.backendStatus.ifBlank { stageLine }
                } else {
                    stageLine
                },
                stage = session.extractStage,
                // Source chain only: anime extraction is not a chain.
                note  = if (session.isBackendExtract) session.extractNote else null,
                chain = if (session.isBackendExtract) session.extractAttempt to session.chainTotal else null,
                source = if (session.isBackendExtract) session.extractSourceLabel else null,
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

/**
 * The anime flow's server overlays, in their fixed order: the "Checking
 * servers" trap, the default-server prompt, the picker and the "couldn't reach
 * the site" notice. Both screens show them together, at the same place in
 * their own overlay order. Reads the controller itself, so the prompt opening
 * or closing re-runs this and not the whole overlay stack.
 */
@Composable
fun BoxScope.AnimeServerOverlays(
    anime: AnimePlaybackController,
    dongleRegular: FontFamily,
    /**
     * The TV page asks for the server inside its episode card (v585), so the
     * prompt and the picker below are not drawn there -- only the movie page's
     * anime flow still opens them as overlays.
     */
    serverChoiceInCard: Boolean = false,
) {
    // ── "Checking servers" overlay (No path, phase 1) ───────────────────
    if (anime.isCheckingServers) {
        // Focus TRAP, not decoration. ExtractionLoadingOverlay draws a scrim but
        // takes no focus of its own, so without the trap the D-pad still reaches
        // the details screen behind it.
        //
        // NOTE ON SCOPE: this only covers the window where the screen is in the
        // FOREGROUND with the overlay up. It does NOT cover the on-device
        // enumerate pass, because that runs in MiruroExtractorActivity and the
        // screen is paused throughout; the bug people actually hit lives on the
        // RESUME from that activity and is guarded in each screen's
        // RestoreFocusOnPlayerReturn.
        //
        // Directional and select keys are swallowed; BACK and Escape deliberately
        // are not -- a trap with no way out on a remote that has no other keys
        // would be worse. BACK goes on to DetailsBackHandlers, which stops the
        // check (AnimePlaybackController.cancelServerCheck).
        ExtractionFocusTrap {
            ExtractionLoadingOverlay(
                status = "Visiting the site to list its servers…",
                stage  = 2
            )
        }
    }

    // ── Instant "default server?" Yes/No prompt ─────────────────────────
    if (anime.showDefaultPrompt && !serverChoiceInCard) {
        DefaultServerPrompt(
            onDefault = { anime.onDefaultServer() },
            onChoose  = { anime.onChooseServer() },
        )
    }

    // ── Server picker (No path, phase 1 result) ─────────────────────────
    if (anime.showServerPicker && anime.serverOptions.isNotEmpty() && !serverChoiceInCard) {
        ServerPickerOverlay(
            servers    = anime.serverOptions,
            audioLabel = if (anime.pendingDub) "Dub" else "Sub",
            onSelect   = { serverName -> anime.onServerPicked(serverName) },
        )
    }

    // ── Transient "couldn't load" toast ─────────────────────────────────
    if (anime.showServerError) {
        DetailsToast("Couldn't reach the site — please try again", dongleRegular)
    }
}
