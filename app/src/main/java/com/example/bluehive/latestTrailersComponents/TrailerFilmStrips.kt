package com.example.bluehive.latestTrailersComponents

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of each film strip, top and bottom of the TV's screen. */
internal val FILM_STRIP_HEIGHT = 12.dp

private val STRIP_BASE = Color(0xFF060607)
private val STRIP_HOLE = Color(0xFFE4E4DE)
private val HOLE_W = 5.5.dp
private val HOLE_H = 4.dp
private val HOLE_PITCH = 10.dp
private val HOLE_RADIUS = 1.dp

/** One hole pitch of travel; about 11 dp a second. */
internal const val FILM_STRIP_LOOP_MS = 900

/**
 * The film strips along the top and bottom of the Latest Trailers TV's
 * screen (v567, option A from the owner's animated mock-ups): white sprocket
 * holes on black, both strips running left without stopping, on their own --
 * the trailer carousel's side swipe and a playing trailer happen between them.
 *
 * Drawn over the screen area ([x], [y], [width], [height]); only the two
 * strips are painted, so the picture between them shows through. The travel
 * is read in the draw phase, so the endless animation never recomposes.
 */
@Composable
internal fun TrailerFilmStrips(x: Dp, y: Dp, width: Dp, height: Dp) {
    val travel = rememberInfiniteTransition(label = "filmStrips").animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(FILM_STRIP_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label         = "filmStripTravel"
    )
    Canvas(Modifier.offset(x, y).size(width, height)) {
        val stripH = FILM_STRIP_HEIGHT.toPx()
        val pitch = HOLE_PITCH.toPx()
        val holeW = HOLE_W.toPx()
        val holeH = HOLE_H.toPx()
        val corner = CornerRadius(HOLE_RADIUS.toPx())
        val shift = travel.value * pitch
        for (top in floatArrayOf(0f, size.height - stripH)) {
            drawRect(STRIP_BASE, topLeft = Offset(0f, top), size = Size(size.width, stripH))
            clipRect(0f, top, size.width, top + stripH) {
                var cx = pitch / 2 - shift
                while (cx - holeW / 2 < size.width) {
                    drawRoundRect(
                        color        = STRIP_HOLE,
                        topLeft      = Offset(cx - holeW / 2, top + (stripH - holeH) / 2),
                        size         = Size(holeW, holeH),
                        cornerRadius = corner
                    )
                    cx += pitch
                }
            }
        }
    }
}
