package com.example.bluehive.details.tvshowdetails

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/*
 * The season accordion: the season picker inside extended_mini_left_upper_body,
 * replacing the season column (approved mockup, 2026-10-27; the design comes
 * from the 2026-10-17 season-box sessions -- wheel + accordion + box-set).
 *
 * A gold card for one season ("Season 3 of 5") sits in the middle of the
 * cap's recessed panel; up to two numbered slabs above and below it make a
 * wheel, the nearer ones wider and lighter. Measured from the user's chosen
 * design (2026-10-29, the wheel of the 2026-10-17 mockups) at its scale, 172 px
 * for the panel's 104 dp. Since v489 the wheel goes all the way round: the
 * seasons after the last are 1, 2, ... and those before Season 1 the last
 * ones, and Up / Down keep turning it past either end (see wheelSeason).
 * OK loads the season on the card, Right goes to the episodes. Each turn rolls
 * the whole wheel one place (v491, see THE ROLL).
 * While browsing, a gold notch marks the season still on show.
 *
 * The geometry is the cap image's: drawn at 4 px per dp (537 x 489 px ->
 * 134.25 x 122.25 dp), its recessed panel runs x 51-467 and y 40-447 px.
 */

/** The cap's left edge: on Add to Favorites' (DetailsHeaderButtons, 27.75 dp). */
internal val SEASON_CAP_X: Dp = 27.75.dp
internal val SEASON_CAP_Y: Dp = 299.dp
internal val SEASON_CAP_WIDTH: Dp = 134.25.dp
internal val SEASON_CAP_HEIGHT: Dp = 122.25.dp

/** The recessed panel inside the cap, where the accordion draws. */
private val PANEL_X = SEASON_CAP_X + 12.75.dp
private val PANEL_Y = SEASON_CAP_Y + 10.dp
private val PANEL_WIDTH = 104.dp
private val PANEL_HEIGHT = 101.75.dp

/** The card's height: 34 dp since v491 (37.5 measured from the design looked too tall; the user's call). */
private val CARD_HEIGHT = 34.dp
private val CARD_TOP = (PANEL_HEIGHT - CARD_HEIGHT) / 2

/**
 * The card's inset each side: narrower than the panel, wider than the nearer
 * slabs (inset 5.5) -- the user's call, 2026-10-29 (v488 filled the panel).
 */
private val CARD_INSET = 2.75.dp

/** Every slab's corners. */
private val SLAB_CORNER = 1.25.dp

/** The card's corners: rounder than the slabs' (the user's call, 3 dp, 2026-10-29). */
private val CARD_CORNER = 3.dp

/** Every slab's height, outline included; they stack with no gaps, the nearer ones touching the card. */
private val SLAB_HEIGHT = 12.1.dp

private val GOLD = Color(0xFFD19814)
private val GOLD_RESTING = Color(0xFFA87A10)
/** The number. */
private val GOLD_INK = Color(0xFF2A1C00)
/** "Season" and "of N": a lighter brown than the number. */
private val GOLD_INK_SOFT = Color(0xFF5A3D00)

private val NEAR_FILL = Color(0xFF3B3535)
private val NEAR_EDGE = Color(0xFF585353)
private val NEAR_INK = Color(0xFFD9D3D3)
private val FAR_FILL = Color(0xFF2E2929)
private val FAR_EDGE = Color(0xFF4A4444)
private val FAR_INK = Color(0xFFB3ACAC)

/** The slabs' outline, and the card's (the nearer slabs' colour since v489; focus shows as the brighter gold). */
private val SLAB_EDGE_WIDTH = 0.5.dp
private val CARD_EDGE_WIDTH = 1.dp

/** The slab numbers' sizes; the card's own text is drawn at its own sizes. */
private const val NEAR_LABEL_SP = 11.5f
private const val FAR_LABEL_SP = 10.5f

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * THE ROLL (v491, the user's design "A6" from the 2026-10-29 mockups, with
 * their numbers). Every season on show is one piece that slides between the
 * places of the wheel: a turn moves each piece one place, so the next slab
 * grows into the gold card and the card shrinks back into a slab.
 *   - each move takes [WHEEL_MOVE_MS] and lands with a small overshoot
 *     ([WheelSpring], the mockup's cubic-bezier(.34, 1.56, .64, 1));
 *   - the card moves first and each place out from it [WHEEL_RIPPLE_MS] later;
 *   - the slab above the card leans its bottom edge away ([NEIGHBOUR_TILT]),
 *     the one below its top edge, and the card leans like the one above
 *     ([CARD_TILT]), as pages in a flip book.
 */
private const val WHEEL_MOVE_MS = 300   // 560 in v491-v495, 380 in v496; faster, the user's calls (2026-10-29)

/**
 * The least time between two turns: one roll, until the card has landed. Up /
 * Down pressed or held sooner are swallowed with no sound -- the user asked
 * that the wheel could not be spammed (2026-10-29).
 */
internal const val WHEEL_TURN_GAP_MS = WHEEL_MOVE_MS.toLong()

/** A turn at [now] is allowed when the last one, at [last], has landed. Pure, so it is tested. */
internal fun wheelTurnAllowed(now: Long, last: Long): Boolean = now - last >= WHEEL_TURN_GAP_MS
private const val WHEEL_RIPPLE_MS = 20
private val WheelSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private const val NEIGHBOUR_TILT = 25f
private const val CARD_TILT = 19f

/**
 * The camera for the tilts: 300 px from the panel, as the mockup's
 * `perspective: 300px` at the same 2 px per dp. A RenderNode measures camera
 * distance in units of 72 px (Skia's camera inches), so 300 / 72.
 */
private const val WHEEL_CAMERA_DISTANCE = 300f / 72f

/**
 * One place of the wheel, [offset] places from the card (0): where a piece
 * sits there and how it looks. A piece between two places takes a mix of the
 * two (see [wheelLook]).
 */
private class WheelPlace(
    val top: Dp,
    val inset: Dp,
    val height: Dp,
    val corner: Dp,
    val fill: Color,
    val edge: Color,
    val edgeWidth: Dp,
    val ink: Color,
    val labelSp: Float,
    /** rotationX: negative leans the bottom edge away, positive the top. */
    val tilt: Float,
    val alpha: Float,
    /** 1 on the card, 0 on a slab: how much of the card's own text shows instead of the slab's number. */
    val cardness: Float,
)

private fun slabPlace(top: Dp, near: Boolean, tilt: Float, alpha: Float) = WheelPlace(
    top = top,
    inset = if (near) 5.5.dp else 10.25.dp,
    height = SLAB_HEIGHT,
    corner = SLAB_CORNER,
    fill = if (near) NEAR_FILL else FAR_FILL,
    edge = if (near) NEAR_EDGE else FAR_EDGE,
    edgeWidth = SLAB_EDGE_WIDTH,
    ink = if (near) NEAR_INK else FAR_INK,
    labelSp = if (near) NEAR_LABEL_SP else FAR_LABEL_SP,
    tilt = tilt,
    alpha = alpha,
    cardness = 0f,
)

/** The places from offset -3 to 3. At +/-3 a piece is out of sight (alpha 0), on its way in or out. */
private val WHEEL_PLACES = listOf(
    slabPlace(CARD_TOP - SLAB_HEIGHT * 3, near = false, tilt = 0f, alpha = 0f),
    slabPlace(CARD_TOP - SLAB_HEIGHT * 2, near = false, tilt = 0f, alpha = 1f),
    slabPlace(CARD_TOP - SLAB_HEIGHT, near = true, tilt = -NEIGHBOUR_TILT, alpha = 1f),
    WheelPlace(
        top = CARD_TOP, inset = CARD_INSET, height = CARD_HEIGHT, corner = CARD_CORNER,
        fill = GOLD, edge = NEAR_EDGE, edgeWidth = CARD_EDGE_WIDTH, ink = NEAR_INK,
        labelSp = NEAR_LABEL_SP, tilt = -CARD_TILT, alpha = 1f, cardness = 1f,
    ),
    slabPlace(CARD_TOP + CARD_HEIGHT, near = true, tilt = NEIGHBOUR_TILT, alpha = 1f),
    slabPlace(CARD_TOP + CARD_HEIGHT + SLAB_HEIGHT, near = false, tilt = 0f, alpha = 1f),
    slabPlace(CARD_TOP + CARD_HEIGHT + SLAB_HEIGHT * 2, near = false, tilt = 0f, alpha = 0f),
)

/** How a piece at (fractional) [position] looks: the two nearest places, mixed. */
private class WheelLook(val place: WheelPlace, val next: WheelPlace, val t: Float) {
    fun dp(f: (WheelPlace) -> Dp): Dp = lerp(f(place), f(next), t)
    fun float(f: (WheelPlace) -> Float): Float = androidx.compose.ui.util.lerp(f(place), f(next), t)
    fun color(f: (WheelPlace) -> Color): Color = androidx.compose.ui.graphics.lerp(f(place), f(next), t)
}

private fun wheelLook(position: Float): WheelLook {
    val p = position.coerceIn(-3f, 3f) + 3f
    val i = p.toInt().coerceIn(0, WHEEL_PLACES.lastIndex)
    val next = (i + 1).coerceAtMost(WHEEL_PLACES.lastIndex)
    return WheelLook(WHEEL_PLACES[i], WHEEL_PLACES[next], p - i)
}

/** The seasons on the wheel turned to [shown], each with its place: within three of the card, each season once. */
internal fun wheelPieces(shown: Int, total: Int): List<Pair<Int, Int>> =
    (-3..3).mapNotNull { offset ->
        val season = wheelSeason(shown, offset, total)
        if (wheelDistance(season, shown, total) == offset) season to offset else null
    }

/**
 * Where every season's piece is, held for the whole wheel and rolled from
 * here on each turn.
 *
 * WHY HERE AND NOT IN EACH PIECE (v492): v491 kept each position in its
 * piece's own remember, and the pieces were drawn by a loop that skipped
 * places with `continue` -- which makes the compiler wrap each pass of the
 * loop in a group of its own, so on every turn each piece was thrown away
 * and made again already at its new place. Nothing rolled: a screen
 * recording of two turns held 7 frames. Held here, a position survives
 * however the pieces are composed.
 */
@Stable
private class WheelMotion(private val scope: CoroutineScope) {
    private val positions = HashMap<Int, Animatable<Float, AnimationVector1D>>()

    /** [season]'s position; a season new to the wheel starts in its place. */
    fun position(season: Int, place: Int): Animatable<Float, AnimationVector1D> =
        positions.getOrPut(season) { Animatable(place.toFloat()) }

    /** Roll every piece to its place in [pieces]: the card first, each place out [WHEEL_RIPPLE_MS] later. */
    fun turnTo(pieces: List<Pair<Int, Int>>, total: Int) {
        val onWheel = pieces.mapTo(HashSet()) { it.first }
        positions.keys.retainAll(onWheel)
        for ((season, place) in pieces) {
            val position = position(season, place)
            val target = place.toFloat()
            if (position.targetValue == target && !position.isRunning && position.value == target) continue
            scope.launch {
                if (abs(position.value - target) * 2 > total) {
                    // Gone round the back of a short wheel (4 to 7 seasons):
                    // reappear in the new place rather than roll across the card.
                    position.snapTo(target)
                } else {
                    // A new animateTo cancels one still running, so holding
                    // Up / Down carries on from wherever the piece is.
                    position.animateTo(
                        target,
                        tween(WHEEL_MOVE_MS, delayMillis = abs(place).coerceAtMost(3) * WHEEL_RIPPLE_MS, easing = WheelSpring),
                    )
                }
            }
        }
    }
}

/** Season [shown] turned [offset] places round a wheel of [total] seasons, past either end. Pure, so it is tested. */
internal fun wheelSeason(shown: Int, offset: Int, total: Int): Int =
    Math.floorMod(shown - 1 + offset, total) + 1

/**
 * Where [season] sits on a wheel of [total] seasons turned to [shown]: its
 * place from the card, the short way round -- in (-total/2, total/2], so every
 * season has exactly one. Pure, so it is tested.
 */
internal fun wheelDistance(season: Int, shown: Int, total: Int): Int {
    val ahead = Math.floorMod(season - shown, total)
    return if (ahead * 2 > total) ahead - total else ahead
}

/**
 * The slabs drawn for [total] seasons, nearest first: each of the other
 * seasons once, at most two each side. With 4 or fewer there are not enough
 * to fill both sides without repeating one, so the extra places stay empty.
 */
internal fun wheelOffsets(total: Int): List<Int> =
    listOf(1, -1, 2, -2).take((total - 1).coerceIn(0, 4))

/**
 * Focus the accordion, waiting for it to be on screen: on the Continue
 * Watching path the episode tile is focused under load, before the season
 * check has let the accordion compose, and a single requestFocus would no-op
 * (Back would seem dead) -- the reason the old column had focusSeasonReliably.
 */
internal suspend fun TvFocusMap.focusSeasonPicker() {
    repeat(40) {
        try { seasonPicker.requestFocus() } catch (_: IllegalStateException) {}
        awaitFrame()
        if (seasonPickerFocused) return
    }
}

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
    // A load from anywhere (OK here, the season check, the first load) moves the card.
    LaunchedEffect(loaded) { browsing = loaded }
    val shown = browsing.coerceIn(1, total)
    val focused = focusMap.seasonPickerFocused
    val motionScope = rememberCoroutineScope()
    val motion = remember { WheelMotion(motionScope) }
    // When the last turn was made (uptimeMillis); plain, not state, so a press
    // recomposes nothing until it turns the wheel -- see WHEEL_TURN_GAP_MS.
    val lastTurn = remember { longArrayOf(Long.MIN_VALUE / 2) }

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = PANEL_X, y = PANEL_Y)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .semantics { contentDescription = "Season $shown of $total" }
            .focusRequester(focusMap.seasonPicker)
            .onFocusChanged { state ->
                focusMap.seasonPickerFocused = state.isFocused
                // Leaving without OK drops the browse: the card goes back to the season on show.
                if (!state.isFocused) browsing = episodesState.selectedSeason.coerceIn(1, total)
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (total > 1) {
                            // Round the wheel: Up from Season 1 is the last season,
                            // once the last turn has landed.
                            val now = SystemClock.uptimeMillis()
                            if (wheelTurnAllowed(now, lastTurn[0])) {
                                lastTurn[0] = now
                                browsing = wheelSeason(shown, -1, total)
                                BlueHiveApplication.playCardFlip()
                            }
                            true
                        } else {
                            // One season, nothing to turn: Up leaves for the header.
                            BlueHiveApplication.playHoverSound()
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        // Round the wheel: Down from the last season is Season 1,
                        // once the last turn has landed.
                        if (total > 1) {
                            val now = SystemClock.uptimeMillis()
                            if (wheelTurnAllowed(now, lastTurn[0])) {
                                lastTurn[0] = now
                                browsing = wheelSeason(shown, 1, total)
                                BlueHiveApplication.playCardFlip()
                            }
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
    ) {
        // Every season within three places of the card, each once, as one
        // piece that rolls between places -- see THE ROLL and WheelMotion.
        val pieces = remember(shown, total) { wheelPieces(shown, total) }
        LaunchedEffect(pieces) { motion.turnTo(pieces, total) }
        for ((season, place) in pieces) {
            key(season) {
                WheelPiece(
                    season = season,
                    total = total,
                    place = place,
                    position = motion.position(season, place),
                    focused = focused,
                    isOnShow = season == loaded && season != shown,
                    passionFont = passionFont,
                )
            }
        }
    }
}

/**
 * One season's piece of the wheel, drawn wherever WheelMotion has rolled
 * [position] to; [place] is where it is headed (it sets the stacking). Every frame
 * of a roll is read in layout and drawing only, so a turn recomposes nothing
 * until the next press.
 */
@Composable
private fun BoxScope.WheelPiece(
    season: Int,
    total: Int,
    place: Int,
    /** Rolled by WheelMotion; read only in layout and drawing. */
    position: Animatable<Float, AnimationVector1D>,
    focused: Boolean,
    isOnShow: Boolean,
    passionFont: FontFamily,
) {
    val cardFill = if (focused) GOLD else GOLD_RESTING
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(10f - abs(place))
            .layout { measurable, _ ->
                val look = wheelLook(position.value)
                val width = (PANEL_WIDTH - look.dp { it.inset } * 2).roundToPx().coerceAtLeast(0)
                val height = look.dp { it.height }.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.place(look.dp { it.inset }.roundToPx(), look.dp { it.top }.roundToPx())
                }
            }
            .graphicsLayer {
                val look = wheelLook(position.value)
                rotationX = look.float { it.tilt }
                alpha = look.float { it.alpha }
                cameraDistance = WHEEL_CAMERA_DISTANCE
                clip = true
            }
            .drawBehind {
                val look = wheelLook(position.value)
                val radius = CornerRadius(look.dp { it.corner }.toPx())
                // The card's place is bright gold while focused, resting gold otherwise.
                val fill = look.color { if (it.cardness == 1f) cardFill else it.fill }
                drawRoundRect(color = fill, cornerRadius = radius)
                val stroke = look.dp { it.edgeWidth }.toPx()
                drawRoundRect(
                    color = look.color { it.edge },
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = radius,
                    style = Stroke(stroke),
                )
                if (isOnShow) {
                    // The season still on show, while browsing: a gold notch.
                    drawRect(GOLD, size = Size(3.dp.toPx(), size.height), alpha = 1f - look.float { it.cardness })
                }
            },
    ) {
        // The slab's number, faded out as the piece becomes the card.
        BasicText(
            text = "$season",
            style = TextStyle(fontFamily = passionFont, fontSize = NEAR_LABEL_SP.sp, platformStyle = NoPadding),
            color = { wheelLook(position.value).color { it.ink } },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 5.25.dp)
                .graphicsLayer {
                    val look = wheelLook(position.value)
                    alpha = 1f - look.float { it.cardness }
                    val scale = look.float { it.labelSp } / NEAR_LABEL_SP
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        )
        // The card's own text, at the card's size and centred, faded in as
        // the piece becomes the card (clipped while it is still slab-sized).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize(unbounded = true)
                .size(width = PANEL_WIDTH - CARD_INSET * 2, height = CARD_HEIGHT)
                .graphicsLayer { alpha = wheelLook(position.value).float { it.cardness } },
        ) {
            SeasonCardText(season = season, total = total, passionFont = passionFont)
        }
    }
}

/** The card's own text: "Season" over "3 of 5". */
@Composable
private fun BoxScope.SeasonCardText(season: Int, total: Int, passionFont: FontFamily) {
    // Paddings put the glyphs where the design has them: "Season"'s
    // baseline just under the card's top, the number's just above its
    // bottom (Roboto ascent 0.928 em; Passion One descent 0.266 em).
    Text(
        text = "Season",
        color = GOLD_INK_SOFT,
        fontFamily = FontFamily.Default,
        fontSize = 10.5.sp,
        style = TextStyle(platformStyle = NoPadding),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 0.5.dp),
    )
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 1.dp),
    ) {
        Text(
            text = "$season",
            color = GOLD_INK,
            fontFamily = passionFont,
            fontSize = 23.5.sp,
            style = TextStyle(platformStyle = NoPadding),
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(3.5.dp))
        Text(
            text = "of $total",
            color = GOLD_INK_SOFT,
            fontFamily = FontFamily.Default,
            fontSize = 10.5.sp,
            style = TextStyle(platformStyle = NoPadding),
            modifier = Modifier.alignByBaseline(),
        )
    }
}
