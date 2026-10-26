package com.example.bluehive.details.shared.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
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
import com.example.bluehive.details.shared.state.ExtrasState
import com.example.bluehive.details.shared.state.FavoriteState
import com.example.bluehive.details.shared.state.TrailerState
import com.example.bluehive.utilities.ExtrasButtonColors
import com.example.bluehive.utilities.FavoriteButtonColors
import com.example.bluehive.utilities.ModularButton
import com.example.bluehive.utilities.ModularButtonAnimationConfig
import com.example.bluehive.utilities.ModularButtonDimensions
import com.example.bluehive.utilities.ModularButtonGlowConfig
import com.example.bluehive.utilities.ModularButtonTextConfig
import com.example.bluehive.utilities.TrailerControlButtonColors

/**
 * How a header button presses: it travels for [durationMillis] each way and
 * stays down for [bounceBackDelayMillis] in between (ModularButtonAnimationConfig
 * has the details). Each screen passes its own: TV 110/80 ms, movie 180/140 ms.
 */
@Immutable
data class DetailsPressTiming(val durationMillis: Int, val bounceBackDelayMillis: Int)

/**
 * The five buttons across the top of both details screens -- Favorites, Extras,
 * Play/Pause Trailer, Rewind and Fast-forward -- with the "Loading trailer..."
 * line between the trailer button and Rewind, drawn in that order.
 *
 * Each screen had its own copy of all five, identical except for how long a
 * press takes, which stays per screen through [pressTiming] (architecture §10,
 * #1). What a press does lives in the page's state holders (Stage 4): the
 * screens' differences are settings of those -- on TV the favorite toggle also
 * sends the title, and the two screens load Extras on different threads.
 *
 * Moved here word for word in Stage 3 of the details redesign. A BoxScope
 * extension so each button keeps its absolute offset in the screen's Box and
 * the D-pad moves between them exactly as before -- never wrap it in a layout.
 * It reads the holders itself, so a press re-runs only this, not the screen.
 */
@Composable
fun BoxScope.DetailsHeaderButtons(
    fontFamily: FontFamily,
    pressTiming: DetailsPressTiming,
    /** False while the screen keeps first focus off the header. */
    enableFocus: Boolean,
    favoriteFocusRequester: FocusRequester,
    extrasFocusRequester: FocusRequester,
    trailerFocusRequester: FocusRequester,
    favorite: FavoriteState,
    extras: ExtrasState,
    trailer: TrailerState,
    /**
     * False in the movie screen's peek layout: no Extras button, and the
     * trailer controls become Rewind | Play Trailer | Forward, with Rewind's
     * left edge on the metadata's (258.25 dp) and the trailer button 90 dp
     * wide, not 100, in a 104.5 dp glow so less of it shows at the sides (the
     * user's calls, 2026-10-26: 80 dp, then 88, 92 and 90 in the same glow).
     */
    showExtras: Boolean = true,
) {
    // Peek layout: Rewind at the metadata's left edge, then the trailer
    // button and Forward, PEEK_TRAILER_GAP apart.
    val rewindX = if (showExtras) 367.25.dp else 258.25.dp
    val trailerX = if (showExtras) 256.25.dp else rewindX + SEEK_BUTTON_WIDTH + PEEK_TRAILER_GAP
    val trailerWidth = if (showExtras) 100.dp else 90.dp
    val trailerGlowWidth = if (showExtras) 116.5.dp else 104.5.dp
    val forwardX = if (showExtras) 400.25.dp else trailerX + trailerWidth + PEEK_TRAILER_GAP

    // Favorite Button - Uses internal offset for positioning
    DetailsFavoriteButton(
        fontFamily = fontFamily,
        focusRequester = favoriteFocusRequester,
        pressTiming = pressTiming,
        enableFocus = enableFocus,
        isFavorited = favorite.isFavorited,
        onToggle = { favorite.toggle() }
    )

    if (showExtras) {
        DetailsExtrasButton(
            fontFamily = fontFamily,
            focusRequester = extrasFocusRequester,
            pressTiming = pressTiming,
            isToggled = extras.isOn,
            enableFocus = enableFocus,
            onClick = { extras.toggle() }
        )
    }



    DetailsTrailerButton(
        fontFamily = fontFamily,
        focusRequester = trailerFocusRequester,
        pressTiming = pressTiming,
        isPlaying = trailer.isPlaying,
        trailerAvailable = trailer.isAvailable,
        offsetX = trailerX,
        width = trailerWidth,
        glowWidth = trailerGlowWidth,
        enableFocus = enableFocus,
        onClick = { trailer.onButtonPressed() }
    )

    // Optional: Show loading indicator
    if (trailer.isExtracting) {
        Text(
            text = "Loading trailer...",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 150.dp, end = 250.dp)
        )
    }


    DetailsRewindButton(
        fontFamily = fontFamily,
        focusRequester = remember { FocusRequester() },
        pressTiming = pressTiming,
        offsetX = rewindX,
        enableFocus = enableFocus,
        onClick = { trailer.rewind() }
    )

    DetailsFastForwardButton(
        fontFamily = fontFamily,
        focusRequester = remember { FocusRequester() },
        pressTiming = pressTiming,
        offsetX = forwardX,
        enableFocus = enableFocus,
        onClick = { trailer.fastForward() }
    )
}


/** The rewind and fast-forward buttons' width. */
private val SEEK_BUTTON_WIDTH = 26.5.dp

/** The space between the three trailer controls in the peek layout. */
private val PEEK_TRAILER_GAP = 8.dp

@Composable
private fun DetailsFavoriteButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    pressTiming: DetailsPressTiming,
    offsetX: Dp = 27.75.dp,
    offsetY: Dp = 139.dp,
    enableFocus: Boolean = true,
    isFavorited: Boolean = false,
    onToggle: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = "+ Add to Favorites",
            toggledText = "- Remove from Favorites"
        ),
        isToggleable = true,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties { canFocus = enableFocus }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft ||
                            event.key == Key.DirectionRight ||
                            event.key == Key.DirectionDown)
                ) {
                    // 🔇 No sound when pressing LEFT on the leftmost button
                    if (event.key != Key.DirectionLeft) {
                        BlueHiveApplication.playHoverSound()
                    }
                }
                false // let focus system handle movement
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 146.dp,
            mainHeight = 24.dp,
            mainYOffset = 0.5.dp,
            secondWidth = 146.dp,
            secondHeight = 22.dp,
            secondYOffset = 7.dp,
            shadowHeight = 6.dp,
            glowWidth = 168.dp,
            glowHeight = 44.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared — see FavoriteButtonColors in utilities/ModularButton.kt.
        colors = FavoriteButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (146.dp - 168.dp) / 2,
            offsetY = (-4).dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = pressTiming.durationMillis,
            bounceBackDelayMillis = pressTiming.bounceBackDelayMillis
        ),
        // 🔇 Disable internal pop sound (we'll use SoundPool instead)
        externalToggled = isFavorited,
        onClick = {
            BlueHiveApplication.playClickSound()
            onToggle()
        }
    )
}

@Composable
private fun DetailsExtrasButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    pressTiming: DetailsPressTiming,
    isToggled: Boolean,                 // 🔹 NEW PARAM
    offsetX: Dp = 182.dp,
    offsetY: Dp = 139.dp,
    enableFocus: Boolean = true,
    onClick: () -> Unit = {},
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            // NOT a toggle any more — the label is driven from outside, exactly
            // like the trailer button's "Play/Pause Trailer". ModularButton only
            // consults toggledText when isToggleable is true, so setting one here
            // would be dead config; the state has to arrive through `text`.
            text = if (isToggled) "Providers" else "Extras"
        ),
        // false, so this behaves as a plain button: press animation, a brief
        // pressed flash, then back to rest. Previously true, which parked it in a
        // permanently "held down" visual state whenever providers were showing.
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties { canFocus = enableFocus }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft ||
                            event.key == Key.DirectionRight ||
                            event.key == Key.DirectionDown)
                ) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 49.5.dp,
            mainHeight = 24.dp,
            mainYOffset = 0.5.dp,
            secondWidth = 49.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 7.dp,
            shadowHeight = 6.dp,
            glowWidth = 71.6.dp,
            glowHeight = 44.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 6f,
        ),
        // Shared — see ExtrasButtonColors in utilities/ModularButton.kt.
        colors = ExtrasButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_narrow_glow,
            toggledRes = R.drawable.button_focus_narrow_nonglow,
            offsetX = (49.5.dp - 71.6.dp) / 2,
            offsetY = (-4).dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = pressTiming.durationMillis,
            bounceBackDelayMillis = pressTiming.bounceBackDelayMillis
        ),
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}

@Composable
private fun DetailsTrailerButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    pressTiming: DetailsPressTiming,
    isPlaying: Boolean = false,
    trailerAvailable: Boolean = true,
    offsetX: Dp =  256.25.dp,
    offsetY: Dp = 139.dp,
    width: Dp = 100.dp,
    /** Centred on the button. */
    glowWidth: Dp = width + 16.5.dp,
    enableFocus: Boolean = true,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = if (isPlaying) "Pause Trailer" else "Play Trailer",
            toggledText = if (isPlaying) "Play Trailer" else "Pause Trailer"
        ),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .alpha(if (trailerAvailable) 1f else 0.5f)
            .focusProperties { canFocus = enableFocus }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft ||
                            event.key == Key.DirectionRight ||
                            event.key == Key.DirectionDown)
                ) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = width,
            mainHeight = 24.dp,
            mainYOffset = 0.5.dp,
            secondWidth = width,
            secondHeight = 22.dp,
            secondYOffset = 7.dp,
            shadowHeight = 6.dp,
            glowWidth = glowWidth,
            glowHeight = 44.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 6f,
        ),
        // Shared — see TrailerControlButtonColors in utilities/ModularButton.kt.
        colors = TrailerControlButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (width - glowWidth) / 2,
            offsetY = (-4).dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = pressTiming.durationMillis,
            bounceBackDelayMillis = pressTiming.bounceBackDelayMillis
        ),
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}

@Composable
private fun DetailsRewindButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    pressTiming: DetailsPressTiming,
    offsetX: Dp = 367.25.dp,
    offsetY: Dp = 139.dp,
    enableFocus: Boolean = true,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            // ── A REAL SEEK ICON, NOT A CHEVRON ──────────────────────
            // "<" and ">" are less-than and greater-than: punctuation that
            // happens to point. They read as paging, which is not what these
            // do -- both seek the trailer by exactly 10 seconds.
            //
            // A DOUBLE TRIANGLE, not the player band's circular "10". Both
            // seek by SEEK_INCREMENT_MS (10s), so the numbered asset was
            // accurate -- but at 14dp on a button this size the digits are
            // detail nobody reads, while two triangles carry the direction on
            // their own. The band keeps the numbered version, where it is
            // large enough for the 10 to be worth printing.
            //
            // `text` still carries the label: it is unused for drawing when
            // iconRes is set, and becomes the icon's contentDescription.
            text = "Rewind 10 seconds",
            iconRes = R.drawable.ic_seek_rewind,
            iconSize = 14.dp,
        ),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties { canFocus = enableFocus }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft ||
                            event.key == Key.DirectionRight ||
                            event.key == Key.DirectionDown)
                ) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 26.5.dp,
            mainHeight = 24.dp,
            mainYOffset = 0.5.dp,
            secondWidth = 26.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 7.dp,
            shadowHeight = 6.dp,
            glowWidth = 41.dp,
            glowHeight = 44.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 6f,
        ),
        // Shared — see TrailerControlButtonColors in utilities/ModularButton.kt.
        colors = TrailerControlButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_narrow_glow,
            toggledRes = R.drawable.button_focus_narrow_nonglow,
            offsetX = (-7.5).dp,
            offsetY = (-4).dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = pressTiming.durationMillis,
            bounceBackDelayMillis = pressTiming.bounceBackDelayMillis
        ),
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}

@Composable
private fun DetailsFastForwardButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    pressTiming: DetailsPressTiming,
    offsetX: Dp = 400.25.dp,
    offsetY: Dp = 139.dp,
    enableFocus: Boolean = true,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            // ── A REAL SEEK ICON, NOT A CHEVRON ──────────────────────
            // "<" and ">" are less-than and greater-than: punctuation that
            // happens to point. They read as paging, which is not what these
            // do -- both seek the trailer by exactly 10 seconds.
            //
            // A DOUBLE TRIANGLE, not the player band's circular "10". Both
            // seek by SEEK_INCREMENT_MS (10s), so the numbered asset was
            // accurate -- but at 14dp on a button this size the digits are
            // detail nobody reads, while two triangles carry the direction on
            // their own. The band keeps the numbered version, where it is
            // large enough for the 10 to be worth printing.
            //
            // `text` still carries the label: it is unused for drawing when
            // iconRes is set, and becomes the icon's contentDescription.
            text = "Forward 10 seconds",
            iconRes = R.drawable.ic_seek_forward,
            iconSize = 14.dp,
        ),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties { canFocus = enableFocus }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft ||
                            event.key == Key.DirectionRight ||
                            event.key == Key.DirectionDown)
                ) {
                    // 🔇 No sound when pressing RIGHT on the rightmost button
                    if (event.key != Key.DirectionRight) {
                        BlueHiveApplication.playHoverSound()
                    }
                }
                false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 26.5.dp,
            mainHeight = 24.dp,
            mainYOffset = 0.5.dp,
            secondWidth = 26.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 7.dp,
            shadowHeight = 6.dp,
            glowWidth = 41.dp,
            glowHeight = 44.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 6f,
        ),
        // Shared — see TrailerControlButtonColors in utilities/ModularButton.kt.
        colors = TrailerControlButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_narrow_glow,
            toggledRes = R.drawable.button_focus_narrow_nonglow,
            offsetX = (-7.5).dp,
            offsetY = (-4).dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = pressTiming.durationMillis,
            bounceBackDelayMillis = pressTiming.bounceBackDelayMillis
        ),
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}
