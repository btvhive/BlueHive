package com.example.bluehive.details.shared.state

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.bluehive.details.shared.playback.AnimePlaybackController
import com.example.bluehive.details.shared.playback.PlaybackSession

/**
 * The details screens' Back handlers, registered in one place and in a fixed
 * order (docs/details-redesign-architecture.md §7, C2).
 *
 * OnBackPressedDispatcher runs the MOST RECENTLY added enabled callback, and
 * Compose adds them in composition order. This registers the server prompt,
 * then the extraction, then the server check: while a film is loading Back
 * cancels it, while the prompt or picker is open Back closes it, while the
 * server list is being checked Back stops the check. The three never overlap.
 * (Extras' handler, registered first of all, went with Extras in v515.) Each
 * screen calls this once, right after creating its state holders, and
 * unconditionally -- a handler inside a section that only sometimes shows
 * would jump the queue.
 *
 * [onExtractionCancelled] runs after the cancel: the screen returns the cursor
 * to the button that started the extraction.
 */
@Composable
fun DetailsBackHandlers(
    anime: AnimePlaybackController,
    session: PlaybackSession,
    onExtractionCancelled: () -> Unit,
) {
    // Back closes whichever picker/prompt is open instead of leaving the screen.
    BackHandler(enabled = anime.showDefaultPrompt || anime.showServerPicker) {
        anime.onPromptBack()
    }

    // ── BACK cancels an extraction in progress ───────────────────────
    // The loading overlay covers windows where THIS screen is focused -- the gap
    // between failover attempts, and the moments around a request starting and
    // finishing. BACK there used to fall through and leave the details screen
    // entirely.
    BackHandler(enabled = session.isExtracting || session.extractPending) {
        session.cancel()
        onExtractionCancelled()
    }

    // ── BACK stops the server-list check ─────────────────────────────
    // "Checking the site for available servers…", after "Choose a server" on
    // a list that is not cached yet. No handler above is enabled while it
    // runs, so BACK used to fall through and leave the details page. Its
    // first 200 ms are the extraction handler's (extractPending), which
    // cancels the same job -- see AnimePlaybackController.cancelServerCheck.
    BackHandler(enabled = anime.isCheckingServers) {
        anime.cancelServerCheck()
    }
}
