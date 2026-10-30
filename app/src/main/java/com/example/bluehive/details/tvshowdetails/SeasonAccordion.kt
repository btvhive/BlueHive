package com.example.bluehive.details.tvshowdetails

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.utilities.AppTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * The season list: the season picker inside extended_mini_left_upper_body
 * (v502, replacing the v488-v501 card wheel; the user's advisor found the
 * wheel too artistic for older viewers).
 *
 * The user's pick from the 2026-10-29 mockups: "raised gold row" with side
 * bar 5 (a gold arrow) and more-seasons cue 4 (carets on the arrow's track).
 * Four rows show at a time, each a recessed slot reading SEASON and its
 * number; the season under the arrow is a raised gold row. Up / Down move one
 * season and STOP at the ends (no wrap): Up from Season 1 leaves for the
 * header. When there are more seasons above or below, a gold caret shows at
 * that end of the arrow's track and the list fades out there; at an end the
 * caret goes grey. OK loads the season (the row presses in), Right goes to the
 * episodes. While browsing, the season still on show has a gold number.
 *
 * The geometry is the cap image's: drawn at 4 px per dp (537 x 489 px ->
 * 134.25 x 122.25 dp), its recessed panel runs x 51-467 and y 40-447 px. The
 * list's sizes are the mockup's at its 1.615 px per dp (168 px for the
 * panel's 104 dp).
 */

/** The cap's left edge: on Add to Favorites' (DetailsHeaderButtons, 27.75 dp). */
internal val SEASON_CAP_X: Dp = 27.75.dp
internal val SEASON_CAP_Y: Dp = 299.dp
internal val SEASON_CAP_WIDTH: Dp = 134.25.dp
internal val SEASON_CAP_HEIGHT: Dp = 122.25.dp

/** The recessed panel inside the cap, where the list draws. */
private val PANEL_X = SEASON_CAP_X + 12.75.dp
private val PANEL_Y = SEASON_CAP_Y + 10.dp
private val PANEL_WIDTH = 104.dp
private val PANEL_HEIGHT = 101.75.dp

/** The panel's own black (measured from the cap image): the fades run into it. */
private val PANEL_BLACK = Color(0xFF070707)

/** Rows on show at once. */
internal const val LIST_VISIBLE_ROWS = 4

/** The rows' window: inset top and bottom, and from the panel's sides (the left leaves the arrow its track). */
private val LIST_INSET_V = 5.dp
private val LIST_LEFT = 16.dp
private val LIST_RIGHT = 5.dp
private val ROW_STEP = (PANEL_HEIGHT - LIST_INSET_V * 2) / LIST_VISIBLE_ROWS
private val ROW_GAP = 2.5.dp
private val ROW_CORNER = 4.3.dp
private val ROW_TEXT_PAD = 7.4.dp

/** The recessed slots: a dark gradient, shaded inside its top edge, lit along its bottom. */
private val SLOT_TOP = Color(0xFF0C0C0F)
private val SLOT_BOTTOM = Color(0xFF111115)
private val SLOT_SHADE = Color(0xD9000000)
private val SLOT_SHADE_DEPTH = 3.dp
private val SLOT_LIGHT = Color(0x0DFFFFFF)

/** The raised gold row: a gold gradient on a darker lip, lit along its top. */
private val ROW_GOLD = listOf(Color(0xFFF0BE44), Color(0xFFD39A17), Color(0xFFBF880F))
private val ROW_GOLD_STOPS = floatArrayOf(0f, 0.55f, 1f)
private val ROW_LIP = Color(0xFF6F4F0B)
private val ROW_LIP_DEPTH = 2.5.dp
private val ROW_SHINE = Color(0x73FFFFFF)
private val ROW_SHINE_DEPTH = 0.6.dp

/** Focus elsewhere: the gold row and arrow dim this far toward black, so focus shows. */
private const val UNFOCUSED_DIM = 0.28f

private val GOLD = Color(0xFFD19814)
private val GOLD_RESTING = Color(0xFFA87A10)
private val CARET_OFF = Color(0xFF2A2A30)

/** Text: dark brown on the gold row, grey in a slot. */
private val INK_ON = Color(0xFF2A1C00)
private val LABEL_ON = Color(0xFF4D3500)
private val INK_OFF = Color(0xFF8D8D96)
private const val LABEL_SP = 6.2f
private const val NUMBER_SP = 12.7f

/** The arrow pointing at the gold row, and the carets at its track's ends (centred on x [CARET_X]). */
private val ARROW_X = 4.3.dp
private val ARROW_WIDTH = 6.8.dp
private val ARROW_HEIGHT = 8.7.dp
private val CARET_X = 7.7.dp
private val CARET_WIDTH = 4.7.dp
private val CARET_HEIGHT = 2.7.dp
private val CARET_UP_TOP = 3.6.dp
private val CARET_DOWN_TOP = 94.1.dp

/** The fades over a cut-off end of the list. */
private val FADE_DEPTH = 9.9.dp

/** One move: the gold row slides and the list scrolls together (the mockup's .32 s cubic-bezier(.2, .8, .2, 1)). */
private const val LIST_MOVE_MS = 320
private val ListEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * The least time between two moves: one slide. Up / Down pressed or held
 * sooner are swallowed with no sound -- the user asked that the picker could
 * not be spammed (2026-10-29).
 */
internal const val SEASON_TURN_GAP_MS = LIST_MOVE_MS.toLong()

/** A move at [now] is allowed when the last one, at [last], has landed. Pure, so it is tested. */
internal fun seasonTurnAllowed(now: Long, last: Long): Boolean = now - last >= SEASON_TURN_GAP_MS

/** OK presses the gold row in: down [PRESS_SINK] in [PRESS_DOWN_MS], then springs back in [PRESS_UP_MS]. */
private val PRESS_SINK = 2.dp
private const val PRESS_DOWN_MS = 80
private const val PRESS_UP_MS = 300
private val PressSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** The season [delta] rows from [shown], or null past either end: the list stops there. Pure, so it is tested. */
internal fun seasonListStep(shown: Int, delta: Int, total: Int): Int? = (shown + delta).takeIf { it in 1..total }

/**
 * How far the list is scrolled, in rows, with [shown] under the arrow: kept
 * in the middle of the window where it can be, never past either end, and a
 * list shorter than the window centred in it (negative). Pure, so it is tested.
 */
internal fun seasonListScroll(shown: Int, total: Int): Float =
    if (total <= LIST_VISIBLE_ROWS) {
        -(LIST_VISIBLE_ROWS - total) / 2f
    } else {
        (shown - 1 - (LIST_VISIBLE_ROWS - 1) / 2f).coerceIn(0f, (total - LIST_VISIBLE_ROWS).toFloat())
    }

/** How far the list is from its last scroll, in rows: 0 at the bottom. */
private fun rowsBelow(scroll: Float, total: Int): Float = total - LIST_VISIBLE_ROWS - scroll

/**
 * Focus the picker, waiting for it to be on screen: on the Continue Watching
 * path the episode tile is focused under load, before the season check has
 * let the picker compose, and a single requestFocus would no-op (Back would
 * seem dead) -- the reason the old column had focusSeasonReliably.
 */
internal suspend fun TvFocusMap.focusSeasonPicker() {
    repeat(40) {
        try { seasonPicker.requestFocus() } catch (_: IllegalStateException) {}
        awaitFrame()
        if (seasonPickerFocused) return
    }
}

/** The list's text, measured once per show: SEASON, and every season's number. */
private class SeasonListText(val label: TextLayoutResult, val numbers: List<TextLayoutResult>)

/** Season 1 through [listedSeasons]: the newest with an aired episode (SeasonAvailability). */
@Composable
internal fun BoxScope.SeasonAccordion(
    listedSeasons: Int?,
    numberOfSeasons: Int,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    passionFont: FontFamily,
    coroutineScope: CoroutineScope,
) {
    // Waits for the season check, as the column did, so a season with
    // nothing aired yet never shows.
    val total = listedSeasons ?: 0
    if (numberOfSeasons <= 0 || total <= 0) return

    val loaded = episodesState.selectedSeason.coerceIn(1, total)
    var browsing by remember { mutableIntStateOf(loaded) }
    // A load from anywhere (OK here, the season check, the first load) moves the gold row.
    LaunchedEffect(loaded) { browsing = loaded }
    val shown = browsing.coerceIn(1, total)
    val focused = focusMap.seasonPickerFocused
    val motionScope = rememberCoroutineScope()
    // When the last move was made (uptimeMillis); plain, not state, so a press
    // recomposes nothing until it moves -- see SEASON_TURN_GAP_MS.
    val lastTurn = remember { longArrayOf(Long.MIN_VALUE / 2) }
    // Where the gold row is and how far the list is scrolled, in rows; and the
    // row's press on OK (0 to 1). All three are read only in drawing.
    val highlight = remember(total) { Animatable(shown - 1f) }
    val scroll = remember(total) { Animatable(seasonListScroll(shown, total)) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(shown, total) {
        launch { highlight.animateTo(shown - 1f, tween(LIST_MOVE_MS, easing = ListEase)) }
        launch { scroll.animateTo(seasonListScroll(shown, total), tween(LIST_MOVE_MS, easing = ListEase)) }
    }
    val text = rememberSeasonListText(total, passionFont)

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = PANEL_X, y = PANEL_Y)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .semantics { contentDescription = "Season $shown of $total" }
            .focusRequester(focusMap.seasonPicker)
            .onFocusChanged { state ->
                focusMap.seasonPickerFocused = state.isFocused
                // Leaving without OK drops the browse: the gold row goes back to the season on show.
                if (!state.isFocused) browsing = episodesState.selectedSeason.coerceIn(1, total)
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        val next = seasonListStep(shown, -1, total)
                        if (next == null) {
                            // At Season 1 the list stops: Up leaves for the header.
                            BlueHiveApplication.playHoverSound()
                            false
                        } else {
                            val now = SystemClock.uptimeMillis()
                            if (seasonTurnAllowed(now, lastTurn[0])) {
                                lastTurn[0] = now
                                browsing = next
                                BlueHiveApplication.playCardFlip()
                            }
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        // At the last season the list stops, and Down stays here.
                        val next = seasonListStep(shown, 1, total)
                        val now = SystemClock.uptimeMillis()
                        if (next != null && seasonTurnAllowed(now, lastTurn[0])) {
                            lastTurn[0] = now
                            browsing = next
                            BlueHiveApplication.playCardFlip()
                        }
                        true
                    }
                    Key.DirectionLeft -> true
                    Key.DirectionRight -> {
                        if (episodesState.episodes.isNotEmpty() && episodesState.focusRequesters.isNotEmpty()) {
                            BlueHiveApplication.playHoverSound()
                            val targetIndex = episodesState.lastFocusedEpisodeIndex
                                .coerceIn(0, episodesState.focusRequesters.lastIndex)
                            coroutineScope.launch {
                                episodesState.rowState.scrollToItem(targetIndex)
                                episodesState.focusRequesters
                                    .getOrNull(targetIndex)
                                    ?.requestFocus()
                                    ?: episodesState.focusRequesters.first().requestFocus()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        BlueHiveApplication.playClickSound()
                        motionScope.launch {
                            press.animateTo(1f, tween(PRESS_DOWN_MS))
                            press.animateTo(0f, tween(PRESS_UP_MS, easing = PressSpring))
                        }
                        if (episodesState.selectedSeason != shown) {
                            episodesState.selectedSeason = shown
                            episodesState.loadSeasonEpisodes(shown)
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .drawBehind {
                drawSeasonList(
                    text = text,
                    total = total,
                    loaded = loaded,
                    browsing = shown,
                    highlight = highlight.value,
                    scroll = scroll.value,
                    press = press.value,
                    focused = focused,
                )
            }
    )
}

@Composable
private fun rememberSeasonListText(total: Int, passionFont: FontFamily): SeasonListText {
    val measurer = rememberTextMeasurer()
    return remember(measurer, total, passionFont) {
        val label = TextStyle(
            fontFamily = AppTypography.asapCondensedBold,
            fontSize = LABEL_SP.sp,
            letterSpacing = 1.1.sp,
            platformStyle = NoPadding,
        )
        val number = TextStyle(fontFamily = passionFont, fontSize = NUMBER_SP.sp, platformStyle = NoPadding)
        SeasonListText(
            label = measurer.measure("SEASON", label),
            numbers = (1..total).map { measurer.measure("$it", number) },
        )
    }
}

/**
 * The whole list, drawn from the animated [highlight] and [scroll] (rows) and
 * [press], so a move redraws and recomposes nothing.
 */
private fun DrawScope.drawSeasonList(
    text: SeasonListText,
    total: Int,
    loaded: Int,
    browsing: Int,
    highlight: Float,
    scroll: Float,
    press: Float,
    focused: Boolean,
) {
    val step = ROW_STEP.toPx()
    val rowHeight = step - ROW_GAP.toPx()
    val left = LIST_LEFT.toPx()
    val right = size.width - LIST_RIGHT.toPx()
    val top = LIST_INSET_V.toPx()
    val bottom = size.height - LIST_INSET_V.toPx()
    val sink = PRESS_SINK.toPx() * press
    val goldTop = top + (highlight - scroll) * step

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        for (season in 1..total) {
            val y = top + (season - 1 - scroll) * step
            if (y > bottom || y + rowHeight < top) continue
            drawSlot(left, y, right - left, rowHeight)
        }
        drawGoldRow(left, goldTop, right - left, rowHeight, sink, focused)
        for (season in 1..total) {
            val y = top + (season - 1 - scroll) * step
            if (y > bottom || y + rowHeight < top) continue
            // How far the gold row is over this one: its text turns dark as it arrives.
            val on = (1f - abs(highlight - (season - 1))).coerceIn(0f, 1f)
            val numberOff = if (season == loaded && season != browsing) GOLD else INK_OFF
            val centre = y + rowHeight / 2 + sink * on
            val label = text.label
            drawText(
                label,
                color = lerp(INK_OFF, LABEL_ON, on),
                topLeft = Offset(left + ROW_TEXT_PAD.toPx(), centre - label.size.height / 2f),
            )
            val number = text.numbers[season - 1]
            drawText(
                number,
                color = lerp(numberOff, INK_ON, on),
                topLeft = Offset(right - ROW_TEXT_PAD.toPx() - number.size.width, centre - number.size.height / 2f),
            )
        }
    }
    drawListFades(left, top, right, bottom, scroll, total)
    if (total > 1) drawArrow(goldTop + rowHeight / 2 + sink, if (focused) GOLD else GOLD_RESTING)
    // The carets: gold while there are more seasons that way, grey at an end.
    drawCaret(CARET_UP_TOP.toPx(), up = true, color = lerp(CARET_OFF, GOLD, scroll.coerceIn(0f, 1f)))
    drawCaret(CARET_DOWN_TOP.toPx(), up = false, color = lerp(CARET_OFF, GOLD, rowsBelow(scroll, total).coerceIn(0f, 1f)))
}

private fun DrawScope.drawSlot(x: Float, y: Float, width: Float, height: Float) {
    val radius = CornerRadius(ROW_CORNER.toPx())
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(SLOT_TOP, SLOT_BOTTOM), startY = y, endY = y + height),
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = radius,
    )
    // Shaded inside the top edge, as if sunk into the panel.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(SLOT_SHADE, Color.Transparent), startY = y, endY = y + SLOT_SHADE_DEPTH.toPx()),
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = radius,
    )
    val light = 0.6.dp.toPx()
    drawRect(SLOT_LIGHT, topLeft = Offset(x + radius.x, y + height - light), size = Size(width - radius.x * 2, light))
}

/** The gold row at [y], pressed [sink] px into its lip. */
private fun DrawScope.drawGoldRow(x: Float, y: Float, width: Float, height: Float, sink: Float, focused: Boolean) {
    val radius = CornerRadius(ROW_CORNER.toPx())
    val dim = if (focused) 0f else UNFOCUSED_DIM
    // The lip stays put; the face sinks onto it.
    drawRoundRect(
        color = lerp(ROW_LIP, Color.Black, dim),
        topLeft = Offset(x, y + ROW_LIP_DEPTH.toPx()),
        size = Size(width, height),
        cornerRadius = radius,
    )
    val faceY = y + sink
    drawRoundRect(
        color = lerp(ROW_SHINE, Color.Black, dim),
        topLeft = Offset(x, faceY),
        size = Size(width, height),
        cornerRadius = radius,
    )
    val shine = ROW_SHINE_DEPTH.toPx()
    val stops = ROW_GOLD_STOPS.indices.map { ROW_GOLD_STOPS[it] to lerp(ROW_GOLD[it], Color.Black, dim) }
    drawRoundRect(
        brush = Brush.verticalGradient(*stops.toTypedArray(), startY = faceY, endY = faceY + height),
        topLeft = Offset(x, faceY + shine),
        size = Size(width, height - shine),
        cornerRadius = radius,
    )
}

/** Fades into the panel's black over whichever ends of the list are cut off. */
private fun DrawScope.drawListFades(left: Float, top: Float, right: Float, bottom: Float, scroll: Float, total: Int) {
    val depth = FADE_DEPTH.toPx()
    val above = scroll.coerceIn(0f, 1f)
    val below = rowsBelow(scroll, total).coerceIn(0f, 1f)
    if (above > 0f) {
        drawRect(
            brush = Brush.verticalGradient(listOf(PANEL_BLACK, Color.Transparent), startY = top, endY = top + depth),
            topLeft = Offset(left, top),
            size = Size(right - left, depth),
            alpha = above,
        )
    }
    if (below > 0f) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, PANEL_BLACK), startY = bottom - depth, endY = bottom),
            topLeft = Offset(left, bottom - depth),
            size = Size(right - left, depth),
            alpha = below,
        )
    }
}

/** The arrow, pointing right at the gold row's middle ([centreY]), with a small drop shadow. */
private fun DrawScope.drawArrow(centreY: Float, color: Color) {
    val x = ARROW_X.toPx()
    val half = ARROW_HEIGHT.toPx() / 2
    fun arrow(dy: Float) = Path().apply {
        moveTo(x, centreY - half + dy)
        lineTo(x + ARROW_WIDTH.toPx(), centreY + dy)
        lineTo(x, centreY + half + dy)
        close()
    }
    drawPath(arrow(0.6.dp.toPx()), Color.Black, alpha = 0.6f)
    drawPath(arrow(0f), color)
}

/** A caret at the top or bottom of the arrow's track, its box's top at [y]. */
private fun DrawScope.drawCaret(y: Float, up: Boolean, color: Color) {
    val centre = CARET_X.toPx()
    val half = CARET_WIDTH.toPx() / 2
    val height = CARET_HEIGHT.toPx()
    val tip = if (up) y else y + height
    val base = if (up) y + height else y
    drawPath(
        Path().apply {
            moveTo(centre, tip)
            lineTo(centre + half, base)
            lineTo(centre - half, base)
            close()
        },
        color,
    )
}
