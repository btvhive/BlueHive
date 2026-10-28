package com.example.bluehive.livetv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.utilities.AppTypography

// ─────────────────────────────────────────────────────────────────────────────
//  The six player states of the approved mockup live_tv_player_states. Each was
//  drawn in a 334 px wide frame, which is the TV's 960 dp, so every px is x 2.874
//  here -- the same one-factor rule as the page (x 1.412 for its 680 px frame).
// ─────────────────────────────────────────────────────────────────────────────

private const val P = 2.874f
private fun px(v: Float): Dp = (v * P).dp
private fun pt(v: Float): TextUnit = (v * P).sp

private val CARD_BG = Color(0xFF121213)
private val CARD_BORDER = Color(0xFF3A3737)
private val CARD_SUB = Color(0xFF9AA1B2)
private val META = Color(0xFFCFD3DC)
private val BANNER_BG = Color(0xD108090C)     // rgba(8,9,12,.82)
private val BANNER_BORDER = Color(0xFF2A2E3B)
private val LIST_BG = Color(0xEB08090C)       // rgba(8,9,12,.92)
private val HINT_FILL = Color(0xE00A0A0E)     // rgba(10,10,14,.88)
private val TUNING_BG = Color(0xFF060607)
private val BUTTON_OFF = Color(0xFF222340)
private val BUTTON_OFF_TEXT = Color(0xFFD2D0D0)

/** 1 -- black, a turning ring, "Tuning in to <channel>…". */
@Composable
internal fun TuningOverlay(name: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(TUNING_BG)) {
        val spin = rememberInfiniteTransition(label = "tuning")
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ring")
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.38f)
                .size(px(30f))
                .rotate(angle)
                .drawBehind {
                    val w = px(3f).toPx()
                    val inset = Offset(w / 2, w / 2)
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(CHIP_ON, 0f, 360f, false, inset, arc, style = Stroke(w))
                    drawArc(Color.White, -135f, 90f, false, inset, arc, style = Stroke(w))
                }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.60f),
        ) {
            LiveText("Tuning in to ", pt(13f), Color.White, AppTypography.interSemiBold)
            LiveText(name, pt(15f), Color.White, AppTypography.asapCondensedBold)
            LiveText("…", pt(13f), Color.White, AppTypography.interSemiBold)
        }
    }
}

/** 2 -- top-left: the channel and "Live · FHD · Motorsport"; fades with the hint bar. */
@Composable
internal fun ChannelBanner(name: String, quality: String, row: String) {
    val shape = RoundedCornerShape(px(8f))
    Column(
        Modifier
            .offset(px(10f), px(10f))
            .background(BANNER_BG, shape)
            .border(px(1f), BANNER_BORDER, shape)
            .padding(horizontal = px(10f), vertical = px(6f))
    ) {
        LiveText(name, pt(16f), Color.White, AppTypography.asapCondensedBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(px(7f)).background(LIVE_DOT, CircleShape))
            Spacer(Modifier.width(px(4f)))
            LiveText(listOf("Live", quality, row).filter { it.isNotEmpty() }.joinToString(" · "),
                pt(11f), META, AppTypography.interSemiBold)
        }
    }
}

/** 2 -- bottom: "▲▼ channel | Click list | ↩ exit". */
@Composable
internal fun BoxScope.PlayerHintBar() {
    PlayerHint(Modifier.align(Alignment.BottomCenter).padding(start = px(10f), end = px(10f), bottom = px(9f)).fillMaxWidth()) {
        PlayerKey("▲▼", "channel")
        Separator()
        PlayerKey("Click", "list")
        Separator()
        PlayerKey("↩", "exit")
    }
}

/** 4 -- top centre: what just changed ("… switched to the backup feed"). */
@Composable
internal fun BoxScope.NoticeBar(text: String) {
    PlayerHint(Modifier.align(Alignment.TopCenter).padding(top = px(10f)).fillMaxWidth(0.8f).height(px(26f))) {
        LiveText(text, pt(11f), Color(0xFFEEEEEE), AppTypography.interSemiBold)
    }
}

/** 3 -- the channel list over the left 44 % while the picture keeps playing. */
@Composable
internal fun ChannelListOverlay(title: String, names: List<String>, focused: Int) {
    val state = rememberLazyListState()
    val visible = 7
    LaunchedEffect(focused) {
        val first = state.firstVisibleItemIndex
        when {
            focused < first -> state.scrollToItem(focused)
            focused >= first + visible -> state.scrollToItem(focused - visible + 1)
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.44f)
            .background(LIST_BG)
            .padding(px(8f))
    ) {
        LiveText(title, pt(14f), Color.White, AppTypography.asapCondensedBold,
            Modifier.padding(start = px(2f), bottom = px(5f)))
        LazyColumn(state = state, userScrollEnabled = false, contentPadding = PaddingValues(px(2f))) {
            itemsIndexed(names) { i, name -> ListRow(name, i == focused) }
        }
    }
}

@Composable
private fun ListRow(name: String, focused: Boolean) {
    val radius = px(5f)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(px(20f))
            .outerRing(focused, radius, px(2f))
            .background(if (focused) CHIP_ON else Color.Transparent, RoundedCornerShape(radius))
            .padding(horizontal = px(7f)),
    ) {
        LiveText(name, pt(12f), if (focused) Color.White else META, AppTypography.interSemiBold)
    }
}

/** 5 -- every feed failed: "Try again" / "Back to channels". */
@Composable
internal fun BoxScope.OffAirCard(name: String, focusedButton: Int) {
    Box(Modifier.fillMaxSize().background(TUNING_BG))
    PlayerCard {
        LiveText("$name is off the air right now", pt(13f), Color.White, AppTypography.interSemiBold, maxLines = 2)
        LiveText("Live channels drop out sometimes. Try another.", pt(11f), CARD_SUB,
            AppTypography.interSemiBold, Modifier.padding(top = px(3f)), maxLines = 2)
        Row(Modifier.padding(top = px(8f))) {
            CardButton("Try again", focusedButton == 0)
            CardButton("Back to channels", focusedButton == 1)
        }
    }
}

/** 6 -- four hours without a press: "Still watching <channel>?". */
@Composable
internal fun BoxScope.StillWatchingCard(name: String, secondsLeft: Int) {
    PlayerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveText("Still watching ", pt(13f), Color.White, AppTypography.interSemiBold)
            LiveText(name, pt(15f), Color.White, AppTypography.asapCondensedBold)
            LiveText("?", pt(13f), Color.White, AppTypography.interSemiBold)
        }
        Row(Modifier.padding(top = px(6f)), verticalAlignment = Alignment.CenterVertically) {
            PlayerKey("Click", "to keep watching", textSize = pt(12f))
        }
        LiveText("Stopping in ${StillWatching.clock(secondsLeft)} to free your TV slot", pt(11f), CARD_SUB,
            AppTypography.interSemiBold, Modifier.padding(top = px(5f)))
    }
}

@Composable
private fun BoxScope.PlayerCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(px(10f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.78f)
            .background(CARD_BG, shape)
            .border(px(1f), CARD_BORDER, shape)
            .padding(horizontal = px(14f), vertical = px(12f)),
    ) { content() }
}

@Composable
private fun CardButton(label: String, focused: Boolean) {
    val radius = px(6f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = px(3f))
            .outerRing(focused, radius, px(2f))
            .background(if (focused) CHIP_ON else BUTTON_OFF, RoundedCornerShape(radius))
            .padding(horizontal = px(12f), vertical = px(3f)),
    ) {
        LiveText(label, pt(14f), if (focused) Color.White else BUTTON_OFF_TEXT, AppTypography.passionRegular)
    }
}

@Composable
private fun PlayerHint(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(px(7f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(8f), Alignment.CenterHorizontally),
        modifier = modifier
            .height(px(24f))
            .background(HINT_FILL, shape)
            .border(px(1.5f), GOLD, shape)
            .padding(horizontal = px(8f)),
        content = content,
    )
}

@Composable
private fun PlayerKey(key: String, label: String, textSize: TextUnit = pt(11f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(px(15f))
                .background(Color.White, RoundedCornerShape(px(3f)))
                .padding(horizontal = px(4f)),
        ) {
            LiveText(key, pt(11f), KEY_TEXT_COLOR, AppTypography.interSemiBold)
        }
        Spacer(Modifier.width(px(3f)))
        LiveText(label, textSize, Color(0xFFEEEEEE), AppTypography.interSemiBold)
    }
}

@Composable
private fun Separator() {
    LiveText("|", pt(11f), HINT_SEPARATOR, AppTypography.interSemiBold)
}

/** `box-shadow: 0 0 0 Npx #fff` from the mockup: a white ring OUTSIDE the box. */
private fun Modifier.outerRing(show: Boolean, radius: Dp, width: Dp): Modifier = if (!show) this else drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-w, -w),
        size = Size(size.width + 2 * w, size.height + 2 * w),
        cornerRadius = CornerRadius(radius.toPx() + w),
    )
}
