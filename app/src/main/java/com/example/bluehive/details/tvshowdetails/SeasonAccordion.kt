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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.bluehive.utilities.RecommendedRowEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * The season list: the season picker inside extended_mini_left_upper_body
 * (v502, replacing the v488-v501 card wheel; the user's advisor found the
 * wheel too artistic for older viewers).
 *
 * Two steps since v505, so Up / Down never pick a season by accident (the
 * user's flow and the 2026-10-29/30 mockups):
 *   - FOCUSED: a thin glowing white line rings the black panel and the season
 *     on show stays the DULL WHITE tile (v508). Up goes to the header, Down to
 *     the recommended row, Right to the episodes; OK starts CHOOSING.
 *   - CHOOSING: the line goes (v506), the season on show turns GREY, and a
 *     bright white tile with the gold arrow beside it moves between seasons
 *     on Up / Down, STOPPING at the ends (no wrap). OK plays that season (its
 *     tile presses in) and stays CHOOSING (v507); Back goes back to FOCUSED,
 *     the white tile returning to the season on show.
 *   - Focus elsewhere: no line; the season on show is a DULL WHITE tile.
 * Four rows show at a time. While choosing, a gold caret at either end of the
 * arrow's track says there are more seasons that way, and the list fades out
 * there.
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

/** A raised tile: a three-stop gradient on a darker lip, lit along its top, and its text. */
private class TilePaint(
    val face: List<Color>,
    val lip: Color,
    val shine: Color,
    val ink: Color,
    val label: Color,
)

/** Blends two tiles' paint, [t] of the way from [a] to [b]. */
private fun lerpTile(a: TilePaint, b: TilePaint, t: Float): TilePaint =
    if (t <= 0f) a else if (t >= 1f) b else TilePaint(
        face = a.face.indices.map { lerp(a.face[it], b.face[it], t) },
        lip = lerp(a.lip, b.lip, t),
        shine = lerp(a.shine, b.shine, t),
        ink = lerp(a.ink, b.ink, t),
        label = lerp(a.label, b.label, t),
    )

/** The season on show while choosing another: mockup F's "lit charcoal" grey (the user's pick, 2026-10-30). */
private val GREY_TILE = TilePaint(
    face = listOf(Color(0xFF4A4A54), Color(0xFF36363E), Color(0xFF2C2C33)),
    lip = Color(0xFF151518),
    shine = Color(0x66FFFFFF),
    ink = Color(0xFFFFFFFF),
    label = Color(0xFFC9C9D2),
)

/** The season being chosen: bright white. */
private val BRIGHT_TILE = TilePaint(
    face = listOf(Color(0xFFFAFAFC), Color(0xFFDCDDE2), Color(0xFFC3C4CB)),
    lip = Color(0xFF6B6C74),
    shine = Color(0xE6FFFFFF),
    ink = Color(0xFF16161B),
    label = Color(0xFF3C3C46),
)

/** The season on show when not choosing: a slightly dull white (the user's mockup, 2026-10-30). */
private val DULL_TILE = TilePaint(
    face = listOf(Color(0xFFC9CACF), Color(0xFFB1B2B9), Color(0xFF9D9EA6)),
    lip = Color(0xFF4F5057),
    shine = Color(0x59FFFFFF),
    ink = Color(0xFF24242A),
    label = Color(0xFF45454F),
)
private val TILE_FACE_STOPS = floatArrayOf(0f, 0.55f, 1f)
private val TILE_LIP_DEPTH = 2.5.dp
private val TILE_SHINE_DEPTH = 0.6.dp

private val GOLD = Color(0xFFD19814)
private val CARET_OFF = Color(0xFF2A2A30)

/** A slot's text. */
private val INK_OFF = Color(0xFF8D8D96)
private const val LABEL_SP = 6.2f
private const val NUMBER_SP = 12.7f

/** The arrow pointing at the white tile, and the carets at its track's ends (centred on x [CARET_X]). */
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

/**
 * The focus line: "C with B's thin glow" of the 2026-10-30 mockups -- a thin
 * white line just outside the black panel ([FOCUS_LINE_OUTSET] from its edge
 * to the line's middle), glowing both ways.
 */
private val FOCUS_LINE_OUTSET = 3.2.dp
private val FOCUS_LINE_WIDTH = 1.2.dp
private val FOCUS_LINE_CORNER = 3.7.dp
private val FOCUS_GLOW = listOf(5.dp to 0.06f, 3.6.dp to 0.12f, 2.4.dp to 0.22f)
private const val FOCUS_FADE_MS = 150

/** One move: the white tile slides and the list scrolls together (the mockup's cubic-bezier(.2, .8, .2, 1)). */
private const val LIST_MOVE_MS = 150   // 320 in v502; "a lot faster", the user (2026-10-29)
private val ListEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** The white tile and arrow fading in as choosing starts, and out as it ends. */
private const val CHOOSING_FADE_MS = 120

/**
 * The least time between two moves: one slide. Up / Down pressed or held
 * sooner are swallowed with no sound -- the user asked that the picker could
 * not be spammed (2026-10-29).
 */
internal const val SEASON_TURN_GAP_MS = LIST_MOVE_MS.toLong()

/** A move at [now] is allowed when the last one, at [last], has landed. Pure, so it is tested. */
internal fun seasonTurnAllowed(now: Long, last: Long): Boolean = now - last >= SEASON_TURN_GAP_MS

/** OK presses the playing tile in: down [PRESS_SINK] in [PRESS_DOWN_MS], then springs back in [PRESS_UP_MS]. */
private val PRESS_SINK = 2.dp
private const val PRESS_DOWN_MS = 80
private const val PRESS_UP_MS = 300
private val PressSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** The season [delta] rows from [shown], or null past either end: the list stops there. Pure, so it is tested. */
internal fun seasonListStep(shown: Int, delta: Int, total: Int): Int? = (shown + delta).takeIf { it in 1..total }

/**
 * How far the list is scrolled, in rows, with [shown] in view: kept in the
 * middle of the window where it can be, never past either end, and a list
 * shorter than the window centred in it (negative). Pure, so it is tested.
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

/** Everything the list draws from; the floats are animated and read only in drawing. */
private class SeasonListFrame(
    val total: Int,
    val loaded: Int,
    /** The white tile's row (0-based) and how far it has faded in. */
    val cursor: Float,
    val choosing: Float,
    val scroll: Float,
    val press: Float,
    /** How far the picker has focus (0 to 1). */
    val focus: Float,
    /** Left while choosing: the line stays off as both fade out (v514). */
    val lineOff: Boolean,
)

/** Season 1 through [listedSeasons]: the newest with an aired episode (SeasonAvailability). */
@Composable
internal fun BoxScope.SeasonAccordion(
    listedSeasons: Int?,
    numberOfSeasons: Int,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    passionFont: FontFamily,
    coroutineScope: CoroutineScope,
    rowEntry: RecommendedRowEntry,
) {
    // Waits for the season check, as the column did, so a season with
    // nothing aired yet never shows.
    val total = listedSeasons ?: 0
    if (numberOfSeasons <= 0 || total <= 0) return

    val loaded = episodesState.selectedSeason.coerceIn(1, total)
    var browsing by remember { mutableIntStateOf(loaded) }
    var choosing by remember { mutableStateOf(false) }
    // Set when focus leaves mid-choosing (Right to the episodes): the choosing
    // fade would otherwise bring the line back for a frame or two on the way out.
    var lineOff by remember { mutableStateOf(false) }
    // A load from anywhere (OK here, the season check, the first load) moves the white tile with it.
    LaunchedEffect(loaded) { browsing = loaded }
    val shown = browsing.coerceIn(1, total)
    val focused = focusMap.seasonPickerFocused
    val motionScope = rememberCoroutineScope()
    // When the last move was made (uptimeMillis); plain, not state, so a press
    // recomposes nothing until it moves -- see SEASON_TURN_GAP_MS.
    val lastTurn = remember { longArrayOf(Long.MIN_VALUE / 2) }
    // Read only in drawing: the white tile's row, the list's scroll (rows),
    // the playing tile's press, the choosing fade and the focus fade.
    val cursor = remember(total) { Animatable(shown - 1f) }
    val scroll = remember(total) { Animatable(seasonListScroll(shown, total)) }
    val press = remember { Animatable(0f) }
    val choosingFade = remember { Animatable(0f) }
    val focusFade = remember { Animatable(0f) }
    LaunchedEffect(shown, total) {
        launch { cursor.animateTo(shown - 1f, tween(LIST_MOVE_MS, easing = ListEase)) }
        launch { scroll.animateTo(seasonListScroll(shown, total), tween(LIST_MOVE_MS, easing = ListEase)) }
    }
    LaunchedEffect(choosing) { choosingFade.animateTo(if (choosing) 1f else 0f, tween(CHOOSING_FADE_MS)) }
    LaunchedEffect(focused) { focusFade.animateTo(if (focused) 1f else 0f, tween(FOCUS_FADE_MS)) }
    val text = rememberSeasonListText(total, passionFont)

    // Choosing ends where it started: back on the season on show.
    fun stopChoosing() {
        choosing = false
        browsing = episodesState.selectedSeason.coerceIn(1, total)
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = PANEL_X, y = PANEL_Y)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .semantics { contentDescription = "Season $shown of $total" }
            .focusRequester(focusMap.seasonPicker)
            .onFocusChanged { state ->
                focusMap.seasonPickerFocused = state.isFocused
                lineOff = !state.isFocused && (choosing || lineOff)
                // Leaving drops the browse.
                if (!state.isFocused) stopChoosing()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (choosing && event.key == Key.DirectionRight) {
                    // Right goes straight to the episodes, choosing or not (v513);
                    // losing focus ends the choosing. Held here while none are loaded.
                    focusEpisodes(episodesState, coroutineScope)
                    true
                } else if (choosing) {
                    onChoosingKey(event.key, shown, total, lastTurn, onMove = { browsing = it }, onBack = ::stopChoosing) {
                        BlueHiveApplication.playClickSound()
                        motionScope.launch {
                            press.animateTo(1f, tween(PRESS_DOWN_MS))
                            press.animateTo(0f, tween(PRESS_UP_MS, easing = PressSpring))
                        }
                        // Choosing carries on (v507, the user's call): only Back ends it.
                        if (episodesState.selectedSeason != shown) {
                            episodesState.selectedSeason = shown
                            episodesState.loadSeasonEpisodes(shown)
                        }
                    }
                } else {
                    when (event.key) {
                        // Focused, not choosing: Up and Down move on, as anywhere else.
                        Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound()
                            false
                        }
                        Key.DirectionDown -> {
                            // The recommended row peeks under the cap, as under the
                            // episodes (EpisodeRow): consumed even before it can take focus.
                            if (rowEntry.focus()) BlueHiveApplication.playHoverSound()
                            true
                        }
                        Key.DirectionLeft -> true
                        Key.DirectionRight -> focusEpisodes(episodesState, coroutineScope)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            BlueHiveApplication.playClickSound()
                            // One season: nothing to choose.
                            if (total > 1) {
                                browsing = loaded
                                choosing = true
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
            .drawBehind {
                drawSeasonList(
                    text,
                    SeasonListFrame(
                        total = total,
                        loaded = loaded,
                        cursor = cursor.value,
                        choosing = choosingFade.value,
                        scroll = scroll.value,
                        press = press.value,
                        focus = focusFade.value,
                        lineOff = lineOff,
                    ),
                )
            }
    )
}

/**
 * A key while choosing: Up / Down move the white tile (stopping at either
 * end, once the last move has landed), OK runs [onPlay], Back runs [onBack];
 * Left is held here. (Right, to the episodes, is the caller's.)
 */
private fun onChoosingKey(
    key: Key,
    shown: Int,
    total: Int,
    lastTurn: LongArray,
    onMove: (Int) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
): Boolean {
    when (key) {
        Key.DirectionUp, Key.DirectionDown -> {
            val next = seasonListStep(shown, if (key == Key.DirectionUp) -1 else 1, total)
            val now = SystemClock.uptimeMillis()
            if (next != null && seasonTurnAllowed(now, lastTurn[0])) {
                lastTurn[0] = now
                onMove(next)
                BlueHiveApplication.playCardFlip()
            }
        }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onPlay()
        Key.Back -> {
            BlueHiveApplication.playBackOutSound()
            onBack()
        }
        Key.DirectionLeft -> Unit
        else -> return false
    }
    return true
}

/** Right from the picker: the episode last focused, scrolled into view. False when there are none yet. */
private fun focusEpisodes(episodesState: EpisodesState, coroutineScope: CoroutineScope): Boolean {
    if (episodesState.episodes.isEmpty() || episodesState.focusRequesters.isEmpty()) return false
    BlueHiveApplication.playHoverSound()
    val targetIndex = episodesState.lastFocusedEpisodeIndex.coerceIn(0, episodesState.focusRequesters.lastIndex)
    coroutineScope.launch {
        episodesState.rowState.scrollToItem(targetIndex)
        episodesState.focusRequesters
            .getOrNull(targetIndex)
            ?.requestFocus()
            ?: episodesState.focusRequesters.first().requestFocus()
    }
    return true
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

/** The whole list, drawn from [frame], so a move redraws and recomposes nothing. */
private fun DrawScope.drawSeasonList(text: SeasonListText, frame: SeasonListFrame) {
    val step = ROW_STEP.toPx()
    val rowHeight = step - ROW_GAP.toPx()
    val left = LIST_LEFT.toPx()
    val right = size.width - LIST_RIGHT.toPx()
    val top = LIST_INSET_V.toPx()
    val bottom = size.height - LIST_INSET_V.toPx()
    val rowTop = { row: Float -> top + (row - frame.scroll) * step }
    val sink = PRESS_SINK.toPx() * frame.press

    // The line shows while focused, and goes while choosing (v506).
    val line = if (frame.lineOff) 0f else frame.focus * (1f - frame.choosing)
    if (line > 0f) drawFocusLine(line)
    // The season on show: dull white, focused or not (v508); grey while choosing,
    // and bright white only under the white tile.
    val playing = lerpTile(DULL_TILE, GREY_TILE, frame.choosing)
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        for (season in 1..frame.total) {
            val y = rowTop(season - 1f)
            if (y > bottom || y + rowHeight < top) continue
            drawSlot(left, y, right - left, rowHeight)
        }
        drawTile(playing, left, rowTop(frame.loaded - 1f), right - left, rowHeight, sink)
        if (frame.choosing > 0f) {
            // OK presses in the season just played, which is the white tile's (v507).
            drawTile(BRIGHT_TILE, left, rowTop(frame.cursor), right - left, rowHeight, sink, frame.choosing)
        }
        for (season in 1..frame.total) {
            val y = rowTop(season - 1f)
            if (y > bottom || y + rowHeight < top) continue
            val isLoaded = season == frame.loaded
            // How far the white tile is over this row: its text turns dark as it arrives.
            val on = frame.choosing * (1f - abs(frame.cursor - (season - 1))).coerceIn(0f, 1f)
            val centre = y + rowHeight / 2 + if (isLoaded) sink else 0f
            val label = text.label
            drawText(
                label,
                color = lerp(if (isLoaded) playing.label else INK_OFF, BRIGHT_TILE.label, on),
                topLeft = Offset(left + ROW_TEXT_PAD.toPx(), centre - label.size.height / 2f),
            )
            val number = text.numbers[season - 1]
            drawText(
                number,
                color = lerp(if (isLoaded) playing.ink else INK_OFF, BRIGHT_TILE.ink, on),
                topLeft = Offset(right - ROW_TEXT_PAD.toPx() - number.size.width, centre - number.size.height / 2f),
            )
        }
    }
    drawListFades(left, top, right, bottom, frame.scroll, frame.total)
    if (frame.choosing > 0f && frame.total > 1) {
        drawArrow(rowTop(frame.cursor) + rowHeight / 2, frame.choosing)
    }
    // The carets, while choosing: gold while there are more seasons that way, grey at an end.
    val more = { amount: Float -> lerp(CARET_OFF, GOLD, amount.coerceIn(0f, 1f) * frame.choosing) }
    drawCaret(CARET_UP_TOP.toPx(), up = true, color = more(frame.scroll))
    drawCaret(CARET_DOWN_TOP.toPx(), up = false, color = more(rowsBelow(frame.scroll, frame.total)))
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

/** A raised tile at [y], its face pressed [sink] px onto its lip. */
private fun DrawScope.drawTile(paint: TilePaint, x: Float, y: Float, width: Float, height: Float, sink: Float, alpha: Float = 1f) {
    val radius = CornerRadius(ROW_CORNER.toPx())
    // The lip stays put; the face sinks onto it.
    drawRoundRect(paint.lip, Offset(x, y + TILE_LIP_DEPTH.toPx()), Size(width, height), radius, alpha = alpha)
    val faceY = y + sink
    drawRoundRect(paint.shine, Offset(x, faceY), Size(width, height), radius, alpha = alpha)
    val shine = TILE_SHINE_DEPTH.toPx()
    val stops = TILE_FACE_STOPS.indices.map { TILE_FACE_STOPS[it] to paint.face[it] }
    drawRoundRect(
        brush = Brush.verticalGradient(*stops.toTypedArray(), startY = faceY, endY = faceY + height),
        topLeft = Offset(x, faceY + shine),
        size = Size(width, height - shine),
        cornerRadius = radius,
        alpha = alpha,
    )
}

/** The focus line round the black panel, its glow faked with wider, fainter strokes under it. */
private fun DrawScope.drawFocusLine(alpha: Float) {
    val outset = FOCUS_LINE_OUTSET.toPx()
    val topLeft = Offset(-outset, -outset)
    val ring = Size(size.width + outset * 2, size.height + outset * 2)
    val radius = CornerRadius(FOCUS_LINE_CORNER.toPx())
    for ((width, glow) in FOCUS_GLOW) {
        drawRoundRect(Color.White, topLeft, ring, radius, style = Stroke(width.toPx()), alpha = glow * alpha)
    }
    drawRoundRect(Color.White, topLeft, ring, radius, style = Stroke(FOCUS_LINE_WIDTH.toPx()), alpha = alpha)
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

/** The gold arrow, pointing right at the white tile's middle ([centreY]), with a small drop shadow. */
private fun DrawScope.drawArrow(centreY: Float, alpha: Float) {
    val x = ARROW_X.toPx()
    val half = ARROW_HEIGHT.toPx() / 2
    fun arrow(dy: Float) = Path().apply {
        moveTo(x, centreY - half + dy)
        lineTo(x + ARROW_WIDTH.toPx(), centreY + dy)
        lineTo(x, centreY + half + dy)
        close()
    }
    drawPath(arrow(0.6.dp.toPx()), Color.Black, alpha = 0.6f * alpha)
    drawPath(arrow(0f), GOLD, alpha = alpha)
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
