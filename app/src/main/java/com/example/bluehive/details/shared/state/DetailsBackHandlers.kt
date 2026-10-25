package com.example.bluehive.details.shared.state

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.bluehive.BlueHiveApplication

/**
 * The details screens' Back handlers, registered in one place and in a fixed
 * order (docs/details-redesign-architecture.md §7, C2).
 *
 * OnBackPressedDispatcher runs the MOST RECENTLY added enabled callback, and
 * Compose adds them in composition order. So each screen calls this right
 * after creating its state holders, above every other handler on the page:
 * the server prompt and the extraction guard further down still win while
 * they are open. Put Extras last and Back would collapse it out from under an
 * open overlay. Called unconditionally -- a handler inside a section that only
 * sometimes shows would jump the queue.
 *
 * Today this holds the Extras handler. The prompt/picker handler and then the
 * extraction handler move in at Stages 6 and 7, registered after it.
 */
@Composable
fun DetailsBackHandlers(extras: ExtrasState) {
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
}
