package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.utilities.RecommendedRowEntry

/**
 * The details screens' peek layout (the movie page's approved mockup, 2026-10-26): no Extras
 * button; the play buttons sit right under the overview, and the recommended
 * row is always there, peeking up from the bottom edge. Down from the bottom
 * play buttons enters the row, and the page (everything but its background)
 * slides up [PEEK_SLIDE] so the row shows in full; leaving the row slides it
 * back.
 *
 * The TV screen uses it too since 2026-10-27, with its row lower: the season
 * cap ends further down than the movie's play buttons ([baseRowTop],
 * [baseSlide]).
 */
@Stable
class DetailsPeekState(
    /** Opened from history in Extras: the row takes focus as soon as it loads. */
    startFocused: Boolean,
    /** The row's top with two rows of play buttons or fewer. */
    private val baseRowTop: Dp = PEEK_ROW_TOP,
    /** The slide with two rows of play buttons or fewer: the row ends in the same place either way. */
    private val baseSlide: Dp = PEEK_SLIDE,
) {
    /**
     * The row takes focus as soon as it loads. Turned off for good when the
     * titles never came and the header took focus instead (PeekRowStart),
     * so titles arriving late cannot pull focus out from under the viewer.
     */
    var startFocused by mutableStateOf(startFocused)
        private set

    fun giveUpStartFocus() {
        startFocused = false
    }

    /** The row has focus, so the page is slid up. */
    var isScrolled by mutableStateOf(false)

    /** Where the overview's last line ends; null until it is laid out, or with no overview. */
    var overviewBottom by mutableStateOf<Dp?>(null)

    /** The first row of play buttons: [PEEK_PLAY_GAP] under the overview's text, never lower than [PEEK_PLAY_TOP]. */
    val playTop: Dp get() = peekPlayTop(overviewBottom)

    /** Play-button rows past two; each pushes the row, and the slide, one row lower. */
    var extraSourceRows by mutableIntStateOf(0)

    /** Moves focus into the row from the play buttons. */
    val rowEntry = RecommendedRowEntry()

    val rowTop: Dp get() = baseRowTop + PEEK_SOURCE_ROW_STEP * extraSourceRows
    val slide: Dp get() = baseSlide + PEEK_SOURCE_ROW_STEP * extraSourceRows
}

/**
 * The page's DetailsPeekState, made again only if [startFocused] changes.
 * Both screens call this one function, so R8 keeps it out of their method
 * budgets (checkMethodSizes).
 */
@Composable
fun rememberDetailsPeek(
    startFocused: Boolean,
    baseRowTop: Dp = PEEK_ROW_TOP,
    baseSlide: Dp = PEEK_SLIDE,
): DetailsPeekState = remember(startFocused) { DetailsPeekState(startFocused, baseRowTop, baseSlide) }

/** The lowest the first row of play buttons sits: under the overview's full box (top 175 + height 108.25). */
val PEEK_PLAY_TOP: Dp = 296.dp

/** Space between the overview's last line and the play buttons. */
val PEEK_PLAY_GAP: Dp = 12.dp

/**
 * The Recommended Media heading's offset in the peek layout, clear of the
 * row's frame: 3 dp lower than v417-v470's -50 (the user's call, 2026-10-27).
 */
val PEEK_TITLE_OFFSET_Y: Dp = (-47).dp

/** The heading's size in the peek layout: 16 sp since v471 (18 before, the user's call). */
val PEEK_TITLE_FONT_SIZE: TextUnit = 16.sp

/**
 * The right column of play buttons (162 dp wide): its right edge on Play
 * Trailer's, 292.75 + 90 = 382.75 dp (the user's call, 2026-10-26; it was 228).
 */
val PEEK_RIGHT_COLUMN_X: Dp = 220.75.dp

/** One row of play buttons, as the grid has always spaced them. */
val PEEK_SOURCE_ROW_STEP: Dp = 55.5.dp

/** RecommendedMediaSection's offset that puts the cards' tops at ~444 dp, so about 60% shows (measured on the onn box). */
val PEEK_ROW_TOP: Dp = 453.25.dp

/** How far the page slides up while the row has focus. */
val PEEK_SLIDE: Dp = 110.dp

/** The first play-button row for an overview ending at [overviewBottom] (null: none laid out). */
fun peekPlayTop(overviewBottom: Dp?): Dp =
    if (overviewBottom == null) PEEK_PLAY_TOP else minOf(overviewBottom + PEEK_PLAY_GAP, PEEK_PLAY_TOP)

/** Play-button rows past two for [count] buttons laid out two to a row. */
fun peekExtraSourceRows(count: Int): Int = ((count + 1) / 2 - 2).coerceAtLeast(0)
