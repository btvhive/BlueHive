package com.example.bluehive.utilities

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Modifier.offset(x, y) with [y] read while placing, not while composing: an
 * animated or late-arriving y moves the node without recomposing what holds
 * it (the v450 lesson -- an animation read in a body rebuilds that body every
 * frame). The same pixels as offset(x, y): see [offsetPx].
 */
fun Modifier.offsetWithY(x: Dp, y: () -> Dp): Modifier = offset { offsetPx(this, x, y()) }

/**
 * Where Modifier.offset(x, y) places a node: each axis rounded to whole
 * pixels on its own (OffsetNode's roundToPx). Pure, so it is tested.
 */
internal fun offsetPx(density: Density, x: Dp, y: Dp): IntOffset =
    with(density) { IntOffset(x.roundToPx(), y.roundToPx()) }
