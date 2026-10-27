package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R

// ====================================================================================================================>  Anime episode overlay (Watch in English / Japanese)
@Composable
internal fun AnimeEpisodeProviderButtons(
    passionFont: FontFamily,
    miruroUrl: String?,                                            // null while resolving OR if unmapped
    /**
     * The source list has not arrived yet.
     *
     * THIS IS WHAT SEPARATES "not yet" FROM "not there", and without it the two
     * were the same thing: miruroUrl is null in both cases, so a press made in
     * the second before the AniList resolve landed reported the episode as
     * unavailable — and the identical press a moment later worked. The list is
     * empty only while loading (it always carries VidFast once built), so its
     * emptiness is the signal.
     */
    resolving: Boolean,
    focusRequesters: List<FocusRequester>,                         // [0]=DuB, [1]=SuB
    onDub: (String) -> Unit,
    onSub: (String) -> Unit,
    onUnavailable: () -> Unit,                                     // genuinely has no mapping
    onBack: () -> Unit,
) {
    /** A press made too early. true = dub, false = sub, null = none waiting. */
    var queued by remember { mutableStateOf<Boolean?>(null) }

    // REMEMBERED, NOT REFUSED. The viewer pressed a button; the right answer is
    // to honour it when the url shows up, not to tell them the episode does not
    // exist because the network was a beat behind their thumb.
    //
    // Re-runs whenever the url or the resolving flag changes, which cancels the
    // delay below — so the bounded wait only expires if nothing ever arrived.
    LaunchedEffect(queued, miruroUrl, resolving) {
        val want = queued ?: return@LaunchedEffect
        if (miruroUrl != null) {
            queued = null
            if (want) onDub(miruroUrl) else onSub(miruroUrl)
            return@LaunchedEffect
        }
        if (!resolving) {           // list arrived and carries no Miruro: real
            queued = null
            onUnavailable()
            return@LaunchedEffect
        }
        // Still resolving. Wait, but not forever — a resolve that never
        // finishes must not leave the button looking dead.
        delay(8_000)
        if (queued != null) {
            queued = null
            onUnavailable()
        }
    }
    // Auto-focus English when the overlay opens (matches EpisodeSourceButtons).
    LaunchedEffect(Unit) {
        delay(50)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    // One under another in the episode overlay, like its Watch on ... buttons:
    // Up / Down move, the ends and Left / Right go nowhere -- see OverlayWatchButton.
    OverlayWatchButton(
        text = "Watch in English",
        colors = AnimeDubButtonColors,
        passionFont = passionFont,
        focusRequester = focusRequesters[0],
        index = 0,
        focusRequesters = focusRequesters.subList(0, 2),
        onBack = onBack,
        onClick = {
            when {
                miruroUrl != null -> onDub(miruroUrl)
                resolving -> queued = true      // honour it when it lands
                else -> onUnavailable()
            }
        }
    )
    OverlayWatchButton(
        text = "Watch in Japanese",
        colors = AnimeSubButtonColors,
        passionFont = passionFont,
        focusRequester = focusRequesters[1],
        index = 1,
        focusRequesters = focusRequesters.subList(0, 2),
        onBack = onBack,
        onClick = {
            when {
                miruroUrl != null -> onSub(miruroUrl)
                resolving -> queued = false     // honour it when it lands
                else -> onUnavailable()
            }
        }
    )
}



//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x80000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)
