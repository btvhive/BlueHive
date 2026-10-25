package com.example.bluehive.details.shared.state

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.details.shared.playback.AnimePlaybackController
import com.example.bluehive.details.shared.playback.PlaybackSession

/**
 * The details screens' Back handlers, registered in one place and in a fixed
 * order (docs/details-redesign-architecture.md §7, C2).
 *
 * OnBackPressedDispatcher runs the MOST RECENTLY added enabled callback, and
 * Compose adds them in composition order. So this registers Extras first, then
 * the server prompt, then the extraction: while a film is loading Back cancels
 * it, while the prompt or picker is open Back closes it, and only then does it
 * collapse Extras. Put Extras last and Back would collapse it out from under
 * an open overlay. Each screen calls this once, right after creating its state
 * holders, and unconditionally -- a handler inside a section that only
 * sometimes shows would jump the queue.
 *
 * [onExtractionCancelled] runs after the cancel: the screen returns the cursor
 * to the button that started the extraction.
 */
@Composable
fun DetailsBackHandlers(
    extras: ExtrasState,
    anime: AnimePlaybackController,
    session: PlaybackSession,
    onExtractionCancelled: () -> Unit,
) {
    // ── Back collapses Extras before it leaves the page ─────────────────────
    //
    // Pressing back while the recommendations row is showing returns to the
    // normal state; a second press then leaves, which is what "back" means
    // everywhere else — undo the last thing you did, not the whole journey.
    //
    // When disabled it does not intercept at all, so back falls through to the
    // Activity's own callback and the page closes exactly as before.
    BackHandler(enabled = extras.isOn) {
        BlueHiveApplication.playBackOutSound()
        extras.close()
    }

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
}
