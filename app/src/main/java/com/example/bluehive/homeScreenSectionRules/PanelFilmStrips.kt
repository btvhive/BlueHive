package com.example.bluehive.homeScreenSectionRules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * Film strips along the top and bottom of a home panel's screen: white
 * sprocket holes on black, both running left without stopping, on their own
 * -- the carousel's side swipe happens between them. First tried on Latest
 * Trailers (v567, removed in v568); on Highest Trending as a test (v572), in
 * place of the title and year.
 *
 * Drawn over the screen area ([x], [y], [width], [height]); only the two
 * strips are painted, so the picture between them shows through. The travel
 * is read in the draw phase, so the endless animation never recomposes.
 *
 * [paused] HOLDS THE STRIPS STILL (v590): while the sidebar is open the home
 * page sits under a full-screen blur, and every frame the holes moved made
 * the onn box blur the whole page again -- ~150 ms a frame, about 7 fps,
 * and the sidebar's options crawled. Held, nothing under the blur moves and
 * the blurred page is drawn once. Also held while home is in the background.
 */
@Composable
internal fun PanelFilmStrips(
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    val travel = remember { Animatable(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect   // held where it is
        while (true) {
            // The rest of this loop, then round again from the start.
            val left = ((1f - travel.value) * FILM_STRIP_LOOP_MS).toInt().coerceAtLeast(1)
            travel.animateTo(1f, tween(left, easing = LinearEasing))
            travel.snapTo(0f)
        }
    }
    Canvas(modifier.offset(x, y).size(width, height)) {
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
