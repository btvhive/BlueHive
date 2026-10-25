package com.example.bluehive.details.tvshowdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * How long a first press on an unaired episode stays armed.
 *
 * Long enough to read the notice and answer it; short enough that the answer is
 * clearly to THAT question. 6s is about two unhurried reads of the band.
 */
internal const val UNAIRED_OVERRIDE_WINDOW_MS = 6_000L

/**
 * THE WAY PAST THE UNAIRED GATE.
 *
 * WHY AN OVERRIDE EXISTS AT ALL. isEpisodeUnaired is a PREDICTION made on
 * this device from TMDB's air_date -- it is not a fact reported by the
 * sources that would actually serve the episode. The two can disagree:
 * provisional simulcast dates, a schedule that moved, a source that has the
 * file early. Without a way past, one wrong date removes an episode with no
 * appeal, and that is the only thing in this app that cannot be retried.
 *
 * A SECOND PRESS, not a longer one: it needs no new gesture to learn, it
 * cannot happen by accident, and the first press has already said why.
 *
 * Moved here from the screen in Stage 8 of the details redesign.
 */
@Stable
class UnairedGate internal constructor() {
    /**
     * Which unaired episode has been pressed once, and so will go through on the
     * next press. -1 for none.
     */
    var armedIndex by mutableIntStateOf(-1)
}

@Composable
fun rememberUnairedGate(): UnairedGate = remember { UnairedGate() }
