package com.example.bluehive.favorites

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluehive.R
import com.example.bluehive.utilities.ModularButtonAnimationConfig
import com.example.bluehive.utilities.ModularButtonColors
import com.example.bluehive.utilities.ModularButtonDimensions
import com.example.bluehive.utilities.ModularButtonGlowConfig
import com.example.bluehive.utilities.ModularButtonTextConfig

/*
 * The Favorites screen's constants: its layout, the Filter and Sort lists, and
 * the configs of its six buttons. They were locals of FavoritesScreen, rebuilt
 * on every composition; here they are built once (P4 in
 * docs/favorites-home-redesign-architecture.md). Values unchanged; moved here
 * in Stage F1.
 */

// ── Grid layout config ───────────────────────────────────────────────────────
internal const val CARDS_PER_ROW          = 7       // max cards in a single row
internal val cardHorizontalSpacing        = 8.dp    // horizontal gap between cards
internal val rowSpacing                   = 12.dp   // vertical gap between rows
internal val gridOffsetY                  = 118.dp  // top of grid, below the control bar
internal val gridHeight                   = 430.dp  // visible scroll window height
internal const val NAVIGATION_COOLDOWN_MS = 80L     // originally 120

// ── Header config ────────────────────────────────────────────────────────────
internal const val HEADER_TEXT      = "Favorite Content"
internal const val HEADER_FONT_SIZE = 29f
internal const val HEADER_OPACITY   = 0.95f
internal val headerOffsetX          = 55.dp
internal val headerOffsetY          = 25.dp

// ── Dropdown config ──────────────────────────────────────────────────────────
internal val dropdownOffsetX = 55.dp
internal val dropdownOffsetY = 70.dp
internal val dropdownSpacing = 170.dp

// ── Filter tab config ────────────────────────────────────────────────────────
internal val filterButtonOffsetX = 618.dp
internal val filterButtonOffsetY = 60.dp
private val filterButtonWidth    = 90.dp
internal val filterButtonSpacing = filterButtonWidth + 9.dp

// ── Filter / sort options ────────────────────────────────────────────────────
internal val genreOptions = listOf(
    "All Content",
    "Action", "Adventure", "Animation", "Comedy", "Crime",
    "Documentary", "Drama", "Family", "Fantasy", "History",
    "Horror", "Music", "Mystery", "Romance", "Science Fiction",
    "TV Movie", "Thriller", "War", "Western",
    "Action & Adventure", "Kids", "News", "Reality",
    "Sci-Fi & Fantasy", "Soap", "Talk", "War & Politics",
)
internal val sortOptions = listOf("Newest", "Oldest", "A–Z", "Z–A", "By Release Date")

// ── Remove Media ─────────────────────────────────────────────────────────────
internal val removeMediaText = ModularButtonTextConfig(text = "Remove Media", fontSize = 9f)
internal val removeMediaDimensions = ModularButtonDimensions(
    mainWidth          = 80.dp,
    mainHeight         = 27.dp,
    mainYOffset        = 7.3.dp,
    secondWidth        = 80.dp,
    secondHeight       = 12.dp,
    secondYOffset      = 26.dp,
    mainCornerRadius   = 7f,
    secondCornerRadius = 8f,
    shadowHeight       = 12.dp,
    glowWidth          = 80.dp + 14.2.dp,
    glowHeight         = 41.dp,
)
internal val removeMediaColors = ModularButtonColors(
    mainDefault          = Color(0xFF6D2121),
    mainToggled          = Color(0xFF280D0D),
    mainFocused          = Color(0xFFAD2626),
    secondDefault        = Color(0xFF3A1515),
    secondToggled        = Color(0xFF341B1B),
    secondFocused        = Color(0xFF783535),
    textFocused          = Color(0xFFDADADA),
    textUnfocused        = Color(0xFF848383),
    textUnfocusedToggled = Color(0xFF343434),
    textToggledFocus     = Color(0xFFDADADA),
)

// ── All Media, Movies and TV Shows ───────────────────────────────────────────
internal val allMediaText = ModularButtonTextConfig(text = "All Media", fontSize = 10f)
internal val moviesText = ModularButtonTextConfig(text = "Movies", fontSize = 10f)
internal val tvShowsText = ModularButtonTextConfig(text = "TV Shows", fontSize = 10f)
internal val filterTabDimensions = ModularButtonDimensions(
    mainWidth          = filterButtonWidth,
    mainHeight         = 27.dp,
    mainYOffset        = 6.5.dp,
    secondWidth        = filterButtonWidth,
    secondHeight       = 12.dp,
    secondYOffset      = 26.dp,
    mainCornerRadius   = 7f,
    secondCornerRadius = 8f,
    shadowHeight       = 12.dp,
    glowWidth          = filterButtonWidth + 14.2.dp,
    glowHeight         = 41.dp,
)
internal val filterTabColors = ModularButtonColors(
    mainDefault          = Color(0xFF192C6F),
    mainToggled          = Color(0xFF101628),
    mainFocused          = Color(0xFF4C63AC),
    secondDefault        = Color(0xFF151C3A),
    secondToggled        = Color(0xFF1B1C34),
    secondFocused        = Color(0xFF2E325F),
    textFocused          = Color(0xFFDADADA),
    textUnfocused        = Color(0xFF868686),
    textUnfocusedToggled = Color(0xFF343434),
    textToggledFocus     = Color(0xFFDADADA),
)

// ── Exit Remove Mode: Accept Changes and Undo Changes ────────────────────────
private val overlayButtonWidth = 200.dp
internal val acceptChangesText = ModularButtonTextConfig(text = "Accept Changes", fontSize = 10f)
internal val undoChangesText = ModularButtonTextConfig(text = "Undo Changes", fontSize = 10f)
internal val overlayButtonDimensions = ModularButtonDimensions(
    mainWidth          = overlayButtonWidth,
    mainHeight         = 30.dp,
    mainYOffset        = 7.dp,
    secondWidth        = overlayButtonWidth,
    secondHeight       = 12.dp,
    secondYOffset      = 29.dp,
    mainCornerRadius   = 8f,
    secondCornerRadius = 8f,
    shadowHeight       = 12.dp,
    // Wider than the others' +14.2: the glow picture's bottom is rounded, and
    // at the button's bottom edge it covers only ~5-95% of its width. At +14.2
    // that ended ~3 dp inside a 200 dp button; at +25 it shows ~2.3 dp past each
    // side, as on the 80 dp buttons. Its offsetX is in overlayButtonGlow.
    glowWidth          = overlayButtonWidth + 25.dp,
    glowHeight         = 44.dp,
)
/** Space between Accept and Undo; with the wider glow box it leaves the buttons where they were. */
internal val overlayButtonGap = 5.2.dp
internal val acceptChangesColors = ModularButtonColors(
    mainDefault          = Color(0xFF192C6F),
    mainFocused          = Color(0xFF2A4BAA),
    mainToggled          = Color(0xFF192C6F),
    secondDefault        = Color(0xFF151C3A),
    secondFocused        = Color(0xFF2E325F),
    secondToggled        = Color(0xFF151C3A),
    textFocused          = Color(0xFFDADADA),
    textUnfocused        = Color(0xFFAAAAAA),
    textUnfocusedToggled = Color(0xFFAAAAAA),
    textToggledFocus     = Color(0xFFDADADA),
)
internal val undoChangesColors = ModularButtonColors(
    mainDefault          = Color(0xFF6D2121),
    mainFocused          = Color(0xFFAA2A2A),
    mainToggled          = Color(0xFF6D2121),
    secondDefault        = Color(0xFF3A1515),
    secondFocused        = Color(0xFF783535),
    secondToggled        = Color(0xFF3A1515),
    textFocused          = Color(0xFFDADADA),
    textUnfocused        = Color(0xFFAAAAAA),
    textUnfocusedToggled = Color(0xFFAAAAAA),
    textToggledFocus     = Color(0xFFDADADA),
)

// ── Glow and press animation, shared by all six buttons ──────────────────────
internal val buttonGlow = ModularButtonGlowConfig(
    enabled               = true,
    defaultRes            = R.drawable.button_focus_wide_glow,
    offsetX               = (-6.85).dp,
    offsetY               = 7.dp,
    cornerRadius          = 100.dp,
    fadeOutDurationMillis = 200,
    fadeInDurationMillis  = 400,
)
/** The glow centred on the wider glow box of Accept and Undo (overlayButtonDimensions). */
internal val overlayButtonGlow = buttonGlow.copy(offsetX = (-12.5).dp)
internal val buttonAnimation = ModularButtonAnimationConfig(
    pressOffset           = 3.5.dp,
    textOffsetDefault     = 7.9.dp,
    textOffsetPressed     = 9.9.dp,
    durationMillis        = 110,
    bounceBackDelayMillis = 200,
)
