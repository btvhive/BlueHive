package com.example.bluehive.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.searchBarComponent.HiveDir
import com.example.bluehive.searchBarComponent.HiveInline
import com.example.bluehive.searchBarComponent.HoneycombKeyboardInline
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.webview.exoplayer_resources.ExoPlayerConfig

/**
 * The search page (design C), opened from the guide's search bar: the
 * honeycomb keyboard on the left; on the right, quick picks until something
 * is typed, then what it finds, live first. Every key filters at once -- the
 * channels are already on the TV.
 *
 * Right off the keyboard's edge moves to the tiles, Left off their first
 * column moves back; Search (or a finished Voice search) jumps to the first
 * result. Click on a pick searches it, on a channel watches it, and the page
 * is still here when the player closes. One BACK closes the page, wherever
 * the focus is.
 */
@Stable
internal class LiveSearchState(private val rows: () -> List<LiveTvData.Row>?) {
    var isOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    /** Bumped when the page sets [query] itself (a quick pick), so the keyboard's box follows. */
    var querySets by mutableIntStateOf(0)
        private set
    /** The tiles have the focus, not the keyboard. */
    var onTiles by mutableStateOf(false)
        private set
    var tile by mutableIntStateOf(0)
        private set
    val keyboardFocus = FocusRequester()

    val channels by derivedStateOf { LiveSearch.channels(rows().orEmpty()) }
    val picks by derivedStateOf { LiveSearch.picks(channels) }
    val found by derivedStateOf { LiveSearch.find(channels, query) }

    /** Quick picks until something is typed. */
    val showingPicks: Boolean get() = query.isBlank()
    private val tileCount: Int get() = if (showingPicks) picks.size else found.size
    private val columns: Int get() = if (showingPicks) LiveSearch.PICK_COLUMNS else LiveSearch.RESULT_COLUMNS

    fun open() {
        query = ""
        tile = 0
        onTiles = false
        isOpen = true
    }

    /** Every key typed, deleted or heard. */
    fun type(text: String) {
        if (text == query) return
        query = text
        tile = 0
    }

    /** The keyboard's Search key, or a finished voice search: on to the first result. */
    fun submit() {
        if (found.isEmpty()) return
        tile = 0
        onTiles = true
    }

    /** Right off the keyboard's edge. */
    fun toTiles() {
        if (tileCount == 0) return
        tile = tile.coerceIn(0, tileCount - 1)
        onTiles = true
        BlueHiveApplication.playHoverSound()
    }

    /** BACK: out of the page in one press, from the keyboard or the tiles (the user's call, v511). */
    fun back() {
        BlueHiveApplication.playBackOutSound()
        onTiles = false
        isOpen = false
    }

    /** The list reloaded (back from the player): the focus stays on the channel just watched. */
    fun reconcile() {
        if (!isOpen) return
        val watched = if (showingPicks) -1 else found.indexOfFirst { it.id == LiveTvData.lastWatchedId }
        tile = if (watched >= 0) watched else tile.coerceIn(0, (tileCount - 1).coerceAtLeast(0))
        if (tileCount == 0) onTiles = false
    }

    /** Remote keys while the tiles have the focus; the keyboard takes its own. */
    fun onKey(code: Int, repeat: Int, onWatch: (LiveTvData.Row, Int) -> Unit): Boolean {
        if (!onTiles) return false
        val dir = when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> HiveDir.UP
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> HiveDir.DOWN
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> HiveDir.LEFT
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> HiveDir.RIGHT
            else -> null
        }
        if (dir == null) {
            if (!ExoPlayerConfig.isCenterKey(code)) return false
            if (repeat == 0) click(onWatch)
            return true
        }
        val next = LiveSearch.step(tile, tileCount, columns, dir)
        if (next != null) {
            tile = next
            BlueHiveApplication.playHoverSound()
        } else if (dir == HiveDir.LEFT) {
            toKeyboard()
        }
        return true
    }

    private fun toKeyboard() {
        onTiles = false
        runCatching { keyboardFocus.requestFocus() }
        BlueHiveApplication.playHoverSound()
    }

    private fun click(onWatch: (LiveTvData.Row, Int) -> Unit) {
        BlueHiveApplication.playClickSound()
        if (showingPicks) {
            val pick = picks.getOrNull(tile) ?: return
            query = pick.query
            querySets++
            tile = 0
            return
        }
        val channel = found.getOrNull(tile) ?: return
        val row = rows().orEmpty().firstOrNull { it.name == channel.row } ?: return
        onWatch(row, row.channels.indexOfFirst { it.id == channel.id })
    }
}

@Composable
internal fun LiveSearchPage(search: LiveSearchState, nowS: Long) {
    // The remote's Back is caught as a key by the page (LiveTvPageState.onKey);
    // this covers one that arrives as a back action instead.
    BackHandler { search.back() }
    LiveText(
        "Search Live TV", SEARCH_TITLE_TEXT, Color.White, AppTypography.asapCondensedBold,
        Modifier.offset(SEARCH_LEFT, SEARCH_TITLE_TOP),
    )
    LiveText(
        LiveSearch.pageNote(search.channels), TILE_META_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold,
        Modifier.offset(SEARCH_LEFT, SEARCH_NOTE_TOP),
    )
    SearchKeyboard(search)
    val focused = if (search.onTiles) search.tile else -1
    if (search.showingPicks) {
        TilesHeader("Quick picks", "A whole family in one click, live ones first")
        PickGrid(search.picks, focused)
    } else {
        TilesHeader(LiveSearch.resultsTitle(search.found), LiveSearch.resultsNote(search.found))
        ResultGrid(search.found, focused, nowS)
    }
    SearchHintBar(search.showingPicks)
}

@Composable
private fun SearchKeyboard(search: LiveSearchState) {
    Box(Modifier.offset(SEARCH_LEFT, SEARCH_KEYBOARD_TOP)) {
        HoneycombKeyboardInline(
            inline = HiveInline(
                text = search.query,
                textSets = search.querySets,
                placeholder = "Search channels",
                status = if (search.showingPicks) null else LiveSearch.keyboardStatus(search.found),
                showFocus = !search.onTiles,
                focus = search.keyboardFocus,
                onEdge = { if (it == HiveDir.RIGHT) search.toTiles() },
            ),
            onTextChange = search::type,
            onSearch = { search.submit() },
        )
    }
}

@Composable
private fun TilesHeader(title: String, note: String) {
    LiveText(title, TILES_TITLE_TEXT, Color.White, AppTypography.asapCondensedBold, Modifier.offset(TILES_LEFT, TILES_TITLE_TOP))
    LiveText(note, TILE_META_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold, Modifier.offset(TILES_LEFT, TILES_NOTE_TOP))
}

@Composable
private fun PickGrid(picks: List<LiveSearch.Pick>, focused: Int) {
    Column(Modifier.offset(TILES_LEFT, TILES_TOP), verticalArrangement = Arrangement.spacedBy(PICK_ROW_GAP)) {
        picks.chunked(LiveSearch.PICK_COLUMNS).forEachIndexed { r, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(PICK_COL_GAP)) {
                line.forEachIndexed { c, pick -> PickTile(pick, focused == r * LiveSearch.PICK_COLUMNS + c) }
            }
        }
    }
}

@Composable
private fun PickTile(pick: LiveSearch.Pick, focused: Boolean) {
    TileFrame(PICK_WIDTH, PICK_HEIGHT, PICK_BAND, Color(pick.color), focused, TILE_PAD_END) {
        LiveText(pick.label, PICK_NAME_TEXT, Color.White, AppTypography.asapCondensedBold)
        LiveText(
            "${pick.count} · ${pick.live} live", TILE_META_TEXT, if (focused) META_FOCUSED else PANEL_NOTE_COLOR,
            AppTypography.interSemiBold, Modifier.padding(top = PICK_META_TOP),
        )
    }
}

@Composable
private fun ResultGrid(found: List<LiveTvData.Channel>, focused: Int, nowS: Long) {
    val state = rememberLazyListState()
    val line = if (focused >= 0) focused / LiveSearch.RESULT_COLUMNS else -1
    // New results while typing start at the top; a focused line stays in view.
    LaunchedEffect(found) { if (line < 0) state.scrollToItem(0) }
    LaunchedEffect(line) {
        if (line < 0) return@LaunchedEffect
        keepInView(line, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, RESULT_VISIBLE_ROWS)
            ?.let { state.scrollToItem(it) }
    }
    // Grown by the ring's width on every side, as the channel list is: a list
    // clips its children, and the focus ring is drawn outside its tile.
    LazyColumn(
        state = state,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(RESULT_ROW_GAP),
        contentPadding = PaddingValues(FOCUS_RING),
        modifier = Modifier
            .offset(TILES_LEFT - FOCUS_RING, TILES_TOP - FOCUS_RING)
            .size(TILES_WIDTH + FOCUS_RING * 2, RESULTS_HEIGHT + FOCUS_RING * 2),
    ) {
        itemsIndexed(found.chunked(LiveSearch.RESULT_COLUMNS)) { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(RESULT_COL_GAP)) {
                pair.forEachIndexed { c, channel -> ResultTile(channel, focused == r * LiveSearch.RESULT_COLUMNS + c, nowS) }
            }
        }
    }
}

@Composable
private fun ResultTile(channel: LiveTvData.Channel, focused: Boolean, nowS: Long) {
    // A game still to start is not judged by its stream: no dimming, no dot.
    val upcoming = LiveGames.upcoming(channel, nowS)
    Box {
        TileFrame(RESULT_WIDTH, RESULT_HEIGHT, RESULT_BAND, Color(channel.rowColor), focused, RESULT_PAD_END) {
            LiveText(
                channel.name, RESULT_NAME_TEXT, if (channel.off && !upcoming && !focused) NAME_OFF else Color.White,
                AppTypography.asapCondensedBold,
            )
            LiveText(
                LiveSearch.tileMeta(channel, nowS), TILE_META_TEXT,
                if (focused) META_FOCUSED else PANEL_NOTE_COLOR, AppTypography.interSemiBold,
            )
        }
        if (!upcoming) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = RESULT_DOT_END)) {
                Dot(if (channel.off) OFF_DOT else LIVE_DOT)
            }
        }
    }
}

/** A tile: its row's colour down the left edge, its lines centred; blue with a white ring when focused. */
@Composable
private fun TileFrame(
    width: Dp,
    height: Dp,
    band: Dp,
    bandColor: Color,
    focused: Boolean,
    endPad: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TILE_RADIUS)
    Box(
        Modifier
            .size(width, height)
            .focusRing(focused, TILE_RADIUS)
            .clip(shape)
            .background(if (focused) ROW_FOCUSED else ROW_IDLE)
    ) {
        Box(Modifier.fillMaxHeight().width(band).background(bandColor))
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(start = TILE_PAD_START, end = endPad),
            content = content,
        )
    }
}

@Composable
private fun SearchHintBar(picks: Boolean) {
    HintBox(Modifier.offset(TILES_LEFT, SEARCH_HINT_TOP).size(TILES_WIDTH, HINT_HEIGHT)) {
        if (picks) KeyHint("◀ ▶", "keys and picks") else KeyHint("▶", "results")
        HintSeparator()
        KeyHint("Click", if (picks) "choose" else "watch")
        HintSeparator()
        KeyHint("↩", "back")
    }
}
