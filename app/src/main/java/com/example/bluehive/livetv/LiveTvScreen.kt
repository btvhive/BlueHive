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

/**
 * The Live TV page, design C: category chips along the top, a numbered channel
 * list on the left, the focused channel's details on the right. One focus target
 * (the page) owns the remote: Up/Down move in the list, Left/Right change the
 * category, Click watches, Back leaves (the Activity's handler).
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
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { page.onKey(it, onWatch, onRetry = { attempt++ }) }
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
            else -> LiveGuide(rows, page.selectedRow, page.focused)
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

    suspend fun load(context: Context, profileId: Int, keepPlace: Boolean) {
        val keepRow = rows?.getOrNull(selectedRow)?.name
        runCatchingCancellable { LiveTvData.load(context, profileId) }
            .onSuccess { fresh ->
                rows = fresh
                failed = false
                if (fresh.isEmpty()) return@onSuccess
                val row = if (keepPlace) fresh.indexOfFirst { it.name == keepRow }.takeIf { it >= 0 } ?: 0 else 0
                val channels = fresh[row].channels
                val watched = channels.indexOfFirst { it.id == LiveTvData.lastWatchedId }
                selectedRow = row
                focused = when {
                    keepPlace && watched >= 0 -> watched
                    keepPlace -> focused.coerceIn(0, channels.lastIndex)
                    else -> 0
                }
            }
            .onFailure { if (rows == null) failed = true }
    }

    /** Remote keys. Back is not handled here: the Activity owns it. */
    fun onKey(event: KeyEvent, onWatch: (LiveTvData.Row, Int) -> Unit, onRetry: () -> Unit): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val native = event.nativeKeyEvent
        val code = native.keyCode
        val rows = rows
        if (rows.isNullOrEmpty()) {
            if (ExoPlayerConfig.isCenterKey(code) && native.repeatCount == 0 && (failed || rows != null)) {
                BlueHiveApplication.playClickSound()
                failed = false
                onRetry()
            }
            return ExoPlayerConfig.isCenterKey(code)
        }
        val last = rows[selectedRow].channels.lastIndex
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> if (focused > 0) { focused--; BlueHiveApplication.playHoverSound() }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> if (focused < last) { focused++; BlueHiveApplication.playHoverSound() }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (selectedRow > 0) moveRow(selectedRow - 1)
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedRow < rows.lastIndex) moveRow(selectedRow + 1)
            else -> {
                if (!ExoPlayerConfig.isCenterKey(code)) return false
                if (native.repeatCount == 0) {
                    BlueHiveApplication.playClickSound()
                    onWatch(rows[selectedRow], focused)
                }
            }
        }
        return true
    }

    private fun moveRow(to: Int) {
        selectedRow = to
        focused = 0
        BlueHiveApplication.playTitleCardNavigation()
    }
}

@Composable
private fun LiveGuide(rows: List<LiveTvData.Row>, selectedRow: Int, focused: Int) {
    val row = rows[selectedRow]
    CategoryChips(rows, selectedRow)
    key(selectedRow) { ChannelList(row.channels, focused) }
    row.channels.getOrNull(focused)?.let { DetailPanel(it) }
    GuideHintBar()
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

@Composable
private fun CategoryChip(name: String, on: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .background(if (on) CHIP_ON else CHIP_OFF, RoundedCornerShape(CHIP_RADIUS))
            .padding(horizontal = CHIP_PAD_H),
    ) {
        LiveText(name, CHIP_TEXT, if (on) Color.White else CHIP_TEXT_OFF, AppTypography.interSemiBold)
    }
}

@Composable
private fun ChannelList(channels: List<LiveTvData.Channel>, focused: Int) {
    val state = rememberLazyListState()
    LaunchedEffect(focused) {
        val first = state.firstVisibleItemIndex
        when {
            focused < first -> state.scrollToItem(focused)
            focused >= first + LIST_VISIBLE_ROWS -> state.scrollToItem(focused - LIST_VISIBLE_ROWS + 1)
        }
    }
    // Grown by the ring's width on every side, rows kept where the mockup puts
    // them: a list clips its children, and the focus ring is drawn outside its row.
    LazyColumn(
        state = state,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        contentPadding = PaddingValues(FOCUS_RING),
        modifier = Modifier
            .offset(LIST_LEFT - FOCUS_RING, LIST_TOP - FOCUS_RING)
            .size(LIST_WIDTH + FOCUS_RING * 2, LIST_HEIGHT + FOCUS_RING * 2),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, c -> ChannelRow(c, i == focused) }
    }
}

@Composable
private fun ChannelRow(channel: LiveTvData.Channel, focused: Boolean) {
    val shape = RoundedCornerShape(ROW_RADIUS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .focusRing(focused, ROW_RADIUS)
            .background(if (focused) ROW_FOCUSED else ROW_IDLE, shape)
            .padding(horizontal = ROW_PAD_H),
    ) {
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

@Composable
private fun DetailPanel(channel: LiveTvData.Channel) {
    val shape = RoundedCornerShape(PANEL_RADIUS)
    val side = Modifier.padding(start = PANEL_SIDE, end = PANEL_SIDE)
    Column(
        Modifier
            .offset(PANEL_LEFT, PANEL_TOP)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .clip(shape)
            .background(PANEL_BG)
            .border(PANEL_BORDER, PANEL_BORDER_COLOR, shape)
    ) {
        Box(Modifier.fillMaxWidth().height(PANEL_BAND).background(Color(channel.rowColor)))
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
private fun GuideHintBar() {
    HintBox(Modifier.offset(HINT_LEFT, HINT_TOP).size(HINT_WIDTH, HINT_HEIGHT)) {
        KeyHint("▲▼", "channels")
        HintSeparator()
        KeyHint("‹ ›", "categories")
        HintSeparator()
        KeyHint("↩", "back")
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
private fun Tag(text: String) {
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
private fun Dot(color: Color) {
    Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
}

@Composable
private fun HintBox(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
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
private fun KeyHint(key: String, label: String) {
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
private fun HintSeparator() {
    LiveText("|", HINT_TEXT, HINT_SEPARATOR, AppTypography.interSemiBold)
}

/** The mockup's `box-shadow: 0 0 0 2px #fff`: a white ring drawn OUTSIDE the row. */
private fun Modifier.focusRing(show: Boolean, radius: Dp): Modifier = if (!show) this else drawBehind {
    val ring = FOCUS_RING.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-ring, -ring),
        size = Size(size.width + 2 * ring, size.height + 2 * ring),
        cornerRadius = CornerRadius(radius.toPx() + ring),
    )
}
