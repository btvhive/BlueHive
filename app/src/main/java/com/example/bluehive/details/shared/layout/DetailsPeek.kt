package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.RecommendedRowEntry

/**
 * The movie screen's peek layout (approved mockup, 2026-10-26): no Extras
 * button; the play buttons sit right under the overview, and the recommended
 * row is always there, peeking up from the bottom edge. Down from the bottom
 * play buttons enters the row, and the page (everything but its background)
 * slides up [PEEK_SLIDE] so the row shows in full; leaving the row slides it
 * back.
 *
 * The TV screen passes no DetailsPeekState and keeps the Extras switch.
 */
@Stable
class DetailsPeekState(
    /** Opened from history in Extras: the row takes focus as soon as it loads. */
    val startFocused: Boolean,
) {
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

    val rowTop: Dp get() = PEEK_ROW_TOP + PEEK_SOURCE_ROW_STEP * extraSourceRows
    val slide: Dp get() = PEEK_SLIDE + PEEK_SOURCE_ROW_STEP * extraSourceRows
}

/** The lowest the first row of play buttons sits: under the overview's full box (top 175 + height 108.25). */
val PEEK_PLAY_TOP: Dp = 296.dp

/** Space between the overview's last line and the play buttons. */
val PEEK_PLAY_GAP: Dp = 12.dp

/** The Recommended Media heading's offset in the peek layout, clear of the row's frame. */
val PEEK_TITLE_OFFSET_Y: Dp = (-50).dp

/** One row of play buttons, as the grid has always spaced them. */
val PEEK_SOURCE_ROW_STEP: Dp = 55.5.dp

/** RecommendedMediaSection's offset that puts the cards' tops at ~444 dp, so about 60% shows (measured on the onn box). */
val PEEK_ROW_TOP: Dp = 453.25.dp

/** The header buttons' label size in the peek layout, in Inter SemiBold. */
const val PEEK_HEADER_LABEL_SP = 11f

/** How far the page slides up while the row has focus. */
val PEEK_SLIDE: Dp = 110.dp

/** The first play-button row for an overview ending at [overviewBottom] (null: none laid out). */
fun peekPlayTop(overviewBottom: Dp?): Dp =
    if (overviewBottom == null) PEEK_PLAY_TOP else minOf(overviewBottom + PEEK_PLAY_GAP, PEEK_PLAY_TOP)

/** Play-button rows past two for [count] buttons laid out two to a row. */
fun peekExtraSourceRows(count: Int): Int = ((count + 1) / 2 - 2).coerceAtLeast(0)
