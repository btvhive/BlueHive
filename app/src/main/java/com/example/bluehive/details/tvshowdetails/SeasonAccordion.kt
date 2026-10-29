package com.example.bluehive.details.tvshowdetails

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
 * OK loads the season on the card, Right goes to the episodes. Each turn flips
 * the card over like a rolodex (v490, see SeasonFlip).
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

private val CARD_HEIGHT = 37.5.dp
private val CARD_TOP = (PANEL_HEIGHT - CARD_HEIGHT) / 2

/**
 * The card's inset each side: narrower than the panel, wider than the nearer
 * slabs (inset 5.5) -- the user's call, 2026-10-29 (v488 filled the panel).
 */
private val CARD_INSET = 2.75.dp

/** Every slab's corners, and the card's since v489. */
private val SLAB_CORNER = 1.25.dp

/** Every slab's height, outline included; they stack with no gaps, the nearer ones touching the card. */
private val SLAB_HEIGHT = 12.1.dp

private val GOLD = Color(0xFFD19814)
private val GOLD_RESTING = Color(0xFFA87A10)
/** The number. */
private val GOLD_INK = Color(0xFF2A1C00)
/** "Season" and "of N": a lighter brown than the number. */
private val GOLD_INK_SOFT = Color(0xFF5A3D00)

/**
 * One slab of the wheel: [offset] seasons from the card, at [top] in the
 * panel, inset [inset] each side, in [fill] with a hairline [edge] and its
 * number in [ink] at [fontSp].
 */
private class Slab(
    val offset: Int,
    val top: Dp,
    val inset: Dp,
    val fill: Color,
    val edge: Color,
    val ink: Color,
    val fontSp: Float,
)

private val NEAR_FILL = Color(0xFF3B3535)
private val NEAR_EDGE = Color(0xFF585353)
private val NEAR_INK = Color(0xFFD9D3D3)
private val FAR_FILL = Color(0xFF2E2929)
private val FAR_EDGE = Color(0xFF4A4444)
private val FAR_INK = Color(0xFFB3ACAC)

/**
 * The card's outline: the nearer slabs' colour (the user's call, 2026-10-29;
 * it was white while focused). Focus shows as the brighter gold.
 */
private val CARD_EDGE = NEAR_EDGE
private val CARD_EDGE_WIDTH = 1.dp

private val SLABS = listOf(
    Slab(-2, CARD_TOP - SLAB_HEIGHT * 2, 10.25.dp, FAR_FILL, FAR_EDGE, FAR_INK, 10.5f),
    Slab(-1, CARD_TOP - SLAB_HEIGHT, 5.5.dp, NEAR_FILL, NEAR_EDGE, NEAR_INK, 11.5f),
    Slab(1, CARD_TOP + CARD_HEIGHT, 5.5.dp, NEAR_FILL, NEAR_EDGE, NEAR_INK, 11.5f),
    Slab(2, CARD_TOP + CARD_HEIGHT + SLAB_HEIGHT, 10.25.dp, FAR_FILL, FAR_EDGE, FAR_INK, 10.5f),
)

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** Each half of the card's flip: turning away, then the new season turning in. 200 ms in all, so holding Up / Down stays quick. */
private const val FLIP_OUT_MS = 90
private const val FLIP_IN_MS = 110

/** How far the card turns before the new season takes its place: edge-on to the viewer. */
private const val FLIP_EDGE_ON = 90f

/**
 * The card's flip. [displayed] is the season drawn -- on the card and, for
 * its neighbours, on the slabs -- and changes only once the card is edge-on,
 * so the old number never flips in and the slabs change with the card.
 *
 * A turn that arrives mid-flip starts from wherever the card is: the next
 * one carries on the rotation, and holding Up / Down shows only the season
 * the wheel has reached by the time each flip is edge-on.
 */
@Stable
private class SeasonFlip(start: Int) {
    var displayed by mutableIntStateOf(start)
        private set
    val angle = Animatable(0f)

    suspend fun turnTo(target: Int, total: Int) {
        if (target == displayed) {
            angle.animateTo(0f, tween(FLIP_IN_MS, easing = LinearOutSlowInEasing))
            return
        }
        // Down (the next season, round the wheel) tips the card's top away;
        // Up the other way.
        val forward = wheelSeason(displayed, 1, total) == target ||
            (target > displayed && wheelSeason(displayed, -1, total) != target)
        val away = if (forward) -FLIP_EDGE_ON else FLIP_EDGE_ON
        angle.animateTo(away, tween(FLIP_OUT_MS, easing = FastOutLinearInEasing))
        displayed = target
        angle.snapTo(-away)
        angle.animateTo(0f, tween(FLIP_IN_MS, easing = LinearOutSlowInEasing))
    }
}

/** Season [shown] turned [offset] places round a wheel of [total] seasons, past either end. Pure, so it is tested. */
internal fun wheelSeason(shown: Int, offset: Int, total: Int): Int =
    Math.floorMod(shown - 1 + offset, total) + 1

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

    // The card flips to each new season -- see SeasonFlip. What is drawn
    // follows it; what Up / Down / OK act on is [shown], at once.
    val flip = remember { SeasonFlip(shown) }
    LaunchedEffect(shown, total) { flip.turnTo(shown, total) }
    val drawn = flip.displayed

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
                            // Round the wheel: Up from Season 1 is the last season.
                            browsing = wheelSeason(shown, -1, total)
                            BlueHiveApplication.playTitleCardNavigation()
                            true
                        } else {
                            // One season, nothing to turn: Up leaves for the header.
                            BlueHiveApplication.playHoverSound()
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        // Round the wheel: Down from the last season is Season 1.
                        if (total > 1) {
                            browsing = wheelSeason(shown, 1, total)
                            BlueHiveApplication.playTitleCardNavigation()
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
        val offsets = remember(total) { wheelOffsets(total) }
        SLABS.forEach { slab ->
            if (slab.offset in offsets) {
                val season = wheelSeason(drawn, slab.offset, total)
                SeasonSlab(
                    season = season,
                    slab = slab,
                    isOnShow = season == loaded && season != drawn,
                    passionFont = passionFont,
                )
            }
        }
        SeasonCard(season = drawn, total = total, focused = focused, flipAngle = flip.angle::value, passionFont = passionFont)
    }
}

/** A numbered slab in the wheel; [isOnShow] adds the gold notch while browsing. */
@Composable
private fun BoxScope.SeasonSlab(season: Int, slab: Slab, isOnShow: Boolean, passionFont: FontFamily) {
    val shape = RoundedCornerShape(SLAB_CORNER)
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = slab.inset, y = slab.top)
            .size(width = PANEL_WIDTH - slab.inset * 2, height = SLAB_HEIGHT)
            .background(slab.fill, shape)
            .border(BorderStroke(0.5.dp, slab.edge), shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (isOnShow) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(GOLD, RoundedCornerShape(1.dp))
            )
        }
        Text(
            text = "$season",
            color = slab.ink,
            fontFamily = passionFont,
            fontSize = slab.fontSp.sp,
            style = TextStyle(platformStyle = NoPadding),
            modifier = Modifier.padding(start = 5.25.dp),
        )
    }
}

/** The gold card: "Season / 3 of 5", bright gold while the accordion has focus, resting gold otherwise. */
@Composable
private fun BoxScope.SeasonCard(
    season: Int,
    total: Int,
    focused: Boolean,
    /** The flip's rotation in degrees; read only while drawing, so a flip never recomposes the card. */
    flipAngle: () -> Float,
    passionFont: FontFamily,
) {
    val shape = RoundedCornerShape(SLAB_CORNER)
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = CARD_INSET, y = CARD_TOP)
            .size(width = PANEL_WIDTH - CARD_INSET * 2, height = CARD_HEIGHT)
            .graphicsLayer {
                rotationX = flipAngle()
                // A far camera keeps the near edge from swelling over the slabs.
                cameraDistance = 16f * density
            }
            .background(if (focused) GOLD else GOLD_RESTING, shape)
            .border(BorderStroke(CARD_EDGE_WIDTH, CARD_EDGE), shape),
    ) {
        // Paddings put the glyphs where the design has them: "Season"'s
        // baseline 10.2 dp under the card's top, the number's 7.3 dp above
        // its bottom (Roboto ascent 0.928 em; Passion One descent 0.266 em).
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
}
