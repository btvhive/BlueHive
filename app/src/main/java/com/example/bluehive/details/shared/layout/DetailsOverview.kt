package com.example.bluehive.details.shared.layout

import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The genres line, then the overview, which drops to a smaller font when it
 * would not fit five lines.
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
     * The most lines shown, ending in an ellipsis; unlimited leaves it to the
     * box's height. The TV page shows 5, so its last line keeps clear of the
     * episodes' air dates (2026-10-27).
     */
    maxLines: Int = Int.MAX_VALUE,
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

        LaunchedEffect(overview) {
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
            fontSize = fontSize,
            fontFamily = dongleRegular,
            lineHeight = lineHeight,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.25.dp, top = 175.dp)
                //.background(Color.Red.copy(alpha = 0.5f))  // Debug background
                .height(108.25.dp)
                .width(400.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
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
