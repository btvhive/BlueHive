package com.example.bluehive.details.shared.layout

import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The genres line, then the overview, which drops to a smaller font when it
 * would not fit: with [lines] (the TV page) step by step until it fits
 * (fitOverviewSize); without, from 18 to 16 sp past five lines.
 *
 * Moved here word for word from both screens in Stage 2 of the details
 * redesign. A BoxScope extension: it positions itself in the screen's Box.
 */
@Composable
fun BoxScope.DetailsOverview(
    genres: String?,
    /** The genres line's top: 96 dp under the four metadata lines. */
    genresTop: Dp = 96.dp,
    overview: String?,
    dongleRegular: FontFamily,
    /**
     * Where the overview's last shown line ends, in the screen's dp, each time
     * it is laid out. The movie screen's peek layout puts its play buttons
     * there.
     */
    onTextBottom: ((Dp) -> Unit)? = null,
    /**
     * The most lines shown, ending in an ellipsis; null leaves it to the
     * box's height. See OverviewLines.
     */
    lines: OverviewLines? = null,
) {
    // ========================================================================================================================================= > // Genres
    if (!genres.isNullOrBlank() && genres != "N/A") {
        Text(
            text = genres,
            color = Color(0xFF8B8B8B),
            fontSize = 16.sp,
            fontFamily = dongleRegular,
            lineHeight = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.25.dp, top = genresTop)
                .width(160.dp)  // Add width constraint so it wraps nicely
        )
    }else {
        Log.d("GenresDebug", "❌ Not rendering genres - value: '$genres'")
    }
    // ========================================================================================================================================= >



    // ========================================================================================================================================= > Overview text display with dynamic font sizing
    if (!overview.isNullOrBlank()) {
        var fontSize by remember { mutableStateOf(18.sp) }
        var lineHeight by remember { mutableStateOf(14.sp) }

        // Measure text to determine if we need smaller font
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // With a line limit (the TV page): the largest size that fits it --
        // see fitOverviewSize. Measured while composing, so the first frame
        // already has the right size.
        val fitted = if (lines == null) null else {
            val style = LocalTextStyle.current
            val widthPx = with(density) { OVERVIEW_WIDTH.roundToPx() }
            remember(overview, lines, widthPx, dongleRegular, style) {
                fitOverviewSize(overview, lines, widthPx, style, dongleRegular, textMeasurer)
            }
        }

        // Without one (the movie page), the two sizes it has always had.
        // NOTE: this measures at 2.75 px per dp, not the box's own density
        // (2.0 on the onn box), so it sees a line ~550 dp wide where the box
        // is 400 and rarely shrinks. Kept as it is: fixing it would change
        // the movie page's text.
        if (fitted == null) LaunchedEffect(overview) {
            val textLayoutResult = textMeasurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(overview),
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    fontFamily = dongleRegular,
                    lineHeight = 14.sp  // Include line height in measurement
                ),
                constraints = androidx.compose.ui.unit.Constraints(
                    maxWidth = (400 * 2.75).toInt(), // dp to px approximation
                    maxHeight = (105.75 * 2.75).toInt() // Add height constraint!
                )
            )

            // Check if text overflows OR doesn't fit in the height
            if (textLayoutResult.lineCount > 5 || textLayoutResult.didOverflowHeight) {
                fontSize = 16.sp
                lineHeight = 13.7.sp
            } else {
                lineHeight = 14.sp
            }
        }

        Text(
            text = overview,
            color = Color(0xFF8B8B8B),
            fontSize = fitted?.size?.fontSize ?: fontSize,
            fontFamily = dongleRegular,
            lineHeight = fitted?.size?.lineHeight ?: lineHeight,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.25.dp, top = 175.dp)
                //.background(Color.Red.copy(alpha = 0.5f))  // Debug background
                .height(108.25.dp)
                .width(OVERVIEW_WIDTH),
            overflow = TextOverflow.Ellipsis,
            maxLines = fitted?.maxLines ?: Int.MAX_VALUE,
            onTextLayout = { layout ->
                if (onTextBottom != null && layout.lineCount > 0) {
                    val bottomPx = minOf(layout.getLineBottom(layout.lineCount - 1), layout.size.height.toFloat())
                    onTextBottom(175.dp + with(density) { bottomPx.toDp() })
                }
            },
        )
    }
    // ========================================================================================================================================= >
}


/** The overview box's width. */
private val OVERVIEW_WIDTH: Dp = 400.dp

/** One overview size: the font and its line height. */
internal data class OverviewSize(val fontSize: TextUnit, val lineHeight: TextUnit)

/**
 * The sizes a line-limited overview steps down through, largest first. 18/14
 * and 16/13.7 are the sizes the page has always used; 17 sits halfway between
 * them, and below 16 the line height keeps 16/13.7's proportion. 14 is the floor: smaller Dongle
 * stops reading from the sofa, so a longer overview ends in an ellipsis there.
 */
internal val OVERVIEW_SIZES = listOf(
    OverviewSize(18.sp, 14.sp),
    OverviewSize(17.sp, 13.85.sp),
    OverviewSize(16.sp, 13.7.sp),
    OverviewSize(15.sp, 12.85.sp),
    OverviewSize(14.sp, 12.sp),
)

/**
 * A line-limited overview's limits: [atFullSize] lines at the first (largest)
 * size, [whenShrunk] at every smaller one, whose lines take less height. The
 * TV page: 5 at 18 sp, so the last line keeps clear of the episodes' air
 * dates, and 6 once it has shrunk (the user's calls, 2026-10-27).
 */
@Immutable
data class OverviewLines(val atFullSize: Int, val whenShrunk: Int)

/** The size an overview is drawn at, and the most lines it shows there. */
internal data class OverviewFit(val size: OverviewSize, val maxLines: Int)

/**
 * The first of [sizes] at which [lineCount] fits its limit in [lines]; the
 * last (smallest) if none does. Pure, so it is unit-tested.
 */
internal fun pickOverviewSize(
    sizes: List<OverviewSize>,
    lines: OverviewLines,
    lineCount: (OverviewSize) -> Int,
): OverviewFit {
    sizes.forEachIndexed { index, size ->
        val limit = if (index == 0) lines.atFullSize else lines.whenShrunk
        if (lineCount(size) <= limit) return OverviewFit(size, limit)
    }
    return OverviewFit(sizes.last(), if (sizes.size == 1) lines.atFullSize else lines.whenShrunk)
}

/**
 * The largest overview size whose text wraps into its [lines] limit in a box
 * [widthPx] wide, measured in the style the Text draws with ([style] is
 * Material's LocalTextStyle, merged as Text merges it), so the count is the
 * one the screen shows. Stops at the first fit, so a short overview costs one
 * measurement.
 */
private fun fitOverviewSize(
    overview: String,
    lines: OverviewLines,
    widthPx: Int,
    style: TextStyle,
    fontFamily: FontFamily,
    measurer: TextMeasurer,
): OverviewFit {
    val text = AnnotatedString(overview)
    return pickOverviewSize(OVERVIEW_SIZES, lines) { size ->
        measurer.measure(
            text = text,
            style = style.merge(TextStyle(fontSize = size.fontSize, fontFamily = fontFamily, lineHeight = size.lineHeight)),
            constraints = Constraints(maxWidth = widthPx),
        ).lineCount
    }
}
