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

// ── Shared button palette — used by all radio buttons + top buttons ───────────
internal val BTN_MAIN_DEFAULT          = Color(0xFF2644A6)
internal val BTN_MAIN_FOCUSED          = Color(0xFF0D00FF)
internal val BTN_MAIN_TOGGLED          = Color(0xFF101628)
internal val BTN_SECOND_DEFAULT        = Color(0xFF222340)
internal val BTN_SECOND_FOCUSED        = Color(0xFF372B67)
internal val BTN_SECOND_TOGGLED        = Color(0xFF1B1C34)
internal val BTN_SHADOW                = Color(0x50000000)
internal val BTN_TEXT_FOCUSED          = Color.White
internal val BTN_TEXT_UNFOCUSED        = Color(0xFFD2D0D0)
internal val BTN_TEXT_UNFOCUSED_TOGGLED = Color(0xFF343434)

// ── Radio button shared config ────────────────────────────────────────────────
internal val RADIO_MAIN_W   = 58.7.dp
internal val RADIO_GLOW_W   = 68.8.dp

internal val passionFont = AppTypography.passionRegular


internal val RADIO_COLORS = ModularButtonColors(
    mainDefault          = BTN_MAIN_DEFAULT,
    mainFocused          = BTN_MAIN_FOCUSED,
    mainToggled          = BTN_MAIN_TOGGLED,
    secondDefault        = BTN_SECOND_DEFAULT,
    secondFocused        = BTN_SECOND_FOCUSED,
    secondToggled        = BTN_SECOND_TOGGLED,
    shadowColor          = BTN_SHADOW,
    textFocused          = BTN_TEXT_FOCUSED,
    textUnfocused        = BTN_TEXT_UNFOCUSED,
    textUnfocusedToggled = BTN_TEXT_UNFOCUSED_TOGGLED,
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
