package com.example.bluehive.details.tvshowdetails

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.example.bluehive.utilities.AppTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/*
 * The season accordion: the season picker inside extended_mini_left_upper_body,
 * replacing the season column (approved mockup, 2026-10-27; the design comes
 * from the 2026-10-17 season-box sessions -- wheel + accordion + box-set).
 *
 * A gold card for one season ("Season 3 of 5") sits in the middle of the cap's
 * recessed panel and slides 4 dp out of the stack; up to two numbered slabs
 * above and below it make a wheel, so any number of seasons stays readable.
 * Up / Down move the card, OK loads that season, Right goes to the episodes.
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

private val CARD_HEIGHT = 40.dp
private val CARD_TOP = (PANEL_HEIGHT - CARD_HEIGHT) / 2

private val GOLD = Color(0xFFD19814)
private val GOLD_RESTING = Color(0xFFA87A10)
private val GOLD_INK = Color(0xFF2A1C00)
private val SLAB = Color(0xFF2B2B31)
private val SLAB_TOP_EDGE = Color(0xFF3C3C44)
private val SLAB_BOTTOM_EDGE = Color(0xFF17171B)
private val SLAB_TEXT = Color(0xFF8E8E96)

/** One slab of the wheel: [offset] seasons from the card, at [top] in the panel, [height] tall, inset [inset] each side. */
private class Slab(val offset: Int, val top: Dp, val height: Dp, val inset: Dp, val fontSp: Float)

private val SLABS = listOf(
    Slab(-2, CARD_TOP - 3.dp - 12.dp - 3.dp - 10.dp, 10.dp, 7.dp, 7.5f),
    Slab(-1, CARD_TOP - 3.dp - 12.dp, 12.dp, 4.dp, 9f),
    Slab(1, CARD_TOP + CARD_HEIGHT + 3.dp, 12.dp, 4.dp, 9f),
    Slab(2, CARD_TOP + CARD_HEIGHT + 3.dp + 12.dp + 3.dp, 10.dp, 7.dp, 7.5f),
)

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

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
                        if (shown > 1) {
                            browsing = shown - 1
                            BlueHiveApplication.playTitleCardNavigation()
                            true
                        } else {
                            // Season 1: Up leaves for the header, as it did from the column.
                            BlueHiveApplication.playHoverSound()
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        // The last season: nothing below, no sound, no move.
                        if (shown < total) {
                            browsing = shown + 1
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
        SLABS.forEach { slab ->
            val season = shown + slab.offset
            if (season in 1..total) {
                SeasonSlab(
                    season = season,
                    slab = slab,
                    isOnShow = season == loaded && season != shown,
                    passionFont = passionFont,
                )
            }
        }
        SeasonCard(season = shown, total = total, focused = focused, passionFont = passionFont)
    }
}

/** A numbered slab in the wheel; [isOnShow] adds the gold notch while browsing. */
@Composable
private fun BoxScope.SeasonSlab(season: Int, slab: Slab, isOnShow: Boolean, passionFont: FontFamily) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = slab.inset, y = slab.top)
            .size(width = PANEL_WIDTH - slab.inset * 2 - 4.dp, height = slab.height)
            .background(SLAB, RoundedCornerShape(2.5.dp))
            .drawBehind {
                val inset = 2.dp.toPx()
                val line = 1.dp.toPx()
                drawLine(SLAB_TOP_EDGE, Offset(inset, line / 2), Offset(size.width - inset, line / 2), line)
                drawLine(SLAB_BOTTOM_EDGE, Offset(inset, size.height - line / 2), Offset(size.width - inset, size.height - line / 2), line)
            },
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
            color = SLAB_TEXT,
            fontFamily = passionFont,
            fontSize = slab.fontSp.sp,
            style = TextStyle(platformStyle = NoPadding),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** The gold card: "Season / 3 of 5", bordered white while the accordion has focus, resting gold otherwise. */
@Composable
private fun BoxScope.SeasonCard(season: Int, total: Int, focused: Boolean, passionFont: FontFamily) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            // Slides 4 dp out of the stack, past the panel's left edge.
            .offset(x = (-4).dp, y = CARD_TOP)
            .size(width = PANEL_WIDTH + 3.dp, height = CARD_HEIGHT)
            .background(if (focused) GOLD else GOLD_RESTING, shape)
            .then(if (focused) Modifier.border(BorderStroke(2.dp, Color.White), shape) else Modifier),
    ) {
        Text(
            text = "Season",
            color = GOLD_INK,
            fontFamily = AppTypography.interSemiBold,
            fontSize = 8.5.sp,
            style = TextStyle(platformStyle = NoPadding),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.5.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp),
        ) {
            Text(
                text = "$season",
                color = GOLD_INK,
                fontFamily = passionFont,
                fontSize = 24.sp,
                style = TextStyle(platformStyle = NoPadding),
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "of $total",
                color = GOLD_INK,
                fontFamily = AppTypography.interSemiBold,
                fontSize = 9.sp,
                style = TextStyle(platformStyle = NoPadding),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
