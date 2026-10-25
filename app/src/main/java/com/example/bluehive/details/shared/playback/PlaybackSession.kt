package com.example.bluehive.details.shared.playback

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * The one loading screen of a details page, shared by the source chain and the
 * anime flow (docs/details-redesign-architecture.md §6, rule 4). Sharing these
 * flags is what stops two extractions running at once, and gives one loading
 * screen and one Back-to-cancel.
 *
 * Moved here from the screens in Stage 6 of the details redesign: the same
 * variables with the same snapshot state, so every write lands in the same
 * frame as before. The flows still write them directly.
 */
@Stable
class PlaybackSession internal constructor() {
    /** An extraction is running: the loading overlay shows. */
    var isExtracting by mutableStateOf(false)

    /** Guards the short pre-launch window, before isExtracting, against a double fire. */
    var extractPending by mutableStateOf(false)

    /** The loading overlay's stage, 0-4, stepped by the stage timer. */
    var extractStage by mutableIntStateOf(0)

    /**
     * Which attempt of the failover chain is on screen. Exists to re-key the
     * stage timer; see [rememberPlaybackSession].
     */
    var extractAttempt by mutableIntStateOf(0)

    // ── BACK during an extraction ───────────────────────────────────────
    //
    // Two pieces, and BOTH are needed. The job handle lets BACK stop the
    // client's polling loop. The flag is what actually prevents the launch:
    // the extraction is wrapped in runCatching, which CATCHES the
    // CancellationException, so a cancelled coroutine still falls through to
    // `body == null` and would raise the failure modal. Cancelling alone
    // fixes the wasted requests and none of the user-visible damage.
    //
    // Without this, pressing BACK hid the overlay but the coroutine ran on —
    // up to ExtractAwait.DEADLINE_MS (120s) — and then launched the player or
    // threw a full-screen error over whatever the user had moved on to.
    var extractJob by mutableStateOf<Job?>(null)
    var extractCancelled by mutableStateOf(false)

    /** The extraction on screen is the source chain's: the overlay shows "trying 2 of 4" and the note. */
    var isBackendExtract by mutableStateOf(false)

    /**
     * The source chain's status line. The movie screen never shows it; the TV
     * screen shows it for an extraction that is not the chain's (see
     * [PlaybackLoadingOverlay]).
     */
    var backendStatus by mutableStateOf("")

    /**
     * The loading screen's "trying 2 of 4": how many sources this chain can
     * try in all, fixed when the viewer presses a source (attempt 1).
     */
    var chainTotal by mutableIntStateOf(1)

    /**
     * Amber line under it once a source has failed -- "VidNest didn't work,
     * trying VidFast" -- kept up for the rest of the chain.
     */
    var extractNote by mutableStateOf<String?>(null)

    /** Non-null shows ExtractionErrorOverlay — a failure the user can act on. */
    var extractionError by mutableStateOf<String?>(null)

    /**
     * BACK while an extraction is in progress. The loading overlay covers
     * windows where the details screen itself is focused -- the gap between
     * failover attempts, and the moments around a request starting and
     * finishing. BACK there used to fall through and leave the screen.
     * Returning focus to the source button is the screen's part.
     */
    fun cancel() {
        Log.d("scraper-vidapi", "↩️ back during extraction — cancelling")
        extractCancelled      = true
        extractJob?.cancel()
        extractJob            = null
        extractPending        = false
        isExtracting          = false
        // Reset here because a cancel never reaches the line in the source
        // chain that clears it -- and the loading screen reads it to decide
        // whether to show "trying 2 of 4", so a stale true would put a source
        // count and an old failover note over the next ANIME load.
        isBackendExtract      = false
        // And its status line, for the same reason: the TV screen's headline
        // shows it for an extraction that is not the chain's.
        backendStatus         = ""
        extractStage          = 0
    }
}

/** The page's PlaybackSession, with the loading overlay's stage timer. */
@Composable
fun rememberPlaybackSession(): PlaybackSession {
    val session = remember { PlaybackSession() }
    PlaybackStageTimer(session)
    return session
}

/**
 * The loading screen's stage timer. A composable of its own so that the flags
 * it is keyed on are read here, not in the screen function: an extraction
 * starting, ending or moving to the next source re-runs this and the loading
 * overlay, not the whole screen (architecture P1).
 */
@Composable
private fun PlaybackStageTimer(session: PlaybackSession) {
    // PACED FOR EVERY EXTRACTION, the source chain's included. Backend
    // extraction used to be exempt because VidFast reported its real state over
    // a broadcast -- but that receiver went with the on-device extractor, so the
    // exemption left the dots on stage 1 for the whole 20-90 s wait, which reads
    // as a hang. Under the chain's "trying 2 of 4" the stage words are only the
    // detail line.
    //
    // KEYED ON extractAttempt AS WELL AS isExtracting. A failover keeps the
    // overlay up on purpose (no blink between sources), so isExtracting may not
    // visibly transition between one attempt and the next -- and without a second
    // key this effect would not restart, leaving the dots frozen at stage 1 for
    // the whole of the next attempt, which reads as a hang.
    //
    // It used to key on the ON-DEVICE retry counter, which the backend path never
    // incremented, so the dots really did freeze during a backend failover.
    // The source chain stamps extractAttempt on entry, so every attempt --
    // first or nth -- changes the key.
    LaunchedEffect(session.isExtracting, session.extractAttempt) {
        if (session.isExtracting) {
            session.extractStage = 1
            delay(900);  session.extractStage = 2
            delay(2500); session.extractStage = 3
            delay(3500); session.extractStage = 4
        } else {
            session.extractStage = 0
        }
    }
}
