package com.example.bluehive.home

import android.util.Log
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.utilities.ModularButton


/*
 * The Movies, TV Shows and Anime tab buttons. Moved here from
 * HomeScreenCompose.kt in Stage H1 of the Favorites and Home redesign.
 */

// =================================================================================> Radio buttons
@Composable
internal fun MoviesRadioButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    selected: Boolean,
    offsetX: androidx.compose.ui.unit.Dp = 228.dp,
    offsetY: androidx.compose.ui.unit.Dp = 270.dp,
    enableFocus: Boolean = true,
    downNeighbor: FocusRequester,
    upNeighbor: FocusRequester,
    rightNeighbor: FocusRequester,
    onOpenSidebar: () -> Unit,
    onFocused: () -> Unit = {},
    // Fired when focus LANDS here from anywhere. The old sound fired from this
    // button's own key handler, so it could only speak when it already had
    // focus -- arriving from the shelf row above was silent.
    onNavSound: () -> Unit = {},
    onSelect: () -> Unit = {}
) {
    ModularButton(
        textConfig = RADIO_MOVIES_TEXT,
        isToggleable = true,
        toggled = selected,
        isFocusable = enableFocus && !selected,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        onFocusGained   = onNavSound,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties {
                canFocus = enableFocus && !selected
                down = downNeighbor
                right = rightNeighbor
                up = upNeighbor  // ✅ ADD THIS
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    BlueHiveApplication.playHoverSound()
                    onOpenSidebar()
                    true // consume
                } else {
                    false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // LEFT and RIGHT land on another radio button, which
                        // announces its own arrival, so speaking here too would
                        // play the same sample twice.
                        Key.DirectionLeft,
                        Key.DirectionRight -> false

                        // UP AND DOWN BOTH LEAVE THE ROW, and neither place they
                        // land says anything: the trailer frame above has no
                        // arrival sound, and a title card in the shelf below
                        // plays from the card you LEAVE, never the one you reach.
                        // So nothing downstream will speak for either move and
                        // both have to be made here.
                        //
                        // UP was missing. The comment this replaces claimed
                        // "everything except DOWN lands on something that
                        // announces its own arrival" -- true of LEFT and RIGHT,
                        // never of UP, so moving onto the trailer frame was
                        // silent while every other move on this screen spoke.
                        Key.DirectionUp -> {
                            BlueHiveApplication.playNavClickSound()
                            false
                        }

                        // DOWN IS THE EXCEPTION. It leaves for the shelf row,
                        // and a title card makes no sound when focus lands on
                        // it -- that row plays from the card you LEAVE, never
                        // the one you reach. So nothing downstream will speak
                        // for this move and it has to be made here.
                        Key.DirectionDown -> {
                            BlueHiveApplication.playNavClickSound()
                            false
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
        dimensions      = RADIO_DIMENSIONS,
        colors          = RADIO_COLORS,
        glowConfig = RADIO_GLOW,
        animationConfig = RADIO_ANIMATION,
        playClickSound = false,
        onClick = {
            if (!selected) {
                BlueHiveApplication.playClickSound()
                onSelect()
            }
            Log.d("RADIO", "Movies button click")
        }
    )
}



@Composable
internal fun TvShowsRadioButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    selected: Boolean,
    offsetX: androidx.compose.ui.unit.Dp = 294.dp,
    offsetY: androidx.compose.ui.unit.Dp = 270.dp,
    enableFocus: Boolean = true,
    downNeighbor: FocusRequester,
    upNeighbor: FocusRequester,
    leftNeighbor: FocusRequester,
    rightNeighbor: FocusRequester,
    onOpenSidebar: () -> Unit,
    shouldOpenSidebar: Boolean = false,
    onFocused: () -> Unit = {},
    // Fired when focus LANDS here from anywhere. The old sound fired from this
    // button's own key handler, so it could only speak when it already had
    // focus -- arriving from the shelf row above was silent.
    onNavSound: () -> Unit = {},
    onSelect: () -> Unit = {}
) {
    ModularButton(
        textConfig = RADIO_TV_SHOWS_TEXT,
        isToggleable = true,
        toggled = selected,
        isFocusable = enableFocus && !selected,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        onFocusGained   = onNavSound,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties {
                canFocus = enableFocus && !selected
                up = upNeighbor
                right = rightNeighbor
                left = leftNeighbor
                down =
                    downNeighbor  // 2B) when DPAD DOWN happens while TV SHOWS is focused, Compose’s focus engine doesn’t guess. It already knows:
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    if (shouldOpenSidebar) {  // ✅ ONLY consume if sidebar should open
                        BlueHiveApplication.playHoverSound()
                        onOpenSidebar()
                        true
                    } else {
                        false  // ✅ Don't consume - allow normal focus navigation
                    }
                } else false
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // LEFT and RIGHT land on another radio button, which
                        // announces its own arrival, so speaking here too would
                        // play the same sample twice.
                        Key.DirectionLeft,
                        Key.DirectionRight -> false

                        // UP AND DOWN BOTH LEAVE THE ROW, and neither place they
                        // land says anything: the trailer frame above has no
                        // arrival sound, and a title card in the shelf below
                        // plays from the card you LEAVE, never the one you reach.
                        // So nothing downstream will speak for either move and
                        // both have to be made here.
                        //
                        // UP was missing. The comment this replaces claimed
                        // "everything except DOWN lands on something that
                        // announces its own arrival" -- true of LEFT and RIGHT,
                        // never of UP, so moving onto the trailer frame was
                        // silent while every other move on this screen spoke.
                        Key.DirectionUp -> {
                            BlueHiveApplication.playNavClickSound()
                            false
                        }

                        // DOWN IS THE EXCEPTION. It leaves for the shelf row,
                        // and a title card makes no sound when focus lands on
                        // it -- that row plays from the card you LEAVE, never
                        // the one you reach. So nothing downstream will speak
                        // for this move and it has to be made here.
                        Key.DirectionDown -> {
                            BlueHiveApplication.playNavClickSound()
                            false  // 2B) your onKeyEvent returns false for DirectionDown, which is good — it doesn’t consume the event, so the focus engine is allowed to move focus.
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
        dimensions      = RADIO_DIMENSIONS,
        colors          = RADIO_COLORS,
        glowConfig = RADIO_GLOW,
        animationConfig = RADIO_ANIMATION,
        playClickSound = false, // ✅ avoid double pop sound
        onClick = {
            if (!selected) {
                BlueHiveApplication.playClickSound()
                onSelect()
            }
            Log.d("RADIO", "Tv Shows button click")
        }
    )
}


@Composable
internal fun AnimeRadioButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    selected: Boolean,
    offsetX: androidx.compose.ui.unit.Dp = 360.dp, // 321.4
    offsetY: androidx.compose.ui.unit.Dp = 270.dp,
    enableFocus: Boolean = true,
    downNeighbor: FocusRequester,
    upNeighbor: FocusRequester,
    leftNeighbor: FocusRequester,
    rightNeighbor: FocusRequester,
    onFocused: () -> Unit = {},
    // Fired when focus LANDS here from anywhere. The old sound fired from this
    // button's own key handler, so it could only speak when it already had
    // focus -- arriving from the shelf row above was silent.
    onNavSound: () -> Unit = {},
    onSelect: () -> Unit = {}
) {
    ModularButton(
        textConfig = RADIO_ANIME_TEXT,
        isToggleable = true,
        toggled = selected,
        isFocusable = enableFocus && !selected,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        onFocusGained   = onNavSound,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .focusProperties {
                canFocus = enableFocus && !selected
                up = upNeighbor
                down = downNeighbor
                left = leftNeighbor
                right = rightNeighbor
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // LEFT and RIGHT land on another radio button, which
                        // announces its own arrival, so speaking here too would
                        // play the same sample twice.
                        Key.DirectionLeft,
                        Key.DirectionRight -> false

                        // UP AND DOWN BOTH LEAVE THE ROW, and neither place they
                        // land says anything: the trailer frame above has no
                        // arrival sound, and a title card in the shelf below
                        // plays from the card you LEAVE, never the one you reach.
                        // So nothing downstream will speak for either move and
                        // both have to be made here.
                        //
                        // UP was missing. The comment this replaces claimed
                        // "everything except DOWN lands on something that
                        // announces its own arrival" -- true of LEFT and RIGHT,
                        // never of UP, so moving onto the trailer frame was
                        // silent while every other move on this screen spoke.
                        Key.DirectionUp -> {
                            BlueHiveApplication.playNavClickSound()
                            false
                        }

                        // DOWN IS THE EXCEPTION. It leaves for the shelf row,
                        // and a title card makes no sound when focus lands on
                        // it -- that row plays from the card you LEAVE, never
                        // the one you reach. So nothing downstream will speak
                        // for this move and it has to be made here.
                        Key.DirectionDown -> {
                            BlueHiveApplication.playNavClickSound()
                            false
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
        dimensions      = RADIO_DIMENSIONS,
        colors          = RADIO_COLORS,
        glowConfig = RADIO_GLOW,
        animationConfig = RADIO_ANIMATION,
        playClickSound = false, // ✅ avoid double pop sound
        onClick = {
            if (!selected) {
                BlueHiveApplication.playClickSound()
                onSelect()
            }
            Log.d("RADIO", "ANIME button click")
        }
    )
}
// =================================================================================> Radio buttons
