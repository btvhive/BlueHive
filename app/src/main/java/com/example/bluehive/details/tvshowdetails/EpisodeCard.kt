package com.example.bluehive.details.tvshowdetails

import androidx.compose.ui.draw.scale
import com.example.bluehive.utilities.WatchedCornerFold
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import com.example.bluehive.utilities.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment

/**
 * Measures [content] at its natural size, reports ZERO height to the parent, and
 * places it directly ABOVE where it would otherwise sit.
 *
 * Exists for the episode air date. That Text sits above the still inside the same
 * Column, so its line height became real layout space and pushed every thumbnail
 * down — the tiles visibly sat lower than before the date was added on
 * 2026-08-16.
 *
 * TWO EARLIER ATTEMPTS AND WHY THEY WERE WRONG, so nobody repeats them:
 *
 *   Leaving it alone — the tiles stay pushed down by however tall the font
 *   happens to render. Nothing in the code says how much, so it cannot be
 *   compensated anywhere else with confidence.
 *
 *   Pinning the Text to a fixed height and lifting the Column by the same
 *   constant — this DELETED THE DATE. dongleRegular's line box at 11.sp is taller
 *   than the 14.dp that was guessed, so the glyphs were clipped away entirely and
 *   the strip rendered blank. Any hardcoded height is the same trap with a
 *   different number: it silently clips the moment a font, size, or fontScale
 *   changes.
 *
 * This measures first and never constrains, so the text renders at whatever size
 * it actually needs, and the parent is told it took none of the column. The
 * result is exact rather than tuned — the still returns to precisely the Y it had
 * before the date existed, for any font metrics, with nothing to keep in sync.
 */
@Composable
internal fun ZeroHeightAbove(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        // Natural height: pass the incoming width through, but no height bound.
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        // Report height 0 — the Column advances not at all for this child.
        layout(width, 0) {
            // Negative Y puts it in the strip above the tile's top edge.
            placeables.forEach { it.place(0, -it.height) }
        }
    }
}

@Composable
fun EpisodeBadge(
    current: Int,
    total: Int,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeText = remember(current, total) { "$current/$total" }

    // ✅ Same "exposure" scale idea used for stills
    val darkScale = 0.40f

    fun darken(color: Color): Color {
        return color.copy(
            red = color.red * darkScale,
            green = color.green * darkScale,
            blue = color.blue * darkScale
        )
    }

    val baseBg = Color(0xFF262629)
    val baseText = Color.White

    val bgColor = if (isFocused) baseBg else darken(baseBg)
    val textColor = if (isFocused) baseText else darken(baseText)

    Box(
        modifier = modifier
            // ⬇️ slightly wider than RatingBadge, same height
            .height(16.5.dp)
            .width(25.dp) // you can tweak if you want more breathing room
            .clip(AppShapes.ratingBadge)
            .border(width = 1.8.dp, color = Color(0xB0000000), shape = AppShapes.ratingBadge)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            fontSize = 7.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}





// ════════════════════════════════════════════════════════════════════════════
//  @Preview — watched corner fold
//
//  Renders the REAL WatchedCornerFold composable (not a copy) on a mock episode
//  still, so tweaking WATCHED_FOLD_* above updates this preview immediately.
//  Open this file in Android Studio and hit "Split" / "Design" to see it.
// ════════════════════════════════════════════════════════════════════════════
@Preview(name = "Watched fold — on card", widthDp = 420, heightDp = 190, showBackground = true, backgroundColor = 0xFF0A0A12)
@Composable
private fun WatchedCornerFoldPreview() {
    val episodeShape = RoundedCornerShape(7.5.dp)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Watched — unfocused card (the hard case: marker must read on a dim still)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF16161E))
                .border(1.1.dp, Color(0xFF505050), episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "6/10",
                color = Color(0xFFD6D6D6),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }

        // Watched — focused card (white border + lighter surface)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF2B2B36))
                .border(1.4.dp, Color.White, episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "7/10",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }
    }
}

@Preview(name = "Watched fold — zoomed", widthDp = 150, heightDp = 150, showBackground = true, backgroundColor = 0xFF16161E)
@Composable
private fun WatchedCornerFoldZoomPreview() {
    // Isolated + enlarged so tick geometry is easy to judge while tuning
    // WATCHED_FOLD_TICK_SIZE / WATCHED_FOLD_TICK_STROKE.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16161E)),
        contentAlignment = Alignment.Center
    ) {
        WatchedCornerFold(modifier = Modifier.scale(3f))
    }
}
