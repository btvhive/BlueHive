package com.example.bluehive.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.bluehive.utilities.AppTypography

// On now's own pieces of the guide (LiveGames.kt has the rules): the sport
// pills in the search bar's place, a game's row, and its panel with the
// matchup picture -- drawn with the guide's rows, tags and panel.

/**
 * The sport pills: [selected] is blue; [cursor] has the ring while the pills
 * have the focus. More sports than the list is wide scroll, as the category
 * chips do, so the cursor's pill is in view; the ring's width is kept round
 * them, as GuideList does, since a lazy row clips.
 */
@Composable
internal fun SportPills(pills: List<LiveGames.Pill>, selected: String?, cursor: Int, focused: Boolean) {
    val state = rememberLazyListState()
    LaunchedEffect(cursor) { state.animateScrollToItem((cursor - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        userScrollEnabled = false,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPORT_PILL_GAP),
        contentPadding = PaddingValues(FOCUS_RING),
        modifier = Modifier
            .offset(LIST_LEFT - FOCUS_RING, SPORT_PILL_TOP - FOCUS_RING)
            .size(LIST_WIDTH + FOCUS_RING * 2, SPORT_PILL_HEIGHT + FOCUS_RING * 2),
    ) {
        itemsIndexed(pills, key = { _, p -> p.label }) { i, pill ->
            SportPill(pill, on = pill.sport == selected, ring = focused && i == cursor)
        }
    }
}

@Composable
private fun SportPill(pill: LiveGames.Pill, on: Boolean, ring: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPORT_PILL_COUNT_GAP),
        modifier = Modifier
            .height(SPORT_PILL_HEIGHT)
            .focusRing(ring, CHIP_RADIUS)
            .background(if (on) CHIP_ON else CHIP_OFF, RoundedCornerShape(CHIP_RADIUS))
            .padding(horizontal = SPORT_PILL_PAD_H),
    ) {
        LiveText(pill.label, TAG_TEXT, if (on) Color.White else CHIP_TEXT_OFF, AppTypography.interSemiBold)
        LiveText(pill.count.toString(), TAG_TEXT, if (on) SPORT_COUNT_ON else SPORT_COUNT_IDLE,
            AppTypography.interSemiBold)
    }
}

/**
 * The league column for [games]: the mockup's 52 px, or as wide as the longest
 * league tag in the list and a gap ("NASCAR", "Premier League" are wider), so
 * no tag is cut short and every name still starts on one line.
 */
@Composable
internal fun leagueColumn(games: List<LiveTvData.Channel>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, games, density) {
        val style = TextStyle(fontSize = TAG_TEXT, fontFamily = AppTypography.interSemiBold,
            platformStyle = PlatformTextStyle(includeFontPadding = false))
        val widest = games.filter { it.start > 0 && it.league.isNotEmpty() }.map { it.league }.distinct()
            .maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        maxOf(GAME_LEAGUE_WIDTH, with(density) { widest.toDp() } + TAG_PAD_H * 2 + GAME_LEAGUE_GAP)
    }
}

/** A game: LIVE (or its start) and its league where a channel has its number and flag. */
@Composable
internal fun GameRow(game: LiveTvData.Channel, focused: Boolean, nowS: Long, leagueWidth: Dp) {
    val started = LiveGames.started(game, nowS)
    RowFrame(focused) {
        Box(Modifier.width(GAME_WHEN_WIDTH)) {
            if (started) LiveTag()
            else LiveText(LiveGames.clock(game.start), NUMBER_TEXT, if (focused) NUMBER_FOCUSED else PANEL_NOTE_COLOR,
                AppTypography.interSemiBold)
        }
        Box(Modifier.width(leagueWidth)) { if (game.league.isNotEmpty()) Tag(game.league) }
        // A game not yet started is not judged by its stream (zlive puts up a slate before kickoff).
        LiveText(game.name, NAME_TEXT, if (started && game.off && !focused) NAME_OFF else Color.White,
            AppTypography.asapCondensedBold, Modifier.weight(1f))
        if (started) {
            Spacer(Modifier.width(DOT_GAP))
            Dot(if (game.off) OFF_DOT else LIVE_DOT)
        }
    }
}

@Composable
private fun LiveTag() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(TAG_HEIGHT)
            .background(LIVE_TAG_BG, RoundedCornerShape(TAG_RADIUS))
            .padding(horizontal = TAG_PAD_H),
    ) {
        LiveText("LIVE", TAG_TEXT, Color.White, AppTypography.interSemiBold)
    }
}

/** A game's panel: the matchup picture, the game, LIVE or its start, and how to watch. */
@Composable
internal fun GamePanel(game: LiveTvData.Channel, nowS: Long) {
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    PanelFrame(band = null) {
        Box(Modifier.fillMaxWidth().height(GAME_ART_HEIGHT).background(GAME_ART_BG), contentAlignment = Alignment.Center) {
            if (game.thumb.isNotEmpty()) {
                AsyncImage(
                    model = game.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(GAME_ART_HEIGHT),
                )
            } else {
                LiveText(game.league.ifEmpty { game.sport }, PANEL_NAME_TEXT, Color.White, AppTypography.asapCondensedBold)
            }
        }
        LiveText(game.name, GAME_NAME_TEXT, Color.White, AppTypography.asapCondensedBold,
            side.padding(top = GAME_NAME_TOP), maxLines = 2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAME_META_GAP),
            modifier = side.padding(top = GAME_META_TOP),
        ) {
            if (LiveGames.started(game, nowS)) LiveTag()
            LiveText(LiveGames.startLine(game, nowS), PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold)
        }
        HintBox(side.padding(top = GAME_HINT_TOP).height(PANEL_HINT_HEIGHT)) { KeyHint("Click", "to watch") }
    }
}
