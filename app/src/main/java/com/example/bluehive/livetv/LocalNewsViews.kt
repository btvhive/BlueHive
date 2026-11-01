package com.example.bluehive.livetv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.example.bluehive.R
import com.example.bluehive.utilities.AppTypography

// Local News' own pieces of the guide (LocalNews.kt has the rules): the state
// pill beside the search bar (design C, the user's pick), the state list it
// opens, and their panels -- drawn with the guide's own rows, tags and panel.

/** Local News' state pill: Up to the bar, then Right. Green until focused, then the guide's blue and ring. */
@Composable
internal fun StatePill(state: String?, focused: Boolean) {
    val shape = RoundedCornerShape(BAR_RADIUS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PILL_GAP, Alignment.CenterHorizontally),
        modifier = Modifier
            .offset(PILL_LEFT, BAR_TOP)
            .size(PILL_WIDTH, BAR_HEIGHT)
            .focusRing(focused, BAR_RADIUS)
            .background(if (focused) ROW_FOCUSED else PILL_BG, shape)
            .then(if (focused) Modifier else Modifier.border(BAR_BORDER, PILL_EDGE, shape)),
    ) {
        val ink = if (focused) Color.White else PILL_INK
        Image(
            painter = painterResource(R.drawable.ic_map_pin),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier.size(BAR_ICON),
        )
        LiveText(LocalNews.pillLabel(state), BAR_TEXT, ink, AppTypography.interSemiBold)
    }
}

/** Where Local News' stations go, until a state is picked. */
@Composable
internal fun PickStateMessage() {
    LiveText(
        "Pick your state with the green button above to see its local news.",
        PANEL_META_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold,
        Modifier.offset(LIST_LEFT + ROW_PAD_H, LIST_TOP + EMPTY_TOP).width(LIST_WIDTH - ROW_PAD_H * 2),
        maxLines = 2,
    )
}

/** The states Local News can show, in the list's place while one is picked. */
@Composable
internal fun StateList(choices: List<LocalNews.StateChoice>, cursor: Int) {
    val state = rememberLazyListState()
    LaunchedEffect(cursor) {
        keepInView(cursor, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, LIST_VISIBLE_ROWS)
            ?.let { state.scrollToItem(it) }
    }
    GuideList(state) {
        itemsIndexed(choices, key = { _, c -> c.code }) { i, c -> StateChoiceRow(c, i == cursor) }
    }
}

@Composable
private fun StateChoiceRow(choice: LocalNews.StateChoice, focused: Boolean) {
    RowFrame(focused) {
        LiveText(
            choice.code, NUMBER_TEXT, if (focused) NUMBER_FOCUSED else NUMBER_IDLE,
            AppTypography.interSemiBold, Modifier.width(NUMBER_WIDTH),
        )
        LiveText(choice.name, NAME_TEXT, Color.White, AppTypography.asapCondensedBold, Modifier.weight(1f))
        Tag(LocalNews.stationsLabel(choice.stations))
    }
}

/** The panel while the pill has the focus: the state picked and its stations, or how to pick one. */
@Composable
internal fun StatePillPanel(state: String?, stations: Int, states: Int, color: Long) {
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    PanelFrame(Color(color)) {
        LiveText(state?.let { LocalNews.stateName(it) } ?: "Pick your state", PANEL_NAME_TEXT, Color.White,
            AppTypography.asapCondensedBold, side.padding(top = PANEL_NAME_TOP), maxLines = 2)
        val meta = if (state != null) LocalNews.stationsLabel(stations) else "$states states"
        LiveText("${LocalNews.ROW} · $meta", PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold,
            side.padding(top = PANEL_META_TOP))
        LiveText(
            if (state != null) "Your state, kept on this TV" else "Local news for where you live",
            PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold, side.padding(top = PANEL_LIVE_TOP),
        )
        HintBox(side.padding(top = PANEL_HINT_TOP).height(PANEL_HINT_HEIGHT)) {
            KeyHint("Click", if (state != null) "to change" else "to choose")
        }
    }
}

/** The panel while picking: the state under the cursor and a few of its stations. */
@Composable
internal fun StateChoicePanel(choice: LocalNews.StateChoice, color: Long) {
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    PanelFrame(Color(color)) {
        LiveText(choice.name, PANEL_NAME_TEXT, Color.White, AppTypography.asapCondensedBold,
            side.padding(top = PANEL_NAME_TOP), maxLines = 2)
        LiveText("${LocalNews.ROW} · ${LocalNews.stationsLabel(choice.stations)}", PANEL_META_TEXT,
            PANEL_META_COLOR, AppTypography.interSemiBold, side.padding(top = PANEL_META_TOP))
        LiveText(LocalNews.examples(choice), PANEL_NOTE_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold,
            side.padding(top = PANEL_LIVE_TOP), maxLines = 2)
        HintBox(side.padding(top = PANEL_HINT_TOP).height(PANEL_HINT_HEIGHT)) { KeyHint("Click", "to choose") }
    }
}
