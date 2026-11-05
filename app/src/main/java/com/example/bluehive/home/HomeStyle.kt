package com.example.bluehive.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluehive.R
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.ModularButtonAnimationConfig
import com.example.bluehive.utilities.ModularButtonColors
import com.example.bluehive.utilities.ModularButtonDimensions
import com.example.bluehive.utilities.ModularButtonGlowConfig
import com.example.bluehive.utilities.ModularButtonTextConfig


/*
 * The home screen's constants: where the shelf stack sits, the button palette,
 * and the tab buttons' configs, built once. Moved here from
 * HomeScreenCompose.kt in Stage H1 of the Favorites and Home redesign.
 */

// ── Shelf stack vertical position ─────────────────────────────────────────────
// Raise this value to push the "What's Popular" row further down the screen.
// The title label sits 48.dp above this line; the cards sit 40.dp above it.
internal val SHELF_STACK_Y = 340.dp  // The whole shelf area up/down on screen — change this to lower "What's Popular"

// ── Radio button shared config ────────────────────────────────────────────────
internal val RADIO_MAIN_W   = 58.7.dp
internal val RADIO_GLOW_W   = 68.8.dp

internal val passionFont = AppTypography.passionRegular


/**
 * MOVIES / TV SHOWS / ANIME (v641, the user's pick "bright periwinkle"):
 * the original flat tab -- one-colour face over its lower layer, no drop
 * shadow -- in a bright blue with a soft lilac tint (hue 232, 70%, 62%) and
 * white words. The lower layer is the face at half its lightness, so each
 * tab reads as a raised 3D button; the selected tab sits pressed in, dark,
 * its words dim, on a blue-grey layer that still shows its edge.
 * Tried the same day: split sapphire / electric / halfway / A1 (v629-v632),
 * flat dusty blue (v633-v635), violet (v636-v637), emerald (v638), bright
 * cobalt (v639), bright cornflower (v640).
 */
internal val RADIO_COLORS = ModularButtonColors(
    mainDefault          = Color(0xFF5A6CE2),
    mainFocused          = Color(0xFF8693EA),
    mainToggled          = Color(0xFF141729),
    secondDefault        = Color(0xFF182686),
    secondFocused        = Color(0xFF1D2FA5),
    secondToggled        = Color(0xFF1C1F36),
    textFocused          = Color.White,
    textUnfocused        = Color.White,
    textUnfocusedToggled = Color(0xFF5C607A),
    textToggled          = Color(0xFF5C607A),
    textToggledFocus     = Color.White,
)

internal val RADIO_DIMENSIONS = ModularButtonDimensions(
    mainWidth          = RADIO_MAIN_W,
    mainHeight         = 21.dp,
    mainYOffset        = (-2.2).dp,
    secondWidth        = RADIO_MAIN_W,
    secondHeight       = 10.dp,
    secondYOffset      = 13.dp,
    shadowHeight       = 4.dp,
    glowWidth          = RADIO_GLOW_W,
    glowHeight         = 32.5.dp,
    mainCornerRadius   = 5f,
    secondCornerRadius = 5f,
    // No drop shadow under the tabs (the user's call, v634).
    dropShadow         = false,
)

internal val RADIO_ANIMATION = ModularButtonAnimationConfig(
    pressOffset           = 2.5.dp,
    textOffsetDefault     = (-0.3).dp,
    textOffsetPressed     = 1.9.dp,
    durationMillis        = 180,
    bounceBackDelayMillis = 220
)

// ── Tab button configs: one each, built once (they were built on every composition) ──
internal val RADIO_MOVIES_TEXT = ModularButtonTextConfig(
    text = "MOVIES",
    fontSize = 11f
)
internal val RADIO_TV_SHOWS_TEXT = ModularButtonTextConfig(
    text = "TV SHOWS",
    fontSize = 11f
)
internal val RADIO_ANIME_TEXT = ModularButtonTextConfig(
    text = "ANIME",
    fontSize = 11f
)
internal val RADIO_GLOW = ModularButtonGlowConfig(
    enabled = true,
    defaultRes = R.drawable.button_focus_wide_glow,
    toggledRes = R.drawable.button_focus_wide_nonglow,
    offsetX = (RADIO_MAIN_W - RADIO_GLOW_W) / 2,
    offsetY = (-1.5).dp
)
