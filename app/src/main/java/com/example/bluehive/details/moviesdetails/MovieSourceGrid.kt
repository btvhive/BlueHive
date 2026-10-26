package com.example.bluehive.details.moviesdetails

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.layout.PEEK_RIGHT_COLUMN_X
import com.example.bluehive.details.shared.layout.PEEK_SOURCE_ROW_STEP
import com.example.bluehive.details.shared.playback.StreamSource
import com.example.bluehive.utilities.*

/**
 * The movie screen's play area: Watch in English / Japanese for anime, the
 * "Watch on ..." grid for everything else.
 *
 * Moved out of MovieDetailsScreen on 2026-10-26, with the four button
 * composables below, to keep the screen function well under the 2,500-unit
 * budget (checkMethodSizes); the layout and key handling are the code they
 * replace. What a press DOES stays the screen's: [onAnimePress],
 * [onSourcePress] and [onDownIntoRow].
 */
@Composable
internal fun BoxScope.MovieSourceGrid(
    isAnime: Boolean,
    sources: List<StreamSource>,
    fontFamily: FontFamily,
    /** The first row's top, just under the overview (DetailsPeekState.playTop). */
    playTop: Dp,
    animeDubFocusRequester: FocusRequester,
    animeSubFocusRequester: FocusRequester,
    /** One per source, in order. */
    streamerFocus: List<FocusRequester>,
    /** Down on a bottom-row button, from that button: true when focus entered the recommended row. */
    onDownIntoRow: (FocusRequester) -> Boolean,
    /** Watch in English (true) or Japanese (false). */
    onAnimePress: (Boolean) -> Unit,
    /** A "Watch on ..." press: the button's requester and its source. */
    onSourcePress: (FocusRequester, StreamSource) -> Unit,
) {
    if (isAnime) {
        // ── Anime: DuB / SuB ────────────────────────────────────
        AnimeDubButton(
            fontFamily = fontFamily,
            focusRequester = animeDubFocusRequester,
            offsetX = 27.75.dp, offsetY = playTop,
            onDown = { onDownIntoRow(animeDubFocusRequester) },
            onClick = { onAnimePress(true) }
        )
        AnimeSubButton(
            fontFamily = fontFamily,
            focusRequester = animeSubFocusRequester,
            offsetX = PEEK_RIGHT_COLUMN_X, offsetY = playTop,
            onDown = { onDownIntoRow(animeSubFocusRequester) },
            onClick = { onAnimePress(false) }
        )
    } else {
        // ── Non-anime: 2-column grid laid out FROM the sources list ──
        // Adding/removing an entry in getStreamingSources() now "just
        // works" — no per-index block to keep in sync (that mismatch is
        // what silently dropped the 6th button). Column X and the first
        // rows keep the legacy spacing, starting just under the overview's
        // last line (peek layout); a 5th+ source continues the grid downward and
        // pushes the recommended row down with it (DetailsPeekState).
        val colX = listOf(27.75.dp, PEEK_RIGHT_COLUMN_X)  // [left column, right column]
        sources.forEachIndexed { i, source ->
            val col = i % 2
            val row = i / 2
            // A trailing odd button (alone on its row) is centered between the two
            // columns instead of hugging the left column.
            val aloneInRow = i % 2 == 0 && i == sources.lastIndex
            MoviesStreamerButton(
                source = source,
                fontFamily = fontFamily,
                focusRequester = streamerFocus[i],
                offsetX = if (aloneInRow) (colX[0] + colX[1]) / 2f else colX[col],
                offsetY = playTop + PEEK_SOURCE_ROW_STEP * row,
                disableLeftSound  = aloneInRow || col == 0, // left column / lone centered → mute left edge
                disableRightSound = aloneInRow || col == 1, // right column / lone centered → mute right edge
                disableDownSound  = i + 2 >= sources.size,  // no button directly below → mute down
                // Bottom row: Down enters the recommended row.
                onDownFromBottom  = { onDownIntoRow(streamerFocus[i]) },
                onClick = { onSourcePress(streamerFocus[i], source) }
            )
        }
    }
}


@Composable
fun MoviesStreamerButton(
    source: StreamSource,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp,
    offsetY: Dp,
    disableLeftSound: Boolean = false,
    disableRightSound: Boolean = false,
    disableUpSound: Boolean = false,
    disableDownSound: Boolean = false,
    /** Down on a bottom-row button; true when it moved focus (into the recommended row). */
    onDownFromBottom: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = if (source.name == "M Extract") "M Extract" else "Watch on ${source.name}",
            fontSize = 14f,
            // A shadow under the label -- see ButtonLabelShadow.
            shadow = ButtonLabelShadow,
        ),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (disableLeftSound) {
                                // edge: no sound, no navigation
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false   // let focus move normally
                            }
                        }
                        Key.DirectionRight -> {
                            if (disableRightSound) {
                                // Right edge: a plain dead end, no sound, no move.
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            if (disableUpSound) {
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (disableDownSound) {
                                // Bottom row: into the recommended row, with a
                                // sound only when it moved; without a row,
                                // nothing below and no sound.
                                if (onDownFromBottom?.invoke() == true) BlueHiveApplication.playHoverSound()
                                onDownFromBottom != null
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp,
            mainHeight = 33.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 162.dp,
            secondHeight = 22.dp,
            secondYOffset = 18.dp,
            shadowHeight = (12).dp,
            glowWidth = 185.8.dp,
            glowHeight = 55.8.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared with the TV screen — see StreamSourceButtonColors in
        // utilities/ModularButton.kt. Change the blues there, not here.
        colors = StreamSourceButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the TV screen — see WatchButtonAnimation in
        // utilities/ModularButton.kt. Change the press feel there, not here.
        animationConfig = WatchButtonAnimation,
        // No playClickSound() here: ModularButton already plays it, immediately,
        // and this second call fired a duplicate. Harmless while the two landed
        // on the same frame — audible as a real double-click once onClick became
        // deferred by clickDelayMillis.
        onClick = onClick
    )
}


/**
 * Down on Watch in English / Japanese. Without [onDown], as before: a sound,
 * and the focus system moves on. With it (the peek layout), Down enters the
 * recommended row, sounding only when it moved.
 */
private fun animeDown(onDown: (() -> Boolean)?): Boolean {
    if (onDown == null) {
        BlueHiveApplication.playHoverSound()
        return false
    }
    if (onDown()) BlueHiveApplication.playHoverSound()
    return true
}

@Composable
fun AnimeDubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp = 27.75.dp,
    offsetY: Dp = 332.5.dp,
    /** Down; true when it moved focus (into the recommended row). */
    onDown: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    ModularButton(
        // "English" / "Japanese" rather than "DuB" / "SuB". Dub and sub are
        // fan shorthand; the language is what a viewer is actually choosing.
        // The same label shadow as the Watch on ... buttons -- see ButtonLabelShadow.
        textConfig = ModularButtonTextConfig(text = "Watch in English", fontSize = 14f, shadow = ButtonLabelShadow),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> true   // left edge — block + no sound
                        Key.DirectionDown -> animeDown(onDown)
                        Key.DirectionRight, Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeDubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeDubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}

@Composable
fun AnimeSubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp = 228.dp,
    offsetY: Dp = 332.5.dp,
    /** Down; true when it moved focus (into the recommended row). */
    onDown: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(text = "Watch in Japanese", fontSize = 14f, shadow = ButtonLabelShadow),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> true  // right edge — block + no sound
                        Key.DirectionDown -> animeDown(onDown)
                        Key.DirectionLeft, Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeSubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeSubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}
