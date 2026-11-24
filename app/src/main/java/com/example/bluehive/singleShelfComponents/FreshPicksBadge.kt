package com.example.bluehive.singleShelfComponents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.AppTypography
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Fresh Picks "24" badge beside the row title (the owner's pick
 * 2026-11-21: docs/mockups/fresh-picks-badge, refresh variation 9 "Motion
 * trail"): a refresh arc that fades in from its tail, a solid arrowhead, and
 * "24" tucked under the arc -- a new set every 24 hours. Same white-to-steel
 * fade as the title (SHELF_TITLE_BRUSH: white through the top quarter, then
 * 7C8190).
 *
 * Drawn in 22 x 22 design units, scaled to [size].
 *
 * SIZED AND CENTRED ON THE TV (2026-11-21, owner: "too small", "centred
 * vertically perfect"). Measured on the Fire TV Stick 4K: at 22dp the badge
 * showed only 55 px tall -- as tall as the capitals -- and sat 9 px HIGH,
 * because the arc covers only the top of its circle, so the drawing's visual
 * middle is well above the box's middle. So: 28dp, the drawing is shifted
 * down to centre its VISIBLE extent in the box, and [TITLE_ALIGN] lines that
 * up with the middle of the title's letters rather than its text box.
 */
@Composable
fun FreshPicksBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = remember(density, size) {
        TextStyle(
            fontFamily = AppTypography.passionRegular,
            fontSize = with(density) { (size * (DIGITS / 22f)).toSp() },
        )
    }
    Canvas(modifier.offset(y = TITLE_ALIGN).size(size)) {
        val u = this.size.height / 22f                        // one design unit in px
        val fade = Brush.verticalGradient(
            0f to Color.White, 0.25f to Color.White, 1f to Color(0xFF7C8190),
            startY = 0f, endY = this.size.height,
        )
        translate(top = SHIFT_DOWN * u) {
            val stroke = Stroke(width = STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val box = Offset((11f - R) * u, (11f - R) * u)
            val boxSize = Size(2 * R * u, 2 * R * u)
            // The trail: three arcs, faint to solid, round the centre (11, 11).
            for ((start, sweep, alpha) in listOf(Triple(160f, 70f, 0.3f), Triple(230f, 60f, 0.6f), Triple(290f, 55f, 1f))) {
                drawArc(fade, start, sweep, useCenter = false, topLeft = box, size = boxSize, alpha = alpha, style = stroke)
            }
            // Solid arrowhead at the arc's end (345 deg), pointing along it (75 deg).
            val ex = 11f + R * cos(Math.toRadians(345.0)).toFloat()
            val ey = 11f + R * sin(Math.toRadians(345.0)).toFloat()
            val c = cos(Math.toRadians(75.0)).toFloat()
            val n = sin(Math.toRadians(75.0)).toFloat()
            val s = HEAD
            val head = Path().apply {
                moveTo((ex + c * s) * u, (ey + n * s) * u)
                lineTo((ex - n * s * 0.9f) * u, (ey + c * s * 0.9f) * u)
                lineTo((ex + n * s * 0.9f) * u, (ey - c * s * 0.9f) * u)
                close()
            }
            drawPath(head, fade)
            drawPath(head, fade, style = Stroke(width = 0.5f * u, join = StrokeJoin.Round))
            // "24" tucked under the arc: centred on x = 11, its digits'
            // middle at DIGITS_MID (digits are ~0.72 em tall in Passion One).
            val text = measurer.measure("24", textStyle)
            val baseline = (DIGITS_MID + DIGITS * 0.36f) * u
            drawText(text, brush = fade, topLeft = Offset(11f * u - text.size.width / 2f, baseline - text.firstBaseline))
        }
    }
}

private const val R = 9f              // arc radius, design units
private const val STROKE = 2.3f
private const val HEAD = 3.4f
private const val DIGITS = 10.4f      // "24" font size, design units
private const val DIGITS_MID = 10.8f  // the digits' vertical middle: up inside the arc's hollow

// The visible drawing runs from the arc's top (11 - R - STROKE/2 = 0.85) to
// the arc's tail at 160 deg (11 + R sin 160 + STROKE/2 = 15.2); its middle,
// 8.0, is moved to the box's middle, 11.
private const val SHIFT_DOWN = 3.0f

// The title's letters sit 3.5 px (~0.9dp) above the middle of its text box
// (measured: glyph middle 1126.5 px, box middle ~1130 px at 4 px/dp); -0.4dp is
// what lands the badge on the letters' middle (second TV measurement).
private val TITLE_ALIGN = (-0.4).dp
