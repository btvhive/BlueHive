package com.example.bluehive.latestTrailersComponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Idle and focused: the set is on standby. */
internal val STATUS_LIGHT_RED = Color(0xFFFF1010)

/** Select mode: neon green, not mint (owner, 2026-10-31). */
internal val STATUS_LIGHT_GREEN = Color(0xFF39FF14)

internal val STATUS_LIGHT_RADIUS = 2.6.dp
private val STATUS_GLOW_IDLE = 6.dp
private val STATUS_GLOW_SELECTED = 10.dp
private const val STATUS_CHANGE_MS = 250

/**
 * The status light at the right end of the Latest Trailers TV's chin (v566),
 * after the four buttons baked into trailer_frame. Red while the panel is idle
 * or only focused; neon green, with a wider glow, while it is in select mode.
 *
 * It is drawn here rather than in the frame art so it can change. The frame
 * art has a dark socket at the same centre ([centerX], [centerY], printed by
 * tools/home-art/make_trailer_frame.py). Colour and glow are read in the draw
 * phase, so a change never recomposes the panel.
 */
@Composable
internal fun TrailerStatusLight(selected: Boolean, centerX: Dp, centerY: Dp) {
    val color by animateColorAsState(
        targetValue   = if (selected) STATUS_LIGHT_GREEN else STATUS_LIGHT_RED,
        animationSpec = tween(STATUS_CHANGE_MS),
        label         = "trailerStatusLight"
    )
    val glow by animateDpAsState(
        targetValue   = if (selected) STATUS_GLOW_SELECTED else STATUS_GLOW_IDLE,
        animationSpec = tween(STATUS_CHANGE_MS),
        label         = "trailerStatusGlow"
    )
    Canvas(
        Modifier
            .offset(centerX - STATUS_GLOW_SELECTED, centerY - STATUS_GLOW_SELECTED)
            .size(STATUS_GLOW_SELECTED * 2)
    ) {
        val c = color
        val g = glow.toPx()
        drawCircle(
            brush  = Brush.radialGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0.22f), Color.Transparent), center, g),
            radius = g
        )
        // The lens: the colour, a touch paler at the centre for green so it reads as lit.
        drawCircle(lerp(c, Color.White, if (c.green > c.red) 0.25f else 0.06f), radius = STATUS_LIGHT_RADIUS.toPx())
    }
}
