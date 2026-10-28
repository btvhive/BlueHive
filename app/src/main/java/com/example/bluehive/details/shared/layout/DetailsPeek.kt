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
 * The TV screen uses it too since 2026-10-27, and since v476 both pages'
 * rows peek from the same place ([PEEK_ROW_TOP]).
 */
@Stable
class DetailsPeekState(
    /** Opened from history in Extras: the row takes focus as soon as it loads. */
    startFocused: Boolean,
    /** How much of the row's cards peeks on this page -- see PeekRule. */
    val rule: PeekRule = PeekRule.Standard,
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

    /** The overview's line count: null until it is laid out, 0 with no overview. */
    var overviewLines by mutableStateOf<Int?>(null)

    /** The first row of play buttons: [PEEK_PLAY_GAP] under the overview's text, never lower than [PEEK_PLAY_TOP]. */
    val playTop: Dp get() = peekPlayTop(overviewBottom)

    /** Play-button rows past two; each pushes the row, and the slide, one row lower. */
    var extraSourceRows by mutableIntStateOf(0)

    /** Moves focus into the row from the play buttons. */
    val rowEntry = RecommendedRowEntry()

    val rowTop: Dp get() = peekRowTop(rule.percent(overviewLines)) + PEEK_SOURCE_ROW_STEP * extraSourceRows

    /** How far the page slides: the slid-up row always lands at [PEEK_SLID_ROW_TOP]. */
    val slide: Dp get() = rowTop - PEEK_SLID_ROW_TOP
}

/**
 * How much of the recommended row's cards peeks up from the bottom edge, per
 * page (the user's calls, 2026-10-28). 40% is the default.
 */
enum class PeekRule {
    /** 40% always: the TV page. */
    Standard,

    /**
     * 40%, or 50% when the overview has [SHORT_OVERVIEW_LINES] lines or fewer:
     * the movie page, whose play buttons rise under a short overview and left
     * an empty band above the row (Buddy, 2026-10-28).
     */
    LiftUnderShortOverview,

    /** 60% always: anime movie pages, which looked flat at 40%. */
    Anime;

    /** The share for an overview of [overviewLines] lines (null: not laid out yet). */
    fun percent(overviewLines: Int?): Int = when (this) {
        Standard -> PEEK_PERCENT_DEFAULT
        LiftUnderShortOverview ->
            if (overviewLines != null && overviewLines <= SHORT_OVERVIEW_LINES) PEEK_PERCENT_SHORT_OVERVIEW
            else PEEK_PERCENT_DEFAULT
        Anime -> PEEK_PERCENT_ANIME
    }
}

const val PEEK_PERCENT_DEFAULT = 40
const val PEEK_PERCENT_SHORT_OVERVIEW = 50
const val PEEK_PERCENT_ANIME = 60

/** An overview this many lines or shorter is short (PeekRule.LiftUnderShortOverview). */
const val SHORT_OVERVIEW_LINES = 3

/**
 * The page's DetailsPeekState, made again only if [startFocused] or [rule]
 * changes. Both screens call this one function, so R8 keeps it out of their
 * method budgets (checkMethodSizes).
 */
@Composable
fun rememberDetailsPeek(startFocused: Boolean, rule: PeekRule = PeekRule.Standard): DetailsPeekState =
    remember(startFocused, rule) { DetailsPeekState(startFocused, rule) }

/** The lowest the first row of play buttons sits: under the overview's full box (top 175 + height 108.25). */
val PEEK_PLAY_TOP: Dp = 296.dp

/** Space between the overview's last line and the play buttons. */
val PEEK_PLAY_GAP: Dp = 12.dp

/**
 * The Recommended Media heading's offset in the peek layout, clear of the
 * row's frame: 5 dp lower than v417-v470's -50 (the user's calls,
 * 2026-10-27: 3 dp in v471, 2 more in v474).
 */
val PEEK_TITLE_OFFSET_Y: Dp = (-45).dp

/**
 * The heading's size in the peek layout, the user's calls: 18 sp until v470,
 * 16 in v471, 14 since v479 (in Asap Condensed Bold since v478).
 */
val PEEK_TITLE_FONT_SIZE: TextUnit = 14.sp

/**
 * The right column of play buttons (162 dp wide): its right edge on Play
 * Trailer's, 292.75 + 90 = 382.75 dp (the user's call, 2026-10-26; it was 228).
 */
val PEEK_RIGHT_COLUMN_X: Dp = 220.75.dp

/** One row of play buttons, as the grid has always spaced them. */
val PEEK_SOURCE_ROW_STEP: Dp = 55.5.dp

/**
 * The row's cards are 160 dp tall and their tops sit 9.25 dp under
 * RecommendedMediaSection's offset, so an offset of 549.25 - 1.6 x percent
 * shows that percent of them above the 540 dp screen edge (measured on the
 * onn box: 453.25 showed ~60%, 485.25 ~40%).
 */
private val PEEK_CARD_HEIGHT: Dp = 160.dp
private val PEEK_ROW_TOP_HIDDEN: Dp = 549.25.dp

/** The row's offset that shows [percent] of its cards. */
fun peekRowTop(percent: Int): Dp = PEEK_ROW_TOP_HIDDEN - PEEK_CARD_HEIGHT * percent / 100

/**
 * The default row offset, ~40% showing (485.25 dp). The TV page needs it
 * this low -- its row's heading has to clear the season cap (ends 421.25) and
 * the episode titles.
 */
val PEEK_ROW_TOP: Dp = peekRowTop(PEEK_PERCENT_DEFAULT)

/**
 * Where the row's offset lands when the page slides up for it: 343.25 dp,
 * where the movie page's always landed (453.25 - 110), whatever its peek.
 */
val PEEK_SLID_ROW_TOP: Dp = 343.25.dp

/** The slide at the default peek: 142 dp. */
val PEEK_SLIDE: Dp = PEEK_ROW_TOP - PEEK_SLID_ROW_TOP

/** The first play-button row for an overview ending at [overviewBottom] (null: none laid out). */
fun peekPlayTop(overviewBottom: Dp?): Dp =
    if (overviewBottom == null) PEEK_PLAY_TOP else minOf(overviewBottom + PEEK_PLAY_GAP, PEEK_PLAY_TOP)

/** Play-button rows past two for [count] buttons laid out two to a row. */
fun peekExtraSourceRows(count: Int): Int = ((count + 1) / 2 - 2).coerceAtLeast(0)
