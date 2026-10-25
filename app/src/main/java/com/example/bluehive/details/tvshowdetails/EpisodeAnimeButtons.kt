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

// ====================================================================================================================>  Anime episode overlay (DuB / SuB / VidFast)
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
    // Auto-focus DuB when the overlay opens (matches EpisodeProviderInlineButtons).
    LaunchedEffect(Unit) {
        delay(50)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    fun moveTo(idx: Int): Boolean {
        val fr = focusRequesters.getOrNull(idx) ?: return true
        fr.requestFocus()
        BlueHiveApplication.playHoverSound()
        return true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier.offset(x = 0.3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            AnimeOverlayButton(
                text = "Watch in English",
                passionFont = passionFont,
                focusRequester = focusRequesters[0],
                buttonColors = AnimeDubButtonColors,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> moveTo(1)
                        Key.DirectionDown  -> true
                        Key.DirectionLeft  -> true
                        Key.DirectionUp    -> true
                        Key.Back           -> { onBack(); true }
                        else               -> false
                    }
                },
                onClick = {
                    when {
                        miruroUrl != null -> onDub(miruroUrl)
                        resolving -> queued = true      // honour it when it lands
                        else -> onUnavailable()
                    }
                }
            )
            AnimeOverlayButton(
                text = "Watch in Japanese",
                passionFont = passionFont,
                focusRequester = focusRequesters[1],
                buttonColors = AnimeSubButtonColors,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft  -> moveTo(0)
                        Key.DirectionDown  -> true
                        Key.DirectionRight -> true
                        Key.DirectionUp    -> true
                        Key.Back           -> { onBack(); true }
                        else               -> false
                    }
                },
                onClick = {
                    when {
                        miruroUrl != null -> onSub(miruroUrl)
                        resolving -> queued = false     // honour it when it lands
                        else -> onUnavailable()
                    }
                }
            )
        }
    }
}

@Composable
private fun AnimeOverlayButton(
    text: String,
    passionFont: FontFamily,
    focusRequester: FocusRequester,
    // ONE PALETTE, not four loose colours. Passing the parts individually is how
    // mainFocused went missing here: there was no parameter for it, so no call
    // site could have supplied one, and every focused anime button fell back to
    // the neutral grey default.
    buttonColors: ModularButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(text = text, fontSize = 9f),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequester,
        modifier = modifier,
        dimensions = ModularButtonDimensions(
            mainWidth = 83.dp,
            mainHeight = 23.62.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 83.dp,
            secondHeight = 22.5.dp,
            secondYOffset = 6.5.dp,
            shadowHeight = 6.dp,
            glowWidth = 96.dp,
            glowHeight = 41.5.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        colors = buttonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-6.5).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the movie screen; only the offsets are per-screen,
        // being tuned to this button's smaller size.
        animationConfig = WatchButtonAnimation.copy(
            pressOffset = 2.0.dp,
            textOffsetDefault = 0.6.dp,
            textOffsetPressed = 1.6.dp,
        ),
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}



//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x80000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)
