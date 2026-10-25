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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.playback.StreamSource

// =====================================================================================================================>  Episode overlay section
@Composable
internal fun EpisodeProviderInlineButtons(
    passionFont: FontFamily,
    sources: List<StreamSource>,
    focusRequesters: List<FocusRequester>,
    onSourceSelected: (StreamSource) -> Unit,
    onBack: () -> Unit,   // ✅ NEW
) {
    val safeSources = remember {
        mutableStateListOf<StreamSource>()
    }

    LaunchedEffect(sources) {
        safeSources.clear()
        // Cap to the focus-requester pool so we never index past it; the pool is
        // sized to the source list, so in practice this keeps every source.
        safeSources.addAll(sources.take(focusRequesters.size))
    }

    LaunchedEffect(safeSources.size) {
        if (safeSources.isNotEmpty()) {
            // give Compose a frame to attach focus nodes
            delay(50)
            focusRequesters.firstOrNull()?.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-6).dp),
        modifier = Modifier
            .wrapContentWidth()
            .offset(x = 0.3.dp) // how far left or right the entire section of button goes!!!
            .focusGroup()
    ) {
        // Two per row, laid out FROM the source list — adding/removing a source
        // now just adds/removes a button (an odd count leaves a lone left button)
        // with no per-index Row block to keep in sync.
        safeSources.chunked(2).forEachIndexed { rowIndex, rowSources ->
            Row(
                horizontalArrangement = Arrangement.spacedBy((-2).dp),
                // Wrap to content (NOT fillMaxWidth): the Column's CenterHorizontally then
                // centers a lone trailing button UNDER the 2-wide grid. With fillMaxWidth
                // the row spanned the whole overlay, so centering threw it far right; the
                // full 2-button rows are the widest, so they stay anchored at the left.
                modifier = Modifier.wrapContentWidth()
            ) {
                rowSources.forEachIndexed { colIndex, source ->
                    val i = rowIndex * 2 + colIndex
                    EpisodeOverlayStreamerButton(
                        index = i,
                        total = safeSources.size,
                        // right column nudged left the same 1.5.dp as before
                        offsetX = if (colIndex == 0) (-1).dp else (-1.5).dp,
                        source = source,
                        passionFont = passionFont,
                        focusRequesters = focusRequesters,
                        onClick = { onSourceSelected(source) },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}




@Composable
private fun EpisodeOverlayStreamerButton(
    index: Int,
    total: Int,                       // total buttons in the grid — drives edge-aware D-pad nav
    offsetX: Dp = (-1).dp,
    source: StreamSource,
    passionFont: FontFamily,
    focusRequesters: List<FocusRequester>,
    onClick: () -> Unit,
    onBack: () -> Unit,
) {
    // 🔹 manual x tweak just for certain names
    val labelOffsetX = when (source.name) {
        "VidEasy" -> (2).dp   // a little left
        "VidRock" -> (2).dp   // same tweak
        else -> 0.dp
    }


    val glowWidth= when (source.name) {
        "VidEasy" -> (96).dp   // a little width
        "VidRock" -> (96).dp   // same tweak
        else -> 96.dp
    }

    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = "Watch on ${source.name}",
            fontSize = 9f,
            offsetX = labelOffsetX
        ),
        isToggleable = false,
        fontFamily = passionFont,
        focusRequester = focusRequesters[index],
        modifier = Modifier
            .offset(x = offsetX)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 2-column grid: col = index % 2, row = index / 2. Targets are
                // computed so the nav is correct for ANY button count (adding a
                // source can't leave a stale index mapping behind). -1 = edge → the
                // key is consumed with no move and no sound, exactly as before.
                when (event.key) {
                    Key.DirectionLeft -> {
                        val target = if (index % 2 == 1) index - 1 else -1   // right col → left neighbour
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()   // 🔊 play on successful move
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        val target = if (index % 2 == 0 && index + 1 < total) index + 1 else -1  // left col → right neighbour
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        // Right column (and a lone trailing button): RIGHT is a
                        // dead end again now the corner button is gone.
                        true
                    }

                    Key.DirectionUp -> {
                        val target = index - 2                               // one row up, same column
                        if (target >= 0) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        var target = index + 2                               // one row down, same column
                        // Lone trailing button (odd source count — e.g. VidApi centered under
                        // the 2x2 grid): the right-column button directly above it (index
                        // total-2, e.g. VidLink) has no same-column neighbour below, so route
                        // DOWN to the lone button instead of dead-ending with no move.
                        if (total % 2 == 1 && index == total - 2) {
                            target = total - 1
                        }
                        if (target < total) {
                            focusRequesters.getOrNull(target)?.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        // Bottom row: nothing below it inside the grid, so DOWN
                        // is consumed with no move.
                        true
                    }

                    Key.Back -> {
                        onBack()
                        true   // consume so Activity back doesn't fire
                    }

                    else -> false
                }
            },
        // just with your narrow width so 2-per-row still fit
        dimensions = ModularButtonDimensions(
            mainWidth = 83.dp,
            mainHeight = 23.62.dp,      // was 23.625.dp
            mainYOffset = 0.7.dp,
            secondWidth = 83.dp,
            secondHeight = 22.5.dp,    // was 21.dp
            secondYOffset = 6.5.dp,   // was 6.5.dp
            shadowHeight = 6.dp,    // was 6.dp
            glowWidth = glowWidth,
            glowHeight = 41.5.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared with the movie screen — see StreamSourceButtonColors in
        // utilities/ModularButton.kt. Change the blues there, not here.
        colors = StreamSourceButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            // keep using your narrow glow asset
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-6.5).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the movie screen — see WatchButtonAnimation
        // in utilities/ModularButton.kt. Only the offsets are per-screen, being
        // tuned to this button's smaller size.
        animationConfig = WatchButtonAnimation.copy(
            pressOffset = 2.0.dp,
            textOffsetDefault = 0.6.dp,
            textOffsetPressed = 1.6.dp,
        ),
        // No playClickSound() here: ModularButton already plays it, immediately,
        // and this second call fired a duplicate. Harmless while the two landed
        // on the same frame — audible as a real double-click once onClick became
        // deferred by clickDelayMillis.
        onClick = onClick
    )
}
// =====================================================================================================================> Episode overlay section
