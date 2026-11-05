package com.example.bluehive.livetv

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.runCatchingCancellable
import com.example.bluehive.webview.exoplayer_resources.ExoPlayerConfig
import kotlinx.coroutines.delay

/**
 * The Live TV page, design C: category chips along the top, a numbered channel
 * list on the left under a search bar, the focused channel's details on the
 * right. One focus target (the page) owns the remote: Up/Down move in the list
 * (Up from the first channel reaches the bar), Left/Right change the category,
 * Click watches -- or, on the bar, opens the search page (LiveSearchPage.kt,
 * whose keyboard takes its own keys) -- and Back leaves (the Activity's handler).
 * On now, today's games, comes first, with sport pills in the bar's place
 * (LiveGames.kt).
 *
 * [resumeTick] goes up each time the page comes back from the player; the list
 * reloads in place (fresh off-air marks, the Recently watched row) and the
 * cursor returns to the channel just watched.
 */
@Composable
internal fun LiveTvScreen(profileId: Int, resumeTick: Int, onWatch: (LiveTvData.Row, Int) -> Unit) {
    val context = LocalContext.current
    val page = remember { LiveTvPageState() }
    val focus = remember { FocusRequester() }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { page.load(context, profileId, keepPlace = false) }
    LaunchedEffect(resumeTick) { if (resumeTick > 0) page.load(context, profileId, keepPlace = true) }
    // The search page's keyboard takes the focus while it is open; the page gets it back after.
    LaunchedEffect(page.search.isOpen) { if (!page.search.isOpen) focus.requestFocus() }
    // On now's LIVE marks follow the clock: a game turns LIVE at its start.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            page.tick()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                page.onKey(it, onWatch, onRetry = { attempt++ }) { state ->
                    LiveTvData.setLocalState(context, state)
                    page.showLocal(LiveTvData.rebuild(context, profileId), state)
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.home_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val rows = page.rows
        when {
            rows == null && page.failed -> PageMessage("Couldn't load the channel list", retry = true)
            rows == null -> PageMessage("Loading channels…", retry = false)
            rows.isEmpty() -> PageMessage("No channels right now", retry = true)
            page.search.isOpen -> LiveSearchPage(page.search, page.nowS)
            else -> LiveGuide(page, rows)
        }
    }
}

/** What the page shows and where its cursor is. */
@Stable
internal class LiveTvPageState {
    var rows by mutableStateOf<List<LiveTvData.Row>?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var selectedRow by mutableIntStateOf(0)
        private set
    var focused by mutableIntStateOf(0)
        private set
    /** The search bar over the list has the focus (Up from the first channel). */
    var onBar by mutableStateOf(false)
        private set
    /** Local News' state pill, beside the bar, has the focus (Up to the bar, then Right). */
    var onPill by mutableStateOf(false)
        private set
    /** The state picked on this TV for Local News; null until one is. */
    var localState by mutableStateOf<String?>(null)
        private set
    val search = LiveSearchState { rows }
    /** Local News' state list is up (the pill was clicked), with its cursor. */
    var picking by mutableStateOf(false)
        private set
    var pickCursor by mutableIntStateOf(0)
        private set
    var choices by mutableStateOf<List<LocalNews.StateChoice>>(emptyList())
        private set
    /** On now's sport pills: the one picked (null = All), and their focus and cursor. */
    var sport by mutableStateOf<String?>(null)
        private set
    var onPills by mutableStateOf(false)
        private set
    var pillCursor by mutableIntStateOf(0)
        private set
    /** The clock On now's LIVE marks follow (epoch seconds), ticked by the screen. */
    var nowS by mutableLongStateOf(System.currentTimeMillis() / 1000)
        private set

    fun tick() {
        nowS = System.currentTimeMillis() / 1000
    }

    /** A row as the page shows it: On now narrowed to the picked sport. */
    fun shown(row: LiveTvData.Row): LiveTvData.Row =
        if (row.name != LiveGames.ROW || sport == null) row else row.copy(channels = LiveGames.shown(row.channels, sport))

    suspend fun load(context: Context, profileId: Int, keepPlace: Boolean) {
        val keepRow = rows?.getOrNull(selectedRow)?.name
        runCatchingCancellable { LiveTvData.load(context, profileId) }
            .onSuccess { fresh ->
                rows = fresh
                localState = LiveTvData.localState(context)
                failed = false
                // A fresh start is All; so is a sport whose games have all ended.
                val games = fresh.firstOrNull { it.name == LiveGames.ROW }?.channels.orEmpty()
                if (!keepPlace || LiveGames.shown(games, sport).isEmpty()) sport = null
                if (fresh.isEmpty()) return@onSuccess
                val row = if (keepPlace) fresh.indexOfFirst { it.name == keepRow }.takeIf { it >= 0 } ?: 0 else 0
                val channels = shown(fresh[row]).channels
                val watched = channels.indexOfFirst { it.id == LiveTvData.lastWatchedId }
                selectedRow = row
                focused = when {
                    keepPlace && watched >= 0 -> watched
                    keepPlace -> focused.coerceIn(0, channels.lastIndex.coerceAtLeast(0))
                    else -> 0
                }
                if (!keepPlace) {
                    onBar = false
                    onPill = false
                    onPills = false
                }
                search.reconcile()
            }
            .onFailure { if (rows == null) failed = true }
    }

    /**
     * Remote keys. Back is the Activity's, except while the search page or the
     * state list is open. [onPickState] saves a state picked from the list.
     */
    fun onKey(
        event: KeyEvent,
        onWatch: (LiveTvData.Row, Int) -> Unit,
        onRetry: () -> Unit,
        onPickState: (String) -> Unit,
    ): Boolean {
        val native = event.nativeKeyEvent
        val code = native.keyCode
        // The search page's Back is taken here, before anything under the page sees
        // it: one press, on release, closes the page. Left alone, Compose spends an
        // unclaimed Back on moving the focus out of the keyboard card (Back is its
        // FocusDirection.Exit), so leaving took two presses (v504-v511). The state
        // list's Back cancels it here too, rather than leaving Live TV.
        if ((search.isOpen || picking) && code == AndroidKeyEvent.KEYCODE_BACK) {
            if (event.type == KeyEventType.KeyUp && !native.isCanceled) {
                if (search.isOpen) search.back() else cancelPick()
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        val rows = rows
        if (rows.isNullOrEmpty()) {
            if (ExoPlayerConfig.isCenterKey(code) && native.repeatCount == 0 && (failed || rows != null)) {
                BlueHiveApplication.playClickSound()
                failed = false
                onRetry()
            }
            return ExoPlayerConfig.isCenterKey(code)
        }
        if (search.isOpen) return search.onKey(code, native.repeatCount, onWatch)
        if (picking) return pickKey(code, native.repeatCount, onPickState)
        if (onPills) return pillsKey(code, native.repeatCount, rows)
        val row = shown(rows[selectedRow])
        val top = onBar || onPill
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> when {
                top -> Unit
                focused > 0 -> { focused--; BlueHiveApplication.playHoverSound() }
                row.name == LiveGames.ROW -> { enterPills(rows); BlueHiveApplication.playHoverSound() }
                else -> { onBar = true; BlueHiveApplication.playHoverSound() }
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> when {
                top -> if (row.channels.isNotEmpty()) {
                    onBar = false
                    onPill = false
                    BlueHiveApplication.playHoverSound()
                }
                focused < row.channels.lastIndex -> { focused++; BlueHiveApplication.playHoverSound() }
            }
            // Local News' top line is [bar][pill]: the pill sits between the bar and the next category.
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> when {
                onPill -> { onPill = false; onBar = true; BlueHiveApplication.playHoverSound() }
                selectedRow > 0 -> moveRow(selectedRow - 1)
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> when {
                onBar && row.name == LocalNews.ROW -> { onBar = false; onPill = true; BlueHiveApplication.playHoverSound() }
                selectedRow < rows.lastIndex -> moveRow(selectedRow + 1)
            }
            else -> {
                if (!ExoPlayerConfig.isCenterKey(code)) return false
                if (native.repeatCount == 0) {
                    BlueHiveApplication.playClickSound()
                    when {
                        onBar -> search.open()
                        onPill -> openPick()
                        focused <= row.channels.lastIndex -> onWatch(row, focused)
                    }
                }
            }
        }
        return true
    }

    /**
     * On now's top line is its sport pills, in the search bar's place: Left and
     * Right pick a sport at once (the list follows), past the last pill is the
     * next category; Up goes straight to the first channel category (Motorsport,
     * v654); Down or Click goes into the games.
     */
    private fun pillsKey(code: Int, repeat: Int, rows: List<LiveTvData.Row>): Boolean {
        val pills = LiveGames.pills(rows[selectedRow].channels)
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> LiveGames.firstCategoryAfter(rows.map { it.name }, selectedRow)?.let(::moveRow)
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> when {
                pillCursor > 0 -> pickSport(pills, pillCursor - 1)
                selectedRow > 0 -> moveRow(selectedRow - 1)
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> when {
                pillCursor < pills.lastIndex -> pickSport(pills, pillCursor + 1)
                selectedRow < rows.lastIndex -> moveRow(selectedRow + 1)
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> leavePills()
            else -> {
                if (!ExoPlayerConfig.isCenterKey(code)) return false
                if (repeat == 0) leavePills()
            }
        }
        return true
    }

    private fun enterPills(rows: List<LiveTvData.Row>) {
        val pills = LiveGames.pills(rows[selectedRow].channels)
        pillCursor = pills.indexOfFirst { it.sport == sport }.coerceAtLeast(0)
        onPills = true
    }

    private fun pickSport(pills: List<LiveGames.Pill>, at: Int) {
        pillCursor = at
        sport = pills[at].sport
        focused = 0
        BlueHiveApplication.playHoverSound()
    }

    private fun leavePills() {
        onPills = false
        focused = 0
        BlueHiveApplication.playHoverSound()
    }

    private fun openPick() {
        val list = LiveTvData.stateChoices()
        if (list.isEmpty()) {
            BlueHiveApplication.playErrorSound()
            return
        }
        choices = list
        pickCursor = list.indexOfFirst { it.code == localState }.coerceAtLeast(0)
        picking = true
    }

    private fun cancelPick() {
        BlueHiveApplication.playBackOutSound()
        picking = false
    }

    private fun pickKey(code: Int, repeat: Int, onPickState: (String) -> Unit): Boolean {
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> if (pickCursor > 0) { pickCursor--; BlueHiveApplication.playHoverSound() }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN ->
                if (pickCursor < choices.lastIndex) { pickCursor++; BlueHiveApplication.playHoverSound() }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> {
                if (!ExoPlayerConfig.isCenterKey(code)) return false
                if (repeat == 0) {
                    BlueHiveApplication.playClickSound()
                    picking = false
                    onPickState(choices[pickCursor].code)
                }
            }
        }
        return true
    }

    /** A state was just picked: the rebuilt rows, Local News on its first station. */
    fun showLocal(fresh: List<LiveTvData.Row>, state: String) {
        rows = fresh
        localState = state
        val row = fresh.indexOfFirst { it.name == LocalNews.ROW }.coerceAtLeast(0)
        selectedRow = row
        focused = 0
        onBar = false
        onPill = fresh[row].channels.isEmpty()
    }

    /**
     * The top line follows from row to row: the bar in most, On now's sport
     * pills in its place there; Local News' pill is its own, so leaving the row
     * from it lands on the bar. Arriving at Local News with no state picked (no
     * stations to land on) starts on its pill.
     */
    private fun moveRow(to: Int) {
        selectedRow = to
        focused = 0
        if (onPill || onPills) {
            onPill = false
            onPills = false
            onBar = true
        }
        val all = rows ?: return
        val row = all.getOrNull(to)
        if (onBar && row?.name == LiveGames.ROW) {
            onBar = false
            enterPills(all)
        }
        if (!onBar && !onPills && row?.name == LocalNews.ROW && row.channels.isEmpty()) onPill = true
        BlueHiveApplication.playTitleCardNavigation()
    }
}

@Composable
private fun LiveGuide(page: LiveTvPageState, rows: List<LiveTvData.Row>) {
    val whole = rows[page.selectedRow]
    val row = page.shown(whole)
    val local = row.name == LocalNews.ROW
    val games = row.name == LiveGames.ROW
    val nowS = page.nowS
    CategoryChips(rows, page.selectedRow)
    if (games) {
        val pills = remember(whole) { LiveGames.pills(whole.channels) }
        SportPills(pills, page.sport, page.pillCursor, page.onPills)
    } else {
        SearchBar(LiveSearch.barHint(page.search.channels.size), page.onBar, if (local) BAR_LOCAL_WIDTH else LIST_WIDTH)
    }
    if (local) StatePill(page.localState, page.onPill)
    if (page.picking) {
        StateList(page.choices, page.pickCursor)
        page.choices.getOrNull(page.pickCursor)?.let { StateChoicePanel(it, row.color) }
    } else {
        // Keyed by the sport too: a list kept across pills would hold its old scroll.
        if (local && row.channels.isEmpty()) PickStateMessage()
        else key(page.selectedRow, page.sport) {
            ChannelList(row.channels, page.focused, showFocus = !page.onBar && !page.onPill && !page.onPills, nowS)
        }
        val states = remember(rows) { LiveTvData.stateChoices().size }
        when {
            page.onBar -> SearchInvitePanel(rows, page.search.channels)
            page.onPill -> StatePillPanel(page.localState, row.channels.size, states, row.color)
            else -> row.channels.getOrNull(page.focused)?.let { if (it.start > 0) GamePanel(it, nowS) else DetailPanel(it) }
        }
    }
    GuideHintBar(page.picking, games)
}

/** Design B's bar: always over the list, one Up from the first channel; Click opens the search page. */
@Composable
private fun SearchBar(hint: String, focused: Boolean, width: Dp) {
    val shape = RoundedCornerShape(BAR_RADIUS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        modifier = Modifier
            .offset(LIST_LEFT, BAR_TOP)
            .size(width, BAR_HEIGHT)
            .focusRing(focused, BAR_RADIUS)
            .background(if (focused) ROW_FOCUSED else BAR_BG, shape)
            .then(if (focused) Modifier else Modifier.border(BAR_BORDER, BAR_EDGE, shape))
            .padding(horizontal = BAR_PAD_H),
    ) {
        Image(
            painter = painterResource(R.drawable.magnifying_glass_raw_image),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (focused) Color.White else BAR_INK),
            modifier = Modifier.size(BAR_ICON),
        )
        LiveText(hint, BAR_TEXT, if (focused) NUMBER_FOCUSED else BAR_INK, AppTypography.interSemiBold)
    }
}

/** The details panel while the bar has the focus: what the search covers, and how to start. */
@Composable
private fun SearchInvitePanel(rows: List<LiveTvData.Row>, channels: List<LiveTvData.Channel>) {
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    val tryLine = remember(channels) { LiveSearch.tryLine(channels) }
    PanelFrame(GOLD) {
        LiveText("Search channels", PANEL_NAME_TEXT, Color.White, AppTypography.asapCondensedBold,
            side.padding(top = PANEL_NAME_TOP))
        LiveText(LiveSearch.scope(rows, channels.size), PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold,
            side.padding(top = PANEL_META_TOP))
        LiveText("Type a few letters or say a name", PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold,
            side.padding(top = PANEL_LIVE_TOP))
        if (tryLine != null) {
            LiveText(tryLine, PANEL_NOTE_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold, side.padding(top = PANEL_NOTE_TOP))
        }
        HintBox(side.padding(top = PANEL_HINT_TOP).height(PANEL_HINT_HEIGHT)) { KeyHint("Click", "to search") }
    }
}

@Composable
private fun CategoryChips(rows: List<LiveTvData.Row>, selected: Int) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        modifier = Modifier.padding(start = CHIPS_LEFT, top = CHIPS_TOP).fillMaxWidth(),
    ) {
        itemsIndexed(rows, key = { _, r -> r.name }) { i, r -> CategoryChip(r.name, on = i == selected) }
    }
}

/** A category's chip; On now's carries a live dot. */
@Composable
private fun CategoryChip(name: String, on: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_DOT_GAP),
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .background(if (on) CHIP_ON else CHIP_OFF, RoundedCornerShape(CHIP_RADIUS))
            .padding(horizontal = CHIP_PAD_H),
    ) {
        if (name == LiveGames.ROW) Dot(LIVE_DOT)
        LiveText(name, CHIP_TEXT, if (on) Color.White else CHIP_TEXT_OFF, AppTypography.interSemiBold)
    }
}

/** A row's channels -- or games, which show their start and league instead (LiveGamesViews.kt). */
@Composable
private fun ChannelList(channels: List<LiveTvData.Channel>, focused: Int, showFocus: Boolean, nowS: Long) {
    val state = rememberLazyListState()
    LaunchedEffect(focused) {
        keepInView(focused, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, LIST_VISIBLE_ROWS)
            ?.let { state.scrollToItem(it) }
    }
    val leagueWidth = leagueColumn(channels)
    GuideList(state) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
            if (c.start > 0) GameRow(c, showFocus && i == focused, nowS, leagueWidth)
            else ChannelRow(c, showFocus && i == focused)
        }
    }
}

/**
 * The left-hand list's frame, for channels and for Local News' states alike:
 * grown by the ring's width on every side, rows kept where the mockup puts
 * them -- a list clips its children, and the focus ring is drawn outside its row.
 */
@Composable
internal fun GuideList(state: LazyListState, content: LazyListScope.() -> Unit) {
    LazyColumn(
        state = state,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        contentPadding = PaddingValues(FOCUS_RING),
        modifier = Modifier
            .offset(LIST_LEFT - FOCUS_RING, LIST_TOP - FOCUS_RING)
            .size(LIST_WIDTH + FOCUS_RING * 2, LIST_HEIGHT + FOCUS_RING * 2),
        content = content,
    )
}

/** One row of the left-hand list: blue with the white ring when focused. */
@Composable
internal fun RowFrame(focused: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .focusRing(focused, ROW_RADIUS)
            .background(if (focused) ROW_FOCUSED else ROW_IDLE, RoundedCornerShape(ROW_RADIUS))
            .padding(horizontal = ROW_PAD_H),
        content = content,
    )
}

@Composable
private fun ChannelRow(channel: LiveTvData.Channel, focused: Boolean) {
    RowFrame(focused) {
        LiveText(
            channel.number.toString(), NUMBER_TEXT, if (focused) NUMBER_FOCUSED else NUMBER_IDLE,
            AppTypography.interSemiBold, Modifier.width(NUMBER_WIDTH),
        )
        Box(Modifier.width(FLAG_WIDTH)) { if (channel.flag.isNotEmpty()) Tag(channel.flag) }
        LiveText(
            channel.name, NAME_TEXT, if (channel.off && !focused) NAME_OFF else Color.White,
            AppTypography.asapCondensedBold, Modifier.weight(1f),
        )
        Tag(channel.quality)
        Spacer(Modifier.width(TAG_GAP))
        Dot(if (channel.off) OFF_DOT else LIVE_DOT)
    }
}

/** The right-hand panel: [band] across its top (none for a game, whose picture is there), then [content]. */
@Composable
internal fun PanelFrame(band: Color?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(PANEL_RADIUS)
    Column(
        Modifier
            .offset(PANEL_LEFT, PANEL_TOP)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .clip(shape)
            .background(PANEL_BG)
            .border(PANEL_BORDER, PANEL_BORDER_COLOR, shape)
    ) {
        if (band != null) Box(Modifier.fillMaxWidth().height(PANEL_BAND).background(band))
        content()
    }
}

@Composable
private fun DetailPanel(channel: LiveTvData.Channel) {
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    PanelFrame(Color(channel.rowColor)) {
        LiveText(channel.name, PANEL_NAME_TEXT, Color.White, AppTypography.asapCondensedBold,
            side.padding(top = PANEL_NAME_TOP), maxLines = 2)
        val meta = listOf(channel.row, channel.flag, channel.quality).filter { it.isNotEmpty() }.joinToString(" · ")
        LiveText(meta, PANEL_META_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold, side.padding(top = PANEL_META_TOP))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = side.padding(top = PANEL_LIVE_TOP)) {
            Dot(if (channel.off) OFF_DOT else LIVE_DOT)
            Spacer(Modifier.width(DOT_GAP))
            LiveText(
                if (channel.off) "Off air when last tried" else "Live now", PANEL_META_TEXT,
                if (channel.off) PANEL_NOTE_COLOR else PANEL_LIVE_COLOR, AppTypography.interSemiBold,
            )
        }
        if (channel.hasBackup) {
            LiveText("Backup feed ready", PANEL_NOTE_TEXT, PANEL_NOTE_COLOR, AppTypography.interSemiBold,
                side.padding(top = PANEL_NOTE_TOP))
        }
        HintBox(side.padding(top = PANEL_HINT_TOP).height(PANEL_HINT_HEIGHT)) { KeyHint("Click", "to watch") }
    }
}

@Composable
private fun GuideHintBar(picking: Boolean, games: Boolean) {
    HintBox(Modifier.offset(HINT_LEFT, HINT_TOP).size(HINT_WIDTH, HINT_HEIGHT)) {
        KeyHint("▲▼", if (picking) "states" else if (games) "games" else "channels")
        HintSeparator()
        if (picking) KeyHint("Click", "choose") else KeyHint("‹ ›", "categories")
        HintSeparator()
        KeyHint("↩", if (picking) "cancel" else "back")
    }
}

@Composable
private fun PageMessage(text: String, retry: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(top = PANEL_TOP * 3)) {
        LiveText(text, MESSAGE_TEXT, CHIP_TEXT_OFF, AppTypography.interSemiBold)
        if (retry) HintBox(Modifier.padding(top = PANEL_HINT_TOP).height(PANEL_HINT_HEIGHT)) { KeyHint("Click", "to try again") }
    }
}

// ── Shared pieces (the player's overlays draw their own, at their own scale) ──

@Composable
internal fun LiveText(
    text: String,
    size: TextUnit,
    color: Color,
    family: FontFamily,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontFamily = family,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = modifier,
    )
}

@Composable
internal fun Tag(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(TAG_HEIGHT)
            .background(TAG_BG, RoundedCornerShape(TAG_RADIUS))
            .padding(horizontal = TAG_PAD_H),
    ) {
        LiveText(text, TAG_TEXT, TAG_TEXT_COLOR, AppTypography.interSemiBold)
    }
}

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
}

@Composable
internal fun HintBox(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(HINT_RADIUS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HINT_GAP),
        modifier = modifier
            .background(HINT_BG, shape)
            .border(HINT_BORDER, GOLD, shape)
            .padding(horizontal = HINT_PAD_H),
        content = content,
    )
}

@Composable
internal fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(TAG_HEIGHT)
                .background(Color.White, RoundedCornerShape(KEY_RADIUS))
                .padding(horizontal = KEY_PAD_H),
        ) {
            LiveText(key, HINT_TEXT, KEY_TEXT_COLOR, AppTypography.interSemiBold)
        }
        Spacer(Modifier.width(KEY_GAP))
        LiveText(label, HINT_TEXT, HINT_TEXT_COLOR, AppTypography.interSemiBold)
    }
}

@Composable
internal fun HintSeparator() {
    LiveText("|", HINT_TEXT, HINT_SEPARATOR, AppTypography.interSemiBold)
}

/**
 * Where a list of [visible] whole rows must scroll so the [focused] one is whole:
 * to it when it sits above the first visible row, or IS that row but partly
 * scrolled off; to [visible] - 1 rows above it when it is below the window;
 * null when it is in view. A list can only scroll to its end, which can stop
 * mid-row (ten channels under nine rows' room), so on the way back up row 0
 * stayed half under the search bar until the offset was checked too (v509).
 */
internal fun keepInView(focused: Int, first: Int, firstOffset: Int, visible: Int): Int? = when {
    focused < first || (focused == first && firstOffset > 0) -> focused
    focused >= first + visible -> focused - visible + 1
    else -> null
}

/** The mockup's `box-shadow: 0 0 0 2px #fff`: a white ring drawn OUTSIDE the row. */
internal fun Modifier.focusRing(show: Boolean, radius: Dp): Modifier = if (!show) this else drawBehind {
    val ring = FOCUS_RING.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-ring, -ring),
        size = Size(size.width + 2 * ring, size.height + 2 * ring),
        cornerRadius = CornerRadius(radius.toPx() + ring),
    )
}
