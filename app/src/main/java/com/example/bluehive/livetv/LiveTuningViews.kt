package com.example.bluehive.livetv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bluehive.R
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.webview.BeeLogo
import com.example.bluehive.webview.GLOBE_RADIUS
import com.example.bluehive.webview.GlobeLoader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The player's tuning screen, design B of live_tv_tuning_globe_designs_abc
// (2026-11-01), in place of the turning ring. LiveTuning.kt has its words.

/** GlobeLoader's own size: the globe and its halo rings. */
private val GLOBE_BOX = GLOBE_RADIUS * 2 + 80.dp

private val HONEYCOMB = Color(0xFF3355FF).copy(alpha = 0.045f)
private val BAR_FILL = Brush.horizontalGradient(listOf(Color(0xFF0044FF), Color(0xFFAA33FF)))

/**
 * 1 -- tuning: the movie loader's globe (GlobeLoader, unchanged, scaled to the
 * mockup's 62 px) over its honeycomb, the channel's card under it, and how far
 * the tune has got -- a line, a bar and three dots, as the movie loader shows
 * its stages. [note] (a backup feed, a retry) takes the loader's amber line
 * under the dots while this screen is up; once the picture is on, the player's
 * notice bar shows it.
 *
 * Only the globe and the active dot move, each in its own layer (the globe's
 * graphicsLayer, the dot's alpha), so the card is not redrawn while it turns.
 */
@Composable
internal fun TuningOverlay(channel: LivePlayerActivity.Channel, stage: Int, note: String?) {
    val nowS = System.currentTimeMillis() / 1000
    val res = LocalContext.current.resources
    val bee = remember { BeeLogo.bitmap ?: runCatching { ImageBitmap.imageResource(res, R.drawable.blue_bee) }.getOrNull() }
    val glow = rememberInfiniteTransition(label = "tuning")
        .animateFloat(0.45f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val progress by animateFloatAsState(
        stage / LiveTuning.STAGES.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "progress",
    )
    Box(Modifier.fillMaxSize().background(TUNING_BG)) {
        Honeycomb()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = TUNE_GLOBE_Y - GLOBE_BOX / 2)
                .height(GLOBE_BOX)
                .graphicsLayer {
                    scaleX = TUNE_GLOBE_SCALE
                    scaleY = TUNE_GLOBE_SCALE
                },
        ) { GlobeLoader(bee) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(TUNE_TAG_GAP),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = TUNE_TAGS_TOP),
        ) {
            LiveTuning.lead(channel.number, channel.league).takeIf { it.isNotEmpty() }
                ?.let { CardTag(it, TAG_BG, TAG_TEXT_COLOR) }
            if (channel.row.isNotEmpty()) CardTag(channel.row, Color(channel.rowColor), Color(LiveTuning.ink(channel.rowColor)))
        }
        LiveText(
            channel.name, TUNE_NAME_TEXT, Color.White, AppTypography.asapCondensedBold,
            Modifier.align(Alignment.TopCenter).offset(y = TUNE_NAME_TOP).widthIn(max = TUNE_NAME_MAX),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = TUNE_META_TOP),
        ) {
            if (LiveTuning.liveDot(channel.start, nowS)) {
                Dot(LIVE_DOT)
                Spacer(Modifier.width(TUNE_DOT_GAP))
            }
            LiveText(
                LiveTuning.meta(channel.quality, channel.row, channel.start, channel.sport, channel.league, nowS),
                TUNE_LINE_TEXT, PANEL_META_COLOR, AppTypography.interSemiBold,
            )
        }
        AnimatedContent(
            targetState = LiveTuning.stageLine(stage),
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "stage",
            modifier = Modifier.align(Alignment.TopCenter).offset(y = TUNE_DETAIL_TOP),
        ) { LiveText(it, TUNE_LINE_TEXT, TUNE_DETAIL, AppTypography.interSemiBold) }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = TUNE_BAR_TOP)
                .size(TUNE_BAR_WIDTH, TUNE_BAR_HEIGHT)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(BAR_FILL))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TUNE_DOTS_GAP),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = TUNE_DOTS_TOP).height(TUNE_DOTS_HEIGHT),
        ) {
            repeat(LiveTuning.STAGES) { i ->
                val active = i == stage - 1
                Box(
                    Modifier
                        .size(if (active) 7.dp else 5.dp)
                        .graphicsLayer { if (active) alpha = glow.value }
                        .background(if (active) TUNE_DOT_ACTIVE else if (i < stage) TUNE_DOT_DONE else TUNE_DOT_TODO, CircleShape)
                )
            }
        }
        if (note != null) {
            Text(
                note,
                color = TUNE_NOTE,
                fontSize = TUNE_LINE_TEXT,
                fontFamily = AppTypography.interSemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = TUNE_NOTE_TOP).widthIn(max = TUNE_NOTE_MAX),
            )
        }
    }
}

/** A tag over the name: the number (or a game's league), then the row in its own colour. */
@Composable
private fun CardTag(text: String, fill: Color, ink: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(TUNE_TAG_HEIGHT)
            .background(fill, RoundedCornerShape(TAG_RADIUS))
            .padding(horizontal = TUNE_TAG_PAD_H),
    ) {
        LiveText(text, TUNE_LINE_TEXT, ink, AppTypography.interSemiBold)
    }
}

/** The movie loader's faint honeycomb, hex by hex as ExtractionLoadingOverlay draws it. */
@Composable
private fun Honeycomb() {
    Canvas(Modifier.fillMaxSize()) {
        val hex = 34.dp.toPx()
        val w = hex * sqrt(3f)
        val h = hex * 2f
        val stroke = Stroke(0.6.dp.toPx())
        for (row in -1..(size.height / (h * 0.75f)).toInt() + 2) {
            for (col in -1..(size.width / w).toInt() + 2) {
                val cx = col * w + if (row % 2 == 0) 0f else w / 2f
                val cy = row * h * 0.75f
                val path = Path()
                for (i in 0 until 6) {
                    val a = Math.toRadians(60.0 * i - 30.0)
                    val x = cx + hex * cos(a).toFloat()
                    val y = cy + hex * sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, HONEYCOMB, style = stroke)
            }
        }
    }
}
