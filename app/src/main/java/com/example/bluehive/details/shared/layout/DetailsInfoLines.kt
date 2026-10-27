package com.example.bluehive.details.shared.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One metadata line under the rating: its text and its font size (the hand-tuned 17 / 16 sp). */
@Immutable
data class DetailsInfoLine(val text: String, val fontSizeSp: Int)

/** The four slots the lines always sat in when all were present, from the top. */
private val INFO_LINE_TOPS = listOf(34.dp, 48.dp, 63.dp, 75.dp)

/** The last slot to the genres line (75 -> 96), kept after whichever line is last. */
private val GENRES_GAP = 21.dp

/** Line [index]'s top: the same slots as before, filled from the first. */
fun detailsInfoLineTop(index: Int): Dp = INFO_LINE_TOPS[index.coerceIn(0, INFO_LINE_TOPS.lastIndex)]

/** Where the genres line goes under [lineCount] lines: 96 dp when all four are there. */
fun detailsGenresTop(lineCount: Int): Dp =
    if (lineCount <= 0) INFO_LINE_TOPS[0] else detailsInfoLineTop(lineCount - 1) + GENRES_GAP

/**
 * The metadata lines both details screens show under the rating, stacked
 * from the top with no gaps: a screen builds only the lines it has a value
 * for, so a missing one leaves no empty slot (the user's call for the movie
 * page, 2026-10-26; the TV page since 2026-10-27). The genres line follows
 * them through DetailsInfo.genresTop = [detailsGenresTop].
 *
 * A BoxScope extension so each line keeps its absolute position in the
 * screen's Box. Each screen builds [lines] inside its own restartable label
 * composable, not in the screen body (v445: a helper folded into the movie
 * screen grew it 328 units).
 */
@Composable
fun BoxScope.DetailsInfoLines(lines: List<DetailsInfoLine>, dongleRegular: FontFamily) {
    lines.forEachIndexed { index, line ->
        Text(
            text = line.text,
            color = Color(0xFF8B8B8B),
            fontSize = line.fontSizeSp.sp,
            fontFamily = dongleRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.25.dp, top = detailsInfoLineTop(index))
        )
    }
}
