package com.example.bluehive.details.shared.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  What the loading screen says while a stream is found (v576, the owner's
//  pick). A real browser on our server opens the source's site, loads the
//  page, starts its video player and catches the stream it asks for; the
//  words follow those steps, so the wait reads as a browser at work.
//
//  REAL STEPS WHEN THE SERVER SENDS THEM. The job poll can carry the browser's
//  step ("opening", "page_loaded", ...): then the line is that step's words.
//  Without one (an older server, a cache-cold start, the anime path) the line
//  falls back to the stage timer's words, in the same order.
// ─────────────────────────────────────────────────────────────────────────────

/** The stage timer's words (PlaybackStageTimer: 0, 0.9, 3.4 and 6.9 s in). */
internal fun stageWords(stage: Int): String = when (stage) {
    0, 1 -> "Opening the site in a secure browser…"
    2    -> "Loading the page…"
    3    -> "Starting the site’s video player…"
    else -> "Catching the stream from the player…"
}

/** A real step from the server, or null for one this build does not know. */
internal fun stepWords(step: String): String? = when (step) {
    "queued"         -> "Waiting for a free browser…"
    "opening"        -> "Opening the site in a secure browser…"
    "page_loaded"    -> "Page loaded, starting the site’s video player…"
    "player_started" -> "Catching the stream from the player…"
    "stream_caught",
    "checking"       -> "Stream found, checking it plays…"
    else             -> null
}

/** Said between the step's own words once one step runs long. */
internal val SLOW_LINES = listOf(
    "Still loading, some sites take up to a minute…",
    "Waiting for the site’s player to respond…",
    "The browser is still working on this page…",
)

/** A line held this long starts alternating with [SLOW_LINES]... */
internal const val SLOW_AFTER_MS = 8_000L

/** ...one turn every this often. */
internal const val SLOW_EVERY_MS = 8_000L

/**
 * [base], or once it has been up [SLOW_AFTER_MS], alternately one of
 * [SLOW_LINES] and [base] again, every [SLOW_EVERY_MS]. A new [base] (the next
 * step) starts over, so the reassurance only shows while something runs long.
 */
@Composable
internal fun withSlowLines(base: String): String {
    var turn by remember(base) { mutableIntStateOf(0) }
    LaunchedEffect(base) {
        delay(SLOW_AFTER_MS)
        while (true) {
            turn++
            delay(SLOW_EVERY_MS)
        }
    }
    return slowLineFor(base, turn)
}

/** Turn 0 is [base]; odd turns are the slow lines in order; even turns [base] again. */
internal fun slowLineFor(base: String, turn: Int): String =
    if (turn % 2 == 1) SLOW_LINES[(turn / 2) % SLOW_LINES.size] else base
